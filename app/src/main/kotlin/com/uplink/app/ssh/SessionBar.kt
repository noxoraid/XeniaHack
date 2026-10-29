package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SessionBar(vps: Vps, onNew: () -> Unit, onAi: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().background(NxBg).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NxFlow(Modifier.weight(1f)) {
            for (s in vps.sessions.toList()) {
                val on = s === vps.current
                Row(
                    Modifier.clip(shape).background(if (on) NxAccent.copy(alpha = 0.14f) else NxSurface)
                        .border(1.dp, if (on) NxAccent.copy(alpha = 0.4f) else NxLine, shape)
                        .nxTap(20.dp) { vps.current = s }
                        .padding(start = 14.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.title, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) NxAccent else NxText, maxLines = 1)
                    Icon(
                        NxIcon.Close, tr("Close session"),
                        Modifier.padding(start = 6.dp).size(18.dp).nxTap(9.dp) { scope.launch(Dispatchers.IO) { vps.closeSession(s) } },
                        tint = NxDim,
                    )
                }
            }
            NxButton(tr("New session"), icon = NxIcon.Plus, compact = true, onTap = onNew)
        }
        NxButton(tr("AI"), icon = NxIcon.Bot, tone = Tone.Accent, compact = true, onTap = onAi)
        NxButton("A-", compact = true) { TermZoom.step(ctx, -1f) }
        NxButton("A+", compact = true) { TermZoom.step(ctx, 1f) }
    }
}
