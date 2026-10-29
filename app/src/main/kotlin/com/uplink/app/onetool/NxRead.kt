package com.uplink.app

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.Locale

typealias NxSay = (String, Array<out Any?>) -> String

val NxPlain: NxSay = { key, args ->
    var out = key
    for ((i, a) in args.withIndex()) out = out.replace("{$i}", a.toString())
    out
}

class NxCancelled : Exception("cancelled")

class NxNode(val name: String, val dir: Boolean, val size: Long)

object NxFormat {
    fun size(b: Long): String = when {
        b < 1024L -> "$b B"
        b < 1024L * 1024 -> String.format(Locale.ROOT, "%.1f KB", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", b / (1024.0 * 1024.0))
        else -> String.format(Locale.ROOT, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
    }

    fun join(dir: String, name: String): String = if (dir == "/") "/$name" else "$dir/$name"

    fun permissions(mode: Int, dir: Boolean): String {
        val chars = StringBuilder(if (dir) "d" else "-")
        val bits = "rwxrwxrwx"
        for (i in 0 until 9) chars.append(if (mode and (1 shl (8 - i)) != 0) bits[i] else '-')
        return chars.toString() + " (" + String.format(Locale.ROOT, "%04o", mode and 0xFFF) + ")"
    }
}

object NxTree {
    fun lines(
        root: String, depth: Int, maxItems: Int, hidden: Boolean, skip: Set<String>, say: NxSay,
        stopped: () -> Boolean, list: (String) -> List<NxNode>,
    ): List<String> {
        val out = ArrayList<String>()
        out.add(root)
        var cut = false
        fun walk(dir: String, prefix: String, level: Int) {
            if (cut) return
            if (stopped()) throw NxCancelled()
            val items = list(dir).filter { hidden || !it.name.startsWith(".") }
            for ((i, item) in items.withIndex()) {
                if (out.size >= maxItems) {
                    cut = true
                    return
                }
                val last = i == items.size - 1
                out.add(prefix + (if (last) "└── " else "├── ") + item.name + (if (item.dir) "/" else ""))
                if (item.dir && level < depth && item.name !in skip) {
                    walk(NxFormat.join(dir, item.name), prefix + (if (last) "    " else "│   "), level + 1)
                    if (cut) return
                }
            }
        }
        walk(root, "", 1)
        if (cut) out.add(say("[cut at {0} entries, continue with: nx tree <subfolder> <depth>]", arrayOf(maxItems)))
        return out
    }
}

class DuRow(val name: String, val dir: Boolean, val bytes: Long, val files: Int, val counted: Boolean)

class NxSizeResult(val bytes: Long, val files: Int, val complete: Boolean)

object NxDu {
    fun render(path: String, rows: List<DuRow>, total: Long?, say: NxSay): List<String> {
        val sum = total ?: rows.filter { it.counted }.sumOf { it.bytes }
        val pending = rows.count { !it.counted }
        val head = if (pending == 0) say("{0}  total  {1}", arrayOf(NxFormat.size(sum), path))
        else say("{0}  total  {1}  (partial, {2} not counted yet)", arrayOf(NxFormat.size(sum), path, pending))
        val out = ArrayList<String>()
        out.add(head)
        for (r in rows.filter { it.counted }.sortedWith(compareBy({ -it.bytes }, { it.name.lowercase() }))) {
            val tail = if (r.files >= 0 && r.dir) "  " + say("{0} files", arrayOf(r.files)) else ""
            out.add(NxFormat.size(r.bytes) + "  " + r.name + (if (r.dir) "/" else "") + tail)
        }
        for (r in rows.filter { !it.counted }.sortedBy { it.name.lowercase() }) {
            out.add(say("not counted yet", arrayOf<Any?>()) + "  " + r.name + (if (r.dir) "/" else ""))
        }
        return out
    }

    fun parseDu(text: String): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (line in text.lines()) {
            val cut = line.indexOfFirst { it == '\t' || it == ' ' }
            if (cut <= 0) continue
            val n = line.substring(0, cut).toLongOrNull() ?: continue
            val name = line.substring(cut).trim()
            if (name.isNotEmpty()) out[name] = n
        }
        return out
    }

    fun measure(
        path: String, maxDepth: Int, deadline: Long, now: () -> Long, stopped: () -> Boolean, list: (String) -> List<NxNode>,
    ): NxSizeResult {
        var bytes = 0L
        var files = 0
        var complete = true
        fun visit(dir: String, level: Int) {
            if (!complete) return
            if (stopped()) throw NxCancelled()
            if (now() > deadline || level > maxDepth) {
                complete = false
                return
            }
            val items = try { list(dir) } catch (_: Exception) { return }
            for (item in items) {
                if (item.dir) visit(NxFormat.join(dir, item.name), level + 1)
                else {
                    bytes += item.size
                    files++
                }
                if (!complete) return
            }
        }
        visit(path, 1)
        return NxSizeResult(bytes, files, complete)
    }
}

class NxSlice(val lines: List<String>, val complete: Boolean)

private class CapInput(private val inner: InputStream, private val max: Long) : InputStream() {
    var cut = false
    private var seen = 0L

