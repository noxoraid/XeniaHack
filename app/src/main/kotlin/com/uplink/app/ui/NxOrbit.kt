package com.uplink.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NxOrbit(modifier: Modifier = Modifier) {
    val accent = NxAccent
    val ink = NxText
    val dim = NxDim
    val unit = LocalDensity.current.density
    val dashed = remember(unit) { PathEffect.dashPathEffect(floatArrayOf(3f * unit, 7f * unit)) }
    val transition = rememberInfiniteTransition(label = "orbit")
    val outer = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "orbitOuter")
    val inner = transition.animateFloat(360f, 0f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "orbitInner")
    val racer = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(7200, easing = LinearEasing)), label = "orbitRacer")
    val wave = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "orbitWave")
    val beat = transition.animateFloat(0.9f, 1.04f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orbitBeat")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val reach = size.minDimension / 2f
            val mid = center
            val hair = 1.dp.toPx()
            rotate(outer.value, mid) {
                drawCircle(dim.copy(alpha = 0.35f), reach * 0.96f, mid, style = Stroke(hair, pathEffect = dashed))
            }
            rotate(inner.value, mid) {
                drawCircle(accent.copy(alpha = 0.4f), reach * 0.56f, mid, style = Stroke(hair, pathEffect = dashed))
            }
            drawCircle(ink.copy(alpha = 0.12f), reach * 0.76f, mid, style = Stroke(hair))
            val t = wave.value
            drawCircle(accent.copy(alpha = (1f - t) * 0.35f), reach * (0.3f + 0.66f * t), mid, style = Stroke(1.5.dp.toPx()))
            for (n in 0..2) {
                val angle = (racer.value + n * 120f) * (PI / 180.0)
                val spot = Offset(mid.x + reach * 0.76f * cos(angle).toFloat(), mid.y + reach * 0.76f * sin(angle).toFloat())
                drawCircle(accent.copy(alpha = 0.22f), 7.dp.toPx(), spot)
                drawCircle(if (n == 0) accent else ink.copy(alpha = 0.8f), 3.dp.toPx(), spot)
            }
            val back = (inner.value * 1.5f) * (PI / 180.0)
            val moon = Offset(mid.x + reach * 0.56f * cos(back).toFloat(), mid.y + reach * 0.56f * sin(back).toFloat())
            drawCircle(accent, 2.5.dp.toPx(), moon)
        }
        LogoMark(1f, Modifier.fillMaxSize(0.42f).graphicsLayer { scaleX = beat.value; scaleY = beat.value })
    }
}
