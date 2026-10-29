package com.uplink.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_SIDE = 2560

internal fun decodeScaled(file: File, side: Int = MAX_SIDE): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > side) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = try { BitmapFactory.decodeFile(file.path, opts) } catch (_: OutOfMemoryError) { null } ?: return null
    val turn = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Throwable) {
        0f
    }
    if (turn == 0f) return raw
    val matrix = Matrix().apply { postRotate(turn) }
    return try { Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true) } catch (_: OutOfMemoryError) { raw }
}

private fun neighbours(t: ViewTarget): List<FsItem> = try {
    t.fs.list(parentOf(t.path)).filter { !it.dir && FileSniff.byName(it.name) == OpenKind.Image }
} catch (_: Throwable) {
    emptyList()
}

@Composable
fun ImageViewer(t: ViewTarget, file: File) {
    var picture by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var siblings by remember(t.path) { mutableStateOf<List<FsItem>>(emptyList()) }
    var zoom by remember(file) { mutableFloatStateOf(1f) }
    var shift by remember(file) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file) {
        val bmp = withContext(Dispatchers.IO) { decodeScaled(file) }
        if (bmp == null) failed = true else picture = bmp.asImageBitmap()
    }
    LaunchedEffect(t.path) { siblings = withContext(Dispatchers.IO) { neighbours(t) } }

    val dir = parentOf(t.path)
    val name = t.path.substringAfterLast('/')
    val at = siblings.indexOfFirst { it.name == name }

    fun hop(step: Int) {
        val next = siblings.getOrNull(at + step) ?: return
        Nav.viewing = ViewTarget(t.fs, joinPath(dir, next.name), next.size, OpenKind.Image, false)
    }

    Column(Modifier.fillMaxSize()) {
        NxBar(
            name,
            subtitle = if (at >= 0) "${at + 1} / ${siblings.size}" else dir,
            nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.viewing = null } },
            actions = {
                NxIconButton(NxIcon.Left, tr("Previous"), if (at > 0) NxText else NxDim) { hop(-1) }
                NxIconButton(NxIcon.Right, tr("Next"), if (at in 0 until siblings.size - 1) NxText else NxDim) { hop(1) }
            },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val shown = picture
            when {
                failed -> Text(tr("Cannot decode this image"), color = NxDim)
                shown == null -> Text(tr("Opening..."), color = NxDim)
                else -> Image(
                    shown, null,
                    Modifier.fillMaxSize()
                        .pointerInput(file) {
                            detectTapGestures(onDoubleTap = {
                                zoom = if (zoom > 1f) 1f else 2.5f
                                shift = Offset.Zero
                            })
                        }
                        .pointerInput(file) {
                            detectTransformGestures { _, pan, scale, _ ->
                                zoom = (zoom * scale).coerceIn(1f, 8f)
                                shift = if (zoom == 1f) Offset.Zero else shift + pan
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = shift.x
                            translationY = shift.y
                        },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
