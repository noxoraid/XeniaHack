package com.uplink.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NxGauge(frac: Float, modifier: Modifier = Modifier, stroke: Dp = 9.dp, content: @Composable BoxScope.() -> Unit) {
    val level = animateFloatAsState(frac.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "gaugeLevel")
    val transition = rememberInfiniteTransition(label = "gauge")
    val spin = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "gaugeSpin")
    val track = NxRaised
    val hot = if (frac > 0.9f) NxDanger else NxAccent
    val unit = LocalDensity.current.density
    val dots = remember(unit) { PathEffect.dashPathEffect(floatArrayOf(2f * unit, 6f * unit)) }
    Box(
        modifier.drawBehind {
            val width = stroke.toPx()
            val box = Size(size.width - width, size.height - width)
            val origin = Offset(width / 2f, width / 2f)
            drawArc(track, 135f, 270f, false, origin, box, style = Stroke(width, cap = StrokeCap.Round))
            val sweep = 270f * level.value
            if (sweep > 0.5f) {
                drawArc(hot, 135f, sweep, false, origin, box, style = Stroke(width, cap = StrokeCap.Round))
                val angle = (135f + sweep) * (PI / 180.0)
                val head = Offset(
                    center.x + box.width / 2f * cos(angle).toFloat(),
                    center.y + box.height / 2f * sin(angle).toFloat(),
                )
                drawCircle(Color.White.copy(alpha = 0.9f), width * 0.2f, head)
            }
            rotate(spin.value, center) {
                drawCircle(
                    hot.copy(alpha = 0.28f), size.minDimension / 2f - width * 1.7f, center,
                    style = Stroke(1.dp.toPx(), pathEffect = dots),
                )
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun NxMeter(frac: Float, modifier: Modifier = Modifier, warn: Boolean = false) {
    val level = animateFloatAsState(frac.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "meterLevel")
    val transition = rememberInfiniteTransition(label = "meter")
    val sweep = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "meterSweep")
    val track = NxRaised
    val hot = if (warn) NxDanger else NxAccent
    Spacer(
        modifier.fillMaxWidth().height(8.dp).drawBehind {
            val round = CornerRadius(size.height / 2f)
            drawRoundRect(track, cornerRadius = round)
            val filled = size.width * level.value
            if (filled > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(hot.copy(alpha = 0.6f), hot), 0f, filled),
                    size = Size(filled, size.height), cornerRadius = round,
                )
                clipRect(right = filled) {
                    val band = 36.dp.toPx()
                    val x = (filled + band) * sweep.value - band
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent), x, x + band))
                }
            }
        },
    )
}
