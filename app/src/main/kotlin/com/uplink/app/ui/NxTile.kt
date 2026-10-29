package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NxTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Accent,
    tint: Color = NxAccent,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val fill = if (tone == Tone.Accent) tint.copy(alpha = 0.12f) else NxRaised
    val ink = if (tone == Tone.Accent) tint else NxText
    Column(
        modifier.height(56.dp).nxPress(onTap).background(fill, shape).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = ink)
        Text(label, Modifier.padding(top = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NxGridTile(label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onTap: () -> Unit) {
    Column(
        modifier.height(100.dp).nxPress(onTap).nxGlass(16.dp).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = tint)
        Text(
            label, Modifier.fillMaxWidth().padding(top = 10.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
