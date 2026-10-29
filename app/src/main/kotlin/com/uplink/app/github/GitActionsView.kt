package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun RunMark(run: RunRow) {
    val (glyph, ink) = when {
        run.live -> NxIcon.Refresh to NxAccent
        run.conclusion == "success" -> NxIcon.Check to NxLive
        run.conclusion == "failure" || run.conclusion == "timed_out" -> NxIcon.Close to NxDanger
        else -> NxIcon.Info to NxDim
    }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(NxRaised), contentAlignment = Alignment.Center) {
        Icon(glyph, null, Modifier.size(20.dp).then(if (run.live) Modifier.nxPulse(0.3f, 700) else Modifier), tint = ink)
    }
}

@Composable
private fun ArtifactLine(art: ArtifactRow, showBranch: Boolean, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(NxIcon.Archive, null, Modifier.size(18.dp), tint = if (art.expired) NxDim else NxAccent)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(art.name, fontFamily = FontPrefs.mono.family, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(art.date, humanSize(art.size), if (showBranch) art.branch else "").filter { it.isNotEmpty() }.joinToString("  ·  "),
                fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim,
            )
        }
        if (art.expired) Text(tr("Expired"), color = NxDim, fontSize = 12.sp)
        else NxButton(tr("Download"), icon = NxIcon.Download, tone = Tone.Accent, compact = true, onTap = onSave)
    }
}

@Composable
private fun SaveDialog(s: GitSession, art: ArtifactRow, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(s.dir) }
    var extract by remember { mutableStateOf(true) }
    NxDialog(
        tr("Save artifact"), onDismiss, tr("Download"),
        {
            onDismiss()
            s.downloadArtifact(art, target, extract)
        },
    ) {
        Text(
            art.name + "  ·  " + humanSize(art.size), Modifier.padding(bottom = 12.dp),
            fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim,
        )
        NxField(target, { target = it }, tr("Save into folder"), mono = true)
        Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("Extract"), icon = NxIcon.Unarchive, tone = if (extract) Tone.Accent else Tone.Quiet, compact = true) { extract = true }
            NxButton(tr("Keep zip"), icon = NxIcon.Archive, tone = if (!extract) Tone.Accent else Tone.Quiet, compact = true) { extract = false }
        }
    }
}

private class LogOpen(val run: RunRow, val text: RunLogText)

