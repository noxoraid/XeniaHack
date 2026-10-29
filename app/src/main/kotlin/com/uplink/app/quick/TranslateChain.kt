package com.uplink.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TranslateOutcome(val text: String, val provider: String)

object TranslateChain {
    private const val TIMEOUT_MS = 10000

    fun translate(text: String, from: String, to: String): TranslateOutcome {
        val attempts: List<Pair<String, () -> String>> = listOf(
            "Google" to { google(text, from, to) },
            "Lingva" to { lingva(text, from, to) },
            "MyMemory" to { myMemory(text, from, to) },
        )
        var failure: Exception? = null
        for ((name, attempt) in attempts) {
            try {
                val out = attempt().trim()
                if (out.isNotEmpty()) return TranslateOutcome(out, name)
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException("Translation failed")
    }

    private fun google(text: String, from: String, to: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx" +
            "&sl=${enc(from)}&tl=${enc(to)}&dt=t&q=${enc(text)}"
        val parts = JSONArray(open(url, null)).getJSONArray(0)
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            out.append(parts.optJSONArray(i)?.optString(0).orEmpty())
        }
        return out.toString()
    }

    private fun lingva(text: String, from: String, to: String): String {
        val path = enc(text).replace("+", "%20")
        return JSONObject(open("https://lingva.ml/api/v1/$from/$to/$path", null)).getString("translation")
    }

    private fun myMemory(text: String, from: String, to: String): String {
        val source = if (from == "auto") "en" else from
        val url = "https://api.mymemory.translated.net/get?q=${enc(text)}&langpair=${enc("$source|$to")}"
        val json = JSONObject(open(url, null))
        if (json.optInt("responseStatus") != 200) throw IllegalStateException(json.optString("responseDetails"))
        return json.getJSONObject("responseData").getString("translatedText")
    }

    private fun open(url: String, body: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        try {
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
