package com.uplink.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

private const val COPY_LIMIT = 150_000

private val ErrorLevel.tint: Color
    get() = when (this) {
        ErrorLevel.Low -> NxLive
        ErrorLevel.Mid, ErrorLevel.High -> NxAccent
        ErrorLevel.Fatal, ErrorLevel.ForceCrash -> NxDanger
    }

private fun copyText(ctx: Context, text: String) {
    val board = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    board.setPrimaryClip(ClipData.newPlainText("error", text))
    toast(ctx, tr("Copied"))
}

private fun stampOf(at: Long): String = SimpleDateFormat("d MMM  HH:mm:ss", Lang.locale()).format(Date(at))

private fun reportOf(list: List<ErrorEntry>): String {
    val out = StringBuilder()
    for (entry in list) {
        if (out.length > COPY_LIMIT) break
        out.append('[').append(entry.level.label).append("]  ").append(stampOf(entry.at)).append('\n')
        out.append(entry.title.text()).append('\n')
        out.append(entry.source.text()).append('\n')
        if (entry.detail.isNotEmpty()) out.append('\n').append(entry.detail).append('\n')
        out.append("\n----\n\n")
    }
    return out.toString()
}

@Composable
private fun LevelBars(level: ErrorLevel) {
    val tint = level.tint
    val track = NxLine
    Spacer(
        Modifier.size(width = 34.dp, height = 12.dp).drawBehind {
            val gap = 2.dp.toPx()
            val bar = (size.width - gap * 4) / 5f
            for (i in 0 until 5) {
                val tall = size.height * (i + 1) / 5f
                drawRoundRect(
                    if (i <= level.ordinal) tint else track,
                    Offset(i * (bar + gap), size.height - tall), Size(bar, tall), CornerRadius(1.5.dp.toPx()),
                )
            }
        },
    )
}

@Composable
private fun ErrorCard(entry: ErrorEntry, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var open by remember(entry.id) { mutableStateOf(false) }
    val tint = entry.level.tint
    NxCard(
        modifier.fillMaxWidth().animateContentSize(tween(220)),
        hot = entry.level.ordinal >= ErrorLevel.Fatal.ordinal,
        onTap = { open = !open },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBars(entry.level)
            Text(
                entry.level.label, Modifier.padding(start = 10.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint, letterSpacing = 1.sp,
            )
            if (!entry.seen) {
                Text(
                    tr("NEW"), Modifier.padding(start = 8.dp).nxPulse(0.4f, 900),
                    fontFamily = FontPrefs.mono.family, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NxAccent,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(stampOf(entry.at), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
        }
        Text(
            entry.title.text(), Modifier.padding(top = 8.dp),
            fontWeight = FontWeight.Bold, maxLines = if (open) 8 else 2, overflow = TextOverflow.Ellipsis,
        )
        Text(entry.source.text(), Modifier.padding(top = 2.dp), fontSize = 12.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (open) {
            if (entry.detail.isNotEmpty()) {
                Text(
                    entry.detail, Modifier.padding(top = 10.dp),
                    fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim,
                )
            }
            NxFlow(Modifier.padding(top = 12.dp)) {
                NxButton(tr("Copy"), icon = NxIcon.Copy, compact = true) { copyText(ctx, reportOf(listOf(entry))) }
                NxButton(tr("Remove"), icon = NxIcon.Trash, compact = true) { ErrorLog.remove(entry) }
            }
        }
    }
}

@Composable
private fun LevelGuide() {
    Column(Modifier.fillMaxWidth()) {
        NxSection(tr("LEVELS"))
        for (level in ErrorLevel.values()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 4.dp)) { LevelBars(level) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(level.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = level.tint)
                    Text(level.hint, fontSize = 12.sp, color = NxDim)
                }
            }
        }
    }
}

@Composable
private fun NoErrors() {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(NxIcon.Triangle, null, Modifier.size(36.dp).nxFloat(5.dp, 2400).nxPulse(0.35f, 1400), tint = NxDim)
        Text(tr("No errors"), Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
        Text(tr("Crashes and failed jobs are recorded here with the reason."), color = NxDim, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ErrorsScreen() {
    val ctx = LocalContext.current
    val all = ErrorLog.entries
    var filter by remember { mutableStateOf<ErrorLevel?>(null) }
    val shown = filter?.let { level -> all.filter { it.level == level } } ?: all
    val seen = rememberNxSeen()

    DisposableEffect(Unit) { onDispose { ErrorLog.markSeen() } }

    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            NxButton(tr("Copy all"), icon = NxIcon.Copy, compact = true) { copyText(ctx, reportOf(shown)) }
            NxButton(tr("Clear"), icon = NxIcon.Trash, tone = Tone.Danger, compact = true) { ErrorLog.clear() }
        }
        NxFlow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
            NxChip(tr("All {0}", all.size), hot = filter == null, onTap = { filter = null })
            for (level in ErrorLevel.values()) {
                NxChip(
                    tr("{0} {1}", level.label, all.count { it.level == level }),
                    hot = filter == level,
                    onTap = { filter = level },
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (shown.isEmpty()) item { NoErrors() }
            itemsIndexed(shown, key = { _, entry -> entry.id }) { index, entry ->
                ErrorCard(entry, Modifier.nxRow(seen, entry.id, index).animateItemPlacement(tween(260)))
            }
            item { LevelGuide() }
        }
    }
}
