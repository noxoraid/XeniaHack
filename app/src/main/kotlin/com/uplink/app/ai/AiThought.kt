package com.uplink.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiThought(thought: String, status: String, live: Boolean, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().nxTap(6.dp) { if (thought.isNotEmpty()) open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) Box(Modifier.size(7.dp).clip(CircleShape).background(NxAccent).nxPulse(0.3f, 450))
            Text(
                status.ifEmpty { tr("Thinking") } + if (thought.isNotEmpty()) "  ·  " + thought.length + " " + tr("chars") else "",
                Modifier.weight(1f).padding(start = if (live) 8.dp else 0.dp),
                fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (thought.isNotEmpty()) {
            if (open) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text(thought, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                }
            } else if (live) {
                Text(
                    thought.takeLast(600), Modifier.padding(top = 4.dp),
                    fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim,
                )
            }
        }
    }
}
