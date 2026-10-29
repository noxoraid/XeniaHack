package com.uplink.app

import android.content.Context

object NxChangeTools {
    val names = setOf("write", "edit", "mkdir", "cp", "mv", "rm")

    private const val INLINE_LIMIT = 20_000
    private const val EDIT_CAP = 1_000_000
    private const val MAX_NAMES = 50
    private val say: NxSay = { key, args -> tr(key, *args) }

    fun run(target: AgentTarget, app: Context, action: String, rest: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        try {
            return when (action) {
                "write" -> write(target, app, rest, stopped, onOut)
                "edit" -> edit(target, rest)
                "mkdir" -> mkdir(target, consoleTokens(rest))
                "cp", "mv" -> transfer(target, app, action, consoleTokens(rest), stopped, onOut)
                "rm" -> remove(target, app, consoleTokens(rest), stopped, onOut)
                else -> throw Exception(tr("unknown action"))
            }
        } catch (e: NxCancelled) {
            throw TaskStopped()
        }
    }

    private fun missing(): Nothing = throw NxChangeError(tr("Missing argument"))

    private fun split(words: List<String>): Pair<Set<Char>, List<String>> {
        val flagWords = words.filter { it.length > 1 && it[0] == '-' }
        val flags = HashSet<Char>()
        for (w in flagWords) {
            for (c in w.drop(1)) {
                if (c != 'r' && c != 'R' && c != 'f') throw NxChangeError(tr("unknown flag: {0}", w))
                flags.add(c)
            }
        }
        return flags to words.filter { it !in flagWords }
    }

    private fun denied(line: String) = ConsoleOut(tr("Denied by you: {0}", line), false)

    private fun write(target: AgentTarget, app: Context, rest: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val args = NxWriteParse.parse(rest, INLINE_LIMIT, say)
        val fs = target.source
        val path = fixPath(args.path, target.cwd)
        val verdict = agentGuard(fs, target.base, AgentOp.CreateFile, path, "", args.content)
        verdict.first?.let { return ConsoleOut(tr("{0}: {1}", args.path, it), false) }
        if (verdict.second && !AgentConfirm.ask(app, target.place, "nx write $path", true, listOf(NxTarget(path, false)), stopped, onOut)) {
            return denied("nx write $path")
        }
        fs.mkdirs(parentOf(path))
        val bytes = args.content.toByteArray(Charsets.UTF_8)
        fs.create(path).use { it.write(bytes) }
        return ConsoleOut(tr("Wrote {0} ({1} lines, {2})", path, args.content.count { it == '\n' } + 1, NxFormat.size(bytes.size.toLong())), true)
    }

