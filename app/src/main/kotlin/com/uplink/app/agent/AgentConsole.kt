package com.uplink.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

class ConsoleOut(val text: String, val ok: Boolean)

class ConsoleStep(val op: AgentOp, val path: String, val to: String, val blocked: String?)

fun consoleTokens(line: String): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var quote = '\u0000'
    var quoted = false
    for (c in line.trim()) {
        when {
            quote != '\u0000' -> if (c == quote) quote = '\u0000' else cur.append(c)
            c == '"' || c == '\'' -> {
                quote = c
                quoted = true
            }
            c.isWhitespace() -> if (cur.isNotEmpty() || quoted) {
                out.add(cur.toString())
                cur.setLength(0)
                quoted = false
            }
            else -> cur.append(c)
        }
    }
    if (cur.isNotEmpty() || quoted) out.add(cur.toString())
    return out
}

private class Argv(tokens: List<String>, valued: Set<String>) {
    val flags = HashSet<Char>()
    val options = HashMap<String, String>()
    val words = ArrayList<String>()

    init {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t in valued && i + 1 < tokens.size) {
                options[t.trimStart('-')] = tokens[i + 1]
                i += 2
                continue
            }
            if (t.length > 1 && t[0] == '-' && t.substring(1).all { it.isDigit() }) {
                options["n"] = t.substring(1)
            } else if (t.length > 1 && t[0] == '-' && !t.startsWith("--")) {
                for (c in t.substring(1)) flags.add(c)
            } else {
                words.add(t)
            }
            i++
        }
    }
}

private val skipDirs = setOf(".git", "node_modules")
private val regexMarks = charArrayOf('.', '*', '+', '?', '[', ']', '(', ')', '{', '}', '|', '^', '$', '\\')

private const val OUT_LIMIT = 60_000
private const val LIST_LIMIT = 400
private const val FIND_LIMIT = 500
private const val GREP_LIMIT = 300
private const val GREP_FILES = 4000
private const val GREP_READ = 2_097_152
private const val CAT_READ = 262_144

