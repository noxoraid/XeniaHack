package com.uplink.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class EntryKind { Prompt, Stage, Live, Thought, Say, Command, Files, Done, Failed }

class TermEntry(val kind: EntryKind, text: String, val ai: Boolean = false) {
    var text by mutableStateOf(text)
    var output by mutableStateOf("")
    var body by mutableStateOf("")
    var thought by mutableStateOf("")
    var status by mutableStateOf("")
    var received by mutableIntStateOf(0)
    var state by mutableStateOf(AgentStep.Running)
    var open by mutableStateOf(kind == EntryKind.Command)
    val startedAt: Long = System.currentTimeMillis()
}

fun termChatFile(key: String): File = File(AppFolder.dir("terminal"), key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80) + ".json")

private class StageReply(val say: String, val commands: List<String>, val files: List<Pair<String, String>>, val done: Boolean, val broken: Boolean = false)

private fun stringsOf(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    val out = ArrayList<String>()
    for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
    return out
}

private fun filesOf(arr: JSONArray?): List<Pair<String, String>> {
    if (arr == null) return emptyList()
    val out = ArrayList<Pair<String, String>>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val path = o.optString("path").trim()
        if (path.isNotEmpty()) out.add(path to (if (o.isNull("content")) "" else o.optString("content")))
    }
    return out
}

private fun parseStage(raw: String): StageReply {
    val text = AiClean.clean(raw)
    val tools = AiClean.toolCommands(raw)
    val from = text.indexOf('{')
    val to = text.lastIndexOf('}')
    if (from < 0 || to <= from) return StageReply(text, tools, emptyList(), tools.isEmpty())
    return try {
        val o = JSONObject(text.substring(from, to + 1))
        val commands = stringsOf(o.optJSONArray("commands")).ifEmpty { stringsOf(o.optJSONArray("console")) }.ifEmpty { tools }
        val files = filesOf(o.optJSONArray("files")).ifEmpty { filesOf(o.optJSONArray("actions")) }
        StageReply(AiClean.clean(o.optString("say")), commands, files, o.optBoolean("done", false))
    } catch (_: Exception) {
        StageReply(AiClean.clean(text.substring(0, from)), tools, emptyList(), tools.isEmpty(), tools.isEmpty())
    }
}

fun streamGlimpse(view: StreamView): String {
    val out = StringBuilder()
    for (c in view.commands) out.append("$ ").append(c).append('\n')
    for ((path, content) in view.files) {
        val rows = content.split('\n')
        out.append("+ ").append(path).append("  (").append(rows.size).append(" lines)\n")
        for (row in rows.takeLast(10)) out.append("  ").append(row.take(160)).append('\n')
    }
    return out.toString().trimEnd()
}

private fun entryJson(e: TermEntry): JSONObject = JSONObject()
    .put("k", e.kind.name)
    .put("t", e.text.take(20000))
    .put("o", e.output.takeLast(12000))
    .put("b", e.body.take(40000))
    .put("h", e.thought.takeLast(8000))
    .put("s", e.state.name)

private fun entryOf(o: JSONObject): TermEntry? {
    val kind = try { EntryKind.valueOf(o.getString("k")) } catch (_: Exception) { return null }
    if (kind == EntryKind.Live) return null
    val e = TermEntry(kind, o.optString("t"))
    e.output = o.optString("o")
    e.body = o.optString("b")
    e.thought = o.optString("h")
    val state = try { AgentStep.valueOf(o.optString("s")) } catch (_: Exception) { AgentStep.Done }
    e.state = if (state == AgentStep.Running) AgentStep.Waiting else state
    e.open = kind == EntryKind.Command
    return e
}

class TermAgent(val target: AgentTarget, private val plan: StagePlan, private val store: File? = null) {
    val entries = mutableStateListOf<TermEntry>()
    var current by mutableStateOf<TaskRun?>(null)
        private set

    val busy: Boolean get() = current?.state == TaskState.Running

    private var call: AiCall? = null
    @Volatile private var compact = false
    private lateinit var appContext: Context
    private var restored = false
    val attached = mutableStateListOf<String>()
    var openedDir: String? = null

    private val surface: MindSurface = if (target is VpsAgentTarget) MindSurface.Ssh else MindSurface.Phone

