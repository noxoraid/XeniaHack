package com.uplink.app

class MindOutlineResult(val decls: List<MindDecl>, val imports: List<String>)

private class MindLine(val raw: String, val code: String, val depth: Int, val commented: Boolean)

object MindOutline {
    const val MAX_DECLS = 300
    const val MAX_IMPORTS = 60

    fun of(lang: String, text: String): MindOutlineResult = when (lang) {
        "python" -> python(text)
        "kotlin", "java", "javascript", "typescript", "c", "cpp" -> braced(lang, text)
        else -> MindOutlineResult(emptyList(), emptyList())
    }

    private fun braced(lang: String, text: String): MindOutlineResult {
        val lines = scan(text, lang == "javascript" || lang == "typescript", lang == "c" || lang == "cpp", lang == "kotlin")
        val decls = ArrayList<MindDecl>()
        val imports = LinkedHashSet<String>()
        for ((index, line) in lines.withIndex()) {
            if (decls.size >= MAX_DECLS && imports.size >= MAX_IMPORTS) break
            if (line.commented) continue
            val raw = line.raw.trim()
            importOf(lang, raw)?.let { if (imports.size < MAX_IMPORTS) imports.add(it) }
            if (decls.size >= MAX_DECLS) continue
            val code = line.code.trim()
            if (code.isEmpty()) continue
            val found = when (lang) {
                "kotlin" -> if (line.depth == 0) kotlinDecl(code) else null
                "java" -> if (line.depth <= 1) javaDecl(code, line.depth) else null
                "javascript", "typescript" -> if (line.depth == 0) scriptDecl(code) else null
                else -> if (line.depth == 0) cDecl(code) else null
            }
            if (found != null) decls.add(MindDecl(found.first, found.second, index + 1))
        }
        return MindOutlineResult(decls, imports.toList())
    }

    private fun importOf(lang: String, raw: String): String? = when (lang) {
        "kotlin", "java" -> Regex("^import\\s+(?:static\\s+)?([\\w.*]+)").find(raw)?.groupValues?.get(1)
        "c", "cpp" -> Regex("^#\\s*include\\s*[<\"]([^>\"]+)[>\"]").find(raw)?.groupValues?.get(1)
        else -> Regex("^import\\s+['\"]([^'\"]+)['\"]").find(raw)?.groupValues?.get(1)
            ?: Regex("^(?:import|export)\\b.*?\\bfrom\\s+['\"]([^'\"]+)['\"]").find(raw)?.groupValues?.get(1)
            ?: Regex("\\brequire\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)").find(raw)?.groupValues?.get(1)
    }

    private val annotation = Regex("^@[\\w.]+(?:\\([^)]*\\))?\\s*")
    private val kotlinModifiers = setOf(
        "public", "private", "internal", "protected", "abstract", "open", "final", "sealed", "data", "enum",
        "annotation", "inner", "inline", "value", "suspend", "operator", "infix", "tailrec", "external",
        "expect", "actual", "const", "lateinit", "override", "noinline", "crossinline", "vararg",
    )

    private fun stripLeading(code: String): String {
        var s = code
        while (annotation.containsMatchIn(s)) s = s.replace(annotation, "")
        return s
    }

    private fun kotlinDecl(code: String): Pair<String, String>? {
        val words = stripLeading(code).split(Regex("\\s+")).toMutableList()
        while (words.isNotEmpty() && words[0] in kotlinModifiers) words.removeAt(0)
        if (words.isEmpty()) return null
        val rest = words.drop(1).joinToString(" ")
        return when (words[0]) {
            "class", "interface", "object", "typealias" -> firstWord(rest)?.let { words[0] to it }
            "fun" -> if (rest.startsWith("interface ")) firstWord(rest.removePrefix("interface "))?.let { "interface" to it } else callName(rest)?.let { "fun" to it }
            "val", "var" -> propName(rest)?.let { "val" to it }
            else -> null
        }
    }

    private fun firstWord(text: String): String? = Regex("^([A-Za-z_]\\w*)").find(text.trim())?.groupValues?.get(1)

