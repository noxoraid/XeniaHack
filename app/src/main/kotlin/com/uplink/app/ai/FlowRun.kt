package com.uplink.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

class StepResult(val step: FlowStep) {
    var state by mutableIntStateOf(WAITING)
    var text by mutableStateOf("")
    var model by mutableStateOf("")
    var thought by mutableStateOf("")
    var status by mutableStateOf("")

    companion object {
        const val WAITING = 0
        const val RUNNING = 1
        const val DONE = 2
        const val FAILED = 3
    }
}

object FlowRun {
    val results = mutableStateListOf<StepResult>()
    var title by mutableStateOf("")
        private set
    var current by mutableStateOf<TaskRun?>(null)
        private set

    val busy: Boolean get() = current?.state == TaskState.Running

    private var call: AiCall? = null
    private var ticket: MindTicket? = null
    private val fence = Regex("```[A-Za-z0-9_+.-]*[ \\t]*\\r?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

    fun finalText(): String = results.lastOrNull { it.state == StepResult.DONE }?.text.orEmpty()

    fun stop() {
        current?.stop = true
        call?.cancel()
    }

    fun start(ctx: Context, flow: FlowDef, input: String) {
        if (busy) return
        AiStore.load(ctx)
        results.clear()
        for (s in flow.steps) results.add(StepResult(s))
        title = flow.name
        ticket = if (MindGate.active()) MindGate.prepareLast() else null
        val rows = results.toList()
        current = TaskBoard.start(TaskKind.Flow, tr("Workflow {0}", flow.name)) { run ->
            run.total = rows.size
            val outputs = ArrayList<String>()
            for (row in rows) {
                if (run.stop) throw TaskStopped()
                row.state = StepResult.RUNNING
                run.detail = row.step.title.ifBlank { row.step.kind.title }
                TaskBoard.publish()
                try {
                    val text = when (row.step.kind) {
                        FlowKind.Ask -> ask(row, input, outputs, run)
                        FlowKind.ReadFile -> read(row, input, outputs)
                        FlowKind.SaveFile -> save(row, input, outputs)
                    }
                    row.text = text
                    outputs.add(text)
                    row.state = StepResult.DONE
                } catch (e: Exception) {
                    row.state = if (run.stop || e is TaskStopped) StepResult.WAITING else StepResult.FAILED
                    if (row.state == StepResult.FAILED && row.text.isEmpty()) row.text = e.message ?: e.toString()
                    throw e
                }
                run.done++
                TaskBoard.publish()
            }
        }
    }

    private fun fill(template: String, input: String, outputs: List<String>): String {
        var out = template.replace("{{input}}", input).replace("{{previous}}", outputs.lastOrNull().orEmpty())
        for (i in outputs.indices) out = out.replace("{{" + (i + 1) + "}}", outputs[i])
        return out
    }

    private fun ask(row: StepResult, input: String, outputs: List<String>, run: TaskRun): String {
        val base = AiStore.profileOf(row.step.profileId) ?: AiStore.active ?: throw Exception(tr("Add an AI provider first"))
        val profile = if (row.step.model.isBlank()) base else base.withModel(row.step.model)
        row.model = profile.name + " · " + profile.model
        val message = fill(row.step.prompt, input, outputs)
        if (message.isBlank()) throw Exception(tr("This step has no prompt"))
        val mindOn = MindGate.active()
        val summary = MindGate.context(MindSurface.Flow, ticket, message, false)
        val sent = if (summary.isEmpty()) profile else profile.withSystem(MindGate.system(profile.system, summary))
        val session = AiCall()
        session.tap = MindTap(MindSurface.Flow, mindOn)
        call = session
        val pending = StringBuilder()
        val thinking = StringBuilder()
        var flushedAt = SystemClock.elapsedRealtime()
        fun flush() {
            if (pending.isNotEmpty()) {
                row.text += pending.toString()
                pending.setLength(0)
            }
            if (thinking.isNotEmpty()) {
                row.thought = (row.thought + thinking.toString()).takeLast(30000)
                thinking.setLength(0)
            }
            flushedAt = SystemClock.elapsedRealtime()
        }
        try {
            session.stream(
                sent, listOf(AiMessage(true, message)), { run.stop },
                onThought = {
                    thinking.append(it)
                    if (SystemClock.elapsedRealtime() - flushedAt >= 50) flush()
                },
                onStatus = {
                    row.status = it
                    run.detail = profile.model + "  ·  " + it
                },
            ) { piece ->
                pending.append(piece)
                if (SystemClock.elapsedRealtime() - flushedAt >= 50) flush()
            }
        } catch (e: Exception) {
            if (!(run.stop || e is TaskStopped)) MindGate.outcome(MindSurface.Flow, mindOn, false, "provider error: " + (e.message ?: e.toString()))
            throw e
        } finally {
            flush()
            row.status = ""
            call = null
        }
        MindGate.outcome(MindSurface.Flow, mindOn, row.text.isNotEmpty(), "empty reply")
        return row.text
    }

    private fun read(row: StepResult, input: String, outputs: List<String>): String {
        val file = File(fill(row.step.path, input, outputs).trim())
        if (!file.isFile) throw Exception(tr("File not found: {0}", file.path))
        if (file.length() > 400_000L) throw Exception(tr("File too large to edit"))
        return file.readText(Charsets.UTF_8)
    }

    private fun save(row: StepResult, input: String, outputs: List<String>): String {
        val body = if (row.step.prompt.isBlank()) outputs.lastOrNull().orEmpty() else fill(row.step.prompt, input, outputs)
        val content = if (row.step.codeOnly) fence.find(body)?.groupValues?.get(1) ?: body else body
        val target = fill(row.step.path, input, outputs).trim()
        if (target.isEmpty()) throw Exception(tr("This step has no file path"))
        val file = File(target)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return file.path
    }
}
