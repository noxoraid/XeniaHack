package com.uplink.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun Modifier.nxEnter(index: Int = 0, rise: Dp = 12.dp): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 40L).coerceAtMost(400L))
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * rise.toPx()
    }
}

fun Modifier.nxPulse(low: Float = 0.45f, millis: Int = 900): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulse")
    val level by transition.animateFloat(
        initialValue = low,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseLevel",
    )
    graphicsLayer { alpha = level }
}

fun Modifier.nxFloat(rise: Dp = 4.dp, millis: Int = 2400, phase: Int = 0): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "float")
    val lift = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(phase % millis, StartOffsetType.FastForward),
        ),
        label = "floatLift",
    )
    graphicsLayer { translationY = lift.value * rise.toPx() }
}

fun Modifier.nxNudge(reach: Dp = 4.dp, millis: Int = 1500, phase: Int = 0): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "nudge")
    val shift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(phase % millis, StartOffsetType.FastForward),
        ),
        label = "nudgeShift",
    )
    graphicsLayer { translationX = shift.value * reach.toPx() }
}

fun Modifier.nxBreathe(depth: Float = 0.05f, millis: Int = 2600): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "breathe")
    val swell = transition.animateFloat(
        initialValue = 1f - depth,
        targetValue = 1f + depth,
        animationSpec = infiniteRepeatable(tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breatheSwell",
    )
    graphicsLayer { scaleX = swell.value; scaleY = swell.value }
}

fun Modifier.nxShine(millis: Int = 4200): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shine")
    val cycle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
        label = "shineCycle",
    )
    drawWithContent {
        drawContent()
        val sweep = (cycle.value / 0.26f).coerceIn(0f, 1f)
        if (sweep > 0f && sweep < 1f) {
            val band = size.width * 0.45f
            val x = (size.width + band) * sweep - band
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.24f), Color.Transparent), x, x + band))
        }
    }
}

fun Modifier.nxScan(): Modifier = composed {
    val line = NxLine
    val glow = NxAccent
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "scanSweep",
    )
    drawBehind {
        drawRect(line)
        val band = size.width * 0.4f
        val x = (size.width + band) * sweep.value - band
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, glow, Color.Transparent), x, x + band))
    }
}

fun Modifier.nxRipple(tint: Color, millis: Int = 1800): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "ripple")
    val spread = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
        label = "rippleSpread",
    )
    drawBehind {
        val reach = size.minDimension / 2f
        val t = spread.value
        drawCircle(tint.copy(alpha = (1f - t) * 0.5f), reach * (0.3f + 0.7f * t), style = Stroke(1.5.dp.toPx()))
        drawCircle(tint, reach * 0.3f)
    }
}

fun Modifier.nxStagger(index: Int, open: Boolean): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            delay(70L + index * 42L)
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f))
        } else {
            delay(320)
            progress.snapTo(0f)
        }
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationX = (progress.value - 1f) * 44.dp.toPx()
    }
}

class NxSeen {
    private val keys = HashSet<Any>()

    fun first(key: Any): Boolean = keys.add(key)
}

@Composable
fun rememberNxSeen(vararg inputs: Any?): NxSeen = remember(*inputs) { NxSeen() }

fun Modifier.nxRow(seen: NxSeen, key: Any, index: Int): Modifier = composed {
    val fresh = remember(key) { seen.first(key) }
    val progress = remember(key) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(key) {
        if (fresh) {
            delay(if (index < 12) index * 40L else 0L)
            progress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

@Composable
fun NxAmbient(modifier: Modifier = Modifier) {
    val warm = NxAccent
    val cool = NxGlow
    val dot = NxText.copy(alpha = 0.07f)
    val gap = 26f * LocalDensity.current.density
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing)),
        label = "ambientDrift",
    )
    val flow = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "ambientFlow",
    )
    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val t = drift.value * 2f * PI.toFloat()
                translationX = size.width * 0.12f * cos(t)
                translationY = size.height * 0.06f * sin(t)
            },
        ) {
            drawRect(
                Brush.radialGradient(listOf(warm.copy(alpha = 0.15f), Color.Transparent), Offset(size.width * 0.25f, size.height * 0.16f), size.width * 0.95f),
                topLeft = Offset(-size.width, -size.height),
                size = Size(size.width * 3f, size.height * 3f),
            )
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val t = drift.value * 2f * PI.toFloat()
                translationX = -size.width * 0.12f * sin(t)
                translationY = size.height * 0.06f * cos(t)
            },
        ) {
            drawRect(
                Brush.radialGradient(listOf(cool.copy(alpha = 0.11f), Color.Transparent), Offset(size.width * 0.86f, size.height * 0.74f), size.width * 0.9f),
                topLeft = Offset(-size.width, -size.height),
                size = Size(size.width * 3f, size.height * 3f),
            )
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = (flow.value - 1f) * gap
                translationY = (flow.value - 1f) * gap * 0.5f
            },
        ) {
            val points = ArrayList<Offset>()
            var y = 0f
            while (y <= size.height + gap) {
                var x = 0f
                while (x <= size.width + gap) {
                    points.add(Offset(x, y))
                    x += gap
                }
                y += gap
            }
            drawPoints(points, PointMode.Points, dot, 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
