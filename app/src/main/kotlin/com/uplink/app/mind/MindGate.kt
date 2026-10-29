package com.uplink.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

enum class MindState(private val key: String) {
    Idle("Not indexed"),
    Checking("Checking"),
    Building("Building"),
    Fresh("Up to date"),
    Stale("Stale"),
    Invalid("Invalid"),
    Failed("Failed"),
    NoProject("No project found");

    val label: String get() = tr(key)
}

enum class MindMode { Ensure, Force, Check }

class MindProjectView(
    val name: String,
    val root: String,
    val folder: String,
    val state: MindState,
    val detail: String,
    val fileCount: Int,
    val indexedAt: Long,
)

class MindTicket(val folderKey: String) {
    val done = CountDownLatch(1)
    @Volatile var root: String = ""
}

class MindTap(val surface: MindSurface, val on: Boolean)

private class MindInvalid(message: String) : Exception(message)

object MindGate {
    const val WAIT_MS = 3000L
    private const val FRESH_MS = 30_000L
    private const val MAX_FINDINGS = 40

    private lateinit var app: Context
    @Volatile private var ready = false
    private val pause = MindPause()

    val meter = MindMeter()
    val findings = mutableStateListOf<MindFinding>()

    var project by mutableStateOf<MindProjectView?>(null)
        private set

    var notice by mutableStateOf("")
        private set

    var tick by mutableIntStateOf(0)
        private set

    var lastSummary by mutableStateOf("")
        private set

    var lastSummaryNote by mutableStateOf("")
        private set

    private val docs = HashMap<String, MindIndexDoc>()
    private val states = HashMap<String, MindState>()
    private val roots = HashMap<String, String>()
    private val checked = HashMap<String, Long>()
    private val inflight = HashMap<String, MindTicket>()

    @Volatile private var lastFs: FsBackend? = null
    @Volatile private var lastFolder: String = ""

    private fun prefs() = app.getSharedPreferences("nx_mind", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        meter.decode(prefs().getString("meter", "").orEmpty())
        ready = true
    }

    fun active(): Boolean = ready && Cfg.mind(app) && !pause.paused

    fun enabled(): Boolean = ready && Cfg.mind(app)

    fun hasFolder(): Boolean = lastFs != null && lastFolder.isNotEmpty()

    private fun note(level: MindLevel, check: String, message: String, path: String = "") {
        val entry = MindFinding(level, check, message, path)
        synchronized(findings) {
            val top = findings.firstOrNull()
            if (top != null && top.check == check && top.message == message) return
            findings.add(0, entry)
            while (findings.size > MAX_FINDINGS) findings.removeAt(findings.size - 1)
        }
    }

    private fun announce() {
        notice = pause.reason
        note(MindLevel.Error, "pause", pause.reason)
        toast(app, tr("Mind paused for this session. Open Mind to turn it back on."))
    }

    fun resume() {
        pause.resume()
        notice = ""
        note(MindLevel.Ok, "pause", "Mind turned back on for this session")
    }

    fun prepare(fs: FsBackend, folder: String): MindTicket? {
        if (!active() || folder.isBlank()) return null
        lastFs = fs
        lastFolder = folder
        return ensure(fs, folder, MindMode.Ensure)
    }

    fun prepareLast(): MindTicket? {
        val fs = lastFs ?: return null
        return prepare(fs, lastFolder)
    }

    fun focus(fs: FsBackend, folder: String) {
        prepare(fs, folder)
    }

    fun rebuild() {
        if (!enabled()) {
            toast(app, tr("Turn Mind on first"))
            return
        }
        val fs = lastFs
        if (fs == null) {
            note(MindLevel.Warning, "rebuild", "No folder has been used by an AI yet, nothing to rebuild")
            return
        }
        ensure(fs, lastFolder, MindMode.Force)
    }

    fun check() {
        val fs = lastFs ?: return
        if (!active()) return
        ensure(fs, lastFolder, MindMode.Check)
    }

