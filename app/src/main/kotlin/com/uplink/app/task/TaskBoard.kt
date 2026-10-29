package com.uplink.app

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskStopped : Exception(tr("Stopped"))

enum class TaskState(private val key: String) {
    Running("RUNNING"),
    Done("DONE"),
    Failed("FAILED"),
    Stopped("STOPPED");

    val label: String get() = tr(key)
}

enum class TaskKind(private val key: String, val icon: ImageVector) {
    Send("Send", NxIcon.Send),
    Copy("Copy", NxIcon.Copy),
    Move("Move", NxIcon.Cut),
    Zip("Zip", NxIcon.Archive),
    Unzip("Unzip", NxIcon.Unarchive),
    Delete("Delete", NxIcon.Trash),
    Upload("Upload", NxIcon.Upload),
    Download("Download", NxIcon.Download),
    Open("Open", NxIcon.File),
    Ask("Ask AI", NxIcon.Bot),
    Agent("AI changes", NxIcon.Bot),
    Detect("Detect AI", NxIcon.Bot),
    Flow("AI workflow", NxIcon.Bolt),
    Git("GitHub", NxIcon.Git),
    Localhost("Localhost", NxIcon.Globe),
    Terminal("Terminal", NxIcon.Terminal),
    Install("Install APK", NxIcon.Download),
    Fetch("Fetch page", NxIcon.Globe),
    Run("Run script", NxIcon.Terminal),
    Package("Install package", NxIcon.Download),
    Mind("Mind", NxIcon.Bolt);

    val label: String get() = tr(key)
}

data class TaskBrief(val active: Int, val done: Int, val total: Int, val label: String)

class TaskRun(val id: Int, val kind: TaskKind, val title: String, val stoppable: Boolean) {
    var state by mutableStateOf(TaskState.Running)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var bytes by mutableLongStateOf(0L)
    var totalBytes by mutableLongStateOf(0L)
    var skipped by mutableIntStateOf(0)
    var detail by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var level by mutableStateOf<ErrorLevel?>(null)
    var endedAt by mutableLongStateOf(0L)
    val startedAt: Long = System.currentTimeMillis()
    @Volatile var stop = false

    val fraction: Float
        get() = when {
            totalBytes > 0 -> (bytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            total > 0 -> (done.toFloat() / total).coerceIn(0f, 1f)
            else -> -1f
        }

    fun counter(): String {
        val parts = ArrayList<String>()
        if (total > 0) parts.add("$done/$total") else if (done > 0) parts.add(tr("{0} files", done))
        if (bytes > 0) parts.add(humanSize(bytes) + if (totalBytes > 0) " / " + humanSize(totalBytes) else "")
        return parts.joinToString("  ")
    }

    fun speed(): String {
        val end = if (endedAt > 0) endedAt else System.currentTimeMillis()
        val secs = (end - startedAt) / 1000.0
        if (bytes <= 0 || secs < 1.0) return ""
        return humanSize((bytes / secs).toLong()) + "/s"
    }

    fun elapsed(): String {
        val end = if (endedAt > 0) endedAt else System.currentTimeMillis()
        val s = ((end - startedAt) / 1000).toInt()
        return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
    }
}

object TaskBoard {
    val runs = mutableStateListOf<TaskRun>()
    val brief = MutableStateFlow(TaskBrief(0, 0, 0, ""))
    var finished by mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var seq = 0
    @Volatile private var lastPublish = 0L

    val active: Int get() = runs.count { it.state == TaskState.Running }

    fun start(kind: TaskKind, title: String, stoppable: Boolean = true, work: (TaskRun) -> Unit): TaskRun {
        val run = TaskRun(++seq, kind, title, stoppable)
        runs.add(0, run)
        publish(true)
        TaskAlerts.running(run)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { work(run) }
                run.state = if (run.stop) TaskState.Stopped else TaskState.Done
            } catch (e: Throwable) {
                if (run.stop || e is TaskStopped) {
                    run.state = TaskState.Stopped
                } else {
                    run.error = e.message?.ifBlank { null } ?: e.toString()
                    run.level = ErrorLog.levelOf(e)
                    run.state = TaskState.Failed
                    ErrorLog.report(ErrorText("Task: {0}", run.title), e)
                }
            }
            run.endedAt = System.currentTimeMillis()
            finished++
            TaskAlerts.ended(run)
            publish(true)
        }
        return run
    }

    fun publish(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublish < 500) return
        lastPublish = now
        val live = runs.filter { it.state == TaskState.Running }
        val lead = live.firstOrNull()
        brief.value = TaskBrief(live.size, lead?.done ?: 0, lead?.total ?: 0, lead?.title ?: "")
        live.forEach { TaskAlerts.running(it) }
    }

    fun stopAll() {
        for (r in runs) if (r.state == TaskState.Running && r.stoppable) r.stop = true
    }

    fun dismiss(run: TaskRun) {
        if (run.state != TaskState.Running) runs.remove(run)
    }

    fun clearFinished() {
        runs.removeAll { it.state != TaskState.Running }
    }
}
