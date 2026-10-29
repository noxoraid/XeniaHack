package com.uplink.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class AiApi(val key: String, val label: String, val baseUrl: String, val model: String) {
    OpenAi("openai", "OpenAI-compatible", "https://api.openai.com/v1", "gpt-4o-mini"),
    Anthropic("anthropic", "Anthropic", "https://api.anthropic.com", "claude-sonnet-5-5"),
    Gemini("gemini", "Gemini", "https://generativelanguage.googleapis.com", "gemini-2.0-flash");

    companion object {
        fun of(key: String): AiApi = entries.firstOrNull { it.key == key } ?: OpenAi
    }
}

class AiProfile(
    val id: String,
    val name: String,
    val api: AiApi,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val system: String,
    val headers: String,
    val body: String,
    val models: List<String> = emptyList(),
    val pinned: Boolean = false,
) {
    fun withModel(next: String): AiProfile = AiProfile(id, name, api, baseUrl, apiKey, next, system, headers, body, models, true)

    fun choices(): List<String> = (listOf(model) + models).filter { it.isNotBlank() }.distinct()
}

object AiStore {
    val profiles = mutableStateListOf<AiProfile>()
    var activeId by mutableStateOf("")
        private set

    private var loaded = false

    val active: AiProfile? get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nx_ai", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(ctx)
        activeId = saved.getString("active", "") ?: ""
        val arr = try { JSONArray(saved.getString("profiles", "[]")) } catch (_: Exception) { JSONArray() }
        var legacy = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val rawKey = o.optString("key")
            val rawHeaders = o.optString("headers")
            if (SecretVault.needsMigration(rawKey) || SecretVault.needsMigration(rawHeaders)) legacy = true
            profiles.add(
                AiProfile(
                    id, o.optString("name"), AiApi.of(o.optString("api")), o.optString("url"),
                    SecretVault.value("ai:key:$id", rawKey), o.optString("model"), o.optString("system"),
                    SecretVault.value("ai:headers:$id", rawHeaders), o.optString("body"),
                    namesOf(o.optJSONArray("models")), o.optBoolean("pinned", true),
                ),
            )
        }
        if (legacy && SecretVault.available) persist(ctx)
    }

    private fun namesOf(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    private fun persist(ctx: Context) {
        val arr = JSONArray()
        for (p in profiles) {
            arr.put(
                JSONObject().put("id", p.id).put("name", p.name).put("api", p.api.key).put("url", p.baseUrl)
                    .put("key", SecretVault.seal("ai:key:${p.id}", p.apiKey)).put("model", p.model).put("system", p.system)
                    .put("headers", SecretVault.seal("ai:headers:${p.id}", p.headers)).put("body", p.body)
                    .put("models", JSONArray(p.models)).put("pinned", p.pinned),
            )
        }
        prefs(ctx).edit().putString("profiles", arr.toString()).putString("active", activeId).apply()
    }

    fun save(ctx: Context, profile: AiProfile) {
        val at = profiles.indexOfFirst { it.id == profile.id }
        if (at >= 0) profiles[at] = profile else profiles.add(profile)
        activeId = profile.id
        persist(ctx)
    }

    fun profileOf(id: String): AiProfile? = profiles.firstOrNull { it.id == id }

    fun choose(ctx: Context, id: String, model: String) {
        val at = profiles.indexOfFirst { it.id == id }
        if (at < 0) return
        profiles[at] = profiles[at].withModel(model)
        activeId = id
        persist(ctx)
    }

    fun pick(ctx: Context, id: String) {
        activeId = id
        persist(ctx)
    }

    fun remove(ctx: Context, id: String) {
        profiles.removeAll { it.id == id }
        if (activeId == id) activeId = profiles.firstOrNull()?.id ?: ""
        persist(ctx)
    }

    fun newId(): String = System.currentTimeMillis().toString(36)
}
