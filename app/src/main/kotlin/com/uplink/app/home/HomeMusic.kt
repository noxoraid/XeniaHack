package com.uplink.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object HomeMusic {
    private val extensions = setOf("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "opus")

    @Volatile private var started = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nx", Context.MODE_PRIVATE)

    fun on(ctx: Context): Boolean = prefs(ctx).getBoolean("home_music", true)

    fun setOn(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("home_music", value).apply()
    }

    fun folder(): File = AppFolder.dir("music")

    @Volatile private var bundledPath = ""

    private fun bundled(ctx: Context): List<File> {
        val dir = File(ctx.cacheDir, "bundled_music").also { it.mkdirs() }
        bundledPath = dir.path
        val names = try { ctx.assets.list("music")?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        val out = ArrayList<File>()
        for (name in names.filter { it.substringAfterLast('.', "").lowercase() in extensions }.sorted()) {
            val file = File(dir, name)
            try {
                if (!file.isFile || file.length() == 0L) ctx.assets.open("music/$name").use { src -> file.outputStream().use { src.copyTo(it) } }
                out.add(file)
            } catch (_: Exception) {
            }
        }
        return out
    }

    fun tracks(ctx: Context): List<File> =
        bundled(ctx) + (folder().listFiles { f -> f.isFile && f.length() > 0 && f.extension.lowercase() in extensions }
            ?.sortedBy { it.name.lowercase() }.orEmpty())

    fun ours(): Boolean {
        val id = Playback.peek()?.currentMediaItem?.mediaId ?: return false
        return id.startsWith(folder().path) || (bundledPath.isNotEmpty() && id.startsWith(bundledPath))
    }

    fun play(ctx: Context, list: List<File>) {
        if (list.isEmpty()) return
        Playback.begin(ctx, list, 0)
        Playback.player(ctx).repeatMode = Player.REPEAT_MODE_ALL
    }

    suspend fun autoStart(ctx: Context) {
        if (started) return
        started = true
        if (!on(ctx)) return
        val list = withContext(Dispatchers.IO) { tracks(ctx) }
        if (list.isEmpty()) return
        if (Playback.peek()?.isPlaying == true) return
        play(ctx, list)
    }

    fun stopOurs() {
        if (ours()) Playback.stop()
    }
}

@Composable
fun HomeMusicCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(HomeMusic.on(ctx)) }
    var count by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HomeMusic.autoStart(ctx)
        while (true) {
            count = withContext(Dispatchers.IO) { HomeMusic.tracks(ctx).size }
            repeat(10) {
                val p = Playback.peek()
                playing = p?.isPlaying == true
                title = if (HomeMusic.ours()) p?.mediaMetadata?.title?.toString().orEmpty() else ""
                delay(500)
            }
        }
    }

    NxCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NxIconBox(NxIcon.Music, box = 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tr("Music"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    when {
                        title.isNotEmpty() -> title
                        count == 0 -> tr("Put mp3 or wav files in Uplink/music")
                        else -> tr("{0} tracks", count)
                    },
                    fontSize = 12.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (on && count > 0) {
                NxIconButton(if (playing) NxIcon.Pause else NxIcon.Play, if (playing) tr("Pause") else tr("Play")) {
                    val p = Playback.peek()
                    if (p != null && HomeMusic.ours()) {
                        if (p.isPlaying) p.pause() else p.play()
                    } else HomeMusic.play(ctx, HomeMusic.tracks(ctx))
                }
                NxIconButton(NxIcon.Left, tr("Previous")) { Playback.peek()?.takeIf { HomeMusic.ours() }?.seekToPreviousMediaItem() }
                NxIconButton(NxIcon.Right, tr("Next")) { Playback.peek()?.takeIf { HomeMusic.ours() }?.seekToNextMediaItem() }
            }
            NxSwitch(on) {
                on = !on
                HomeMusic.setOn(ctx, on)
                if (on) HomeMusic.play(ctx, HomeMusic.tracks(ctx)) else HomeMusic.stopOurs()
            }
        }
    }
}
