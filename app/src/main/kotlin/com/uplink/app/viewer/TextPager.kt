package com.uplink.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

private const val LINE_READ_CAP = 8192L

private fun readLine(raf: RandomAccessFile, table: LineTable, i: Int): String {
    val start = table.start(i)
    val span = table.end(i) - start
    val len = minOf(span, LINE_READ_CAP).toInt()
    if (len <= 0) return ""
    val buf = ByteArray(len)
    return try {
        synchronized(raf) {
            raf.seek(start)
            raf.readFully(buf)
        }
        val text = String(buf, Charsets.UTF_8).trimEnd('\n', '\r')
        if (span > len) "$text ..." else text
    } catch (_: Throwable) {
        ""
    }
}

@Composable
fun TextPager(t: ViewTarget, file: File) {
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var table by remember(file) { mutableStateOf<LineTable?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var jump by remember { mutableStateOf(false) }
    val raf = remember(file) { RandomAccessFile(file, "r") }

    DisposableEffect(raf) { onDispose { raf.close() } }

    LaunchedEffect(file) {
        TaskBoard.start(TaskKind.Open, tr("Index {0}", file.name), false) { run ->
            run.detail = file.name
            run.totalBytes = file.length()
            val found = FileSniff.lines(file.path)
            if (found == null) failed = true else table = found
            run.bytes = run.totalBytes
        }
    }

    val ready = table
    Column(Modifier.fillMaxSize()) {
        NxBar(
            t.path.substringAfterLast('/'),
            subtitle = when {
                ready == null -> tr("read only")
                ready.truncated -> tr("read only  /  {0} lines  /  partial", ready.count)
                else -> tr("read only  /  {0} lines", ready.count)
            },
            nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.viewing = null } },
            actions = { if (ready != null && ready.count > 0) NxIconButton(NxIcon.Right, tr("Go to line")) { jump = true } },
        )
        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("Cannot read file"), color = NxDim) }
            ready == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("Indexing..."), color = NxDim) }
            ready.count == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("Empty file"), color = NxDim) }
            else -> {
                val gutter = (ready.count.toString().length * 8 + 20).dp
                LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), state = list) {
                    items(ready.count) { i ->
                        val text = remember(i) { readLine(raf, ready, i) }
                        Row(Modifier.padding(end = 16.dp)) {
                            Text(
                                (i + 1).toString(), Modifier.width(gutter).padding(end = 10.dp),
                                fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim, textAlign = TextAlign.End,
                            )
                            Text(text, fontFamily = FontPrefs.mono.family, fontSize = 12.sp, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    if (jump && ready != null) {
        NxAsk(tr("Go to line"), "1", "1 - ${ready.count}", 1, onDismiss = { jump = false }) { text ->
            jump = false
            val n = text.trim().toIntOrNull()?.coerceIn(1, ready.count) ?: return@NxAsk
            scope.launch { list.scrollToItem(n - 1) }
        }
    }
}
