package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NxComposer(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .nxGlass(28.dp).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (onAttach != null) {
            Box(
                Modifier.padding(end = 6.dp).size(44.dp).nxPress { onAttach() }.clip(CircleShape).background(NxRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NxIcon.Upload, tr("Attach files"), Modifier.size(20.dp), tint = NxDim)
            }
        }
        Box(Modifier.weight(1f).heightIn(min = 44.dp).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = NxDim, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                maxLines = 5,
                textStyle = TextStyle(fontFamily = FontPrefs.ui.family, fontSize = 14.sp, color = NxText),
                cursorBrush = SolidColor(NxAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.padding(start = 6.dp).size(44.dp).nxPress { if (busy) onStop() else onSend() }
                .clip(CircleShape).background(if (busy) NxDanger else NxAccent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (busy) NxIcon.Close else NxIcon.Send, if (busy) tr("Stop") else tr("Send"),
                Modifier.size(20.dp), tint = if (busy) NxOnHead else NxOnAccent,
            )
        }
    }
}
