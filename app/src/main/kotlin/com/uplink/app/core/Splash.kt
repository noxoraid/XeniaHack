package com.uplink.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LogoMark(progress: Float, modifier: Modifier = Modifier) {
    val accent = NxAccent
    val ink = NxText
    Canvas(modifier) {
        val s = size.minDimension / 96f
        val path = Path().apply {
            moveTo(26f * s, 22f * s)
            lineTo(26f * s, 50f * s)
            cubicTo(26f * s, 76f * s, 70f * s, 76f * s, 70f * s, 50f * s)
            lineTo(70f * s, 22f * s)
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val seg = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), seg, true)
        drawPath(
            seg, accent,
            style = Stroke(width = 9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (progress >= 0.98f) {
            drawLine(ink, Offset(40f * s, 44f * s), Offset(56f * s, 44f * s), strokeWidth = 7f * s, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun Splash(onDone: () -> Unit) {
    val draw = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    var step by remember { mutableStateOf(tr("Initializing")) }
    val finish by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        launch { draw.animateTo(1f, tween(950, easing = FastOutSlowInEasing)) }
        launch { glow.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        launch {
            delay(450)
            title.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
        }
        launch { bar.animateTo(1f, tween(2300, easing = LinearEasing)) }
        delay(750)
        step = tr("Loading modules")
        delay(750)
        step = tr("Preparing workspace")
        delay(650)
        step = tr("Ready")
        delay(400)
        finish()
    }

    val word = "UntilyFlow"
    val accent = NxAccent

    Box(Modifier.fillMaxSize().background(NxBg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.20f * glow.value), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.40f),
                    radius = size.minDimension * 0.75f,
                ),
                radius = size.minDimension * 0.75f,
                center = Offset(size.width / 2f, size.height * 0.40f),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(bottom = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.nx_logo), null,
                Modifier.size(112.dp).graphicsLayer {
                    alpha = draw.value.coerceIn(0f, 1f)
                    scaleX = 0.9f + 0.1f * draw.value
                    scaleY = 0.9f + 0.1f * draw.value
                },
            )
            Row(Modifier.padding(top = 28.dp)) {
                word.forEachIndexed { i, c ->
                    val a = ((title.value * (word.length + 4) - i) / 4f).coerceIn(0f, 1f)
                    Text(
                        c.toString(),
                        Modifier.graphicsLayer {
                            alpha = a
                            translationY = (1f - a) * 18f
                        },
                        fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        color = if (i >= 6) NxAccent else NxText,
                    )
                }
            }
            Text(
                tr("tools  /  utilities  /  flow"),
                Modifier.padding(top = 10.dp).graphicsLayer { alpha = title.value.coerceIn(0f, 1f) },
                fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(NxLine)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bar.value).background(NxAccent))
            }
            Text(
                step,
                Modifier.padding(top = 14.dp),
                fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim,
            )
        }
    }
}
