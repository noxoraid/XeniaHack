package com.uplink.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NxSegment(
    options: List<Pair<String, ImageVector?>>,
    selected: Int,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onSelect: (Int) -> Unit,
) {
    val shape = CircleShape
    val slot = animateFloatAsState(selected.toFloat(), spring(0.8f, 420f), label = "segSlot")
    val count = options.size.coerceAtLeast(1)
    BoxWithConstraints(modifier.height(height).clip(shape).background(NxRaised)) {
        Box(
            Modifier.padding(3.dp).fillMaxHeight().width((maxWidth - 6.dp) / count)
                .graphicsLayer { translationX = slot.value * size.width }
                .clip(shape).background(NxAccent),
        )
        Row(Modifier.fillMaxSize().padding(3.dp)) {
            for ((i, option) in options.withIndex()) {
                val on = i == selected
                val ink by animateColorAsState(if (on) NxOnAccent else NxDim, tween(200), label = "segInk")
                val source = remember { MutableInteractionSource() }
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .clickable(interactionSource = source, indication = null) { onSelect(i) }
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    option.second?.let {
                        Icon(it, null, Modifier.size(15.dp), tint = ink)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(option.first, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun NxChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hot: Boolean = false,
    tint: Color? = null,
    onTap: (() -> Unit)? = null,
) {
    val fill = when {
        hot -> NxAccent
        tint != null -> tint.copy(alpha = 0.12f)
        else -> NxRaised
    }
    val ink = when {
        hot -> NxOnAccent
        tint != null -> tint
        else -> NxDim
    }
    Row(
        modifier.heightIn(min = 32.dp)
            .then(if (onTap != null) Modifier.nxPress(onTap) else Modifier)
            .clip(CircleShape).background(fill)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = ink)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = if (hot) FontWeight.SemiBold else FontWeight.Medium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NxMenuMorph(open: Boolean, tint: Color = NxText, onTap: () -> Unit) {
    val t by animateFloatAsState(if (open) 1f else 0f, spring(0.72f, 420f), label = "menuMorph")
    val onHead = LocalOnHead.current
    val ink = if (onHead) NxOnHead else tint
    Box(Modifier.size(48.dp).nxPress(onTap), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(40.dp).then(if (onHead) Modifier.clip(CircleShape).background(NxOnHead.copy(alpha = 0.12f)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val stroke = 2.dp.toPx()
                val half = size.width * 0.42f
                val gap = 5.dp.toPx() * (1f - t)
                rotate(45f * t, Offset(cx, cy)) {
                    drawLine(ink, Offset(cx - half, cy - gap), Offset(cx + half, cy - gap), stroke, StrokeCap.Round)
                }
                val mid = half * (1f - t)
                if (mid > 0.5f) drawLine(ink.copy(alpha = (1f - t).coerceIn(0f, 1f)), Offset(cx - mid, cy), Offset(cx + mid, cy), stroke, StrokeCap.Round)
                rotate(-45f * t, Offset(cx, cy)) {
                    drawLine(ink, Offset(cx - half, cy + gap), Offset(cx + half, cy + gap), stroke, StrokeCap.Round)
                }
            }
        }
    }
}