    fun focus() {
        MindGate.focus(target.source, target.cwd)
    }

    suspend fun restore() {
        if (restored || store == null) return
        restored = true
        val saved = withContext(Dispatchers.IO) {
            val out = ArrayList<TermEntry>()
            try {
                if (store.isFile && store.length() < 6_000_000) {
                    val arr = JSONArray(store.readText())
                    for (i in 0 until arr.length()) entryOf(arr.getJSONObject(i))?.let { out.add(it) }
                }
            } catch (_: Exception) {
            }
            out
        }
        entries.addAll(0, saved)
    }

    private fun save() {
        if (store == null) return
        try {
            val arr = JSONArray()
            for (e in entries.toList().filter { it.kind != EntryKind.Live }.takeLast(150)) arr.put(entryJson(e))
            AppFolder.writeAtomic(store, arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun rules(stage: StageSpec, index: Int, count: Int, handoff: String, summary: String): String = """
You are an AI agent inside a terminal. The user may write in any language; answer in the same language.
You work by sending commands and files, they run at once and you receive the output, then you continue until your part is finished.
Reply with ONE JSON object and nothing else, no markdown fences:
{"say":"short note for the user","commands":[],"files":[{"path":"...","content":"..."}],"done":false}
commands: command lines run in order. files: complete files to create or replace, paths may be relative to the current folder.
Set done to true when your part is finished and nothing else needs to run. Do not repeat work that already succeeded. Escape every double quote and newline inside JSON strings.
If a command fails, read the output and fix it before declaring done.
Never write tables with the | character. List one line per item: name, size in KB, MB or GB, a short note, largest first, total on the first line. A folder that was not measured is "not counted yet", never a guess.
If a command is refused as unknown or unavailable, switch to the nx equivalent the refusal names and never retry other spellings of it. At most ${AgentLimits.tries} attempts per goal; after that report what failed and what was tried.
Your role (stage ${index + 1} of $count): ${if (count == 1) StageRole.Whole.brief else stage.role.brief}
${if (handoff.isBlank()) "" else "Result of the previous stage:\n$handoff\n"}
Environment:
Location: ${target.place}
Current folder: ${target.cwd}
Current folder contents:
${target.listing()}
Tools:
${if (compact) OneTool.spec(target) else target.toolbox}${if (summary.isEmpty()) "" else "\n\nProject summary (Mind):\n$summary"}
""".trim()

    private fun credit() {
        if (compact) OneTool.credit(appContext, target)
    }

    fun send(ctx: Context, prompt: String) {
        val clean = prompt.trim()
        if (busy || clean.isEmpty()) return
        AiStore.load(ctx)
        AgentLimits.load(ctx)
        compact = Cfg.oneTool(ctx)
        appContext = ctx.applicationContext
        val specs = plan.stages.toList()
        val profiles = specs.map { plan.resolve(it) }
        if (profiles.isEmpty() || profiles.any { it == null }) {
            toast(ctx, tr("Add an AI provider first"))
            return
        }
        val files = synchronized(attached) { attached.toList().also { attached.clear() } }
        val prompt = if (files.isEmpty()) clean else clean + "\n\nAttached files, already saved in the current folder:\n" + files.joinToString("\n")
        val shown = if (files.isEmpty()) clean else clean + "\n" + tr("Attached: {0}", files.joinToString(", ") { it.substringAfterLast('/') })
        entries.add(TermEntry(EntryKind.Prompt, shown).also { it.state = AgentStep.Done })
        val mindOn = MindGate.active()
        val ticket = if (mindOn) MindGate.prepare(target.source, target.cwd) else null
        val session = AiCall()
        session.tap = MindTap(surface, mindOn)
        call = session
        current = TaskBoard.start(TaskKind.Ask, tr("File AI")) { run ->
            var handoff = ""
            run.total = specs.size
            save()
            try {
                val summary = MindGate.context(surface, ticket, prompt, compact)
                for ((index, spec) in specs.withIndex()) {
                    if (run.stop) throw TaskStopped()
                    val profile = profiles[index]!!
                    run.done = index
                    run.detail = profile.model
                    TaskBoard.publish()
                    val head = TermEntry(EntryKind.Stage, tr("{0}/{1}  {2}  {3}", index + 1, specs.size, profile.model, spec.role.title))
                    head.output = tr("Thinking")
                    entries.add(head)
                    handoff = stage(spec, index, specs.size, profile, prompt, handoff, head, session, run, summary, mindOn)
                    head.state = AgentStep.Done
                    head.output = ""
                    run.done = index + 1
                    TaskBoard.publish()
                }
                val done = TermEntry(EntryKind.Done, tr("Task complete"))
                done.state = AgentStep.Done
                entries.add(done)
            } catch (e: Exception) {
                entries.lastOrNull { it.kind == EntryKind.Stage && it.state == AgentStep.Running }?.let {
                    it.state = if (run.stop || e is TaskStopped) AgentStep.Waiting else AgentStep.Failed
                    it.output = ""
                }
                entries.removeAll { it.kind == EntryKind.Live }
                if (run.stop || e is TaskStopped) {
                    entries.add(TermEntry(EntryKind.Failed, tr("Stopped")))
                    throw TaskStopped()
                }
                entries.add(TermEntry(EntryKind.Failed, e.message?.ifBlank { null } ?: e.toString()))
                throw e
            } finally {
                save()
            }
        }
    }

    private fun stage(
        spec: StageSpec, index: Int, count: Int, profile: AiProfile, prompt: String, handoff: String,
        head: TermEntry, session: AiCall, run: TaskRun, summary: String, mindOn: Boolean,
    ): String {
        val feed = ArrayList<AiMessage>()
        feed.add(AiMessage(true, prompt))
        val notes = StringBuilder()
        val ledger = NxRetry(target !is VpsAgentTarget, ConsoleSession.reads + ConsoleSession.writes + setOf("help", "date", "echo"), NxCatalog.readyNames().toSet(), AgentLimits.tries)
        var rounds = 0
        var truncatedStreak = 0
        var emptyStreak = 0
        while (true) {
            if (run.stop) throw TaskStopped()
            head.output = tr("Thinking")
            credit()
            val ai = profile.withSystem(rules(spec, index, count, handoff, summary))
            val out = StringBuilder()
            val thoughts = StringBuilder()
            val live = TermEntry(EntryKind.Live, "")
            live.status = tr("Connecting")
            entries.add(live)
            var shownAt = 0L
            fun refresh(force: Boolean) {
                val now = SystemClock.elapsedRealtime()
                if (!force && now - shownAt < 100) return
                shownAt = now
                val view = streamViewOf(out.toString())
                live.text = NxNoPipes.clean(view.say)
                live.output = streamGlimpse(view)
                live.thought = thoughts.takeLast(6000).toString()
                live.received = out.length + thoughts.length
                val tail = out.substring(maxOf(0, out.length - 400))
                head.output = when {
                    tail.contains("\"content\"") -> tr("Writing code")
                    tail.contains("\"commands\"") -> tr("Running")
                    else -> tr("Thinking")
                }
            }
            try {
                session.stream(
                    ai, feed, { run.stop },
                    onThought = {
                        thoughts.append(it)
                        refresh(false)
                    },
                    onStatus = {
                        live.status = it
                        run.detail = profile.model + "  ·  " + it
                    },
                ) { piece ->
                    out.append(piece)
                    if (live.status != tr("Receiving")) live.status = tr("Receiving")
                    refresh(false)
                }
                refresh(true)
            } catch (e: Exception) {
                if (!(run.stop || e is TaskStopped)) MindGate.outcome(surface, mindOn, false, "provider error: " + (e.message ?: e.toString()))
                throw e
            } finally {
                entries.remove(live)
            }
            head.output = ""
            if (thoughts.isNotEmpty()) {
                entries.add(TermEntry(EntryKind.Thought, thoughts.toString()).also { it.state = AgentStep.Done })
            }
            if (out.isBlank()) {
                MindGate.outcome(surface, mindOn, false, "empty reply")
                entries.add(TermEntry(EntryKind.Failed, if (session.truncated) tr("The model ran out of output tokens before it answered. Raise max_tokens in the provider's Advanced options, or ask for a smaller result") else tr("The model returned an empty reply")))
                save()
                emptyStreak++
                if (emptyStreak > AgentLimits.EMPTY_RETRIES) return notes.toString().takeLast(6000)
                feed.add(AiMessage(true, "RESULTS\nYour last reply was empty. Send ONE complete JSON object now."))
                continue
            }
            emptyStreak = 0
            val reply = parseStage(out.toString()).let { StageReply(NxNoPipes.clean(it.say), it.commands, it.files, it.done, it.broken) }
            if (reply.say.isNotEmpty()) {
                entries.add(TermEntry(EntryKind.Say, reply.say).also { it.state = AgentStep.Done })
                notes.append(reply.say).append('\n')
            }
            if (!reply.broken) {
                truncatedStreak = 0
                MindGate.outcome(surface, mindOn, true, "")
            }
            if (reply.broken) {
                MindGate.outcome(surface, mindOn, false, "invalid JSON")
                entries.add(TermEntry(EntryKind.Failed, if (session.truncated) tr("The model ran out of output tokens in the middle of its reply. Raise max_tokens in the provider's Advanced options, or ask for a smaller result") else tr("The reply was cut off or is not valid JSON")))
                save()
                if (session.truncated) {
                    truncatedStreak++
                    if (truncatedStreak > AgentLimits.TRUNCATED_RETRIES) return notes.toString().takeLast(6000)
                } else {
                    if (rounds >= target.rounds) return notes.toString().takeLast(6000)
                    rounds++
                }
                feed.add(AiMessage(false, out.toString().takeLast(3000)))
                feed.add(AiMessage(true, if (session.truncated) "RESULTS\nYour last reply hit the output limit and was cut off, nothing was run. Send ONE complete JSON object again. Split the work into several smaller files, each under 120 lines, and keep the notes short." else "RESULTS\nYour last reply was cut off or is not valid JSON, nothing was run. Send ONE complete JSON object again, with shorter files if needed."))
                continue
            }
            val report = StringBuilder()
            for ((path, content) in reply.files.take(40)) {
                if (run.stop) throw TaskStopped()
                val row = TermEntry(EntryKind.Files, path)
                row.body = content
                entries.add(row)
                target.begin("# " + path)
                try {
                    target.write(path, content)
                    target.end(ConsoleOut(path, true))
                    row.state = AgentStep.Done
                    row.output = tr("{0} lines", content.count { it == '\n' } + 1)
                    report.append("WROTE ").append(path).append('\n')
                    notes.append("wrote ").append(path).append('\n')
                } catch (e: Exception) {
                    row.state = AgentStep.Failed
                    row.output = e.message ?: e.toString()
                    target.end(ConsoleOut(row.output, false))
                    report.append("FAILED to write ").append(path).append(": ").append(row.output).append('\n')
                }
                save()
            }
            for (line in reply.commands.take(30)) {
                if (run.stop) throw TaskStopped()
                val row = TermEntry(EntryKind.Command, line)
                entries.add(row)
                target.begin(line)
                val result = try {
                    OneTool.run(target, appContext, line, { run.stop }, ledger) { row.output = it.takeLast(6000) }
                } catch (e: TaskStopped) {
                    throw e
                } catch (e: Exception) {
                    ConsoleOut(e.message ?: e.toString(), false)
                }
                target.end(result)
                row.output = result.text.takeLast(6000)
                row.state = if (result.ok) AgentStep.Done else AgentStep.Failed
                notes.append("$ ").append(line).append(if (result.ok) "  (ok)" else "  (failed)").append('\n')
                if (report.length < 14000) report.append("$ ").append(line).append('\n').append(OneTool.forModel(result.text)).append("\n\n")
                TaskBoard.publish()
                save()
            }
            val acted = reply.commands.isNotEmpty() || reply.files.isNotEmpty()
            if (!acted || reply.done || rounds >= target.rounds) {
                if (acted && !reply.done && rounds >= target.rounds) {
                    entries.add(TermEntry(EntryKind.Say, tr("Stopped after {0} rounds", target.rounds)).also { it.state = AgentStep.Done })
                }
                save()
                return notes.toString().takeLast(6000)
            }
            rounds++
            feed.add(AiMessage(false, out.toString()))
            feed.add(AiMessage(true, "RESULTS\n$report\nCurrent folder: ${target.cwd}"))
        }
    }

    fun stop() {
        current?.stop = true
        call?.cancel()
    }

    fun clear() {
        stop()
        entries.clear()
        save()
    }
}
