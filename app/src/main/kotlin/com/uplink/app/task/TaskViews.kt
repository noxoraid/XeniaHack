package com.uplink.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun TaskBar(fraction: Float, modifier: Modifier = Modifier) {
    if (fraction < 0f) {
        LinearProgressIndicator(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = NxAccent, trackColor = NxRaised)
    } else {
        NxMeter(fraction, modifier.height(6.dp))
    }
}

@Composable
fun TaskStrip() {
    val lead = TaskBoard.runs.firstOrNull { it.state == TaskState.Running }
    val fraction = lead?.fraction ?: 0f
    val level = animateFloatAsState(fraction.coerceAtLeast(0f), tween(240), label = "strip")
    Box(Modifier.fillMaxWidth().height(3.dp).background(NxLine)) {
        if (lead != null) {
            if (fraction < 0f) {
                LinearProgressIndicator(Modifier.fillMaxSize(), color = NxAccent, trackColor = NxLine)
            } else {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(NxAccent, size = Size(size.width * level.value, size.height)) })
            }
        }
    }
}

@Composable
fun TaskTicker(modifier: Modifier = Modifier) {
    val lead = TaskBoard.runs.firstOrNull { it.state == TaskState.Running } ?: return
    val more = TaskBoard.active - 1
    Text(
        lead.title + "  " + lead.counter() + if (more > 0) "  +$more" else "",
        modifier.nxTap(6.dp) { Nav.route = Route.Tasks }.padding(horizontal = 6.dp, vertical = 2.dp),
        fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxAccent, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TaskCard(run: TaskRun, modifier: Modifier = Modifier) {
    val live = run.state == TaskState.Running
    val tint = when (run.state) {
        TaskState.Running -> NxAccent
        TaskState.Done -> NxLive
        TaskState.Failed -> NxDanger
        TaskState.Stopped -> NxDim
    }
    val info = listOf(run.counter(), run.speed(), run.elapsed()).filter { it.isNotEmpty() }.joinToString("  ·  ")
    Column(
        modifier.fillMaxWidth().nxGlass(18.dp, live).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NxIconBox(run.kind.icon, if (live) Modifier.nxPulse() else Modifier, box = 38.dp, tint = tint)
            Text(
                run.title, Modifier.weight(1f).padding(horizontal = 12.dp),
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val level = run.level
            if (level != null) Text(level.label, Modifier.padding(end = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NxDim)
            NxChip(run.state.label, hot = live)
        }
        if (live) TaskBar(run.fraction)
        Text(info, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
        if (live && run.detail.isNotEmpty()) {
            Text(
                run.detail, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxAccent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (run.skipped > 0) {
            Text(tr("{0} skipped by exclude list", run.skipped), fontSize = 12.sp, color = NxDim)
        }
        val err = run.error
        if (err != null) Text(err, fontSize = 12.sp, color = NxDanger)
        if (live && run.stoppable) {
            NxButton(if (run.stop) tr("Stopping...") else tr("Stop"), icon = NxIcon.Close, tone = Tone.Danger, compact = true) { run.stop = true }
        } else if (!live) {
            NxButton(tr("Remove"), icon = NxIcon.Trash, compact = true) { TaskBoard.dismiss(run) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksScreen() {
    val runs = TaskBoard.runs
    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            NxButton(tr("Clear finished"), icon = NxIcon.Trash, compact = true) { TaskBoard.clearFinished() }
            NxButton(tr("Stop all"), icon = NxIcon.Close, tone = Tone.Danger, compact = true) { TaskBoard.stopAll() }
            NxButton(
                tr("Task Error"), icon = NxIcon.Triangle,
                tone = if (ErrorLog.unseen > 0) Tone.Danger else Tone.Quiet, compact = true,
            ) { Nav.route = Route.Errors }
        }
        if (runs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(NxIcon.Tasks, null, Modifier.size(36.dp).nxFloat(5.dp, 2400).nxPulse(0.35f, 1400), tint = NxDim)
                    Text(tr("No tasks"), Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                    Text(tr("Transfers and file jobs show up here."), color = NxDim, fontSize = 13.sp)
                    Text(
                        if (TreeScan.nativeReady) tr("Native scanner on") else tr("Native scanner off"),
                        Modifier.padding(top = 14.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp,
                        color = if (TreeScan.nativeReady) NxLive else NxDim,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(runs, key = { it.id }) { TaskCard(it, Modifier.animateItemPlacement(tween(260))) }
            }
        }
    }
}
