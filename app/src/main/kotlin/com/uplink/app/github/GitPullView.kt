package com.uplink.app

import kotlinx.coroutines.delay
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun PullCreateDialog(s: GitSession, base: String, onDismiss: () -> Unit) {
    val at = s.link ?: return
    var title by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(base) }
    var notes by remember { mutableStateOf("") }
    NxDialog(
        tr("New pull request"), onDismiss, tr("Create"),
        {
            if (title.isBlank() || head.isBlank()) {
                toast(s.ctx, tr("Fill in title and source branch"))
            } else {
                onDismiss()
                s.load(tr("Creating"), { GitHubApi.createPull(at.repo, title.trim(), head.trim(), target.trim(), notes) }) {
                    s.say(tr("Opened pull request #{0}", it.number))
                    s.say(it.page)
                    s.version++
                }
            }
        },
    ) {
        NxField(title, { title = it }, tr("Title"))
        NxField(head, { head = it }, tr("From branch"), mono = true)
        NxField(target, { target = it }, tr("Into branch"), mono = true)
        NxField(notes, { notes = it }, tr("Description (optional)"), lines = 3)
    }
}

@Composable
fun PullsView(s: GitSession) {
    val at = s.link ?: return
    var state by remember { mutableStateOf("open") }
    var rows by remember { mutableStateOf<List<PullRow>>(emptyList()) }
    var open by remember { mutableStateOf(0) }
    var files by remember { mutableStateOf<List<PullFile>>(emptyList()) }
    var making by remember { mutableStateOf(false) }
    val seen = rememberNxSeen(at.repo, state, rows.size)

    fun reload() = s.load(tr("Loading"), { GitHubApi.pulls(at.repo, state) }) { rows = it }

    LaunchedEffect(at.repo, state, s.version) { reload() }
    LaunchedEffect(at.repo, state) {
        while (true) {
            delay(8000)
            if (s.busy == null) s.quiet({ GitHubApi.pulls(at.repo, state) }) { rows = it }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            NxButton(tr("Open"), tone = if (state == "open") Tone.Accent else Tone.Quiet, compact = true) { state = "open" }
            NxButton(tr("Closed"), tone = if (state == "closed") Tone.Accent else Tone.Quiet, compact = true) { state = "closed" }
            NxButton(tr("All"), tone = if (state == "all") Tone.Accent else Tone.Quiet, compact = true) { state = "all" }
            NxButton(tr("Refresh"), icon = NxIcon.Refresh, compact = true) { reload() }
            NxButton(tr("New pull request"), icon = NxIcon.Plus, compact = true) { making = true }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty() && s.busy == null) Text(tr("No pull requests"), Modifier.align(Alignment.Center), color = NxDim)
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows, key = { _, r -> r.number }) { index, r ->
                    val shown = open == r.number
                    val ink = when {
                        r.merged -> NxAccent
                        r.state == "open" -> NxLive
                        else -> NxDim
                    }
                    Column(Modifier.fillMaxWidth().nxRow(seen, r.number, index)) {
                        Row(
                            Modifier.fillMaxWidth().nxTap(0.dp) {
                                open = if (shown) 0 else r.number
                                if (!shown) s.load(tr("Loading"), { GitHubApi.pullFiles(at.repo, r.number) }) { files = it }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(NxIcon.Git, null, Modifier.size(20.dp), tint = ink)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "#${r.number}  ·  ${r.head} → ${r.base}  ·  ${r.author}  ·  ${r.date}",
                                    fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(if (r.merged) tr("Merged") else if (r.state == "open") tr("Open") else tr("Closed"), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (shown) {
                            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                if (r.body.isNotBlank()) Text(r.body.take(600), fontSize = 13.sp, color = NxDim)
                                Text(r.page, Modifier.padding(top = 8.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxAccent)
                                NxButton(tr("Copy link"), icon = NxIcon.Copy, compact = true, modifier = Modifier.padding(top = 8.dp)) { copyOutput(s.ctx, r.page) }
                                for (f in files.take(60)) {
                                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(f.name, Modifier.weight(1f), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("+${f.adds} -${f.dels}", fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(NxLine))
                }
            }
        }
    }
    if (making) PullCreateDialog(s, at.branch) { making = false }
}
