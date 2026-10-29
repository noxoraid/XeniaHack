package com.uplink.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UploadView(s: GitSession) {
    val at = s.link ?: return
    var branch by remember { mutableStateOf(at.branch) }
    var subdir by remember { mutableStateOf(at.subdir) }
    var message by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(true) }
    var skip by remember { mutableStateOf(Cfg.excludes(s.ctx)) }
    var picking by remember { mutableStateOf(false) }
    var prune by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        NxSection(tr("FROM"), Modifier.nxEnter(0))
        NxCard(Modifier.fillMaxWidth().nxEnter(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(NxIcon.Folder, null, Modifier.size(20.dp), tint = NxAccent)
                Text(s.dir, Modifier.padding(start = 10.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp)
            }
        }
        NxSection(tr("TO"), Modifier.nxEnter(1))
        NxCard(Modifier.fillMaxWidth().nxEnter(1)) {
            Text(at.repo, fontFamily = FontPrefs.mono.family, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NxButton(branch, icon = NxIcon.Git, compact = true) { picking = true }
            }
        }
        NxSection(tr("DETAILS"), Modifier.nxEnter(2))
        NxCard(Modifier.fillMaxWidth().nxEnter(2)) {
            NxField(subdir, { subdir = it }, tr("Folder inside the repository (optional)"), mono = true)
            NxField(message, { message = it }, if (auto) tr("Commit message (automatic with date and time)") else tr("Commit message"))
            NxField(skip, { skip = it }, tr("Skip these names"), mono = true, lines = 2)
        }
        Spacer(Modifier.height(10.dp))
        NxButton(
            if (auto) tr("Automatic commit name: on") else tr("Automatic commit name: off"),
            icon = NxIcon.History, tone = if (auto) Tone.Accent else Tone.Quiet, compact = true,
        ) { auto = !auto }
        Spacer(Modifier.height(8.dp))
        NxButton(
            if (prune) tr("Delete removed files: on") else tr("Delete removed files: off"),
            icon = NxIcon.Refresh, tone = if (prune) Tone.Accent else Tone.Quiet, compact = true,
        ) { prune = !prune }
        Text(
            if (GitAccount.mode == GitMode.Token) tr("Sync: GitHub is made identical to this folder. Files that are gone here are deleted there.") else tr("Git mode: commit and push from this folder."),
            Modifier.padding(top = 8.dp), color = NxDim, fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))
        NxButton(tr("Upload to GitHub"), icon = NxIcon.Upload, tone = Tone.Accent, wide = true, modifier = Modifier.nxEnter(3)) {
            val next = RepoLink(at.repo, branch.trim().ifEmpty { at.branch }, subdir.trim().trim('/'))
            val patterns = TreeScan.parse(skip)
            val note = if (auto || message.isBlank()) s.autoMessage() else message
            val label = s.dir.trimEnd('/').substringAfterLast('/').ifEmpty { s.dir }
            Cfg.setExcludes(s.ctx, skip)
            s.bind(next)
            val engine = GitAccount.mode == GitMode.Engine && isLocal(s.fs)
            s.say("$ " + (if (engine) "git push" else "sync") + " ${next.repo}@${next.branch}  \"$note\"")
            TaskBoard.start(TaskKind.Git, tr("Upload {0}", label)) { run ->
                try {
                    if (engine) GitEngine.push(s.ctx, s.dir, next, note, patterns, prune, run) { s.say(it) }
                    else GitPush.upload(s.fs, s.dir, next, note, patterns, run, prune) { s.say(it) }
                    s.say(tr("Done"))
                } catch (e: Exception) {
                    s.say(tr("Failed: {0}", e.message ?: e.toString()))
                    throw e
                }
            }
            toast(s.ctx, tr("Running in background, see Tasks"))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) {
        BranchDialog(s, at.repo, branch, false, onPick = { picked ->
            if (picked != null) branch = picked
            picking = false
        }, onDismiss = { picking = false })
    }
}
