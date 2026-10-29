package com.uplink.app

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
fun VideoPlayer(t: ViewTarget, file: File) {
    val app = LocalContext.current.applicationContext
    var failure by remember(file) { mutableStateOf<String?>(null) }
    val player = remember(file) {
        Playback.pause()
        ExoPlayer.Builder(app).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val watcher = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failure = error.errorCodeName
            }
        }
        player.addListener(watcher)
        onDispose {
            player.removeListener(watcher)
            player.release()
        }
    }

    Column(Modifier.fillMaxSize()) {
        NxBar(
            t.path.substringAfterLast('/'),
            subtitle = humanSize(t.size),
            nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.viewing = null } },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = true
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.player = null },
            )
            failure?.let { Text(tr("Cannot play: {0}", it), color = NxDanger) }
        }
    }
}
