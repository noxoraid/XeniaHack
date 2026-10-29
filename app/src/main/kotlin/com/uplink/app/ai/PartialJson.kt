package com.uplink.app

class PartialJson private constructor(private val src: String) {
    private var at = 0

    private fun blank() {
        while (at < src.length && src[at].isWhitespace()) at++
    }

    private fun value(): Any? {
        blank()
        if (at >= src.length) return null
        return when (val c = src[at]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            else -> if (c == '-' || c.isDigit()) num() else word()
        }
    }

    private fun obj(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        at++
        while (true) {
            blank()
            if (at >= src.length) return out
            if (src[at] == '}') {
                at++
                return out
            }
            if (src[at] == ',') {
                at++
                continue
            }
            if (src[at] != '"') {
                at++
                continue
            }
            val key = str()
            blank()
            if (at >= src.length) return out
            if (src[at] != ':') continue
            at++
            out[key] = value()
            if (at >= src.length) return out
        }
    }

    private fun arr(): List<Any?> {
        val out = ArrayList<Any?>()
        at++
        while (true) {
            blank()
            if (at >= src.length) return out
            if (src[at] == ']') {
                at++
                return out
            }
            if (src[at] == ',') {
                at++
                continue
            }
            val before = at
            val v = value()
            if (at == before) at++ else out.add(v)
            if (at >= src.length) return out
        }
    }

    private fun str(): String {
        val out = StringBuilder()
        at++
        while (at < src.length) {
            val c = src[at++]
            if (c == '"') return out.toString()
            if (c != '\\') {
                out.append(c)
                continue
            }
            if (at >= src.length) return out.toString()
            when (val e = src[at++]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' -> {
                    if (at + 4 > src.length) {
                        at = src.length
                        return out.toString()
                    }
                    val code = src.substring(at, at + 4).toIntOrNull(16)
                    if (code != null) out.append(code.toChar())
                    at += 4
                }
                else -> out.append(e)
            }
        }
        return out.toString()
    }

    private fun num(): Any? {
        val from = at
        while (at < src.length && (src[at].isDigit() || src[at] in "+-.eE")) at++
        return src.substring(from, at).toDoubleOrNull()
    }

    private fun word(): Any? {
        val from = at
        while (at < src.length && src[at].isLetter()) at++
        return when (src.substring(from, at)) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    companion object {
        fun objectIn(text: String): Map<String, Any?>? {
            val from = text.indexOf('{')
            if (from < 0) return null
            val parser = PartialJson(text.substring(from))
            @Suppress("UNCHECKED_CAST")
            return parser.value() as? Map<String, Any?>
        }
    }
}

class StreamView(val say: String, val commands: List<String>, val files: List<Pair<String, String>>, val json: Boolean)

fun streamViewOf(raw: String, sayKey: String = "say", commandKeys: List<String> = listOf("commands", "console"), fileKeys: List<String> = listOf("files", "actions")): StreamView {
    val text = AiClean.clean(raw)
    val map = PartialJson.objectIn(text)
    if (map == null || !(text.trimStart().startsWith("{") || text.contains("{\"$sayKey\""))) {
        val tools = AiClean.toolCommands(raw)
        return StreamView(text, tools, emptyList(), false)
    }
    val say = (map[sayKey] as? String).orEmpty()
    val commands = ArrayList<String>()
    for (key in commandKeys) (map[key] as? List<*>)?.forEach { if (it is String && it.isNotBlank()) commands.add(it) }
    val files = ArrayList<Pair<String, String>>()
    for (key in fileKeys) {
        (map[key] as? List<*>)?.forEach { item ->
            val o = item as? Map<*, *> ?: return@forEach
            val path = (o["path"] as? String).orEmpty()
            if (path.isNotEmpty()) files.add(path to ((o["content"] as? String) ?: (o["body"] as? String).orEmpty()))
        }
    }
    return StreamView(say, commands, files, true)
}
