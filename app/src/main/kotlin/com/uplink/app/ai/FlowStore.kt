package com.uplink.app

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class FlowKind(val key: String, private val label: String) {
    Ask("ask", "AI step"),
    ReadFile("read", "Read file"),
    SaveFile("save", "Save file");

    val title: String get() = tr(label)

    companion object {
        fun of(key: String): FlowKind = entries.firstOrNull { it.key == key } ?: Ask
    }
}

data class FlowStep(
    val id: String,
    val kind: FlowKind,
    val title: String = "",
    val profileId: String = "",
    val model: String = "",
    val prompt: String = "",
    val path: String = "",
    val codeOnly: Boolean = false,
)

data class FlowDef(val id: String, val name: String, val steps: List<FlowStep>)

object FlowStore {
    val flows = mutableStateListOf<FlowDef>()
    private var loaded = false

    private fun file(): File = File(AppFolder.dir("ai"), "flows.json")

    suspend fun load() {
        if (loaded) return
        loaded = true
        val saved = withContext(Dispatchers.IO) {
            val out = ArrayList<FlowDef>()
            try {
                val f = file()
                if (f.isFile && f.length() < 4_000_000) {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) out.add(flowOf(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {
            }
            out
        }
        flows.addAll(saved)
    }

    private fun flowOf(o: JSONObject): FlowDef {
        val arr = o.optJSONArray("steps") ?: JSONArray()
        val steps = ArrayList<FlowStep>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            steps.add(
                FlowStep(
                    s.optString("id").ifBlank { newId() }, FlowKind.of(s.optString("kind")), s.optString("title"),
                    s.optString("profile"), s.optString("model"), s.optString("prompt"), s.optString("path"),
                    s.optBoolean("code", false),
                ),
            )
        }
        return FlowDef(o.optString("id").ifBlank { newId() }, o.optString("name"), steps)
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            for (f in flows) {
                val steps = JSONArray()
                for (s in f.steps) {
                    steps.put(
                        JSONObject().put("id", s.id).put("kind", s.kind.key).put("title", s.title).put("profile", s.profileId)
                            .put("model", s.model).put("prompt", s.prompt).put("path", s.path).put("code", s.codeOnly),
                    )
                }
                arr.put(JSONObject().put("id", f.id).put("name", f.name).put("steps", steps))
            }
            AppFolder.writeAtomic(file(), arr.toString())
        } catch (_: Exception) {
        }
    }

    fun save(flow: FlowDef) {
        val at = flows.indexOfFirst { it.id == flow.id }
        if (at >= 0) flows[at] = flow else flows.add(flow)
        persist()
    }

    fun remove(id: String) {
        flows.removeAll { it.id == id }
        persist()
    }

    fun newId(): String = java.lang.Long.toString(System.nanoTime(), 36)

    fun example(): FlowDef = FlowDef(
        newId(), tr("Read then write code"),
        listOf(
            FlowStep(newId(), FlowKind.Ask, tr("Reader"), prompt = tr("Read the request below and list the exact requirements as short bullet points.\n\n{{input}}")),
            FlowStep(newId(), FlowKind.Ask, tr("Coder"), prompt = tr("Write the complete code that satisfies these requirements. Reply with code only.\n\n{{previous}}")),
            FlowStep(newId(), FlowKind.SaveFile, tr("Save"), path = File(AppFolder.dir("ai"), "result.txt").absolutePath, codeOnly = true),
        ),
    )
}
