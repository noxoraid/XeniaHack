package com.uplink.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiWire {
    const val BROWSER = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36 UntilyFlow/1.0"
    const val PLAIN = "curl/8.5.0"

    fun cleanKey(raw: String): String {
        var k = raw.replace(Regex("\\s+"), "").trim('"', '\'', '`')
        if (k.startsWith("Bearer", ignoreCase = true)) k = k.substring(6)
        return k
    }

    fun connect(url: String, agent: String = BROWSER): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", agent)
        return c
    }

    fun looksJson(body: String): Boolean = body.trimStart().startsWith("{") || body.trimStart().startsWith("[")

    fun reason(body: String): String {
        val text = body.trim()
        if (text.isEmpty()) return ""
        if (looksJson(text)) {
            try {
                val o = JSONObject(text)
                val err = o.opt("error")
                val found = when (err) {
                    is JSONObject -> err.optString("message").ifBlank { err.optString("code") }
                    is String -> err
                    else -> o.optString("message").ifBlank { o.optString("detail") }
                }
                if (found.isNotBlank()) return found.take(160)
            } catch (_: Exception) {
            }
        }
        return if (text.startsWith("<")) "" else text.take(160)
    }
}
