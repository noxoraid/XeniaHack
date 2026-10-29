package com.uplink.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL

class AiProbeResult(val api: AiApi, val baseUrl: String, val model: String, val models: List<String>)

private class AiModelEntry(val id: String, val created: Long)

private class AiProbeStep(val api: AiApi, val root: String, val url: String)

private class AiProbeReply(val code: Int, val body: String)

object AiProbe {
    private val tails = listOf("/chat/completions", "/completions", "/messages", "/models")
    private val skipTokens = listOf(
        "embed", "whisper", "tts", "dall", "image", "imagen", "moderation", "transcribe", "audio",
        "realtime", "rerank", "aqa", "veo", "lyria", "guard", "babbage", "davinci",
    )
    private val snapshot = Regex("-(\\d{4}-\\d{2}-\\d{2}|\\d{8}|\\d{4})$")
    private val versioned = Regex("/v\\d+[a-z]*(/|$)")

    private fun isLocal(host: String): Boolean =
        host.startsWith("localhost") || host.startsWith("127.") || host.startsWith("192.168.") ||
            host.startsWith("10.") || host.startsWith("0.0.0.0") || host.endsWith(".local")

    fun normalize(raw: String): String {
        var url = raw.trim().replace(Regex("\\s+"), "")
        if (url.isEmpty()) throw IOException(tr("Enter the API URL"))
        if (!url.contains("://")) url = (if (isLocal(url)) "http://" else "https://") + url
        url = url.substringBefore('?').trimEnd('/')
        var trimmed = true
        while (trimmed) {
            trimmed = false
            for (tail in tails) {
                if (url.endsWith(tail)) {
                    url = url.removeSuffix(tail).trimEnd('/')
                    trimmed = true
                }
            }
        }
        return url
    }

    private fun hostOf(url: String): String = try { URL(url).host.lowercase() } catch (_: Exception) { "" }

    private fun stepsFor(base: String): List<AiProbeStep> {
        val host = hostOf(base)
        val ver = versioned.containsMatchIn(base)
        val openRoots = if (ver) listOf(base) else listOf("$base/v1", base)
        val open = openRoots.map { AiProbeStep(AiApi.OpenAi, it, "$it/models") }
        val anthropicRoot = if (ver) base else "$base/v1"
        val anthropic = AiProbeStep(AiApi.Anthropic, anthropicRoot, "$anthropicRoot/models")
        val geminiRoot = if (ver) base else "$base/v1beta"
        val gemini = AiProbeStep(AiApi.Gemini, geminiRoot, "$geminiRoot/models?pageSize=1000")
        return when {
            host.contains("anthropic") -> listOf(anthropic) + open + gemini
            host.contains("generativelanguage") || host.contains("googleapis") -> listOf(gemini) + open + anthropic
            else -> open + anthropic + gemini
        }
    }

    private fun get(step: AiProbeStep, key: String, agent: String): AiProbeReply {
        val c = AiWire.connect(step.url, agent)
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 10000
            c.readTimeout = 12000
            c.setRequestProperty("Accept", "application/json")
            val token = key
            if (token.isNotEmpty()) {
                when (step.api) {
                    AiApi.OpenAi -> c.setRequestProperty("Authorization", "Bearer $token")
                    AiApi.Anthropic -> {
                        c.setRequestProperty("x-api-key", token)
                        c.setRequestProperty("anthropic-version", "2023-06-01")
                    }
                    AiApi.Gemini -> c.setRequestProperty("x-goog-api-key", token)
                }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { r ->
                val out = StringBuilder()
                val buf = CharArray(8192)
                while (out.length < 2_000_000) {
                    val n = r.read(buf)
                    if (n < 0) break
                    out.append(buf, 0, n)
                }
                out.toString()
            }.orEmpty()
            return AiProbeReply(code, text)
        } finally {
            c.disconnect()
        }
    }

