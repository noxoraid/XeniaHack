package com.uplink.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun copyOutput(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("text", text))
    toast(ctx, tr("Copied"))
}

@Composable
fun GitConsole(s: GitSession) {
    val state = rememberLazyListState()
    LaunchedEffect(s.console.size) {
        if (s.console.isNotEmpty()) state.scrollToItem(s.console.size - 1)
    }
    Column(Modifier.fillMaxWidth().background(NxSurface)) {
        Row(
            Modifier.fillMaxWidth().nxTap(0.dp) { s.consoleOpen = !s.consoleOpen }.padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NxIcon.Terminal, null, Modifier.size(16.dp), tint = NxAccent)
            Text(
                tr("Output"), Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            NxIconButton(NxIcon.Copy, tr("Copy"), NxDim) {
                copyOutput(s.ctx, s.console.joinToString("\n"))
            }
            NxIconButton(NxIcon.Trash, tr("Clear"), NxDim) { s.console.clear() }
            NxIconButton(if (s.consoleOpen) NxIcon.Down else NxIcon.Up, tr("Output"), NxDim) { s.consoleOpen = !s.consoleOpen }
        }
        if (s.consoleOpen) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp).background(NxBg).padding(horizontal = 12.dp, vertical = 6.dp), state = state) {
                if (s.console.isEmpty()) {
                    item { Text(tr("Nothing yet"), color = NxDim, fontSize = 11.sp, fontFamily = FontPrefs.mono.family) }
                }
                items(s.console.toList()) { line ->
                    Text(
                        line, fontFamily = FontPrefs.mono.family, fontSize = 11.sp,
                        color = if (line.contains("error", true) || line.contains("failed", true)) NxDanger else NxDim,
                    )
                }
            }
        }
    }
}