    fun delete() {
        TaskBoard.start(TaskKind.Mind, tr("Delete Mind index"), false) { _ ->
            try {
                val removed = MindStore.deleteAll()
                synchronized(docs) {
                    docs.clear()
                    states.clear()
                    roots.clear()
                    checked.clear()
                }
                project = null
                note(MindLevel.Ok, "delete", "Index deleted: $removed project folders removed")
            } catch (e: Exception) {
                note(MindLevel.Error, "delete", e.message ?: e.toString())
                throw e
            }
        }
    }

    private fun ensure(fs: FsBackend, folder: String, mode: MindMode): MindTicket {
        val folderKey = fs.label + "|" + folder
        val key = mode.name + "|" + folderKey
        val running = synchronized(inflight) { inflight[key] }
        if (running != null) return running
        val ticket = MindTicket(folderKey)
        val known = synchronized(docs) { roots[folderKey] }
        if (mode != MindMode.Force && known != null) {
            val at = synchronized(docs) { checked[known] } ?: 0L
            val fresh = synchronized(docs) { states[known] } == MindState.Fresh
            if (fresh && SystemClock.elapsedRealtime() - at < FRESH_MS) {
                ticket.root = known
                ticket.done.countDown()
                return ticket
            }
        }
        synchronized(inflight) { inflight[key] = ticket }
        TaskBoard.start(TaskKind.Mind, tr("Mind index"), true) { run ->
            try {
                index(fs, folder, mode, run, ticket)
            } finally {
                synchronized(inflight) { inflight.remove(key) }
                ticket.done.countDown()
            }
        }
        return ticket
    }

    private fun mark(folder: String, root: String, state: MindState, detail: String) {
        val doc = synchronized(docs) {
            states[root] = state
            docs[root]
        }
        project = MindProjectView(
            MindDetect.nameOf(root), root, folder, state, detail,
            doc?.header?.fileCount ?: 0, doc?.header?.indexedAt ?: 0L,
        )
    }

    private fun stored(root: String): MindIndexDoc? {
        val cached = synchronized(docs) { docs[root] }
        if (cached != null) {
            if (MindStore.file(root).isFile) return cached
            synchronized(docs) { docs.remove(root) }
        }
        val text = MindStore.read(root) ?: return null
        val verdict = MindValidate.check(text)
        if (verdict.level != MindLevel.Ok) {
            note(MindLevel.Warning, "existing", "Stored index is invalid (${verdict.check}: ${verdict.message}), building a new one", MindStore.file(root).path)
            return null
        }
        return MindIndexFile.parse(text).doc
    }

    private fun failBuild(key: String, message: String) {
        if (pause.build(key, false, message)) announce()
    }