    override fun read(): Int {
        if (seen >= max) {
            cut = true
            return -1
        }
        val b = inner.read()
        if (b >= 0) seen++
        return b
    }

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (seen >= max) {
            cut = true
            return -1
        }
        val n = inner.read(buf, off, minOf(len.toLong(), max - seen).toInt())
        if (n > 0) seen += n
        return n
    }
}

object NxLines {
    const val LINE_CAP = 2000

    private fun clip(line: String): String = if (line.length > LINE_CAP) line.substring(0, LINE_CAP) + "…" else line

    fun head(input: InputStream, n: Int, stopped: () -> Boolean): List<String> {
        val reader = input.bufferedReader(Charsets.UTF_8)
        val out = ArrayList<String>()
        while (out.size < n) {
            if (stopped()) throw NxCancelled()
            out.add(clip(reader.readLine() ?: break))
        }
        return out
    }

    fun tail(input: InputStream, n: Int, maxBytes: Long, stopped: () -> Boolean): NxSlice {
        val capped = CapInput(input, maxBytes)
        val reader = capped.bufferedReader(Charsets.UTF_8)
        val ring = ArrayDeque<String>()
        var count = 0
        while (true) {
            if (count % 1024 == 0 && stopped()) throw NxCancelled()
            val line = reader.readLine() ?: break
            count++
            ring.addLast(clip(line))
            if (ring.size > n) ring.removeFirst()
        }
        return NxSlice(ring.toList(), !capped.cut)
    }
}

class NxJsonError(message: String) : Exception(message)

object NxJsonPath {
    fun split(path: String): List<String> = path.split('.').filter { it.isNotEmpty() }

    fun extract(input: InputStream, path: List<String>, cap: Int, maxBytes: Long): String? {
        val capped = CapInput(input, maxBytes)
        val scan = Scan(InputStreamReader(capped, Charsets.UTF_8).buffered(), cap)
        val found = try {
            scan.find(path, 0)
        } catch (e: NxJsonError) {
            if (capped.cut) throw NxJsonError("file too large to scan")
            throw e
        }
        if (capped.cut && !found) throw NxJsonError("file too large to scan")
        return if (found) scan.captured() else null
    }

    private class Scan(private val r: Reader, private val cap: Int) {
        private var peeked = -2
        private val sb = StringBuilder()
        private var capturing = false
        private var clipped = false

        fun captured(): String = sb.toString() + if (clipped) "…" else ""

        private fun peek(): Int {
            if (peeked == -2) peeked = r.read()
            return peeked
        }

        private fun next(): Int {
            val c = peek()
            peeked = -2
            if (capturing && c >= 0) {
                if (sb.length < cap) sb.append(c.toChar()) else clipped = true
            }
            return c
        }

        private fun ws() {
            while (true) {
                val c = peek()
                if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) next() else return
            }
        }

        private fun expect(ch: Char) {
            ws()
            if (next() != ch.code) throw NxJsonError("invalid JSON, expected '$ch'")
        }

