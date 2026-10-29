package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

private fun surfaceLabel(s: MindSurface): String = when (s) {
    MindSurface.Phone -> tr("AI File on the phone")
    MindSurface.Ssh -> tr("AI on the server")
    MindSurface.Chat -> tr("AI Chat")
    MindSurface.Flow -> tr("Workflows")
}

private fun checkLabel(check: String): String = when (check) {
    "validate", "existing" -> tr("Index check")
    "summary" -> tr("Summary check")
    "size" -> tr("Size check")
    "pause" -> tr("Auto-pause")
    "stale" -> tr("Stale index")
    "build", "index" -> tr("Index build")
    "wait" -> tr("Index wait")
    "delete" -> tr("Delete")
    else -> tr("Mind")
}

private fun levelColor(level: MindLevel): Color = when (level) {
    MindLevel.Ok -> NxLive
    MindLevel.Warning -> NxYellow
    MindLevel.Error -> NxDanger
}

private fun stampText(millis: Long): String =
    if (millis <= 0L) "-" else SimpleDateFormat("yyyy-MM-dd HH:mm", Lang.locale()).format(Date(millis))

private fun numberText(value: Double?): String = if (value == null) "-" else value.toLong().toString()

private class MindStatRow(
    val surface: MindSurface,
    val avgOn: Double?,
    val avgOff: Double?,
    val countOn: Int,
    val countOff: Int,
    val diff: Double?,
    val last: Pair<Boolean, Int>?,
)

private fun statRows(): List<MindStatRow> = MindSurface.values().map {
    val m = MindGate.meter
    MindStatRow(it, m.averageOn(it), m.averageOff(it), m.countOn(it), m.countOff(it), m.percentDiff(it), m.last(it))
}

@Composable
private fun MindGroup(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    NxSection(title, Modifier.nxEnter(index))
    Column(Modifier.fillMaxWidth().nxEnter(index).nxGlass(20.dp).padding(16.dp), content = content)
}

@Composable
fun MindControls() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Cfg.mind(ctx)) }
    var budget by remember { mutableStateOf(Cfg.mindBudget(ctx).toString()) }
    val paused = MindGate.notice.isNotEmpty()

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        NxIconBox(NxIcon.Bolt, box = 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(tr("Mind"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                when {
                    !on -> tr("Off")
                    paused -> tr("Paused for this session")
                    else -> tr("On")
                },
                fontSize = 12.sp, color = NxDim,
            )
        }
        NxSwitch(on) {
            on = !on
            Cfg.setMind(ctx, on)
        }
    }
    Text(
        tr("A short outline of the project is added to every AI request. When off, requests are built exactly as before."),
        fontSize = 12.sp, color = NxDim, modifier = Modifier.padding(bottom = 14.dp),
    )
    NxField(
        budget,
        {
            val digits = it.filter { c -> c.isDigit() }.take(6)
            budget = digits
            digits.toIntOrNull()?.let { n -> Cfg.setMindBudget(ctx, n) }
        },
        tr("Summary budget (characters)"), mono = true,
    )
    Text(
        tr("Between {0} and {1} characters. Default {2}.", MindSummary.MIN_BUDGET, MindSummary.MAX_BUDGET, MindSummary.DEFAULT_BUDGET),
        fontSize = 12.sp, color = NxDim, modifier = Modifier.padding(bottom = 14.dp),
    )
    NxFlow {
        NxButton(tr("Rebuild index"), icon = NxIcon.Refresh) { MindGate.rebuild() }
        NxButton(tr("Delete index"), icon = NxIcon.Trash, tone = Tone.Danger) { MindGate.delete() }
    }
}

