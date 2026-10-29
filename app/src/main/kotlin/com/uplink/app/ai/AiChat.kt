package com.uplink.app

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AiTurn(val id: Long, val mine: Boolean, text: String, var settled: Boolean = false) {
    var text by mutableStateOf(text)
    var failed by mutableStateOf(false)
    var thought by mutableStateOf("")
    var status by mutableStateOf("")
}

object AiChat {
    val turns = mutableStateListOf<AiTurn>()

    var current by mutableStateOf<TaskRun?>(null)
        private set

    val busy: Boolean get() = current?.state == TaskState.Running

    private var call: AiCall? = null
    private var seq = 0L
    private var loaded = false

    private fun file(): File = File(AppFolder.dir("ai"), "chat.json")

    suspend fun load() {
        if (loaded) return
        loaded = true
        val saved = withContext(Dispatchers.IO) {
            val out = ArrayList<AiTurn>()
            try {
                val f = file()
                if (f.isFile && f.length() < 8_000_000) {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        out.add(AiTurn(++seq, o.getBoolean("m"), o.getString("t"), true))
                    }
                }
            } catch (_: Exception) {
            }
            out
        }
        turns.addAll(0, saved)
    }

    fun ask(text: String) {
        val profile = AiStore.active ?: return
        val clean = text.trim()
        if (busy || clean.isEmpty()) return
        turns.add(AiTurn(++seq, true, clean))
        val reply = AiTurn(++seq, false, "")
        turns.add(reply)
        val history = turns.filter { it !== reply && !it.failed }.map { AiMessage(it.mine, it.text) }
        val mindOn = MindGate.active()
        val ticket = if (mindOn) MindGate.prepareLast() else null
        val session = AiCall()
        session.tap = MindTap(MindSurface.Chat, mindOn)
        call = session
        current = TaskBoard.start(TaskKind.Ask, tr("Ask {0}", profile.name)) { run ->
            run.detail = profile.model
            val summary = MindGate.context(MindSurface.Chat, ticket, clean, false)
            val sent = if (summary.isEmpty()) profile else profile.withSystem(MindGate.system(profile.system, summary))
            val pending = StringBuilder()
            val thinking = StringBuilder()
            var flushedAt = SystemClock.elapsedRealtime()
            fun flush() {
                if (pending.isNotEmpty()) {
                    reply.text += pending.toString()
                    pending.setLength(0)
                }
                if (thinking.isNotEmpty()) {
                    reply.thought = (reply.thought + thinking.toString()).takeLast(30000)
                    thinking.setLength(0)
                }
                flushedAt = SystemClock.elapsedRealtime()
            }
            try {
                session.stream(
                    sent, history, { run.stop },
                    onThought = {
                        thinking.append(it)
                        if (SystemClock.elapsedRealtime() - flushedAt >= 50) flush()
                    },
                    onStatus = {
                        reply.status = it
                        run.detail = profile.model + "  ·  " + it
                    },
                ) { piece ->
                    pending.append(piece)
                    if (SystemClock.elapsedRealtime() - flushedAt >= 50) flush()
                }
                flush()
                if (session.cut && reply.text.isNotEmpty()) reply.text += "\n\n" + tr("The connection dropped before the reply finished")
                MindGate.outcome(MindSurface.Chat, mindOn, reply.text.isNotEmpty(), "empty reply")
                if (reply.text.isEmpty()) {
                    reply.failed = true
                    reply.text = if (session.truncated) tr("The model ran out of output tokens before it answered. Raise max_tokens in the provider's Advanced options, or ask for a smaller result") else tr("The model returned an empty reply")
                }
                reply.status = ""
            } catch (e: Exception) {
                flush()
                if (run.stop || e is TaskStopped) {
                    if (reply.text.isEmpty()) turns.remove(reply)
                    throw TaskStopped()
                }
                val message = e.message?.ifBlank { null } ?: e.toString()
                MindGate.outcome(MindSurface.Chat, mindOn, false, "provider error: $message")
                reply.failed = true
                reply.text = if (reply.text.isEmpty()) message else reply.text + "\n\n" + message
                throw e
            } finally {
                save()
            }
        }
    }

    fun stop() {
        current?.stop = true
        call?.cancel()
    }

    fun clear() {
        stop()
        turns.clear()
        save()
    }

    private fun save() {
        try {
            val arr = JSONArray()
            for (t in turns.toList().filter { it.text.isNotEmpty() }.takeLast(200)) {
                arr.put(JSONObject().put("m", t.mine).put("t", t.text))
            }
            AppFolder.writeAtomic(file(), arr.toString())
        } catch (_: Exception) {
        }
    }
}
