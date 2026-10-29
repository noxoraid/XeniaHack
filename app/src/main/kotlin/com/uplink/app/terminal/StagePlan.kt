package com.uplink.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

enum class StageRole(val key: String, private val label: String, val brief: String) {
    Whole("whole", "Everything", "Do the whole task yourself: plan it, create the structure, write the code and run what is needed."),
    Structure("structure", "Structure", "Handle only the structure: folders, empty or stub files, config, dependency installs and a clear list of what each file must contain. Do not write full implementations, a later stage writes the code."),
    Code("code", "Code", "Handle the code: fill in the files so the project works, following the structure from the previous stage. Do not reorganize folders unless something is broken."),
    Review("review", "Review", "Review and fix: check the result of the previous stages, run checks or builds, repair mistakes, and report anything you could not fix.");

    val title: String get() = tr(label)

    companion object {
        fun of(key: String): StageRole = entries.firstOrNull { it.key == key } ?: Whole
    }
}

class StageSpec(val profileId: String, val model: String, val role: StageRole)

class StagePlan(private val scope: String) {
    val stages = mutableStateListOf<StageSpec>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nx_term", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (stages.isNotEmpty()) return
        val arr = try { JSONArray(prefs(ctx).getString(scope, "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            stages.add(StageSpec(o.optString("id"), o.optString("model"), StageRole.of(o.optString("role"))))
        }
        if (stages.isEmpty()) stages.add(StageSpec("", "", StageRole.Whole))
    }

    fun save(ctx: Context) {
        val arr = JSONArray()
        for (s in stages) arr.put(JSONObject().put("id", s.profileId).put("model", s.model).put("role", s.role.key))
        prefs(ctx).edit().putString(scope, arr.toString()).apply()
    }

    fun resolve(spec: StageSpec): AiProfile? {
        val base = AiStore.profileOf(spec.profileId) ?: AiStore.active ?: return null
        return if (spec.model.isBlank()) base else base.withModel(spec.model)
    }

    companion object {
        val phone = StagePlan("phone")
        val ssh = StagePlan("ssh")
    }
}
