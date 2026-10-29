package com.uplink.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

object Playback {
    @Volatile
    private var held: ExoPlayer? = null
    private var link: Any? = null

    fun peek(): ExoPlayer? = held

    fun player(ctx: Context): ExoPlayer {
        held?.let { return it }
        synchronized(this) {
            held?.let { return it }
            val built = ExoPlayer.Builder(ctx.applicationContext)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
            held = built
            return built
        }
    }

    fun queueOf(t: ViewTarget, file: File): Pair<List<File>, Int> {
        if (!isLocal(t.fs)) return listOf(file) to 0
        val folder = file.parentFile ?: return listOf(file) to 0
        val found = folder.listFiles { f -> f.isFile && FileSniff.byName(f.name) == OpenKind.Audio }
            ?.sortedBy { it.name.lowercase() }
            ?: return listOf(file) to 0
        val at = found.indexOfFirst { it.path == file.path }
        return if (at < 0) listOf(file) to 0 else found to at
    }

    private fun itemOf(file: File): MediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(file))
        .setMediaId(file.path)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(file.nameWithoutExtension).build())
        .build()

    fun begin(ctx: Context, files: List<File>, index: Int) {
        val p = player(ctx)
        connect(ctx.applicationContext)
        val same = p.mediaItemCount == files.size && p.currentMediaItem?.mediaId == files[index].path
        if (same) {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(index, 0L)
            if (!p.isPlaying) p.play()
            return
        }
        p.setMediaItems(files.map { itemOf(it) }, index, 0L)
        p.prepare()
        p.play()
    }

    private fun connect(app: Context) {
        if (link != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        link = MediaController.Builder(app, token).buildAsync()
    }

    fun pause() {
        held?.pause()
    }

    fun stop() {
        held?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    fun drop() {
        held?.release()
        held = null
        link = null
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val loopCommand = SessionCommand(LOOP_ACTION, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val player = Playback.player(this)
        session = MediaSession.Builder(this, player).setCallback(LoopCallback()).build()
        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                publishLoop()
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        Playback.drop()
        super.onDestroy()
    }

    private fun loopLayout(mode: Int): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName(loopLabel(mode))
            .setIconResId(loopIcon(mode))
            .setSessionCommand(loopCommand)
            .build(),
    )

    private fun publishLoop() {
        val s = session ?: return
        val layout = loopLayout(s.player.repeatMode)
        for (controller in s.connectedControllers) s.setCustomLayout(controller, layout)
    }

    private inner class LoopCallback : MediaSession.Callback {
        override fun onConnect(s: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(s)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(loopCommand).build(),
                )
                .setCustomLayout(loopLayout(s.player.repeatMode))
                .build()

        override fun onCustomCommand(
            s: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != LOOP_ACTION) return super.onCustomCommand(s, controller, customCommand, args)
            s.player.repeatMode = nextLoop(s.player.repeatMode)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val LOOP_ACTION = "com.uplink.app.LOOP"
    }
}

private fun nextLoop(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

private fun loopLabel(mode: Int): String = when (mode) {
    Player.REPEAT_MODE_ALL -> tr("Repeat: All")
    Player.REPEAT_MODE_ONE -> tr("Repeat: One")
    else -> tr("Repeat: Off")
}

private fun loopIcon(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
    else -> R.drawable.ic_repeat_off
}
