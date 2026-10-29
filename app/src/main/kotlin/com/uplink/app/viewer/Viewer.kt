package com.uplink.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

private const val EDIT_LIMIT = 256 * 1024L
private const val EDIT_READ_CAP = 1024 * 1024

private fun readCapped(fs: FsBackend, path: String): ByteArray {
    fs.open(path).use { s ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            if (out.size() + n > EDIT_READ_CAP) throw IllegalStateException(tr("File too large to edit"))
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

@Composable
fun ViewerScreen(t: ViewTarget) {
    BackHandler { Nav.viewing = null }
    when (t.kind) {
        OpenKind.Text ->
            if (!t.readOnly && t.size <= EDIT_LIMIT) {
                EditorScreen(
                    path = t.path,
                    load = { readCapped(t.fs, t.path) },
                    save = { data -> t.fs.create(t.path).use { it.write(data) } },
                    onBack = { Nav.viewing = null },
                )
            } else {
                StagedGate(t) { file -> TextPager(t, file) }
            }
        OpenKind.Image -> StagedGate(t) { file -> ImageViewer(t, file) }
        OpenKind.Audio -> StagedGate(t) { file -> AudioPlayer(t, file) }
        OpenKind.Video -> StagedGate(t) { file -> VideoPlayer(t, file) }
        OpenKind.Binary -> BinaryNotice(t)
    }
}

@Composable
private fun BinaryNotice(t: ViewTarget) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        NxBar(
            t.path.substringAfterLast('/'),
            subtitle = t.path.substringBeforeLast('/', "/"),
            nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.viewing = null } },
        )
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(NxIcon.File, null, Modifier.size(44.dp), tint = NxDim)
                Text(tr("Binary file"), Modifier.padding(top = 14.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(humanSize(t.size), Modifier.padding(top = 4.dp, bottom = 20.dp), color = NxDim, fontSize = 13.sp)
                NxButton(tr("Read as text"), icon = NxIcon.File) { FileOpen.start(ctx, t.fs, t.path, t.size, asText = true) }
            }
        }
    }
}
