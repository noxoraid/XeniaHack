package com.uplink.app

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val THUMB_SIDE = 720

class AgentMedia(val kind: OpenKind, val fs: FsBackend, val path: String, val size: Long, val file: File) {
    val name: String get() = path.substringAfterLast('/')
    var thumb by mutableStateOf<ImageBitmap?>(null)
}

private fun frameOf(file: File): ImageBitmap? {
    val reader = MediaMetadataRetriever()
    return try {
        reader.setDataSource(file.path)
        reader.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    } finally {
        try { reader.release() } catch (_: Throwable) {}
    }
}

@Composable
private fun StillCard(media: AgentMedia, onOpen: () -> Unit) {
    LaunchedEffect(media) {
        if (media.thumb == null) {
            media.thumb = withContext(Dispatchers.IO) {
                if (media.kind == OpenKind.Image) decodeScaled(media.file, THUMB_SIDE)?.asImageBitmap() else frameOf(media.file)
            }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 260.dp).clip(shape).background(NxRaised).nxTap(12.dp, onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = media.thumb
        if (bmp != null) {
            Image(bmp, media.name, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        } else {
            Icon(if (media.kind == OpenKind.Image) NxIcon.Image else NxIcon.Video, null, Modifier.size(28.dp), tint = NxDim)
        }
        if (media.kind == OpenKind.Video) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(NxBg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Icon(NxIcon.Play, null, Modifier.size(22.dp), tint = NxAccent)
            }
        }
        Text(
            media.name,
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(NxBg.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp),
            fontFamily = FontPrefs.mono.family, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SongCard(media: AgentMedia) {
    val ctx = LocalContext.current
    val player = remember { Playback.player(ctx) }
    var live by remember { mutableStateOf(player.isPlaying && player.currentMediaItem?.mediaId == media.file.path) }
    DisposableEffect(player) {
        val watch = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                live = isPlaying && player.currentMediaItem?.mediaId == media.file.path
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                live = player.isPlaying && mediaItem?.mediaId == media.file.path
            }
        }
        player.addListener(watch)
        onDispose { player.removeListener(watch) }
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(NxRaised).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(NxAccent)
                .nxTap(20.dp) { if (live) Playback.pause() else Playback.begin(ctx, listOf(media.file), 0) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (live) NxIcon.Pause else NxIcon.Play, if (live) tr("Pause") else tr("Play"), Modifier.size(20.dp), tint = NxOnAccent)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(media.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(humanSize(media.size), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
        }
        Icon(NxIcon.Music, null, Modifier.size(18.dp), tint = NxDim)
    }
}

@Composable
fun AgentMediaStack(items: List<AgentMedia>) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (media in items) {
            if (media.kind == OpenKind.Audio) SongCard(media)
            else StillCard(media) { FileOpen.start(ctx, media.fs, media.path, media.size) }
        }
    }
}
