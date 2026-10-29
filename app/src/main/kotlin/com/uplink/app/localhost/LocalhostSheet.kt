package com.uplink.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun LocalhostSheet(path: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val root = remember(path) { File(path) }
    var port by remember { mutableStateOf("8080") }
    var autoReload by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    val running = TaskBoard.runs.filter { it.kind == TaskKind.Localhost && it.state == TaskState.Running }

    NxDialog(tr("Localhost"), onDismiss, tr("Start"), {
        val number = port.toIntOrNull()
        if (number == null || number !in 1024..65535) {
            problem = tr("Port must be 1024 to 65535")
        } else {
            problem = null
            startServer(root, number, autoReload)
        }
    }) {
        Text(path, fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim)
        NxField(port, { port = it.filter(Char::isDigit).take(5) }, tr("Port"), mono = true)
        NxChip(
            if (autoReload) tr("Auto reload: On") else tr("Auto reload: Off"),
            icon = NxIcon.Refresh, hot = autoReload, onTap = { autoReload = !autoReload },
        )
        Text(
            tr("Reloads the browser when a file in this folder changes"),
            fontSize = 12.sp, color = NxDim, modifier = Modifier.padding(top = 6.dp),
        )
        problem?.let { Text(it, fontSize = 12.sp, color = NxDanger, modifier = Modifier.padding(top = 8.dp)) }
        Text(tr("Running servers"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        if (running.isEmpty()) {
            Text(tr("No server running"), fontSize = 12.sp, color = NxDim, modifier = Modifier.padding(top = 6.dp))
        }
        running.forEach { RunningRow(it, ctx) }
    }
}

private fun startServer(root: File, port: Int, autoReload: Boolean) {
    TaskBoard.start(TaskKind.Localhost, root.name) { run ->
        run.detail = "http://localhost:$port"
        LocalhostServer(root, port, autoReload, run).serve()
    }
}

@Composable
private fun RunningRow(run: TaskRun, ctx: Context) {
    NxCard(Modifier.fillMaxWidth().padding(top = 10.dp), hot = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NxIconBox(NxIcon.Globe, Modifier.nxPulse(0.6f, 1400), box = 36.dp, tint = NxLive)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(run.detail, fontFamily = FontPrefs.mono.family, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(tr("{0} requests", run.done), fontSize = 12.sp, color = NxDim)
            }
        }
        NxFlow(Modifier.padding(top = 10.dp)) {
            NxButton(tr("Copy URL"), compact = true) { copyText(ctx, run.detail) }
            NxButton(tr("Open in Chrome"), icon = NxIcon.Globe, tone = Tone.Accent, compact = true) {
                openInChrome(ctx, run.detail)
            }
            NxButton(tr("Stop"), tone = Tone.Danger, compact = true) { run.stop = true }
        }
    }
}

private fun copyText(ctx: Context, text: String) {
    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.setPrimaryClip(ClipData.newPlainText("url", text))
    toast(ctx, tr("Copied"))
}

private fun openInChrome(ctx: Context, url: String) {
    val chrome = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setPackage("com.android.chrome")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(chrome)
    } catch (_: ActivityNotFoundException) {
        val any = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(any)
        } catch (_: ActivityNotFoundException) {
            toast(ctx, tr("No app can open this link"))
        }
    }
}
