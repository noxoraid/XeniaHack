package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModelPicker(
    selectedId: String,
    selectedModel: String,
    followActive: Boolean,
    onPick: (AiProfile?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    NxDialog(tr("Choose model"), onDismiss, tr("Close"), onDismiss) {
        if (AiStore.profiles.isEmpty()) {
            Text(tr("No providers yet"), Modifier.padding(bottom = 14.dp), color = NxDim)
        } else {
            NxField(filter, { filter = it }, tr("Search models"), mono = true)
        }
        if (followActive) {
            NxListRow(
                tr("Chat model (active)"), NxIcon.Bot, Modifier.padding(bottom = 10.dp),
                trailing = { if (selectedId.isEmpty()) Icon(NxIcon.Check, null, Modifier.size(18.dp), tint = NxAccent) },
            ) { onPick(null, "") }
        }
        for (p in AiStore.profiles) {
            val shown = p.choices().filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }
            if (shown.isEmpty()) continue
            Text(p.name, Modifier.padding(top = 4.dp, bottom = 6.dp), fontSize = 12.sp, color = NxDim, fontWeight = FontWeight.Bold)
            for (m in shown.take(120)) {
                val on = p.id == selectedId && (m == selectedModel || (selectedModel.isEmpty() && m == p.model))
                NxListRow(
                    m, NxIcon.Chip, Modifier.padding(bottom = 6.dp),
                    trailing = { if (on) Icon(NxIcon.Check, null, Modifier.size(18.dp), tint = NxAccent) },
                ) { onPick(p, m) }
            }
        }
    }
}