    private fun callName(rest: String): String? {
        val cut = rest.indexOf('(')
        if (cut <= 0) return null
        val head = rest.substring(0, cut).trim()
        val word = Regex("([A-Za-z_]\\w*)\\s*$").find(head)?.groupValues?.get(1)
        return word
    }

    private fun propName(rest: String): String? {
        var head = rest
        for (stop in listOf(":", "=", " by ", " get(", " set(")) {
            val at = head.indexOf(stop)
            if (at >= 0) head = head.substring(0, at)
        }
        return Regex("([A-Za-z_]\\w*)\\s*$").find(head.trim())?.groupValues?.get(1)
    }

    private val javaType = Regex("^(?:(?:public|private|protected|static|final|abstract|sealed|non-sealed|strictfp)\\s+)*(class|interface|enum|record)\\s+(\\w+)")
    private val javaMethod = Regex("^(?:(?:public|private|protected|static|final|abstract|synchronized|native|default)\\s+)*(?:<[^>]+>\\s*)?[\\w<>\\[\\],.?\\s]+?\\s+(\\w+)\\s*\\(")
    private val javaSkip = Regex("^(?:return|new|throw|else|if|for|while|switch|case|try|catch|do|import|package)\\b")

    private fun javaDecl(code: String, depth: Int): Pair<String, String>? {
        val stripped = stripLeading(code)
        javaType.find(stripped)?.let { return it.groupValues[1] to it.groupValues[2] }
        if (depth != 1 || javaSkip.containsMatchIn(stripped)) return null
        val m = javaMethod.find(stripped) ?: return null
        val before = stripped.substring(0, m.range.last)
        if (before.contains('=')) return null
        return "fun" to m.groupValues[1]
    }

    private val scriptClass = Regex("^(?:export\\s+)?(?:default\\s+)?(?:declare\\s+)?(?:abstract\\s+)?class\\s+(\\w+)")
    private val scriptFun = Regex("^(?:export\\s+)?(?:default\\s+)?(?:declare\\s+)?(?:async\\s+)?function\\s*\\*?\\s*(\\w+)")
    private val scriptVal = Regex("^(?:export\\s+)?(?:declare\\s+)?(?:const|let|var)\\s+(\\w+)")
    private val scriptType = Regex("^(?:export\\s+)?(?:declare\\s+)?(interface|enum|type)\\s+(\\w+)")

    private fun scriptDecl(code: String): Pair<String, String>? {
        scriptClass.find(code)?.let { return "class" to it.groupValues[1] }
        scriptFun.find(code)?.let { return "fun" to it.groupValues[1] }
        scriptType.find(code)?.let { return it.groupValues[1] to it.groupValues[2] }
        scriptVal.find(code)?.let { return "val" to it.groupValues[1] }
        return null
    }

    private val cType = Regex("^(?:typedef\\s+)?(class|struct|union|enum\\s+class|enum)\\s+(?:\\w+\\s+)?(\\w+)\\s*(?:final\\s*)?(?::[^{;]*)?\\{?\\s*$")
    private val cNamespace = Regex("^namespace\\s+(\\w+)")
    private val cDefine = Regex("^#\\s*define\\s+(\\w+)")
    private val cFun = Regex("^(?!(?:if|for|while|switch|return|else|do|using|typedef|namespace|template)\\b)(?:[\\w:<>,*&~\\s]*?[\\s*&])?((?:\\w+::)*~?\\w+)\\s*\\(")

    private fun cDecl(code: String): Pair<String, String>? {
        cDefine.find(code)?.let { return "define" to it.groupValues[1] }
        if (code.startsWith("#")) return null
        cNamespace.find(code)?.let { return "namespace" to it.groupValues[1] }
        cType.find(code)?.let { return it.groupValues[1].replace(Regex("\\s+"), " ") to it.groupValues[2] }
        cFun.find(code)?.let { return "fun" to it.groupValues[1] }
        return null
    }

    private val transparentHead = Regex("(?:\\bnamespace\\b[^{]*|\\bextern\\s*\"\"\\s*)$")

