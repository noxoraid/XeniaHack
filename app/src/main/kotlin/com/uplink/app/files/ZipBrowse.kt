package com.uplink.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

object ZipOpen {
    var fs by mutableStateOf<ZipFs?>(null)
    var path by mutableStateOf("/")
    var tab by mutableStateOf(0)

    fun enter(z: ZipFs, onTab: Int) {
        fs?.discard()
        fs = z
        path = "/"
        tab = onTab
    }

    fun leave() {
        fs?.discard()
        fs = null
        path = "/"
    }
}

class PasswordAsk(val file: File, val writable: Boolean, val onReady: (ZipFs) -> Unit)

class ZipMaker(val dir: String, val names: List<String>)

fun openZipFile(
    ctx: Context,
    scope: CoroutineScope,
    srcFs: FsBackend,
    full: String,
    size: Long,
    onAsk: (PasswordAsk) -> Unit,
    onReady: (ZipFs) -> Unit,
) {
    val app = ctx.applicationContext
    scope.launch {
        val local = isLocal(srcFs)
        val file: File
        if (local) {
            file = File(full)
        } else {
            val target = ViewTarget(srcFs, full, size, OpenKind.Binary, true)
            val dest = MediaStage.target(app, target)
            if (!(dest.isFile && dest.length() == size)) {
                val run = MediaStage.start(target, dest)
                toast(ctx, tr("Running in background, see Tasks"))
                snapshotFlow { run.state }.first { it != TaskState.Running }
                if (run.state != TaskState.Done) return@launch
            }
            file = dest
        }
        val made = withContext(Dispatchers.IO) {
            runCatching { ZipFs(file, "", local, app.cacheDir).let { it to it.needsPassword } }
        }
        val pair = made.getOrNull()
        if (pair == null) {
            toast(ctx, tr("Cannot open archive: {0}", made.exceptionOrNull()?.message ?: ""))
            return@launch
        }
        if (pair.second) onAsk(PasswordAsk(file, local, onReady)) else onReady(pair.first)
    }
}

fun startZipBuild(app: Context, srcFs: FsBackend, dir: String, names: List<String>, name: String, secret: String, strong: Boolean) {
    val outName = if (name.lowercase().endsWith(".zip")) name else "$name.zip"
    val outFile = File(dir, outName)
    val method = if (strong) EncryptionMethod.AES else EncryptionMethod.ZIP_STANDARD
    TaskBoard.start(TaskKind.Zip, tr("Zip {0}", outName)) { run ->
        val z = ZipFs(outFile, secret, true, app.cacheDir, method)
        z.beginBatch()
        try {
            run.detail = tr("Scanning")
            for (n in names) {
                val r = TreeScan.scan(srcFs, joinPath(dir, n), emptyList())
                run.total += r.files
                run.totalBytes += r.bytes
            }
            for (n in names) {
                if (run.stop) throw TaskStopped()
                copyTracked(srcFs, joinPath(dir, n), z, "/" + n, emptyList(), run, false)
            }
        } catch (e: Throwable) {
            z.discard()
            throw e
        } finally {
            z.endBatch()
        }
    }
}

@Composable
fun PasswordDialog(a: PasswordAsk, onDismiss: () -> Unit, onOpened: (ZipFs) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    NxDialog(tr("Password required"), onDismiss, if (checking) tr("Checking") else tr("Open"), {
        if (!checking) {
            checking = true
            wrong = false
            scope.launch {
                val made = withContext(Dispatchers.IO) {
                    runCatching { ZipFs(a.file, secret, a.writable, ctx.applicationContext.cacheDir).let { z -> if (z.passwordOk()) z else null } }
                }
                checking = false
                val z = made.getOrNull()
                if (z == null) wrong = true else onOpened(z)
            }
        }
    }) {
        Text(a.file.name, Modifier.padding(bottom = 12.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim)
        NxField(secret, { secret = it }, tr("Archive password"), obscure = true)
        if (wrong) Text(tr("Wrong password"), color = NxDanger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
fun ZipDialog(job: ZipMaker, onDismiss: () -> Unit, onMake: (String, String, Boolean) -> Unit) {
    val first = job.names.first()
    var name by remember { mutableStateOf(if (job.names.size == 1) first.substringBeforeLast('.', first) + ".zip" else "archive.zip") }
    var secret by remember { mutableStateOf("") }
    var strong by remember { mutableStateOf(true) }
    NxDialog(tr("Create zip"), onDismiss, tr("Create"), { if (name.isNotBlank()) onMake(name.trim(), secret, strong) }) {
        NxField(name, { name = it }, tr("Zip name"), mono = true)
        NxField(secret, { secret = it }, tr("Password (optional)"), obscure = true)
        if (secret.isNotEmpty()) {
            Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NxButton("AES-256", tone = if (strong) Tone.Accent else Tone.Quiet, compact = true) { strong = true }
                NxButton("ZipCrypto", tone = if (!strong) Tone.Accent else Tone.Quiet, compact = true) { strong = false }
            }
        }
    }
}

