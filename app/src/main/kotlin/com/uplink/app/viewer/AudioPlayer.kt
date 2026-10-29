package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private fun titleOf(item: MediaItem?): String =
    item?.mediaMetadata?.title?.toString() ?: item?.mediaId?.substringAfterLast('/') ?: ""

private fun resume(player: Player) {
    if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(0)
    player.play()
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun AudioPlayer(t: ViewTarget, file: File) {
    val ctx = LocalContext.current
    val player = remember { Playback.player(ctx) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var title by remember { mutableStateOf(titleOf(player.currentMediaItem)) }
    var order by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var repeat by remember { mutableIntStateOf(player.repeatMode) }
    var shuffled by remember { mutableStateOf(player.shuffleModeEnabled) }

    LaunchedEffect(file) {
        val queue = withContext(Dispatchers.IO) { Playback.queueOf(t, file) }
        Playback.begin(ctx, queue.first, queue.second)
    }

    DisposableEffect(player) {
        val watcher = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = titleOf(mediaItem)
                order = player.currentMediaItemIndex
                failure = null
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                repeat = repeatMode
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffled = shuffleModeEnabled
            }

            override fun onPlayerError(error: PlaybackException) {
                failure = error.errorCodeName
            }
        }
        player.addListener(watcher)
        onDispose { player.removeListener(watcher) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            length = player.duration.takeIf { it > 0 } ?: 0L
            delay(300)
        }
    }

    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxSize()) {
        NxBar(
            tr("Player"),
            subtitle = "${order + 1} / ${player.mediaItemCount.coerceAtLeast(1)}",
            nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.viewing = null } },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(200.dp).clip(shape).background(NxRaised).border(1.dp, NxLine, shape),
                contentAlignment = Alignment.Center,
            ) { Icon(NxIcon.Music, null, Modifier.size(72.dp), tint = NxAccent) }
            Text(
                title, Modifier.padding(top = 24.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                failure?.let { tr("Cannot play: {0}", it) } ?: if (playing) tr("Playing") else tr("Paused"),
                Modifier.padding(top = 4.dp), fontSize = 12.sp,
                color = if (failure != null) NxDanger else NxDim,
            )
            Slider(
                value = drag ?: position.toFloat().coerceAtMost(length.toFloat()),
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { player.seekTo(it.toLong()) }
                    drag = null
                },
                valueRange = 0f..maxOf(length.toFloat(), 1f),
                modifier = Modifier.padding(top = 20.dp),
                colors = SliderDefaults.colors(thumbColor = NxAccent, activeTrackColor = NxAccent, inactiveTrackColor = NxLine),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(clock((drag ?: position.toFloat()).toLong()), Modifier.weight(1f), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim)
                Text(clock(length), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim)
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                NxIconButton(NxIcon.Left, tr("Previous")) { player.seekToPreviousMediaItem() }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(NxAccent)
                        .clickable { if (playing) player.pause() else resume(player) },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (playing) NxIcon.Pause else NxIcon.Play, if (playing) tr("Pause") else tr("Play"), Modifier.size(30.dp), tint = NxOnAccent) }
                NxIconButton(NxIcon.Right, tr("Next")) { player.seekToNextMediaItem() }
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NxButton(
                    if (playing) tr("Pause") else tr("Resume"),
                    icon = if (playing) NxIcon.Pause else NxIcon.Play,
                    tone = Tone.Accent, compact = true,
                ) { if (playing) player.pause() else resume(player) }
                NxButton(
                    when (repeat) {
                        Player.REPEAT_MODE_ALL -> tr("Repeat: All")
                        Player.REPEAT_MODE_ONE -> tr("Repeat: One")
                        else -> tr("Repeat: Off")
                    },
                    tone = if (repeat == Player.REPEAT_MODE_OFF) Tone.Quiet else Tone.Accent, compact = true,
                ) {
                    player.repeatMode = when (repeat) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
                NxButton(tr("Shuffle"), tone = if (shuffled) Tone.Accent else Tone.Quiet, compact = true) {
                    player.shuffleModeEnabled = !shuffled
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NxButton("-10s", compact = true) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)) }
                NxButton("+10s", compact = true) { player.seekTo(player.currentPosition + 10_000) }
                NxButton(tr("Stop"), tone = Tone.Danger, compact = true) {
                    Playback.stop()
                    Nav.viewing = null
                }
            }
        }
    }
}
