package com.uplink.app

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

object NxArchiveTools {
    val names = setOf("zip", "unzip")

    private val say: NxSay = { key, args -> tr(key, *args) }

    fun run(target: AgentTarget, app: Context, action: String, rest: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val words = consoleTokens(rest)
        try {
            return if (action == "zip") zip(target, app, words, stopped, onOut) else unzip(target, app, words, stopped, onOut)
        } catch (e: NxZipUnsafe) {
            return ConsoleOut(e.message.orEmpty(), false)
        } catch (e: NxCancelled) {
            throw TaskStopped()
        }
    }

    private fun vpsOf(target: AgentTarget): Vps? = (target.source as? VpsFs)?.vps

    private fun serverTool(app: Context, tool: String): String? {
        val tools = NxEnvReport.vpsTools(app) ?: return null
        return if (tools[tool] == true) null else tr("{0} is not installed on this server, nx {0} is not available here", tool)
    }

    private fun script(vps: Vps, text: String, stopped: () -> Boolean, run: TaskRun): Int {
        val out = StringBuilder()
        vps.stream(text + "\necho\necho __NXCODE__\$?", stopped) { piece ->
            out.append(piece)
            run.detail = out.toString().lines().lastOrNull { it.isNotBlank() && !it.startsWith("__NXCODE__") }.orEmpty().take(80)
        }
        if (stopped()) throw TaskStopped()
        val code = Regex("__NXCODE__(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        if (code > 1) throw Exception(out.toString().substringBefore("__NXCODE__").trim().takeLast(400).ifEmpty { tr("Exit code {0}", code) })
        return code
    }

    private fun zip(target: AgentTarget, app: Context, words: List<String>, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        if (words.size != 2) throw NxChangeError(tr("nx zip needs a source and a target.zip"))
        val fs = target.source
        val src = fixPath(words[0], target.cwd)
        val out = fixPath(words[1], target.cwd)
        if (!out.lowercase().endsWith(".zip")) throw NxChangeError(tr("The target must end with .zip"))
        val vps = vpsOf(target)
        if (vps == null && !isLocal(fs)) return ConsoleOut(tr("nx zip works on phone storage and on a server, not on this storage"), false)
        if (vps != null) serverTool(app, "zip")?.let { return ConsoleOut(it, false) }
        when (fs.kind(src)) {
            0 -> throw NxChangeError(tr("{0}: not found", src))
        }
        if (DeletePolicy.canonical(out).startsWith(DeletePolicy.canonical(src).trimEnd('/') + "/")) {
            return ConsoleOut(tr("The zip cannot be created inside the folder it packs: {0}", out), false)
        }
        val verdict = agentGuard(fs, target.base, AgentOp.Copy, src, out, "", true)
        verdict.first?.let { return ConsoleOut(tr("{0}: {1}", src, it), false) }
        val have = fs.kind(out)
        if (have == 2) return ConsoleOut(tr("{0} is an existing folder, pick another name", out), false)
        val replaces = have == 1
        val line = "nx zip $src $out"
        if (replaces && !AgentConfirm.ask(app, target.place, line, true, listOf(NxTarget(out, false)), stopped, onOut)) {
            return ConsoleOut(tr("Denied by you: {0}", line), false)
        }
        val name = src.substringAfterLast('/')
        val staged = "$out.nxnew"
        val run = NxTask.await(TaskKind.Zip, tr("Zip {0}", out.substringAfterLast('/')), stopped) { r ->
            if (fs.kind(staged) != 0) fs.delete(staged)
            if (vps != null) {
                r.detail = tr("Running on the server")
                val cmd = "cd ${sq(parentOf(src))} && zip -rq ${sq(staged)} ${sq(name)} && mv -f ${sq(staged)} ${sq(out)}"
                script(vps, cmd, stopped, r)
            } else {
                r.detail = tr("Scanning")
                val scan = TreeScan.scan(fs, src, emptyList())
                r.total = scan.files
                r.totalBytes = scan.bytes
                val z = ZipFs(File(staged), "", true, app.cacheDir)
                z.beginBatch()
                try {
                    copyTracked(fs, src, z, "/$name", emptyList(), r, false)
                } catch (e: Throwable) {
                    z.discard()
                    throw e
                } finally {
                    z.endBatch()
                }
                try {
                    if (replaces) NxChangeTools.replaceFile(fs, staged, out) else fs.rename(staged, out)
                } catch (e: Throwable) {
                    try { fs.delete(staged) } catch (_: Exception) { }
                    throw e
                }
            }
        }
        val size = try { fs.meta(out)?.size ?: 0L } catch (_: Exception) { 0L }
        val detail = if (vps != null) NxFormat.size(size) else tr("{0} files, {1}", run.done, NxFormat.size(size))
        return ConsoleOut(tr("Zipped {0} into {1} ({2})", src, out, detail), true)
    }

    private fun defaultTarget(zip: String): String {
        val base = zip.substringAfterLast('/').substringBeforeLast('.', "").ifEmpty { "extracted" }
        return NxFormat.join(parentOf(zip), base)
    }

    private fun unzip(target: AgentTarget, app: Context, words: List<String>, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        if (words.isEmpty() || words.size > 2) throw NxChangeError(tr("nx unzip needs a zip and optionally a target folder"))
        val fs = target.source
        val zip = fixPath(words[0], target.cwd)
        val dest = if (words.size > 1) fixPath(words[1], target.cwd) else defaultTarget(zip)
        val vps = vpsOf(target)
        if (vps == null && !isLocal(fs)) return ConsoleOut(tr("nx unzip works on phone storage and on a server, not on this storage"), false)
        if (vps != null) serverTool(app, "unzip")?.let { return ConsoleOut(it, false) }
        when (fs.kind(zip)) {
            0 -> throw NxChangeError(tr("{0}: not found", zip))
            2 -> throw NxChangeError(tr("{0}: is a folder", zip))
        }
        if (!zip.lowercase().endsWith(".zip")) throw NxChangeError(tr("Not a zip file"))
        if (fs.kind(dest) == 1) return ConsoleOut(tr("{0} is a file, pick a folder", dest), false)
        val verdict = agentGuard(fs, target.base, AgentOp.Copy, zip, dest, "", true)
        verdict.first?.let { return ConsoleOut(tr("{0}: {1}", zip, it), false) }
        return if (vps != null) unzipServer(vps, zip, dest, stopped) else unzipLocal(target, app, zip, dest, stopped, onOut)
    }

    private fun unzipServer(vps: Vps, zip: String, dest: String, stopped: () -> Boolean): ConsoleOut {
        val limits = NxUnzipLimits.DEFAULT
        val listing = vps.exec("unzip -Z -1 ${sq(zip)} 2>&1")
        val totals = NxUnzip.parseTotals(vps.exec("unzip -Z -t ${sq(zip)} 2>&1"))
        val entries = NxUnzip.entriesFromNames(listing)
        if (entries.isEmpty()) return ConsoleOut(tr("The zip has no entries or could not be read"), false)
        for (e in entries) NxZipPath.clean(e.name, say)
        NxUnzip.check(totals?.first ?: entries.size, totals?.second ?: 0L, limits, say)
        val run = NxTask.await(TaskKind.Unzip, tr("Unzip {0}", zip.substringAfterLast('/')), stopped) { r ->
            r.detail = tr("Running on the server")
            r.total = entries.count { !it.dir }
            script(vps, "mkdir -p ${sq(dest)} && unzip -n -q ${sq(zip)} -d ${sq(dest)}", stopped, r)
            r.done = r.total
        }
        return ConsoleOut(tr("Unzipped {0} files into {1}, existing files were kept", run.total, dest), true)
    }

    private fun unzipLocal(target: AgentTarget, app: Context, zip: String, dest: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val fs = target.source
        val limits = NxUnzipLimits.DEFAULT
        val file = File(zip)
        val entries = try {
            ZipFile(file).use { zf -> zf.entries().asSequence().map { NxZipEntry(it.name, it.isDirectory, it.size) }.toList() }
        } catch (e: java.util.zip.ZipException) {
            return ConsoleOut(tr("Cannot open archive: {0}", e.message ?: ""), false)
        }
        val plan = NxUnzip.plan(entries, dest, limits, { fs.kind(it) }, say)
        if (plan.replaces.isNotEmpty()) {
            val line = "nx unzip $zip $dest"
            val named = plan.replaces.map { NxTarget(it, false) }
            if (!AgentConfirm.ask(app, target.place, line, true, named, stopped, onOut)) return ConsoleOut(tr("Denied by you: {0}", line), false)
        }
        val run = NxTask.await(TaskKind.Unzip, tr("Unzip {0}", file.name), stopped) { r ->
            r.total = plan.items.count { !it.dir }
            r.totalBytes = plan.totalBytes
            fs.mkdirs(dest)
            val meter = NxUnzipMeter(limits, say)
            ZipFile(file).use { zf ->
                val buf = ByteArray(65536)
                for (item in plan.items) {
                    if (r.stop) throw TaskStopped()
                    val full = NxFormat.join(dest, item.rel)
                    if (item.dir) {
                        fs.mkdirs(full)
                        continue
                    }
                    val entry = zf.getEntry(item.entryName) ?: throw Exception(tr("Entry missing in zip: {0}", item.rel))
                    fs.mkdirs(parentOf(full))
                    r.detail = item.rel.substringAfterLast('/')
                    zf.getInputStream(entry).use { input ->
                        fs.create(full).use { output ->
                            while (true) {
                                if (r.stop) throw TaskStopped()
                                val n = input.read(buf)
                                if (n < 0) break
                                meter.add(n)
                                output.write(buf, 0, n)
                                r.bytes += n
                                TaskBoard.publish()
                            }
                        }
                    }
                    r.done++
                }
            }
        }
        return ConsoleOut(tr("Unzipped {0} files into {1} ({2})", run.done, dest, NxFormat.size(run.bytes)), true)
    }
}
