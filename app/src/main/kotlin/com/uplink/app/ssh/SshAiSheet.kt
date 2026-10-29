package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val agents = HashMap<Vps, TermAgent>()

fun vpsAgent(vps: Vps, app: android.content.Context): TermAgent = agents.getOrPut(vps) {
    TermAgent(VpsAgentTarget(vps, app.applicationContext), StagePlan.ssh, termChatFile("ssh-" + vps.profile.name + "-" + vps.profile.host))
}

@Composable
fun SshAiPanel(vps: Vps, modifier: Modifier, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val agent = remember(vps) { vpsAgent(vps, ctx) }
    val hints = listOf(tr("Install node and set up an express app"), tr("Update packages and install nginx"))

    Column(modifier.background(NxBg)) {
        Row(Modifier.fillMaxWidth().background(NxSurface).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Server AI"), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            NxIconButton(NxIcon.Trash, tr("Clear chat")) { agent.clear() }
            NxIconButton(NxIcon.Close, tr("Close"), NxDim) { onClose() }
        }
        AgentChat(agent, StagePlan.ssh, null, hints, Modifier.weight(1f).fillMaxWidth(), onClose)
    }
}