        private fun string(): String {
            expect('"')
            val s = StringBuilder()
            while (true) {
                val c = next()
                if (c < 0) throw NxJsonError("invalid JSON, unterminated string")
                if (c == '"'.code) return s.toString()
                if (c == '\\'.code) {
                    val e = next()
                    if (e < 0) throw NxJsonError("invalid JSON, unterminated string")
                    if (e == 'u'.code) {
                        repeat(4) { next() }
                        s.append('?')
                    } else {
                        s.append(
                            when (e.toChar()) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; 'b' -> '\b'; else -> e.toChar() },
                        )
                    }
                } else {
                    s.append(c.toChar())
                }
            }
        }

        fun skip() {
            ws()
            when (val c = peek()) {
                '{'.code -> {
                    next()
                    ws()
                    if (peek() == '}'.code) {
                        next()
                        return
                    }
                    while (true) {
                        string()
                        expect(':')
                        skip()
                        ws()
                        val d = next()
                        if (d == '}'.code) return
                        if (d != ','.code) throw NxJsonError("invalid JSON, expected ',' or '}'")
                    }
                }
                '['.code -> {
                    next()
                    ws()
                    if (peek() == ']'.code) {
                        next()
                        return
                    }
                    while (true) {
                        skip()
                        ws()
                        val d = next()
                        if (d == ']'.code) return
                        if (d != ','.code) throw NxJsonError("invalid JSON, expected ',' or ']'")
                    }
                }
                '"'.code -> string()
                -1 -> throw NxJsonError("invalid JSON, unexpected end")
                else -> {
                    if (c == '}'.code || c == ']'.code || c == ','.code) throw NxJsonError("invalid JSON, unexpected '${c.toChar()}'")
                    while (true) {
                        val d = peek()
                        if (d < 0 || d == ','.code || d == '}'.code || d == ']'.code || d == ' '.code || d == '\n'.code || d == '\r'.code || d == '\t'.code) return
                        next()
                    }
                }
            }
        }

        fun find(path: List<String>, at: Int): Boolean {
            ws()
            if (at == path.size) {
                capturing = true
                skip()
                capturing = false
                return true
            }
            val want = path[at]
            when (peek()) {
                '{'.code -> {
                    next()
                    ws()
                    if (peek() == '}'.code) return false
                    while (true) {
                        val key = string()
                        expect(':')
                        if (key == want) return find(path, at + 1)
                        skip()
                        ws()
                        val d = next()
                        if (d == '}'.code) return false
                        if (d != ','.code) throw NxJsonError("invalid JSON, expected ',' or '}'")
                    }
                }
                '['.code -> {
                    val index = want.toIntOrNull() ?: return false
                    next()
                    ws()
                    if (peek() == ']'.code) return false
                    var i = 0
                    while (true) {
                        if (i == index) return find(path, at + 1)
                        skip()
                        ws()
                        val d = next()
                        if (d == ']'.code) return false
                        if (d != ','.code) throw NxJsonError("invalid JSON, expected ',' or ']'")
                        i++
                    }
                }
                else -> return false
            }
        }
    }
}

object NxDiff {
    private const val CELLS = 4_000_000

    fun unified(a: List<String>, b: List<String>, nameA: String, nameB: String, context: Int, maxOut: Int, say: NxSay): List<String> {
        if (a == b) return listOf(say("Files are identical", arrayOf<Any?>()))
        var p = 0
        while (p < a.size && p < b.size && a[p] == b[p]) p++
        var s = 0
        while (s < a.size - p && s < b.size - p && a[a.size - 1 - s] == b[b.size - 1 - s]) s++
        val am = a.subList(p, a.size - s)
        val bm = b.subList(p, b.size - s)
        if (am.size.toLong() * bm.size > CELLS) {
            return listOf(say("Files differ from line {0} on, too many changed lines ({1} vs {2}) to compare here", arrayOf(p + 1, am.size, bm.size)))
        }
        val w = bm.size + 1
        val t = IntArray((am.size + 1) * w)
        for (i in am.size - 1 downTo 0) for (j in bm.size - 1 downTo 0) {
            t[i * w + j] = if (am[i] == bm[j]) t[(i + 1) * w + j + 1] + 1 else maxOf(t[(i + 1) * w + j], t[i * w + j + 1])
        }
        val ops = ArrayList<Pair<Char, String>>()
        for (i in 0 until p) ops.add(' ' to a[i])
        var i = 0
        var j = 0
        while (i < am.size && j < bm.size) {
            when {
                am[i] == bm[j] -> { ops.add(' ' to am[i]); i++; j++ }
                t[(i + 1) * w + j] >= t[i * w + j + 1] -> { ops.add('-' to am[i]); i++ }
                else -> { ops.add('+' to bm[j]); j++ }
            }
        }
        while (i < am.size) ops.add('-' to am[i++])
        while (j < bm.size) ops.add('+' to bm[j++])
        for (k in a.size - s until a.size) ops.add(' ' to a[k])
        val changed = ops.indices.filter { ops[it].first != ' ' }
        val out = ArrayList<String>()
        out.add("--- $nameA")
        out.add("+++ $nameB")
        var idx = 0
        var cut = false
        while (idx < changed.size && !cut) {
            val start = maxOf(0, changed[idx] - context)
            var end = minOf(ops.size - 1, changed[idx] + context)
            while (idx + 1 < changed.size && changed[idx + 1] - context <= end + 1) {
                idx++
                end = minOf(ops.size - 1, changed[idx] + context)
            }
            idx++
            var aStart = 1
            var bStart = 1
            for (k in 0 until start) {
                if (ops[k].first != '+') aStart++
                if (ops[k].first != '-') bStart++
            }
            val slice = ops.subList(start, end + 1)
            out.add("@@ -$aStart,${slice.count { it.first != '+' }} +$bStart,${slice.count { it.first != '-' }} @@")
            for (op in slice) {
                if (out.size >= maxOut) {
                    cut = true
                    break
                }
                out.add(op.first + op.second)
            }
        }
        if (cut) out.add(say("[cut after {0} lines]", arrayOf(maxOut)))
        return out
    }
}

