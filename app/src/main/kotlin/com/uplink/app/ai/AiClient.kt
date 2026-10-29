package com.uplink.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection

class AiMessage(val mine: Boolean, val text: String)

private class AiPiece(val text: String, val thought: String, val stop: String = "")

class AiRateLimit(message: String, val waitMs: Long) : IOException(message)

class AiDropped(message: String) : IOException(message)

class AiCall {
    @Volatile private var conn: HttpURLConnection? = null

    @Volatile var finish: String = ""
        private set

    @Volatile var tap: MindTap? = null

    @Volatile var cut: Boolean = false
        private set

    val truncated: Boolean get() = finish.lowercase() in setOf("length", "max_tokens")

    fun cancel() {
        try { conn?.disconnect() } catch (_: Exception) { }
    }

    fun stream(
        profile: AiProfile,
        history: List<AiMessage>,
        stopped: () -> Boolean,
        onThought: (String) -> Unit = {},
        onStatus: (String) -> Unit = {},
        onText: (String) -> Unit,
    ) {
        finish = ""
        cut = false
        val turns = alternate(history)
        if (turns.isEmpty()) throw IOException(tr("Nothing to send"))
        if (profile.model.isBlank()) throw IOException(tr("No model detected. Edit the provider and connect again."))
        tap?.let { MindGate.measure(it.surface, it.on, MindMeter.sizeOf(profile.system, turns.map { m -> m.text })) }
        val limits = if (profile.api != AiApi.Gemini && !profile.body.contains("max_tokens")) listOf(16000, 8192, 4096) else listOf(0)
        var i = 0
        var rateRetries = 0
        var dropRetries = 0
        while (i < limits.size) {
            try {
                send(profile, turns, limits[i], stopped, onThought, onStatus, onText)
                return
            } catch (e: AiRateLimit) {
                if (rateRetries >= RATE_RETRIES || stopped()) throw e
                rateRetries++
                onStatus(tr("Waiting for the model"))
                pause(e.waitMs, stopped)
            } catch (e: AiDropped) {
                if (dropRetries >= DROP_RETRIES || stopped()) throw IOException(tr("The connection to the model dropped before any reply arrived ({0})", e.message.orEmpty()))
                dropRetries++
                onStatus(tr("Reconnecting"))
                pause(1500, stopped)
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                val tooBig = msg.contains("max_tokens", ignoreCase = true) || msg.contains("max_completion_tokens", ignoreCase = true)
                if (i == limits.size - 1 || !tooBig || stopped()) throw e
                i++
            }
        }
    }