    private fun index(fs: FsBackend, folder: String, mode: MindMode, run: TaskRun, ticket: MindTicket) {
        val source = MindFsSource(fs)
        val limits = if (isLocal(fs)) MindLimits.local else MindLimits.remote
        val halt = { run.stop }
        var root = folder
        try {
            mark(folder, folder, MindState.Checking, "")
            run.detail = tr("Detecting project")
            TaskBoard.publish(true)
            val found = MindDetect.root(folder, source)
            if (found == null) {
                mark(folder, folder, MindState.NoProject, folder)
                return
            }
            root = found
            ticket.root = found
            synchronized(docs) { roots[ticket.folderKey] = found }
            val old = stored(found)
            if (old != null) synchronized(docs) { docs[found] = old }
            run.detail = tr("Scanning {0}", MindDetect.nameOf(found))
            TaskBoard.publish(true)
            val began = System.currentTimeMillis()
            val scan = MindWalk.scan(found, source, limits, halt) {
                run.done = it
                TaskBoard.publish()
            }
            val verdict = old?.let { MindChecks.stale(it.header, scan.newestModified, scan.signature) }
            val stale = verdict?.stale == true
            if (old != null && !stale && mode != MindMode.Force) {
                mark(folder, found, MindState.Fresh, "")
                synchronized(docs) { checked[found] = SystemClock.elapsedRealtime() }
                pause.build(found, true, "")
                return
            }
            if (stale && verdict != null) {
                mark(folder, found, MindState.Stale, verdict.reason)
                note(MindLevel.Warning, "stale", "$found: ${verdict.reason}")
            }
            if (mode == MindMode.Check) {
                if (old == null) mark(folder, found, MindState.Idle, "")
                return
            }
            mark(folder, found, MindState.Building, "")
            run.total = scan.refs.size
            run.done = 0
            val built = MindWalk.build(found, source, scan, limits, began, halt) { done, total, name ->
                run.done = done
                run.total = total
                run.detail = name
                TaskBoard.publish()
            }
            val verdictWritten = MindStore.write(built.doc)
            if (verdictWritten.level != MindLevel.Ok) {
                val message = "${verdictWritten.check}: ${verdictWritten.message} (${MindStore.file(found).path})"
                synchronized(docs) { docs.remove(found) }
                mark(folder, found, MindState.Invalid, message)
                note(MindLevel.Error, "validate", message, MindStore.file(found).path)
                failBuild(found, message)
                throw MindInvalid(message)
            }
            synchronized(docs) {
                docs[found] = built.doc
                checked[found] = SystemClock.elapsedRealtime()
            }
            mark(folder, found, MindState.Fresh, "")
            for (problem in built.problems.take(5)) note(MindLevel.Warning, "index", problem)
            if (built.problems.size > 5) note(MindLevel.Warning, "index", "${built.problems.size - 5} more problems while indexing $found")
            if (built.doc.header.truncated) note(MindLevel.Warning, "index", "$found is larger than the index limits, the index is partial")
            pause.build(found, true, "")
        } catch (e: MindCancelled) {
            throw TaskStopped()
        } catch (e: TaskStopped) {
            throw e
        } catch (e: Exception) {
            if (e !is MindInvalid) {
                val message = e.message ?: e.toString()
                mark(folder, root, MindState.Failed, message)
                note(MindLevel.Error, "build", message)
                failBuild(root, message)
            }
            throw e
        }
    }

    fun context(surface: MindSurface, ticket: MindTicket?, request: String, withNx: Boolean): String {
        if (ticket == null || !active()) return ""
        val finished = try {
            ticket.done.await(WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        val root = ticket.root.ifEmpty { synchronized(docs) { roots[ticket.folderKey] }.orEmpty() }
        val doc = if (root.isEmpty()) null else synchronized(docs) { docs[root] }
        if (!finished) {
            note(MindLevel.Warning, "wait", "The index was not ready after 3 seconds, the request used " + if (doc != null) "the existing index" else "no summary")
        }
        if (doc == null) {
            if (finished && root.isNotEmpty()) note(MindLevel.Warning, "summary", "No index is available for $root, the request was sent without a summary")
            return ""
        }
        if (synchronized(docs) { states[root] } == MindState.Invalid) return ""
        if (!surface.tools && MindSummary.order(doc, request).none { it.score > 0 }) return ""
        val budget = MindSummary.clampBudget(Cfg.mindBudget(app))
        val raw = MindSummary.build(doc, request, budget, MindSummary.readLine(withNx, surface.tools))
        val verdict = MindChecks.summary(raw, budget, true)
        for (f in verdict.findings) note(f.level, f.check, f.message)
        lastSummary = verdict.text
        lastSummaryNote = surface.key + " | " + verdict.text.length + " / " + budget + " | " + doc.header.name
        return verdict.text
    }

    fun system(base: String, summary: String): String =
        if (summary.isEmpty()) base else listOf(base, "Project summary (Mind):\n$summary").filter { it.isNotBlank() }.joinToString("\n\n")

    fun measure(surface: MindSurface, on: Boolean, chars: Int) {
        if (!ready) return
        meter.record(surface, on, chars)
        if (on) MindChecks.size(surface, chars, meter)?.let { note(it.level, it.check, it.message) }
        prefs().edit().putString("meter", meter.encode()).apply()
        tick++
    }

    fun outcome(surface: MindSurface, on: Boolean, ok: Boolean, why: String) {
        if (!ready || !on) return
        if (pause.request(true, ok, "${surface.key}: $why")) announce()
    }
}