    private fun edit(target: AgentTarget, rest: String): ConsoleOut {
        val words = consoleTokens(rest)
        if (words.size != 3) throw NxChangeError(tr("nx edit needs a path, the old text and the new text, quote each one"))
        val fs = target.source
        val path = fixPath(words[0], target.cwd)
        when (fs.kind(path)) {
            0 -> throw NxChangeError(tr("{0}: not found", path))
            2 -> throw NxChangeError(tr("{0}: is a folder", path))
        }
        val old = NxEscape.decode(words[1])
        val fresh = NxEscape.decode(words[2])
        val raw = fs.open(path).use { s ->
            val buf = ByteArray(EDIT_CAP + 1)
            var n = 0
            while (n < buf.size) {
                val r = s.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            if (n > EDIT_CAP) throw NxChangeError(tr("{0} is larger than 1 MB, nx edit works on smaller files", path))
            buf.copyOf(n)
        }
        if (TextGrep.binary(raw, raw.size)) throw NxChangeError(tr("Binary files cannot be edited"))
        val text = String(raw, Charsets.UTF_8)
        if (!text.toByteArray(Charsets.UTF_8).contentEquals(raw)) throw NxChangeError(tr("{0} is not valid UTF-8, nx edit would damage it", path))
        val result = NxEdit.apply(text, old, fresh, path, say)
        val guard = agentGuard(fs, target.base, AgentOp.CreateFile, path, "", result.text)
        guard.first?.let { return ConsoleOut(tr("{0}: {1}", words[0], it), false) }
        fs.create(path).use { it.write(result.text.toByteArray(Charsets.UTF_8)) }
        return ConsoleOut(tr("Edited {0} at line {1}", path, result.line), true)
    }

    private fun mkdir(target: AgentTarget, words: List<String>): ConsoleOut {
        if (words.isEmpty()) missing()
        if (words.size > 20) throw NxChangeError(tr("At most {0} names per call", 20))
        val fs = target.source
        val report = ArrayList<String>()
        var failed = false
        for (w in words) {
            val path = fixPath(w, target.cwd)
            when (fs.kind(path)) {
                2 -> report.add(tr("{0}: folder already exists", path))
                1 -> {
                    failed = true
                    report.add(tr("{0}: a file with this name exists", path))
                }
                else -> {
                    val verdict = agentGuard(fs, target.base, AgentOp.MakeFolder, path, "", "")
                    if (verdict.first != null) {
                        failed = true
                        report.add(tr("{0}: {1}", path, verdict.first))
                    } else {
                        fs.mkdirs(path)
                        report.add(tr("Created folder {0}", path))
                    }
                }
            }
        }
        return ConsoleOut(report.joinToString("\n"), !failed)
    }

    fun replaceFile(fs: FsBackend, from: String, to: String) {
        val backup = "$to.nxold"
        if (fs.kind(backup) != 0) fs.delete(backup)
        fs.rename(to, backup)
        try {
            fs.rename(from, to)
        } catch (e: Exception) {
            fs.rename(backup, to)
            throw e
        }
        fs.delete(backup)
    }

    private fun transfer(
        target: AgentTarget, app: Context, action: String, words: List<String>, stopped: () -> Boolean, onOut: (String) -> Unit,
    ): ConsoleOut {
        val (_, pos) = split(words)
        if (pos.size != 2) throw NxChangeError(tr("nx {0} needs a source and a target", action))
        val fs = target.source
        val src = fixPath(pos[0], target.cwd)
        val dst = fixPath(pos[1], target.cwd)
        val move = action == "mv"
        if (move) DeletePolicy.refusal(src)?.let { return ConsoleOut(tr("{0}: {1}", src, refusalText(it, src)), false) }
        val plan = when (val found = NxTransfer.resolve(src, fs.kind(src), dst, fs.kind(dst), { fs.kind(it) }, say)) {
            is NxMove.Refuse -> return ConsoleOut(found.reason, false)
            is NxMove.Go -> found
        }
        val verdict = agentGuard(fs, target.base, if (move) AgentOp.Rename else AgentOp.Copy, src, plan.to, "", true)
        verdict.first?.let { return ConsoleOut(tr("{0}: {1}", src, it), false) }
        val line = "nx $action $src $dst"
        if (plan.replaces && DeletePolicy.needsConfirm(action, true)) {
            if (!AgentConfirm.ask(app, target.place, line, true, listOf(NxTarget(plan.to, false)), stopped, onOut)) return denied(line)
        }
        if (move) {
            if (plan.replaces) {
                replaceFile(fs, src, plan.to)
            } else {
                fs.mkdirs(parentOf(plan.to))
                fs.rename(src, plan.to)
            }
            return ConsoleOut(tr("Moved {0} to {1}", src, plan.to), true)
        }
        val name = src.substringAfterLast('/')
        val run = NxTask.await(TaskKind.Copy, tr("Copy {0}", name), stopped) { r ->
            r.detail = tr("Scanning")
            r.total = countFiles(fs, src)
            val staged = if (plan.replaces) plan.to + ".nxnew" else plan.to
            if (fs.kind(staged) != 0) fs.delete(staged)
            try {
                copyTracked(fs, src, fs, staged, emptyList(), r, false)
                if (plan.replaces) replaceFile(fs, staged, plan.to)
            } catch (e: Throwable) {
                if (plan.replaces) try { fs.delete(staged) } catch (_: Exception) { }
                throw e
            }
        }
        return ConsoleOut(tr("Copied {0} to {1} ({2} files, {3})", src, plan.to, run.done, NxFormat.size(run.bytes)), true)
    }

    private fun remove(target: AgentTarget, app: Context, words: List<String>, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val (flags, pos) = split(words)
        if (pos.isEmpty()) missing()
        if (pos.size > MAX_NAMES) throw NxChangeError(tr("At most {0} names per call", MAX_NAMES))
        for (w in pos) if (w.any { it in "*?[" }) throw NxChangeError(tr("Wildcards are not supported, list each name: {0}", w))
        val fs = target.source
        val recursive = 'r' in flags || 'R' in flags
        val doomed = ArrayList<NxTarget>()
        for (w in pos) {
            val path = fixPath(w, target.cwd)
            DeletePolicy.refusal(path)?.let { return ConsoleOut(tr("{0}: {1}", path, refusalText(it, path)), false) }
            val kind = fs.kind(path)
            if (kind == 0) throw NxChangeError(tr("{0}: not found", path))
            if (kind == 2 && !recursive) throw NxChangeError(tr("{0} is a folder, use -r", path))
            val verdict = agentGuard(fs, target.base, AgentOp.Delete, path, "", "")
            verdict.first?.let { return ConsoleOut(tr("{0}: {1}", path, it), false) }
            doomed.add(NxTarget(path, kind == 2))
        }
        val unique = NxConfirm.distinct(doomed)
        val line = "nx rm " + unique.joinToString(" ") { it.path }
        if (!AgentConfirm.ask(app, target.place, line, false, unique, stopped, onOut)) return denied(line)
        val deleteAll = {
            for (t in unique) {
                if (stopped()) throw TaskStopped()
                fs.delete(t.path)
            }
        }
        if (unique.any { it.folder }) {
            val title = if (unique.size == 1) tr("Delete 1 item") else tr("Delete {0} items", unique.size)
            NxTask.await(TaskKind.Delete, title, stopped) { deleteAll() }
        } else {
            deleteAll()
        }
        return ConsoleOut(unique.joinToString("\n") { tr("Deleted {0}", it.path) }, true)
    }
}
