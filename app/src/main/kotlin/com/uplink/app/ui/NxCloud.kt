package com.uplink.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class NxBarItem(val label: String, val icon: ImageVector, val on: Boolean, val onTap: () -> Unit)

@Composable
fun NxIconBox(icon: ImageVector, modifier: Modifier = Modifier, box: Dp = 44.dp, tint: Color = NxAccent) {
    Box(
        modifier.size(box).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(box * 0.5f), tint = tint)
    }
}

@Composable
fun NxSearch(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(NxRaised).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NxIcon.Search, null, Modifier.size(18.dp), tint = NxDim)
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontPrefs.ui.family, fontSize = 13.sp, color = NxText),
                cursorBrush = SolidColor(NxAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(32.dp).nxPress { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(NxIcon.Close, tr("Clear"), Modifier.size(16.dp), tint = NxDim)
            }
        }
    }
}

@Composable
fun NxListRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    meta: String? = null,
    tint: Color = NxAccent,
    trailing: (@Composable () -> Unit)? = null,
    card: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onTap != null) Modifier.nxPress(onTap) else Modifier)
            .then(if (card) Modifier.nxGlass(16.dp) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NxIconBox(icon, tint = tint)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) Text(meta, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = NxDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else if (onTap != null) Icon(NxIcon.Right, null, Modifier.size(18.dp), tint = NxDim)
    }
}

@Composable
fun NxBottomBar(items: List<NxBarItem>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
            .height(80.dp).nxGlass(28.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            val raw by animateFloatAsState(if (item.on) 1f else 0f, spring(0.8f, 380f), label = "barSel")
            val sel = raw.coerceIn(0f, 1f)
            Box(
                Modifier.weight(1f + 1.2f * sel).height(48.dp).padding(horizontal = 2.dp)
                    .nxPress(item.onTap).clip(CircleShape).background(NxAccent.copy(alpha = sel)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, item.label, Modifier.size(22.dp), tint = lerp(NxDim, NxOnAccent, sel))
                    if (sel > 0.05f) {
                        Text(
                            item.label, Modifier.padding(start = 6.dp).graphicsLayer { alpha = sel },
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NxOnAccent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NxFab(icon: ImageVector, description: String, modifier: Modifier = Modifier, turned: Boolean = false, onTap: () -> Unit) {
    val turn by animateFloatAsState(if (turned) 45f else 0f, spring(0.7f, 420f), label = "fabTurn")
    Box(
        modifier.size(56.dp).nxPress(onTap).nxShadow(CircleShape).clip(CircleShape).background(NxAccent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(24.dp).graphicsLayer { rotationZ = turn }, tint = NxOnAccent)
    }
}
