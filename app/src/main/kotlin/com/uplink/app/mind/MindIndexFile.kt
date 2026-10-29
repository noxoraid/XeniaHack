package com.uplink.app

class MindParse(val doc: MindIndexDoc?, val error: String?)

object MindIndexFile {
    const val MAGIC = "#mind-index"
    const val VERSION = "1"

    private fun clean(value: String): String = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun write(doc: MindIndexDoc): String {
        val out = StringBuilder()
        val h = doc.header
        out.append(MAGIC).append('\t').append(VERSION).append('\n')
        out.append("name\t").append(clean(h.name)).append('\n')
        out.append("root\t").append(clean(h.root)).append('\n')
        out.append("files\t").append(h.fileCount).append('\n')
        out.append("indexed\t").append(h.indexedAt).append('\n')
        out.append("signature\t").append(clean(h.signature)).append('\n')
        out.append("truncated\t").append(if (h.truncated) 1 else 0).append('\n')
        for (f in doc.files) {
            out.append("F\t").append(clean(f.path)).append('\t').append(f.size).append('\t')
                .append(clean(f.lang)).append('\t').append(f.modified).append('\n')
            for (d in f.decls) out.append("D\t").append(clean(d.kind)).append('\t').append(clean(d.name)).append('\t').append(d.line).append('\n')
            for (i in f.imports) out.append("I\t").append(clean(i)).append('\n')
        }
        return out.toString()
    }

    private class FileDraft(val path: String, val size: Long, val lang: String, val modified: Long) {
        val decls = ArrayList<MindDecl>()
        val imports = ArrayList<String>()
        fun seal() = MindFileEntry(path, size, lang, modified, decls.toList(), imports.toList())
    }

    fun parse(text: String): MindParse {
        val rows = text.split('\n').filter { it.isNotEmpty() }
        if (rows.isEmpty()) return MindParse(null, "line 1: file is empty")
        val first = rows[0].split('\t')
        if (first[0] != MAGIC) return MindParse(null, "line 1: missing $MAGIC marker")
        if (first.getOrNull(1) != VERSION) return MindParse(null, "line 1: unsupported version ${first.getOrNull(1) ?: "(none)"}")
        val head = HashMap<String, String>()
        val drafts = ArrayList<FileDraft>()
        for ((offset, row) in rows.withIndex().drop(1)) {
            val line = offset + 1
            val cols = row.split('\t')
            when (cols[0]) {
                "F" -> {
                    if (cols.size < 5) return MindParse(null, "line $line: file row needs 5 columns, found ${cols.size}")
                    val size = cols[2].toLongOrNull() ?: return MindParse(null, "line $line: size is not a number: ${cols[2]}")
                    val modified = cols[4].toLongOrNull() ?: return MindParse(null, "line $line: modified is not a number: ${cols[4]}")
                    drafts.add(FileDraft(cols[1], size, cols[3], modified))
                }
                "D" -> {
                    val owner = drafts.lastOrNull() ?: return MindParse(null, "line $line: declaration before any file row")
                    if (cols.size < 4) return MindParse(null, "line $line: declaration row needs 4 columns, found ${cols.size}")
                    val number = cols[3].toIntOrNull() ?: return MindParse(null, "line $line: line number is not a number: ${cols[3]}")
                    owner.decls.add(MindDecl(cols[1], cols[2], number))
                }
                "I" -> {
                    val owner = drafts.lastOrNull() ?: return MindParse(null, "line $line: import before any file row")
                    if (cols.size < 2) return MindParse(null, "line $line: import row needs 2 columns")
                    owner.imports.add(cols[1])
                }
                else -> {
                    if (cols.size < 2) return MindParse(null, "line $line: unknown row: ${cols[0]}")
                    head[cols[0]] = cols[1]
                }
            }
        }
        for (key in listOf("name", "root", "files", "indexed")) {
            if (key !in head) return MindParse(null, "header is missing $key")
        }
        val count = head["files"]!!.toIntOrNull() ?: return MindParse(null, "header files is not a number: ${head["files"]}")
        val at = head["indexed"]!!.toLongOrNull() ?: return MindParse(null, "header indexed is not a number: ${head["indexed"]}")
        val header = MindHeader(head["name"]!!, head["root"]!!, count, at, head["signature"].orEmpty(), head["truncated"] == "1")
        return MindParse(MindIndexDoc(header, drafts.map { it.seal() }), null)
    }
}

object MindValidate {
    fun check(text: String): MindFinding {
        val parsed = MindIndexFile.parse(text)
        val doc = parsed.doc ?: return MindFinding(MindLevel.Error, "parse", parsed.error ?: "index could not be parsed")
        if (doc.header.fileCount != doc.files.size) {
            return MindFinding(MindLevel.Error, "count", "header says ${doc.header.fileCount} files, index has ${doc.files.size} entries")
        }
        val blank = doc.files.indexOfFirst { it.path.isBlank() }
        if (blank >= 0) {
            val before = if (blank > 0) doc.files[blank - 1].path else "(first entry)"
            return MindFinding(MindLevel.Error, "path", "entry ${blank + 1} has an empty path, previous entry: $before", before)
        }
        return MindFinding(MindLevel.Ok, "index", "index is valid: ${doc.files.size} files")
    }
}
