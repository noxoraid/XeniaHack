package com.uplink.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun sliceColor(i: Int): Color = when (i % 6) {
    0 -> NxAccent
    1 -> NxLive
    2 -> NxDanger
    3 -> NxAccent.copy(alpha = 0.55f)
    4 -> NxLive.copy(alpha = 0.55f)
    else -> NxDim
}

@Composable
fun BarsChart(buckets: List<Bucket>) {
    val gain = NxLive
    val loss = NxDanger
    val axis = NxLine
    val peak = maxOf(1L, buckets.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1L)
    val step = maxOf(1, (buckets.size + 7) / 8)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(buckets) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val n = buckets.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val bar = minOf(slot * 0.36f, 14.dp.toPx())
        val gap = 1.dp.toPx()
        val room = size.height - 6.dp.toPx()
        val round = CornerRadius(3.dp.toPx())
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        for ((i, b) in buckets.withIndex()) {
            val mid = slot * i + slot / 2
            val lag = ((grow.value * (n + 3) - i) / 3f).coerceIn(0f, 1f)
            val up = room * b.income / peak * lag
            val down = room * b.expense / peak * lag
            if (up > 0f) drawRoundRect(gain, Offset(mid - bar - gap, size.height - up), Size(bar, up), round)
            if (down > 0f) drawRoundRect(loss, Offset(mid + gap, size.height - down), Size(bar, down), round)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        for ((i, b) in buckets.withIndex()) {
            Text(
                if (i % step == 0) b.label else "", Modifier.weight(1f),
                fontSize = 9.sp, color = NxDim, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            )
        }
    }
}

@Composable
fun CategoryDonut(slices: List<Slice>) {
    val total = slices.sumOf { it.amount }.coerceAtLeast(1L)
    val colors = slices.indices.map { sliceColor(it) }
    val unfold = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        unfold.snapTo(0f)
        unfold.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(130.dp)) {
            val stroke = 20.dp.toPx()
            val box = Size(size.width - stroke, size.height - stroke)
            var from = -90f
            for ((i, s) in slices.withIndex()) {
                val sweep = 360f * s.amount / total * unfold.value
                val gap = if (slices.size > 1) 2f else 0f
                drawArc(
                    colors[i], from, (sweep - gap * unfold.value).coerceAtLeast(0.5f * unfold.value), false,
                    Offset(stroke / 2, stroke / 2), box, style = Stroke(stroke),
                )
                from += sweep
            }
        }
        Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, s) in slices.withIndex()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                    Text(s.name, Modifier.padding(start = 8.dp), fontSize = 13.sp, maxLines = 1)
                    Text(
                        "  ${100 * s.amount / total}%", fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim,
                    )
                }
            }
        }
    }
}
