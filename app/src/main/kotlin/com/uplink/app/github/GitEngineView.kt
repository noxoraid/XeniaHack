package com.uplink.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EngineView(s: GitSession) {
    var ready by remember { mutableStateOf(GitEngine.installed(s.ctx)) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(s.version, s.dir) {
        s.load(tr("Loading"), { GitEngine.history(s.ctx, s.dir) }) { log = it }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        NxSection(tr("GIT ENGINE"), Modifier.nxEnter(0))
        NxCard(Modifier.fillMaxWidth().nxEnter(0), hot = ready) {
            Text(if (ready) tr("Git is ready") else tr("Git is not set up yet"), fontWeight = FontWeight.Bold)
            Text(tr("Built into the app, stored in NoxoraStorage. No Termux and no downloads."), Modifier.padding(top = 4.dp), color = NxDim, fontSize = 12.sp)
            Text(GitEngine.store(s.ctx).path, Modifier.padding(top = 6.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
            Spacer(Modifier.height(12.dp))
            NxButton(if (ready) tr("Check Git again") else tr("Set up Git"), icon = NxIcon.Git, tone = Tone.Accent, wide = true) {
                s.say("$ pkg install git")
                TaskBoard.start(TaskKind.Git, tr("Set up Git")) { run ->
                    GitEngine.install(s.ctx, run) { s.say(it) }
                    ready = true
                }
            }
        }
        NxSection(tr("RECOMMENDED"), Modifier.nxEnter(1))
        NxCard(Modifier.fillMaxWidth().nxEnter(1)) {
            Text(tr("Add a GitHub Personal Access Token as a fallback in case Git does not work."), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            NxButton(tr("Use token mode"), icon = NxIcon.Shield, wide = true) {
                GitAccount.pickMode(s.ctx, GitMode.Token)
                s.view = GitView.Hub
            }
        }
        NxSection(tr("LOCAL HISTORY"), Modifier.nxEnter(2))
        NxCard(Modifier.fillMaxWidth().nxEnter(2)) {
            if (log.isEmpty()) Text(tr("No commits yet for this folder"), color = NxDim, fontSize = 13.sp)
            for (line in log) Text(line, fontFamily = FontPrefs.mono.family, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            if (log.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                NxButton(tr("Reset local history"), icon = NxIcon.Trash, tone = Tone.Danger, compact = true) {
                    GitEngine.forget(s.ctx, s.dir)
                    s.say(tr("Local Git history removed"))
                    s.version++
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
