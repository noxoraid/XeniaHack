package com.uplink.app

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NxReadTools {
    val names = setOf("ls", "du", "tree", "stat", "head", "tail", "hash", "diff", "json")

    private const val LS_LIMIT = 300
    private const val TREE_LIMIT = 300
    private const val DU_SECONDS = 20L
    private const val DU_DEPTH = 12
    private const val FILE_CAP = 524_288
    private const val SCAN_CAP = 64L * 1024 * 1024
    private const val JSON_CAP = 4000
    private const val OUT_CAP = 12_000
    private val skipDirs = setOf(".git", "node_modules")
    private val say: NxSay = { key, args -> tr(key, *args) }

    private fun missing(): Nothing = throw Exception(tr("Missing argument"))

    private fun split(words: List<String>): Pair<Set<String>, List<String>> {
        val flags = words.filter { it.length > 1 && it[0] == '-' && !it[1].isDigit() }.toSet()
        return flags to words.filter { it !in flags }
    }

    private fun nodes(fs: FsBackend, dir: String): List<NxNode> = fs.list(dir).map { NxNode(it.name, it.dir, it.size) }

    private fun fileSize(fs: FsBackend, path: String): Long =
        fs.meta(path)?.size ?: fs.list(parentOf(path)).firstOrNull { it.name == path.substringAfterLast('/') }?.size ?: 0L

    private fun needFile(fs: FsBackend, path: String) {
        when (fs.kind(path)) {
            0 -> throw Exception(tr("{0}: not found", path))
            2 -> throw Exception(tr("{0}: is a folder", path))
        }
    }

    private fun grab(fs: FsBackend, path: String, cap: Int): Pair<ByteArray, Boolean> {
        needFile(fs, path)
        return fs.open(path).use { s ->
            val buf = ByteArray(cap + 1)
            var n = 0
            while (n < buf.size) {
                val r = s.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            buf.copyOf(minOf(n, cap)) to (n > cap)
        }
    }

    private fun isBinary(fs: FsBackend, path: String): Boolean {
        val head = fs.open(path).use { s ->
            val buf = ByteArray(4096)
            val n = maxOf(s.read(buf), 0)
            buf.copyOf(n)
        }
        return TextGrep.binary(head, head.size)
    }

    fun run(target: AgentTarget, action: String, words: List<String>, stopped: () -> Boolean): ConsoleOut {
        val fs = target.source
        val (flags, pos) = split(words)
        fun abs(p: String) = fixPath(p, target.cwd)
        try {
            val text = when (action) {
                "ls" -> ls(fs, abs(pos.firstOrNull() ?: "."), "-n" !in flags, "-a" in flags, stopped)
                "du" -> du(fs, abs(pos.firstOrNull() ?: "."), stopped)
                "tree" -> {
                    val depth = (pos.firstNotNullOfOrNull { it.toIntOrNull() } ?: 2).coerceIn(1, 6)
                    val root = abs(pos.firstOrNull { it.toIntOrNull() == null } ?: ".")
                    if (fs.kind(root) != 2) throw Exception(tr("Not a folder"))
                    NxTree.lines(root, depth, TREE_LIMIT, "-a" in flags, skipDirs, say, stopped) { nodes(fs, it) }.joinToString("\n")
                }
                "stat" -> stat(fs, abs(pos.firstOrNull() ?: missing()))
                "head", "tail" -> slice(fs, abs(pos.firstOrNull() ?: missing()), pos.getOrNull(1)?.toIntOrNull(), action == "tail", stopped)
                "hash" -> hash(fs, pos.ifEmpty { missing() }.take(10).map { abs(it) }, stopped)
                "diff" -> {
                    if (pos.size < 2) missing()
                    diff(fs, abs(pos[0]), abs(pos[1]))
                }
                "json" -> json(fs, abs(pos.firstOrNull() ?: missing()), pos.getOrNull(1).orEmpty())
                else -> throw Exception(tr("unknown action"))
            }
            return ConsoleOut(text.take(OUT_CAP), true)
        } catch (e: NxCancelled) {
            throw TaskStopped()
        } catch (e: NxJsonError) {
            throw Exception(tr(e.message.orEmpty()))
        }
    }

    private fun ls(fs: FsBackend, path: String, bySize: Boolean, all: Boolean, stopped: () -> Boolean): String {
        when (fs.kind(path)) {
            0 -> throw Exception(tr("{0}: not found", path))
            1 -> return NxFormat.size(fileSize(fs, path)) + "  " + path.substringAfterLast('/')
        }
        val items = nodes(fs, path).filter { all || !it.name.startsWith(".") }
        val counts = HashMap<String, Int>()
        for (d in items.filter { it.dir }.take(100)) {
            if (stopped()) throw NxCancelled()
            try {
                counts[d.name] = fs.list(NxFormat.join(path, d.name)).count { all || !it.name.startsWith(".") }
            } catch (_: Exception) {
            }
        }
        return NxLs.lines(path, items, counts, bySize, LS_LIMIT, say).joinToString("\n")
    }

    private fun du(fs: FsBackend, path: String, stopped: () -> Boolean): String {
        when (fs.kind(path)) {
            0 -> throw Exception(tr("{0}: not found", path))
            1 -> return NxFormat.size(fileSize(fs, path)) + "  " + path
        }
        val children = nodes(fs, path)
        val vps = (fs as? VpsFs)?.vps
        var remote: Map<String, Long>? = null
        if (vps != null) {
            val script = "T=\"\"; command -v timeout >/dev/null 2>&1 && T=\"timeout $DU_SECONDS\"; cd ${sq(path)} 2>/dev/null && \$T du -sb . ./* ./.[!.]* 2>/dev/null"
            remote = try { NxDu.parseDu(vps.exec(script)).takeIf { it.containsKey(".") } } catch (_: Exception) { null }
        }
        val rows = ArrayList<DuRow>()
        val deadline = System.currentTimeMillis() + DU_SECONDS * 1000
        for (c in children) {
            if (stopped()) throw NxCancelled()
            if (remote != null) {
                val b = remote["./" + c.name]
                rows.add(DuRow(c.name, c.dir, b ?: 0L, -1, b != null))
                continue
            }
            if (!c.dir) {
                rows.add(DuRow(c.name, false, c.size, 1, true))
                continue
            }
            if (System.currentTimeMillis() > deadline) {
                rows.add(DuRow(c.name, true, 0L, 0, false))
                continue
            }
            val full = NxFormat.join(path, c.name)
            if (isLocal(fs)) {
                val r = TreeScan.scan(fs, full, emptyList())
                rows.add(DuRow(c.name, true, r.bytes, r.files, true))
            } else {
                val r = NxDu.measure(full, DU_DEPTH, deadline, { System.currentTimeMillis() }, { stopped() }) { nodes(fs, it) }
                rows.add(DuRow(c.name, true, r.bytes, r.files, r.complete))
            }
        }
        return NxDu.render(path, rows, remote?.get("."), say).joinToString("\n")
    }

    private fun stat(fs: FsBackend, path: String): String {
        val kind = fs.kind(path)
        if (kind == 0) throw Exception(tr("{0}: not found", path))
        val meta = fs.meta(path)
        val out = ArrayList<String>()
        out.add(tr("path: {0}", path))
        out.add(tr("type: {0}", if (kind == 2) tr("folder") else tr("file")))
        if (kind == 1) {
            val size = meta?.size ?: fileSize(fs, path)
            out.add(tr("size: {0} ({1} B)", NxFormat.size(size), size))
        } else {
            out.add(tr("size: use nx du for folders"))
        }
        val stamp = meta?.modified ?: 0L
        out.add(tr("modified: {0}", if (stamp > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(stamp)) else tr("unknown")))
        val mode = meta?.mode ?: -1
        out.add(tr("permissions: {0}", if (mode >= 0) NxFormat.permissions(mode, kind == 2) else tr("unknown")))
        return out.joinToString("\n")
    }

    private fun slice(fs: FsBackend, path: String, count: Int?, tail: Boolean, stopped: () -> Boolean): String {
        needFile(fs, path)
        if (isBinary(fs, path)) return tr("Binary file {0}", path)
        val n = (count ?: 10).coerceIn(1, 500)
        val lines: List<String>
        var note = ""
        if (tail) {
            val r = fs.open(path).use { NxLines.tail(it, n, SCAN_CAP, stopped) }
            lines = r.lines
            if (!r.complete) note = "\n" + tr("[scanned only the first 64 MB, the end of the file was not reached]")
        } else {
            lines = fs.open(path).use { NxLines.head(it, n, stopped) }
        }
        return (if (lines.isEmpty()) tr("(empty)") else lines.joinToString("\n")) + note
    }

    private fun hash(fs: FsBackend, paths: List<String>, stopped: () -> Boolean): String {
        val out = ArrayList<String>()
        for (p in paths) {
            needFile(fs, p)
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            fs.open(p).use { s ->
                val buf = ByteArray(65536)
                while (true) {
                    if (stopped()) throw NxCancelled()
                    val r = s.read(buf)
                    if (r < 0) break
                    digest.update(buf, 0, r)
                    size += r
                }
            }
            out.add("sha256  " + SecretBox.hex(digest.digest()) + "  " + p.substringAfterLast('/') + "  (" + NxFormat.size(size) + ")")
        }
        return out.joinToString("\n")
    }

    private fun lines(bytes: ByteArray): List<String> {
        val all = String(bytes, Charsets.UTF_8).split('\n')
        return if (all.isNotEmpty() && all.last().isEmpty()) all.dropLast(1) else all
    }

    private fun diff(fs: FsBackend, a: String, b: String): String {
        val (ba, cutA) = grab(fs, a, FILE_CAP)
        val (bb, cutB) = grab(fs, b, FILE_CAP)
        if (TextGrep.binary(ba, ba.size) || TextGrep.binary(bb, bb.size)) throw Exception(tr("Binary files cannot be compared"))
        val body = NxDiff.unified(lines(ba), lines(bb), a, b, 2, 200, say).joinToString("\n")
        return if (cutA || cutB) body + "\n" + tr("[only the first 512 KB of each file was compared]") else body
    }

    private fun json(fs: FsBackend, path: String, key: String): String {
        needFile(fs, path)
        val value = fs.open(path).use { NxJsonPath.extract(it, NxJsonPath.split(key), JSON_CAP, SCAN_CAP) }
        return value ?: throw Exception(tr("key not found: {0}", key))
    }
}