@Composable
fun MindScreen() {
    val tick = MindGate.tick
    val stats = remember(tick) { statRows() }
    val project = MindGate.project
    val notice = MindGate.notice

    LaunchedEffect(Unit) { MindGate.check() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (notice.isNotEmpty()) {
            NxCard(Modifier.fillMaxWidth().padding(bottom = 14.dp).nxEnter(0), hot = true) {
                Text(tr("Mind is paused for this session"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NxDanger)
                Text(notice, Modifier.padding(top = 6.dp, bottom = 10.dp), fontSize = 12.sp, color = NxDim)
                NxButton(tr("Turn Mind back on"), tone = Tone.Accent, icon = NxIcon.Bolt) { MindGate.resume() }
            }
        }

        MindGroup(tr("MIND"), 1) { MindControls() }

        MindGroup(tr("PROJECT"), 2) {
            if (project == null) {
                Text(tr("No folder yet. Open AI File in a folder, or send a request, and Mind detects the project there."), fontSize = 13.sp, color = NxDim)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(8.dp).background(if (project.state == MindState.Fresh) NxLive else if (project.state == MindState.Invalid || project.state == MindState.Failed) NxDanger else NxYellow, CircleShape))
                    Text(project.name, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(project.root, Modifier.padding(top = 4.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                Text(tr("Status: {0}", project.state.label), Modifier.padding(top = 10.dp), fontSize = 13.sp)
                if (project.detail.isNotEmpty()) Text(project.detail, fontSize = 12.sp, color = NxDim)
                Text(tr("Files indexed: {0}", project.fileCount), fontSize = 13.sp)
                Text(tr("Last indexed: {0}", stampText(project.indexedAt)), fontSize = 13.sp)
                if (project.folder != project.root) Text(tr("Working folder: {0}", project.folder), fontSize = 12.sp, color = NxDim)
            }
        }

        MindGroup(tr("CHECKS"), 3) {
            val rows = MindGate.findings.toList()
            if (rows.isEmpty()) {
                Text(tr("No check has reported anything yet."), fontSize = 13.sp, color = NxDim)
            }
            for (f in rows.take(12)) {
                Row(Modifier.padding(vertical = 5.dp)) {
                    Spacer(Modifier.padding(top = 5.dp).size(8.dp).background(levelColor(f.level), CircleShape))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(checkLabel(f.check), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(f.message, fontSize = 12.sp, color = NxDim)
                        if (f.path.isNotEmpty()) Text(f.path, fontFamily = FontPrefs.mono.family, fontSize = 10.sp, color = NxDim)
                    }
                }
            }
        }

        MindGroup(tr("LAST SUMMARY SENT"), 4) {
            val shown = MindGate.lastSummary
            if (shown.isEmpty()) {
                Text(tr("No summary has been sent yet. Send a request to an AI in a project folder."), fontSize = 13.sp, color = NxDim)
            } else {
                Text(MindGate.lastSummaryNote, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                Text(shown.take(900) + if (shown.length > 900) "\n..." else "", Modifier.padding(top = 8.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp)
            }
        }

        MindGroup(tr("CHARACTERS SENT"), 5) {
            Text(tr("Counted in characters, not tokens. The last 20 requests per state."), fontSize = 12.sp, color = NxDim, modifier = Modifier.padding(bottom = 10.dp))
            for (row in stats) {
                val diff = row.diff
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(surfaceLabel(row.surface), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (diff == null) "-" else "%+.0f%%".format(diff),
                        fontFamily = FontPrefs.mono.family, fontSize = 13.sp,
                        color = if (diff == null) NxDim else if (diff <= 0.0) NxLive else NxYellow,
                    )
                }
                Text(
                    tr("Mind on: {0} ({1})   Mind off: {2} ({3})", numberText(row.avgOn), row.countOn, numberText(row.avgOff), row.countOff),
                    fontSize = 12.sp, color = NxDim,
                )
                val last = row.last
                Text(
                    when {
                        last == null -> tr("Last request: -")
                        last.first -> tr("Last request: {0} (Mind on)", last.second)
                        else -> tr("Last request: {0} (Mind off)", last.second)
                    },
                    fontSize = 12.sp, color = NxDim,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