object NxGroup {
    fun find(hits: List<String>, limit: Int, say: NxSay): List<String> {
        val real = hits.filter { it.isNotBlank() && !it.startsWith("…") }
        val cutAlready = real.size != hits.count { it.isNotBlank() }
        val groups = LinkedHashMap<String, MutableList<String>>()
        for (h in real) {
            val at = h.lastIndexOf('/')
            val dir = if (at < 0) "." else h.substring(0, at).ifEmpty { "/" }
            groups.getOrPut(dir) { ArrayList() }.add(h.substring(at + 1))
        }
        val out = ArrayList<String>()
        var shown = 0
        var cut = cutAlready
        for ((dir, names) in groups) {
            if (shown >= limit) {
                cut = true
                break
            }
            out.add("$dir/  (${names.size})")
            for (n in names) {
                if (shown >= limit) {
                    cut = true
                    break
                }
                out.add("  $n")
                shown++
            }
        }
        if (cut) out.add(say("[cut at {0} results, narrow with: nx find <glob> <subfolder>]", arrayOf(limit)))
        return out
    }

    fun grep(lines: List<String>, perFile: Int, limit: Int, say: NxSay): List<String> {
        val real = lines.filter { it.isNotBlank() && !it.startsWith("…") }
        var cut = real.size != lines.count { it.isNotBlank() }
        val groups = LinkedHashMap<String, MutableList<String>>()
        for (l in real) {
            val first = l.indexOf(':')
            if (first <= 0) {
                groups.getOrPut("") { ArrayList() }.add(l)
                continue
            }
            groups.getOrPut(l.substring(0, first)) { ArrayList() }.add(l.substring(first + 1))
        }
        val out = ArrayList<String>()
        var shown = 0
        for ((file, rows) in groups) {
            if (shown >= limit) {
                cut = true
                break
            }
            if (file.isNotEmpty()) out.add("$file  (${rows.size})")
            for ((k, row) in rows.withIndex()) {
                if (k >= perFile) {
                    out.add("  " + say("… {0} more in this file", arrayOf(rows.size - perFile)))
                    cut = true
                    break
                }
                if (shown >= limit) {
                    cut = true
                    break
                }
                out.add("  $row")
                shown++
            }
        }
        if (cut) out.add(say("[cut at {0} matches, narrow with: nx grep <text> <subfolder>]", arrayOf(limit)))
        return out
    }
}

object NxLs {
    fun lines(path: String, items: List<NxNode>, counts: Map<String, Int>, bySize: Boolean, limit: Int, say: NxSay): List<String> {
        val dirs = items.filter { it.dir }.sortedBy { it.name.lowercase() }
        val files = items.filter { !it.dir }.let { f ->
            if (bySize) f.sortedWith(compareBy({ -it.size }, { it.name.lowercase() })) else f.sortedBy { it.name.lowercase() }
        }
        val out = ArrayList<String>()
        out.add(say("{0}  {1} in files  ({2} folders, {3} files)", arrayOf(path, NxFormat.size(files.sumOf { it.size }), dirs.size, files.size)))
        val rows = ArrayList<String>()
        fun fileRow(f: NxNode) = rows.add(NxFormat.size(f.size) + "  " + f.name)
        fun dirRow(d: NxNode) {
            val n = counts[d.name]
            rows.add(say("not counted yet", arrayOf<Any?>()) + "  " + d.name + "/" + (if (n == null) "" else "  " + say("{0} items", arrayOf(n))))
        }
        if (bySize) {
            files.forEach { fileRow(it) }
            dirs.forEach { dirRow(it) }
        } else {
            dirs.forEach { dirRow(it) }
            files.forEach { fileRow(it) }
        }
        out.addAll(rows.take(limit))
        val rest = rows.size - limit
        if (rest > 0) out.add(say("[{0} more not shown, narrow with a subfolder path]", arrayOf(rest)))
        if (items.isEmpty()) out.add(say("(empty)", arrayOf<Any?>()))
        return out
    }
}
