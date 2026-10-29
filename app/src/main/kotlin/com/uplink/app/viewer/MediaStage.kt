package com.uplink.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

object MediaStage {
    private fun dir(ctx: Context): File = File(ctx.cacheDir, "stage").also { it.mkdirs() }

    fun sweep(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    fun target(ctx: Context, t: ViewTarget): File {
        val tag = (t.fs.label + t.path + t.size).hashCode().toUInt().toString(16)
        return File(dir(ctx), tag + "_" + t.path.substringAfterLast('/'))
    }

    fun start(t: ViewTarget, dest: File): TaskRun {
        val name = t.path.substringAfterLast('/')
        return TaskBoard.start(TaskKind.Open, tr("Prepare {0}", name), true) { run ->
            run.detail = name
            run.totalBytes = t.size
            val temp = File(dest.parentFile, dest.name + ".part")
            try {
                t.fs.open(t.path).use { raw ->
                    TrackedIn(raw, run).use { tracked ->
                        temp.outputStream().use { out -> tracked.copyTo(out, 65536) }
                    }
                }
                dest.delete()
                if (!temp.renameTo(dest)) throw java.io.IOException(tr("Cannot stage {0}", name))
            } finally {
                temp.delete()
            }
        }
    }
}

@Composable
fun StagedGate(t: ViewTarget, content: @Composable (File) -> Unit) {
    if (isLocal(t.fs)) {
        content(File(t.path))
        return
    }
    val app = LocalContext.current.applicationContext
    val dest = remember(t) { MediaStage.target(app, t) }
    var run by remember(t) { mutableStateOf<TaskRun?>(null) }
    val cached = remember(t) { dest.isFile && dest.length() == t.size }

    LaunchedEffect(t) {
        if (!cached) run = MediaStage.start(t, dest)
    }

    val current = run
    when {
        cached || (current != null && current.state == TaskState.Done) -> content(dest)
        current != null && (current.state == TaskState.Failed || current.state == TaskState.Stopped) -> StageNote(current.error ?: tr("Stopped"))
        else -> StageNote(
            if (current == null || current.fraction < 0f) tr("Preparing...") else tr("Preparing {0}%", (current.fraction * 100).toInt()),
        )
    }
}

@Composable
private fun StageNote(text: String) {
    Column(Modifier.fillMaxSize()) {
        NxBar(tr("Open"), nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.viewing = null } })
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text, color = NxDim, fontSize = 14.sp)
        }
    }
}