@Composable
private fun LogDialog(s: GitSession, repo: String, open: LogOpen, startErrors: Boolean, onDismiss: () -> Unit) {
    val ctx = s.ctx
    val clip = LocalClipboardManager.current
    var errorsOnly by remember { mutableStateOf(startErrors) }
    val body = if (errorsOnly) open.text.errors else open.text.full
    val lines = remember(body) { body.split('\n') }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(NxBg).nxSafe()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                NxIconButton(NxIcon.Back, tr("Back"), onTap = onDismiss)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("#" + open.run.number + "  " + open.run.title.ifEmpty { "-" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(open.run.branch + "  ·  " + humanSize(body.length.toLong()), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1)
                }
            }
            NxFlow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                NxButton(tr("Full log"), icon = NxIcon.File, tone = if (!errorsOnly) Tone.Accent else Tone.Quiet, compact = true) { errorsOnly = false }
                NxButton(tr("Errors only"), icon = NxIcon.Info, tone = if (errorsOnly) Tone.Accent else Tone.Quiet, compact = true) { errorsOnly = true }
                NxButton(tr("Copy"), icon = NxIcon.Copy, compact = true) {
                    val cap = 400_000
                    clip.setText(AnnotatedString(if (body.length > cap) body.takeLast(cap) else body))
                    toast(ctx, if (body.length > cap) tr("Copied the last part. Save the file to keep everything.") else tr("Copied"))
                }
                NxButton(tr("Save"), icon = NxIcon.Save, compact = true) {
                    s.load(tr("Saving"), { RunLog.save(repo, open.run.number, errorsOnly, body) }) { file ->
                        toast(ctx, tr("Saved to {0}", file.path))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                itemsIndexed(lines) { _, line ->
                    val bad = line.contains("##[error]") || line.contains("error", ignoreCase = true) && errorsOnly
                    Text(
                        line.ifEmpty { " " },
                        fontFamily = FontPrefs.mono.family, fontSize = 11.sp, lineHeight = 15.sp,
                        color = if (line.startsWith("=====")) NxAccent else if (bad) NxDanger else NxText,
                        fontWeight = if (line.startsWith("=====")) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
fun ActionsView(s: GitSession) {
    val at = s.link ?: return
    var mode by remember { mutableStateOf(0) }
    var branch by remember { mutableStateOf<String?>(null) }
    var runs by remember { mutableStateOf<List<RunRow>>(emptyList()) }
    var arts by remember { mutableStateOf<List<ArtifactRow>>(emptyList()) }
    var opened by remember { mutableStateOf<Long?>(null) }
    var perRun by remember { mutableStateOf<Map<Long, List<ArtifactRow>>>(emptyMap()) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<ArtifactRow?>(null) }
    var reading by remember { mutableStateOf<LogOpen?>(null) }
    var readErrors by remember { mutableStateOf(false) }
    val seen = rememberNxSeen(mode, branch, runs.size, arts.size)

    fun reload() {
        if (mode == 0) s.load(tr("Loading"), { GitHubApi.runs(at.repo, branch) }) { runs = it }
        else s.load(tr("Loading"), { GitHubApi.allArtifacts(at.repo) }) { arts = it }
    }

    fun toggle(run: RunRow) {
        if (opened == run.id) {
            opened = null
            return
        }
        opened = run.id
        if (perRun[run.id] == null) {
            s.load(tr("Loading"), { GitHubApi.runArtifacts(at.repo, run.id) }) { perRun = perRun + (run.id to it) }
        }
    }

    LaunchedEffect(mode, branch, at.repo) { reload() }
    LaunchedEffect(mode, runs) {
        while (true) {
            delay(15000)
            if (mode == 0 && runs.any { it.live }) reload()
        }
    }

    val shownArts = arts.filter { branch == null || it.branch == branch }

    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            NxButton(tr("Runs"), icon = NxIcon.Bolt, tone = if (mode == 0) Tone.Accent else Tone.Quiet, compact = true) { mode = 0 }
            NxButton(tr("Artifacts"), icon = NxIcon.Archive, tone = if (mode == 1) Tone.Accent else Tone.Quiet, compact = true) { mode = 1 }
            NxButton(branch ?: tr("All branches"), icon = NxIcon.Git, compact = true) { picking = true }
            NxButton(tr("Refresh"), icon = NxIcon.Refresh, compact = true) { reload() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val empty = if (mode == 0) runs.isEmpty() else shownArts.isEmpty()
            if (empty && s.busy == null) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(NxIcon.Bolt, null, Modifier.size(36.dp).nxFloat(5.dp, 2200), tint = NxDim)
                    Text(if (mode == 0) tr("No runs yet") else tr("No artifacts yet"), Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                    Text(tr("Push a commit with a workflow to see it here."), color = NxDim, fontSize = 13.sp)
                }
            }
            if (mode == 0) {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(runs, key = { _, r -> r.id }) { index, run ->
                        Column(Modifier.fillMaxWidth().nxRow(seen, run.id, index).padding(horizontal = 16.dp, vertical = 5.dp).nxGlass(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth().nxTap(16.dp) { toggle(run) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RunMark(run)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(run.title.ifEmpty { "-" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "#" + run.number + "  ·  " + run.branch + "  ·  " + run.date,
                                        fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(if (opened == run.id) NxIcon.Up else NxIcon.Down, null, Modifier.size(18.dp), tint = NxDim)
                            }
                            if (opened == run.id) {
                                Column(Modifier.fillMaxWidth().padding(start = 52.dp, end = 12.dp, bottom = 12.dp)) {
                                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        NxButton(tr("Re-run"), icon = NxIcon.Refresh, compact = true) {
                                            s.load(tr("Starting"), { GitHubApi.rerun(at.repo, run.id) }) { reload() }
                                        }
                                        NxButton(tr("Full log"), icon = NxIcon.File, compact = true) {
                                            readErrors = false
                                            s.load(tr("Loading"), { LogOpen(run, RunLog.fetch(at.repo, run.id)) }) { reading = it }
                                        }
                                        NxButton(tr("Errors only"), icon = NxIcon.Info, tone = Tone.Danger, compact = true) {
                                            readErrors = true
                                            s.load(tr("Loading"), { LogOpen(run, RunLog.fetch(at.repo, run.id)) }) { reading = it }
                                        }
                                        if (run.live) {
                                            NxButton(tr("Cancel run"), icon = NxIcon.Close, tone = Tone.Danger, compact = true) {
                                                s.load(tr("Stopping"), { GitHubApi.cancel(at.repo, run.id) }) { reload() }
                                            }
                                        }
                                    }
                                    val list = perRun[run.id]
                                    if (list == null) Text(tr("Loading"), color = NxDim, fontSize = 12.sp)
                                    else if (list.isEmpty()) Text(tr("No artifacts in this run"), color = NxDim, fontSize = 12.sp)
                                    else {
                                        for (art in list) ArtifactLine(art, false) { saving = art }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(shownArts, key = { _, a -> a.id }) { index, art ->
                        Column(Modifier.fillMaxWidth().nxRow(seen, art.id, index).padding(horizontal = 16.dp)) {
                            ArtifactLine(art, true) { saving = art }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(NxLine))
                    }
                }
            }
        }
    }

    if (picking) {
        BranchDialog(s, at.repo, branch, true, onPick = { picked ->
            branch = picked
            picking = false
        }, onDismiss = { picking = false })
    }
    reading?.let { open -> LogDialog(s, at.repo, open, readErrors) { reading = null } }
    saving?.let { art -> SaveDialog(s, art, onDismiss = { saving = null }) }
}