    private fun parse(body: String): List<AiModelEntry> {
        val root: Any = try { JSONObject(body) } catch (_: Exception) { try { JSONArray(body) } catch (_: Exception) { return emptyList() } }
        val arr = when (root) {
            is JSONObject -> root.optJSONArray("data") ?: root.optJSONArray("models")
            is JSONArray -> root
            else -> null
        } ?: return emptyList()
        val out = ArrayList<AiModelEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.opt(i)
            if (e is String) {
                if (e.isNotBlank()) out.add(AiModelEntry(e.removePrefix("models/"), 0L))
            } else if (e is JSONObject) {
                val id = e.optString("id").ifBlank { e.optString("name") }.ifBlank { e.optString("model") }.removePrefix("models/")
                if (id.isBlank()) continue
                val methods = e.optJSONArray("supportedGenerationMethods")
                if (methods != null) {
                    var chat = false
                    for (k in 0 until methods.length()) if (methods.optString(k) == "generateContent") chat = true
                    if (!chat) continue
                }
                out.add(AiModelEntry(id, e.optLong("created", 0L)))
            }
        }
        return out
    }

    private fun pick(models: List<AiModelEntry>): String {
        val usable = models.filter { m -> skipTokens.none { m.id.lowercase().contains(it) } }.ifEmpty { models }
        val steady = usable.filter { !snapshot.containsMatchIn(it.id) && !it.id.lowercase().contains("preview") }.ifEmpty { usable }
        return steady.maxByOrNull { it.created }?.id ?: steady.first().id
    }

    private fun keyIsBad(code: Int, body: String): Boolean =
        code == 401 || code == 403 && AiWire.looksJson(body) ||
            code == 400 && (body.contains("api key", true) || body.contains("api_key", true) || body.contains("authenticat", true))

    fun detect(rawUrl: String, rawKey: String, forced: String, run: TaskRun): AiProbeResult {
        val base = normalize(rawUrl)
        val key = AiWire.cleanKey(rawKey)
        var rejected = false
        var rejectNote = ""
        var firstCode = 0
        var firstNote = ""
        var lastNote = ""
        for (step in stepsFor(base)) {
            if (run.stop) throw TaskStopped()
            run.detail = step.url.removePrefix("https://").removePrefix("http://").take(60)
            var reply = try {
                get(step, key, AiWire.BROWSER)
            } catch (e: IOException) {
                lastNote = e.message ?: e.toString()
                continue
            }
            if (reply.code == 403 && !AiWire.looksJson(reply.body)) {
                reply = try { get(step, key, AiWire.PLAIN) } catch (_: IOException) { reply }
            }
            if (reply.code in 200..299) {
                val models = parse(reply.body)
                if (models.isNotEmpty()) {
                    val chosen = forced.trim().ifEmpty { pick(models) }
                    return AiProbeResult(step.api, step.root, chosen, models.map { it.id })
                }
            } else {
                if (firstCode == 0) {
                    firstCode = reply.code
                    firstNote = AiWire.reason(reply.body)
                }
                if (keyIsBad(reply.code, reply.body) && !rejected) {
                    rejected = true
                    rejectNote = AiWire.reason(reply.body)
                }
            }
        }
        if (forced.isNotBlank()) {
            val host = hostOf(base)
            val api = when {
                host.contains("anthropic") -> AiApi.Anthropic
                host.contains("generativelanguage") -> AiApi.Gemini
                else -> AiApi.OpenAi
            }
            return AiProbeResult(api, base, forced.trim(), listOf(forced.trim()))
        }
        if (rejected) {
            throw IOException(if (rejectNote.isEmpty()) tr("The API key was rejected") else tr("The API key was rejected: {0}", rejectNote))
        }
        if (firstCode > 0) {
            throw IOException(
                if (firstNote.isEmpty()) tr("Could not detect the API at this URL ({0})", firstCode)
                else tr("Could not detect the API at this URL ({0}): {1}", firstCode, firstNote),
            )
        }
        throw IOException(tr("Could not reach the server: {0}", lastNote.ifEmpty { tr("no response") }))
    }
}