class ConsoleSession(
    private val fs: FsBackend,
    private val app: Context,
    private val home: String,
    var cwd: String,
) {
    companion object {
        val reads = setOf("pwd", "cd", "ls", "cat", "head", "tail", "wc", "stat", "du", "tree", "find", "grep", "play", "show")
        val writes = setOf("mkdir", "touch", "cp", "mv", "rm", "rmdir", "zip", "unzip")

        fun nameOf(line: String): String = consoleTokens(line).firstOrNull() ?: ""
    }

    private val main = Handler(Looper.getMainLooper())

    private fun abs(p: String): String = fixPath(p, cwd)

    private fun shown(p: String): String = if (cwd != "/" && p.startsWith("$cwd/")) p.substring(cwd.length + 1) else p

    private fun clip(text: String): String =
        if (text.length > OUT_LIMIT) text.substring(0, OUT_LIMIT) + "\n" + tr("… truncated") else text

    fun run(line: String, stopped: () -> Boolean, onMedia: (AgentMedia) -> Unit): ConsoleOut {
        val t = consoleTokens(line)
        if (t.isEmpty()) return ConsoleOut("", true)
        val rest = t.drop(1)
        return try {
            val text = when (t[0]) {
                "pwd" -> cwd
                "cd" -> changeDir(rest)
                "ls" -> listDir(Argv(rest, emptySet()))
                "cat" -> catFiles(Argv(rest, emptySet()))
                "head" -> slice(Argv(rest, setOf("-n")), false)
                "tail" -> slice(Argv(rest, setOf("-n")), true)
                "wc" -> count(Argv(rest, emptySet()), stopped)
                "stat" -> statOf(Argv(rest, emptySet()))
                "du" -> usage(Argv(rest, emptySet()))
                "tree" -> tree(Argv(rest, setOf("-L")), stopped)
                "find" -> find(Argv(rest, setOf("-name", "-iname", "-type", "-maxdepth")), stopped)
                "grep" -> grep(Argv(rest, emptySet()), stopped)
                "play" -> play(Argv(rest, emptySet()), stopped, onMedia)
                "show" -> show(Argv(rest, emptySet()), stopped, onMedia)
                in writes -> return ConsoleOut(tr("Needs approval: {0}", line), false)
                else -> return ConsoleOut(tr("Unknown command: {0}", t[0]), false)
            }
            ConsoleOut(clip(text), true)
        } catch (e: TaskStopped) {
            throw e
        } catch (e: Exception) {
            ConsoleOut(e.message?.ifBlank { null } ?: e.toString(), false)
        }
    }

    fun steps(line: String): List<ConsoleStep> {
        val t = consoleTokens(line)
        if (t.isEmpty()) return emptyList()
        val a = Argv(t.drop(1), emptySet())
        return when (t[0]) {
            "mkdir" -> a.words.map { ConsoleStep(AgentOp.MakeFolder, abs(it), "", null) }
            "touch" -> a.words.map { abs(it) }.filter { fs.kind(it) == 0 }.map { ConsoleStep(AgentOp.CreateFile, it, "", null) }
            "rm" -> a.words.map { w ->
                val p = abs(w)
                val folderNeedsFlag = fs.kind(p) == 2 && 'r' !in a.flags && 'R' !in a.flags
                ConsoleStep(AgentOp.Delete, p, "", if (folderNeedsFlag) tr("{0} is a folder, use -r", p) else null)
            }
            "rmdir" -> a.words.map { w ->
                val p = abs(w)
                val why = when {
                    fs.kind(p) != 2 -> tr("{0} is not a folder", p)
                    try { fs.list(p).isNotEmpty() } catch (_: Exception) { true } -> tr("{0} is not empty", p)
                    else -> null
                }
                ConsoleStep(AgentOp.Delete, p, "", why)
            }
            "cp", "mv" -> transfers(t[0], a)
            "zip" -> a.words.map { w ->
                val p = abs(w)
                ConsoleStep(AgentOp.Zip, p, "$p.zip", null)
            }
            "unzip" -> a.words.map { ConsoleStep(AgentOp.Unzip, abs(it), "", null) }
            else -> emptyList()
        }
    }

    private fun transfers(name: String, a: Argv): List<ConsoleStep> {
        if (a.words.size < 2) throw Exception(tr("Missing argument"))
        val dest = abs(a.words.last())
        val sources = a.words.dropLast(1).map { abs(it) }
        val into = sources.size > 1 || fs.kind(dest) == 2
        return sources.map { src ->
            val to = if (into) joinPath(dest, src.substringAfterLast('/')) else dest
            val folderNeedsFlag = name == "cp" && fs.kind(src) == 2 && 'r' !in a.flags && 'R' !in a.flags
            ConsoleStep(
                if (name == "cp") AgentOp.Copy else AgentOp.Rename, src, to,
                if (folderNeedsFlag) tr("{0} is a folder, use -r", src) else null,
            )
        }
    }

    private fun grab(path: String, cap: Int, check: Boolean = true): Pair<ByteArray, Boolean> {
        if (check) {
            when (fs.kind(path)) {
                0 -> throw Exception(tr("{0}: not found", path))
                2 -> throw Exception(tr("{0}: is a folder", path))
            }
        }
        return fs.open(path).use { s ->
            val buf = ByteArray(cap + 1)
            var n = 0
            while (n < buf.size) {
                val r = s.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            if (n > cap) buf.copyOf(cap) to true else buf.copyOf(n) to false
        }
    }

    private fun sizeOf(path: String): Long =
        fs.list(parentOf(path)).firstOrNull { it.name == path.substringAfterLast('/') }?.size ?: 0L

    private fun need(a: Argv): String = a.words.firstOrNull() ?: throw Exception(tr("Missing argument"))

    private fun changeDir(words: List<String>): String {
        val target = if (words.isEmpty()) home else abs(words[0])
        if (fs.kind(target) != 2) throw Exception(tr("Not a folder"))
        cwd = target
        return cwd
    }

    private fun listDir(a: Argv): String {
        val long = 'l' in a.flags
        val all = 'a' in a.flags
        val targets = if (a.words.isEmpty()) listOf(cwd) else a.words.map { abs(it) }
        val out = StringBuilder()
        for (p in targets) {
            val kind = fs.kind(p)
            if (kind == 0) throw Exception(tr("{0}: not found", p))
            if (targets.size > 1) out.append(p).append(":\n")
            if (kind == 1) {
                out.append(p.substringAfterLast('/')).append('\n')
                continue
            }
            val items = fs.list(p).filter { all || !it.name.startsWith(".") }
            for (item in items.take(LIST_LIMIT)) {
                if (long) {
                    out.append(if (item.dir) "d  -  " else "f  ${humanSize(item.size)}  ")
                    out.append(item.name).append(if (item.dir) "/" else "").append('\n')
                } else {
                    out.append(item.name).append(if (item.dir) "/" else "").append('\n')
                }
            }
            if (items.size > LIST_LIMIT) out.append(tr("… {0} more", items.size - LIST_LIMIT)).append('\n')
            if (items.isEmpty()) out.append(tr("(empty)")).append('\n')
        }
        return out.toString().trimEnd()
    }

    private fun catFiles(a: Argv): String {
        if (a.words.isEmpty()) throw Exception(tr("Missing argument"))
        val out = StringBuilder()
        for (w in a.words.take(8)) {
            val p = abs(w)
            val (bytes, cut) = grab(p, CAT_READ)
            if (a.words.size > 1) out.append("== ").append(shown(p)).append(" ==\n")
            if (TextGrep.binary(bytes, bytes.size)) {
                out.append(tr("Binary file {0}", shown(p))).append('\n')
            } else {
                out.append(String(bytes, Charsets.UTF_8))
                if (cut) out.append('\n').append(tr("… truncated"))
                out.append('\n')
            }
        }
        return out.toString().trimEnd()
    }

    private fun slice(a: Argv, tail: Boolean): String {
        val n = (a.options["n"]?.toIntOrNull() ?: 10).coerceIn(1, 500)
        val p = abs(need(a))
        val (bytes, _) = grab(p, 1_048_576)
        if (TextGrep.binary(bytes, bytes.size)) return tr("Binary file {0}", shown(p))
        val all = String(bytes, Charsets.UTF_8).split('\n')
        val lines = if (all.isNotEmpty() && all.last().isEmpty()) all.dropLast(1) else all
        return (if (tail) lines.takeLast(n) else lines.take(n)).joinToString("\n")
    }

    private fun count(a: Argv, stopped: () -> Boolean): String {
        if (a.words.isEmpty()) throw Exception(tr("Missing argument"))
        val out = ArrayList<String>()
        for (w in a.words.take(20)) {
            val p = abs(w)
            if (fs.kind(p) != 1) throw Exception(tr("{0}: not found", p))
            var lines = 0L
            var bytes = 0L
            val buf = ByteArray(65536)
            fs.open(p).use { s ->
                while (true) {
                    if (stopped()) throw TaskStopped()
                    val r = s.read(buf)
                    if (r < 0) break
                    bytes += r
                    for (i in 0 until r) if (buf[i] == 10.toByte()) lines++
                }
            }
            out.add(if ('l' in a.flags) "$lines ${shown(p)}" else "$lines $bytes ${shown(p)}")
        }
        return out.joinToString("\n")
    }

    private fun statOf(a: Argv): String {
        val p = abs(need(a))
        return when (fs.kind(p)) {
            0 -> throw Exception(tr("{0}: not found", p))
            2 -> "d  $p"
            else -> sizeOf(p).let { "f  ${humanSize(it)} ($it B)  $p" }
        }
    }

    private fun usage(a: Argv): String {
        val p = abs(a.words.firstOrNull() ?: ".")
        if (fs.kind(p) == 0) throw Exception(tr("{0}: not found", p))
        val r = TreeScan.scan(fs, p, emptyList())
        return "${humanSize(r.bytes)}  ${tr("{0} files", r.files)}  ${shown(p)}"
    }

    private fun tree(a: Argv, stopped: () -> Boolean): String {
        val depth = (a.options["L"]?.toIntOrNull() ?: 2).coerceIn(1, 5)
        val withHidden = 'a' in a.flags
        val root = abs(a.words.firstOrNull() ?: ".")
        if (fs.kind(root) != 2) throw Exception(tr("Not a folder"))
        val out = ArrayList<String>()
        out.add(root)
        fun walk(dir: String, prefix: String, level: Int) {
            if (stopped()) throw TaskStopped()
            val items = fs.list(dir).filter { withHidden || !it.name.startsWith(".") }
            for ((i, item) in items.withIndex()) {
                if (out.size >= LIST_LIMIT) {
                    out.add("…")
                    return
                }
                val last = i == items.size - 1
                out.add(prefix + (if (last) "└── " else "├── ") + item.name + (if (item.dir) "/" else ""))
                if (item.dir && level < depth && item.name !in skipDirs) {
                    walk(joinPath(dir, item.name), prefix + (if (last) "    " else "│   "), level + 1)
                }
            }
        }
        walk(root, "", 1)
        return out.joinToString("\n")
    }

    private fun find(a: Argv, stopped: () -> Boolean): String {
        val root = abs(a.words.firstOrNull() ?: ".")
        val nameGlob = a.options["name"]
        val lowerGlob = a.options["iname"]?.lowercase()
        val type = a.options["type"]
        val maxDepth = (a.options["maxdepth"]?.toIntOrNull() ?: 12).coerceIn(1, 20)
        if (fs.kind(root) == 0) throw Exception(tr("{0}: not found", root))
        val hits = ArrayList<String>()
        fun fits(item: FsItem): Boolean {
            if (type == "f" && item.dir) return false
            if (type == "d" && !item.dir) return false
            if (nameGlob != null && !TreeScan.excluded(item.name, listOf(nameGlob))) return false
            if (lowerGlob != null && !TreeScan.excluded(item.name.lowercase(), listOf(lowerGlob))) return false
            return true
        }
        fun walk(dir: String, level: Int) {
            if (hits.size >= FIND_LIMIT || level > maxDepth) return
            if (stopped()) throw TaskStopped()
            val items = try { fs.list(dir) } catch (_: Exception) { return }
            for (item in items) {
                if (hits.size >= FIND_LIMIT) return
                val full = joinPath(dir, item.name)
                if (fits(item)) hits.add(shown(full))
                if (item.dir && item.name !in skipDirs) walk(full, level + 1)
            }
        }
        walk(root, 1)
        if (hits.isEmpty()) return tr("No matches")
        return hits.joinToString("\n") + if (hits.size >= FIND_LIMIT) "\n" + tr("… more results not shown") else ""
    }

    private fun grep(a: Argv, stopped: () -> Boolean): String {
        val pattern = a.words.firstOrNull() ?: throw Exception(tr("grep needs a pattern"))
        val recursive = 'r' in a.flags || 'R' in a.flags
        val ignoreCase = 'i' in a.flags
        val namesOnly = 'l' in a.flags
        val countOnly = 'c' in a.flags
        val numbered = 'n' in a.flags
        val roots = a.words.drop(1).map { abs(it) }.ifEmpty { listOf(cwd) }
        val literal = 'F' in a.flags || pattern.none { it in regexMarks }
        val regex = if (literal) null else try {
            Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        } catch (_: Exception) {
            throw Exception(tr("Bad pattern: {0}", pattern))
        }
        val files = ArrayList<String>()
        fun descend(dir: String) {
            if (stopped()) throw TaskStopped()
            val items = try { fs.list(dir) } catch (_: Exception) { return }
            for (item in items) {
                if (files.size >= GREP_FILES) return
                val full = joinPath(dir, item.name)
                if (item.dir) {
                    if (item.name !in skipDirs) descend(full)
                } else if (FileSniff.byName(item.name) == null) {
                    files.add(full)
                }
            }
        }
        for (r in roots) {
            when (fs.kind(r)) {
                0 -> throw Exception(tr("{0}: not found", r))
                1 -> files.add(r)
                else -> if (recursive) descend(r) else throw Exception(tr("{0} is a folder, use -r", r))
            }
        }
        val out = ArrayList<String>()
        val prefixed = recursive || files.size > 1
        for (f in files) {
            if (stopped()) throw TaskStopped()
            if (out.size >= GREP_LIMIT) break
            val bytes = try { grab(f, GREP_READ, false).first } catch (_: Exception) { continue }
            if (TextGrep.binary(bytes, bytes.size)) continue
            val hits = if (regex == null) {
                TextGrep.literal(bytes, bytes.size, pattern, ignoreCase, 100)
            } else {
                TextGrep.pattern(bytes, bytes.size, regex, 100)
            }
            if (hits.isEmpty()) continue
            val label = shown(f)
            if (namesOnly) {
                out.add(label)
            } else if (countOnly) {
                out.add("$label:${hits.size}")
            } else {
                for (h in hits) {
                    if (out.size >= GREP_LIMIT) break
                    val row = StringBuilder()
                    if (prefixed) row.append(label).append(':')
                    if (numbered) row.append(h.number).append(':')
                    row.append(h.text.take(240))
                    out.add(row.toString())
                }
            }
        }
        if (out.isEmpty()) return tr("No matches")
        return out.joinToString("\n") + if (out.size >= GREP_LIMIT) "\n" + tr("… more results not shown") else ""
    }

    private fun staged(path: String, size: Long, kind: OpenKind, stopped: () -> Boolean): File {
        if (isLocal(fs)) return File(path)
        val target = ViewTarget(fs, path, size, kind, false)
        val dest = MediaStage.target(app, target)
        if (dest.isFile && dest.length() == size) return dest
        val run = MediaStage.start(target, dest)
        while (run.state == TaskState.Running) {
            if (stopped()) {
                run.stop = true
                throw TaskStopped()
            }
            Thread.sleep(80)
        }
        if (run.state != TaskState.Done) throw Exception(run.error ?: tr("Stopped"))
        return dest
    }

    private fun play(a: Argv, stopped: () -> Boolean, onMedia: (AgentMedia) -> Unit): String {
        val target = abs(need(a))
        val songs: List<Pair<String, Long>> = when (fs.kind(target)) {
            0 -> throw Exception(tr("{0}: not found", target))
            2 -> fs.list(target).filter { !it.dir && FileSniff.byName(it.name) == OpenKind.Audio }
                .map { joinPath(target, it.name) to it.size }
            else -> listOf(target to sizeOf(target))
        }
        if (songs.isEmpty()) throw Exception(tr("No audio files"))
        val first = songs[0]
        if (FileSniff.byName(first.first.substringAfterLast('/')) != OpenKind.Audio) {
            throw Exception(tr("Not an audio file: {0}", shown(first.first)))
        }
        val head = staged(first.first, first.second, OpenKind.Audio, stopped)
        val queue = if (isLocal(fs)) songs.map { File(it.first) } else listOf(head)
        main.post { Playback.begin(app, queue, 0) }
        onMedia(AgentMedia(OpenKind.Audio, fs, first.first, first.second, head))
        return tr("Playing {0}", first.first.substringAfterLast('/'))
    }

    private fun show(a: Argv, stopped: () -> Boolean, onMedia: (AgentMedia) -> Unit): String {
        if (a.words.isEmpty()) throw Exception(tr("Missing argument"))
        val wanted = ArrayList<Pair<String, Long>>()
        for (w in a.words) {
            val p = abs(w)
            when (fs.kind(p)) {
                0 -> throw Exception(tr("{0}: not found", p))
                2 -> for (item in fs.list(p)) {
                    val k = FileSniff.byName(item.name)
                    if (!item.dir && (k == OpenKind.Image || k == OpenKind.Video)) wanted.add(joinPath(p, item.name) to item.size)
                }
                else -> wanted.add(p to sizeOf(p))
            }
        }
        if (wanted.isEmpty()) throw Exception(tr("No photos or videos"))
        val names = ArrayList<String>()
        for ((p, size) in wanted.take(12)) {
            val kind = FileSniff.byName(p.substringAfterLast('/')) ?: OpenKind.Binary
            if (kind != OpenKind.Image && kind != OpenKind.Video) throw Exception(tr("Not a photo or video: {0}", shown(p)))
            onMedia(AgentMedia(kind, fs, p, size, staged(p, size, kind, stopped)))
            names.add(p.substringAfterLast('/'))
        }
        return tr("Shown {0}", names.joinToString(", "))
    }
}