    private fun pause(ms: Long, stopped: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (stopped()) throw TaskStopped()
            Thread.sleep(250)
        }
    }

    private companion object {
        const val RATE_RETRIES = 3
        const val DROP_RETRIES = 2
    }

    private fun send(
        profile: AiProfile, turns: List<AiMessage>, limit: Int, stopped: () -> Boolean,
        onThought: (String) -> Unit, onStatus: (String) -> Unit, onText: (String) -> Unit,
    ) {
        val c = AiWire.connect(endpoint(profile))
        conn = c
        try {
            onStatus(tr("Connecting"))
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 600000
            c.doOutput = true
            c.useCaches = false
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "text/event-stream, application/json")
            c.setRequestProperty("Accept-Encoding", "identity")
            c.setRequestProperty("Cache-Control", "no-cache")
            applyAuth(c, profile)
            applyHeaders(c, profile.headers)
            val body = buildBody(profile, turns, limit).toString().toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            onStatus(tr("Waiting for the model"))
            val code = c.responseCode
            if (code == 429 || code == 529) {
                val wait = c.getHeaderField("retry-after")?.trim()?.toLongOrNull()?.coerceIn(1L, 60L) ?: 15L
                throw AiRateLimit(tr("Request failed ({0}): {1}", code, failure(c)), wait * 1000)
            }
            if (code >= 400) throw IOException(tr("Request failed ({0}): {1}", code, failure(c)))
            onStatus(tr("Receiving"))
            readReply(c, profile.api, stopped, onThought, onText)
        } finally {
            c.disconnect()
            conn = null
        }
    }

    private fun readReply(c: HttpURLConnection, api: AiApi, stopped: () -> Boolean, onThought: (String) -> Unit, onText: (String) -> Unit) {
        val plain = StringBuilder()
        var gotText = false
        try {
        BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                if (stopped()) throw TaskStopped()
                val line = reader.readLine() ?: break
                if (line.startsWith("data:")) {
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    val piece = try { pieceOf(api, JSONObject(data)) } catch (e: IOException) { throw e } catch (_: Exception) { null }
                    if (piece != null) {
                        if (piece.stop.isNotEmpty()) finish = piece.stop
                        if (piece.thought.isNotEmpty()) onThought(piece.thought)
                        if (piece.text.isNotEmpty()) {
                            gotText = true
                            onText(piece.text)
                        }
                    }
                } else if (line.isNotBlank() && !line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith(":")) {
                    if (plain.length < 2_000_000) plain.append(line).append('\n')
                }
            }
        }
        } catch (e: java.io.EOFException) {
            dropped(e, gotText, stopped)
        } catch (e: java.net.SocketException) {
            dropped(e, gotText, stopped)
        }
        if (!gotText && plain.isNotBlank()) {
            val whole = try { wholeOf(api, JSONObject(plain.toString())) } catch (e: IOException) { throw e } catch (_: Exception) { "" }
            if (whole.isNotEmpty()) onText(whole)
            else if (plain.length < 600) throw IOException(plain.toString().trim())
        }
    }

    private fun dropped(e: IOException, gotText: Boolean, stopped: () -> Boolean) {
        if (stopped()) throw TaskStopped()
        if (!gotText) throw AiDropped(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        cut = true
    }

    private fun pieceOf(api: AiApi, o: JSONObject): AiPiece {
        o.optJSONObject("error")?.let { throw IOException(it.optString("message").ifBlank { it.toString() }) }
        return when (api) {
            AiApi.OpenAi -> {
                val choice = o.optJSONArray("choices")?.optJSONObject(0)
                val delta = choice?.optJSONObject("delta")
                val text = when {
                    delta != null && !delta.isNull("content") -> delta.optString("content")
                    choice != null && !choice.isNull("text") -> choice.optString("text")
                    else -> ""
                }
                val thought = when {
                    delta == null -> ""
                    !delta.isNull("reasoning_content") -> delta.optString("reasoning_content")
                    !delta.isNull("reasoning") -> delta.optString("reasoning")
                    else -> ""
                }
                val stop = if (choice == null || choice.isNull("finish_reason")) "" else choice.optString("finish_reason")
                AiPiece(text, thought, stop)
            }
            AiApi.Anthropic -> {
                if (o.optString("type") == "message_delta") AiPiece("", "", o.optJSONObject("delta")?.optString("stop_reason").orEmpty())
                else if (o.optString("type") != "content_block_delta") AiPiece("", "")
                else {
                    val delta = o.optJSONObject("delta")
                    when (delta?.optString("type")) {
                        "thinking_delta" -> AiPiece("", delta.optString("thinking"))
                        else -> AiPiece(delta?.optString("text").orEmpty(), "")
                    }
                }
            }
            AiApi.Gemini -> geminiPiece(o)
        }
    }

    private fun wholeOf(api: AiApi, o: JSONObject): String {
        o.optJSONObject("error")?.let { throw IOException(it.optString("message").ifBlank { it.toString() }) }
        return when (api) {
            AiApi.OpenAi -> {
                val message = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                if (message == null || message.isNull("content")) "" else message.optString("content")
            }
            AiApi.Anthropic -> {
                val parts = o.optJSONArray("content") ?: JSONArray()
                buildString { for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty()) }
            }
            AiApi.Gemini -> geminiPiece(o).text
        }
    }

    private fun geminiPiece(o: JSONObject): AiPiece {
        o.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let { throw IOException(tr("Blocked by the model: {0}", it)) }
        val candidate = o.optJSONArray("candidates")?.optJSONObject(0)
        val stop = candidate?.optString("finishReason").orEmpty().let { if (it == "MAX_TOKENS") "length" else "" }
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts") ?: return AiPiece("", "", stop)
        val text = StringBuilder()
        val thought = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val piece = part.optString("text")
            if (part.optBoolean("thought", false)) thought.append(piece) else text.append(piece)
        }
        return AiPiece(text.toString(), thought.toString(), stop)
    }

    private fun endpoint(p: AiProfile): String {
        val base = p.baseUrl.trim().trimEnd('/')
        return when (p.api) {
            AiApi.OpenAi -> if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            AiApi.Anthropic -> when {
                base.endsWith("/messages") -> base
                base.endsWith("/v1") -> "$base/messages"
                else -> "$base/v1/messages"
            }
            AiApi.Gemini -> {
                val root = if (base.contains("/v1")) base else "$base/v1beta"
                "$root/models/${p.model.trim()}:streamGenerateContent?alt=sse"
            }
        }
    }

    private fun applyAuth(c: HttpURLConnection, p: AiProfile) {
        val key = AiWire.cleanKey(p.apiKey)
        if (key.isEmpty()) return
        when (p.api) {
            AiApi.OpenAi -> c.setRequestProperty("Authorization", "Bearer $key")
            AiApi.Anthropic -> {
                c.setRequestProperty("x-api-key", key)
                c.setRequestProperty("anthropic-version", "2023-06-01")
            }
            AiApi.Gemini -> c.setRequestProperty("x-goog-api-key", key)
        }
    }

    private fun applyHeaders(c: HttpURLConnection, text: String) {
        for (line in text.lines()) {
            val at = line.indexOf(':')
            if (at <= 0) continue
            val name = line.substring(0, at).trim()
            val value = line.substring(at + 1).trim()
            if (name.isNotEmpty()) c.setRequestProperty(name, value)
        }
    }

    private fun buildBody(p: AiProfile, turns: List<AiMessage>, limit: Int): JSONObject {
        val root = JSONObject()
        val system = p.system.trim()
        when (p.api) {
            AiApi.OpenAi -> {
                val arr = JSONArray()
                if (system.isNotEmpty()) arr.put(JSONObject().put("role", "system").put("content", system))
                for (t in turns) arr.put(JSONObject().put("role", if (t.mine) "user" else "assistant").put("content", t.text))
                root.put("model", p.model.trim()).put("messages", arr).put("stream", true)
                if (limit > 0) root.put("max_tokens", limit)
            }
            AiApi.Anthropic -> {
                val arr = JSONArray()
                for (t in turns) arr.put(JSONObject().put("role", if (t.mine) "user" else "assistant").put("content", t.text))
                root.put("model", p.model.trim()).put("max_tokens", if (limit > 0) limit else 4096).put("messages", arr).put("stream", true)
                if (system.isNotEmpty()) root.put("system", system)
            }
            AiApi.Gemini -> {
                val arr = JSONArray()
                for (t in turns) {
                    arr.put(
                        JSONObject().put("role", if (t.mine) "user" else "model")
                            .put("parts", JSONArray().put(JSONObject().put("text", t.text))),
                    )
                }
                root.put("contents", arr)
                if (system.isNotEmpty()) {
                    root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                }
            }
        }
        val extra = p.body.trim()
        if (extra.isNotEmpty()) {
            val o = try { JSONObject(extra) } catch (_: Exception) { throw IOException(tr("Extra body is not valid JSON")) }
            for (k in o.keys()) root.put(k, o.get(k))
        }
        return root
    }

    private fun alternate(history: List<AiMessage>): List<AiMessage> {
        val out = ArrayList<AiMessage>()
        for (m in history) {
            if (m.text.isBlank()) continue
            val last = out.lastOrNull()
            if (last != null && last.mine == m.mine) out[out.size - 1] = AiMessage(m.mine, last.text + "\n\n" + m.text)
            else if (last != null || m.mine) out.add(m)
        }
        return out
    }

    private fun failure(c: HttpURLConnection): String {
        val raw = try {
            c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { r ->
                val buf = CharArray(4000)
                val n = r.read(buf)
                if (n > 0) String(buf, 0, n) else ""
            }.orEmpty()
        } catch (_: Exception) { "" }
        val message = try {
            val o = JSONObject(raw)
            val err = o.opt("error")
            when (err) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> o.optString("message")
            }
        } catch (_: Exception) { "" }
        return message.ifBlank { raw.trim().take(300).ifEmpty { c.responseMessage.orEmpty() } }
    }
}
