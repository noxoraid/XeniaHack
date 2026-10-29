package com.uplink.app

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object MindSummary {
    const val DEFAULT_BUDGET = 4000
    const val MIN_BUDGET = 500
    const val MAX_BUDGET = 60000
    const val MAX_MATCHED = 25
    const val MAX_DECLS_SHOWN = 14
    const val MAX_IMPORTS_SHOWN = 8
    private const val MORE_RESERVE = 44

    private val stop = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "make", "create", "build", "yang", "dan",
        "untuk", "buat", "dengan", "pada", "ini", "itu", "file", "code", "please",
    )

    fun clampBudget(value: Int): Int = value.coerceIn(MIN_BUDGET, MAX_BUDGET)

    fun readLine(withNx: Boolean, tools: Boolean): String = when {
        withNx -> "This is an outline of the project (paths, sizes, declarations), not full content. Run nx read <path> <from> <to> to read the lines you need."
        tools -> "This is an outline of the project (paths, sizes, declarations), not full content. Read a file with cat or head before you change it."
        else -> "This is an outline of the project (paths, sizes, declarations), not full content. Ask the user to paste a file if you need its lines."
    }

    fun terms(request: String): List<String> {
        val spaced = request.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        val out = LinkedHashSet<String>()
        for (word in spaced.lowercase().split(Regex("[^a-z0-9]+"))) {
            if (word.length >= 3 && word !in stop) out.add(word)
        }
        return out.toList()
    }

    class Scored(val file: MindFileEntry, val score: Int, val hits: List<MindDecl>)

    fun score(file: MindFileEntry, terms: List<String>): Scored {
        if (terms.isEmpty()) return Scored(file, 0, emptyList())
        val lowerPath = file.path.lowercase()
        val base = lowerPath.substringAfterLast('/').substringBeforeLast('.')
        val folders = lowerPath.substringBeforeLast('/', "")
        var total = 0
        val hits = LinkedHashSet<MindDecl>()
        for (term in terms) {
            if (base == term) total += 6 else if (base.contains(term)) total += 4
            if (folders.contains(term)) total += 1
            val found = file.decls.filter { it.name.lowercase().contains(term) }
            if (found.isNotEmpty()) {
                total += 3
                hits.addAll(found)
            }
        }
        return Scored(file, total, hits.toList())
    }

    fun order(doc: MindIndexDoc, request: String): List<Scored> =
        terms(request).let { t -> doc.files.map { score(it, t) } }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.file.path })

    fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(millis)

    fun sizeText(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}K"
        else -> "${bytes / (1024 * 1024)}M"
    }

    private fun declText(d: MindDecl) = "${d.kind} ${d.name}:${d.line}"

    private class Lines(private val room: Int) {
        val rows = ArrayList<String>()
        var used = 0

        fun left(): Int = room - used - (if (rows.isEmpty()) 0 else 1)

        fun add(line: String): Boolean {
            val cost = line.length + (if (rows.isEmpty()) 0 else 1)
            if (used + cost > room) return false
            rows.add(line)
            used += cost
            return true
        }
    }

    fun build(doc: MindIndexDoc, request: String, budget: Int, note: String): String {
        val total = clampBudgetFor(budget)
        val reserve = note.length + 1
        if (total < reserve + 1) return fit(note, total)
        val lines = Lines(total - reserve)
        val h = doc.header
        val head = listOf(
            "Project: ${h.name}",
            "Root: ${h.root}",
            "Files: ${h.fileCount}${if (h.truncated) " (partial, limits reached)" else ""}, indexed ${stamp(h.indexedAt)}",
        )
        for (line in head) if (!lines.add(line)) return finish(lines, note, total)
        val ranked = order(doc, request)
        val matched = ranked.filter { it.score > 0 }.take(MAX_MATCHED)
        val shown = HashSet<String>()
        if (matched.isNotEmpty() && lines.add("Matches for this request:")) {
            for (m in matched) {
                if (!lines.add("${m.file.path} ${m.file.lang} ${sizeText(m.file.size)}")) break
                shown.add(m.file.path)
                val rest = m.file.decls.filter { it !in m.hits }
                val decls = (m.hits + rest).take(MAX_DECLS_SHOWN)
                if (decls.isNotEmpty()) lines.add("  " + decls.joinToString(", ") { declText(it) })
                if (m.file.imports.isNotEmpty()) lines.add("  imports " + m.file.imports.take(MAX_IMPORTS_SHOWN).joinToString(", "))
            }
        }
        val others = doc.files.filter { it.path !in shown }
        if (others.isNotEmpty() && lines.add("Files:")) {
            var listed = 0
            for (f in others) {
                val row = "${f.path} ${f.lang} ${sizeText(f.size)}"
                if (listed + 1 < others.size && lines.left() - row.length < MORE_RESERVE) break
                if (!lines.add("${f.path} ${f.lang} ${sizeText(f.size)}")) break
                listed++
            }
            if (listed < others.size) lines.add("(+${others.size - listed} more files not listed)")
        }
        return finish(lines, note, total)
    }

    private fun clampBudgetFor(budget: Int): Int = if (budget < 1) 1 else budget

    private fun finish(lines: Lines, note: String, total: Int): String {
        val text = (lines.rows + note).joinToString("\n")
        return if (text.length <= total) text else fit(text, total)
    }

    fun fit(text: String, budget: Int): String {
        if (budget <= 0) return ""
        if (text.length <= budget) return text
        val cut = text.lastIndexOf('\n', budget)
        if (cut > 0) return text.substring(0, cut)
        return text.substring(0, budget)
    }
}