    private fun scan(text: String, backtick: Boolean, transparentBlocks: Boolean, rawStrings: Boolean): List<MindLine> {
        val out = ArrayList<MindLine>()
        val stack = ArrayList<Boolean>()
        var inBlock = false
        var inRaw = false
        var opaque = 0
        var prevCode = ""
        for (raw in text.split('\n')) {
            val code = StringBuilder()
            val startsInside = inBlock || inRaw
            val depth = opaque
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                val next = if (i + 1 < raw.length) raw[i + 1] else ' '
                if (inBlock) {
                    if (c == '*' && next == '/') { inBlock = false; i += 2 } else i++
                    continue
                }
                if (inRaw) {
                    if (raw.startsWith("\"\"\"", i)) { inRaw = false; i += 3 } else i++
                    continue
                }
                if (c == '/' && next == '/') break
                if (c == '/' && next == '*') { inBlock = true; i += 2; continue }
                if (rawStrings && raw.startsWith("\"\"\"", i)) { inRaw = true; i += 3; code.append("\"\""); continue }
                if (c == '"' || c == '\'' || (backtick && c == '`')) {
                    var j = i + 1
                    while (j < raw.length && raw[j] != c) {
                        if (raw[j] == '\\') j++
                        j++
                    }
                    code.append(c).append(c)
                    i = j + 1
                    continue
                }
                if (c == '{') {
                    val head = if (code.isBlank()) prevCode else code.toString()
                    val transparent = transparentBlocks && transparentHead.containsMatchIn(head)
                    stack.add(transparent)
                    if (!transparent) opaque++
                } else if (c == '}' && stack.isNotEmpty()) {
                    val transparent = stack.removeAt(stack.size - 1)
                    if (!transparent) opaque--
                }
                code.append(c)
                i++
            }
            if (code.isNotBlank()) prevCode = code.toString()
            out.add(MindLine(raw, code.toString(), depth, startsInside))
        }
        return out
    }

    private val pyDef = Regex("^(?:async\\s+)?def\\s+(\\w+)")
    private val pyClass = Regex("^class\\s+(\\w+)")
    private val pyVal = Regex("^([A-Za-z_]\\w*)\\s*(?::[^=]+)?=(?!=)")
    private val pyImport = Regex("^import\\s+(.+)")
    private val pyFrom = Regex("^from\\s+([\\w.]+)\\s+import")

    private fun occurrences(line: String, token: String): Int {
        var count = 0
        var at = line.indexOf(token)
        while (at >= 0) {
            count++
            at = line.indexOf(token, at + token.length)
        }
        return count
    }

    private fun python(text: String): MindOutlineResult {
        val decls = ArrayList<MindDecl>()
        val imports = LinkedHashSet<String>()
        var quote = ""
        for ((index, raw) in text.split('\n').withIndex()) {
            val line = raw.trimEnd('\r')
            if (quote.isNotEmpty()) {
                if (occurrences(line, quote) % 2 == 1) quote = ""
                continue
            }
            val opener = listOf("\"\"\"", "'''").firstOrNull { occurrences(line, it) % 2 == 1 }
            val top = line.isNotEmpty() && !line[0].isWhitespace() && line[0] != '#'
            if (top) pythonLine(line, index + 1, decls, imports)
            if (opener != null) quote = opener
        }
        return MindOutlineResult(decls, imports.toList())
    }

    private fun pythonLine(line: String, number: Int, decls: MutableList<MindDecl>, imports: MutableSet<String>) {
        val plain = pyImport.find(line)
        if (plain != null) {
            for (part in plain.groupValues[1].split(',')) {
                val name = part.trim().split(Regex("\\s+"))[0]
                if (name.isNotEmpty() && imports.size < MAX_IMPORTS) imports.add(name)
            }
            return
        }
        val from = pyFrom.find(line)
        if (from != null) {
            if (imports.size < MAX_IMPORTS) imports.add(from.groupValues[1])
            return
        }
        if (decls.size >= MAX_DECLS) return
        val fn = pyDef.find(line)
        val cls = pyClass.find(line)
        val value = pyVal.find(line)
        when {
            fn != null -> decls.add(MindDecl("fun", fn.groupValues[1], number))
            cls != null -> decls.add(MindDecl("class", cls.groupValues[1], number))
            value != null -> decls.add(MindDecl("val", value.groupValues[1], number))
        }
    }
}
