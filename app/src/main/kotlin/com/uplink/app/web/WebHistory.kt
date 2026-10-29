package com.uplink.app

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class Visit(val url: String, val title: String, val at: Long)

object WebHistory {
    private const val KEEP = 3000

    val entries = mutableStateListOf<Visit>()

    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private var loaded = false

    private fun file(): File = File(AppFolder.dir("browser"), "history.jsonl")

    private fun encode(v: Visit): String =
        JSONObject().put("u", v.url).put("t", v.title).put("a", v.at).toString()

    suspend fun load() {
        if (loaded) return
        val found = withContext(Dispatchers.IO) {
            val f = file()
            if (!f.isFile) return@withContext emptyList<Visit>()
            val all = ArrayList<Visit>()
            f.forEachLine { line ->
                try {
                    val o = JSONObject(line)
                    all.add(Visit(o.getString("u"), o.optString("t"), o.getLong("a")))
                } catch (_: Throwable) {
                }
            }
            if (all.size > KEEP) {
                val trimmed = all.subList(all.size - KEEP, all.size).toList()
                AppFolder.writeAtomic(f, trimmed.joinToString("\n", postfix = "\n") { encode(it) })
                trimmed
            } else {
                all
            }
        }
        entries.clear()
        entries.addAll(found.asReversed())
        loaded = true
    }

    fun add(url: String, title: String) {
        val last = entries.firstOrNull()
        if (last != null && last.url == url) return
        val visit = Visit(url, title, System.currentTimeMillis())
        entries.add(0, visit)
        scope.launch {
            try { file().appendText(encode(visit) + "\n") } catch (_: Throwable) { }
        }
    }

    fun remove(v: Visit) {
        entries.remove(v)
        rewrite()
    }

    fun clear() {
        entries.clear()
        rewrite()
    }

    private fun rewrite() {
        val snap = entries.asReversed().toList()
        scope.launch {
            try {
                AppFolder.writeAtomic(file(), snap.joinToString("\n", postfix = if (snap.isEmpty()) "" else "\n") { encode(it) })
            } catch (_: Throwable) {
            }
        }
    }
}
