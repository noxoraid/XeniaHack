package com.uplink.app

import org.json.JSONArray

object AiClean {
    private const val BARS = "[|｜¦]"
    private val tag = "<\\s*/?\\s*$BARS+\\s*DSML\\s*$BARS+[^>]*>".toRegex(RegexOption.IGNORE_CASE)
    private val calls = "<\\s*$BARS+\\s*DSML\\s*$BARS+\\s*function_calls\\s*>[\\s\\S]*?(<\\s*/\\s*$BARS+\\s*DSML\\s*$BARS+\\s*function_calls\\s*>|$)".toRegex(RegexOption.IGNORE_CASE)
    private val param = "<\\s*$BARS+\\s*DSML\\s*$BARS+\\s*parameter\\s+name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)<\\s*/\\s*$BARS+\\s*DSML\\s*$BARS+\\s*parameter\\s*>".toRegex(RegexOption.IGNORE_CASE)
    private val think = "<(think|thinking|reasoning)>[\\s\\S]*?(</\\1>|$)".toRegex(RegexOption.IGNORE_CASE)
    private val xmlCalls = "<(function_calls|tool_call|tool_calls)>[\\s\\S]*?(</\\1>|$)".toRegex(RegexOption.IGNORE_CASE)
    private val dangling = "<\\s*$BARS[^>]*$".toRegex()
    private val blanks = "\n{3,}".toRegex()

    fun toolCommands(raw: String): List<String> {
        val out = ArrayList<String>()
        for (m in param.findAll(raw)) {
            if (!m.groupValues[1].contains("command", ignoreCase = true)) continue
            val body = m.groupValues[2].trim()
            if (body.startsWith("[")) {
                try {
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                } catch (_: Exception) {
                }
            } else if (body.isNotEmpty()) {
                out.add(body)
            }
        }
        return out
    }

    fun clean(raw: String): String {
        var t = raw.replace("\r\n", "\n")
        t = think.replace(t, "")
        t = calls.replace(t, "")
        t = xmlCalls.replace(t, "")
        t = tag.replace(t, "")
        t = dangling.replace(t, "")
        return blanks.replace(t, "\n\n").trim()
    }

    private val sayField = "\"say\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)".toRegex()
    private val pathField = "\"path\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    private val textItem = "\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()

    private fun unescape(v: String): String = v.replace("\\n", "\n").replace("\\t", "  ").replace("\\\"", "\"").replace("\\\\", "\\")

    fun preview(raw: String): String {
        val t = clean(raw)
        val json = t.trimStart().startsWith("{") || t.contains("{\"say\"")
        if (!json) {
            val calls = toolCommands(raw)
            return (t + calls.joinToString("") { "\n$ $it" }).takeLast(900)
        }
        val lines = ArrayList<String>()
        sayField.find(t)?.groupValues?.get(1)?.let { if (it.isNotBlank()) lines.add(unescape(it)) }
        val from = t.indexOf("\"commands\"")
        if (from >= 0) {
            val open = t.indexOf('[', from)
            if (open >= 0) {
                val end = t.indexOf(']', open).let { if (it < 0) t.length else it }
                for (m in textItem.findAll(t.substring(open + 1, end))) lines.add("$ " + unescape(m.groupValues[1]))
            }
        }
        for (m in pathField.findAll(t)) lines.add("+ " + m.groupValues[1])
        if (t.contains("\"content\"")) lines.add("…")
        return lines.joinToString("\n").takeLast(900)
    }
}
