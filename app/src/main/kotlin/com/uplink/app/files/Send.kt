package com.uplink.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Link(val profile: Profile, val vps: Vps, val own: Boolean)

private val quickSkips = listOf(".git", "node_modules", ".gradle", "build", ".idea", "*.log", ".DS_Store")

private fun sameServer(a: Profile, b: Profile) = a.host == b.host && a.port == b.port && a.user == b.user

@Composable
fun SendDialog(fs: FsBackend, dir: String, names: List<String>, onDismiss: () -> Unit, onDone: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles = remember { loadProfiles(ctx) }
    var sel by remember {
        mutableStateOf(
            profiles.indexOfFirst { p -> Hub.vps?.profile?.let { sameServer(it, p) } == true }.coerceAtLeast(0),
        )
    }
    var dest by remember { mutableStateOf(Cfg.sendPath(ctx)) }
    var move by remember { mutableStateOf(false) }
    var skips by remember { mutableStateOf(Cfg.excludes(ctx)) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf<Link?>(null) }
    var preview by remember { mutableStateOf<ScanResult?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            val l = link
            if (l != null && l.own) Thread { try { l.vps.close() } catch (_: Throwable) {} }.start()
        }
    }

    LaunchedEffect(skips) {
        if (!isLocal(fs)) return@LaunchedEffect
        preview = null
        delay(350)
        val patterns = TreeScan.parse(skips)
        preview = try {
            withContext(Dispatchers.IO) {
                var files = 0
                var bytes = 0L
                var skipped = 0
                for (n in names) {
                    val r = TreeScan.scan(fs, joinPath(dir, n), patterns)
                    files += r.files
                    bytes += r.bytes
                    skipped += r.skipped
                }
                ScanResult(files, bytes, skipped)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun ensure(): Vps {
        val p = profiles[sel]
        val live = Hub.vps
        if (live != null && sameServer(live.profile, p)) return live
        val cur = link
        if (cur != null && sameServer(cur.profile, p)) return cur.vps
        if (cur != null && cur.own) try { cur.vps.close() } catch (_: Throwable) {}
        val v = Vps(p)
        v.connect()
        link = Link(p, v, true)
        return v
    }

    fun check() {
        if (busy != null || profiles.isEmpty()) return
        scope.launch {
            busy = tr("Checking")
            status = null
            try {
                val r = withContext(Dispatchers.IO) {
                    val v = ensure()
                    val fixed = fixPath(dest, v.home())
                    fixed to v.kind(fixed)
                }
                dest = r.first
                when (r.second) {
                    2 -> { status = tr("Folder exists"); statusOk = true }
                    0 -> { status = tr("Folder will be created"); statusOk = true }
                    else -> { status = tr("This path is a file, pick a folder"); statusOk = false }
                }
            } catch (e: Throwable) {
                status = e.message ?: e.toString()
                statusOk = false
            }
            busy = null
        }
    }

    fun send() {
        if (busy != null || profiles.isEmpty()) return
        Cfg.setExcludes(ctx, skips)
        startSend(ctx, fs, dir, names, profiles[sel], dest, move, TreeScan.parse(skips))
        toast(ctx, tr("Running in background, see Tasks"))
        onDone(false)
    }

    fun toggleSkip(word: String) {
        val list = TreeScan.parse(skips).toMutableList()
        if (!list.remove(word)) list.add(word)
        skips = list.joinToString(", ")
    }

    NxDialog(
        title = tr("Send to VPS"),
        onDismiss = { if (busy == null) onDismiss() },
        confirm = if (move) tr("Move") else tr("Copy"),
        onConfirm = { send() },
    ) {
        val label = names.take(3).joinToString(", ") + if (names.size > 3) tr(" +{0} more", names.size - 3) else ""
        Text(
            tr("From {0}: {1}", fs.label, label),
            Modifier.padding(bottom = 14.dp),
            fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim,
        )
        if (profiles.isEmpty()) {
            Text(tr("No servers saved yet. Add one in SSH first."), Modifier.padding(bottom = 14.dp), color = NxDanger, fontSize = 13.sp)
        } else {
            Text(tr("Server"), Modifier.padding(bottom = 6.dp), fontSize = 12.sp, color = NxDim)
            NxFlow(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                profiles.forEachIndexed { i, p ->
                    NxButton(p.name, icon = NxIcon.Server, tone = if (i == sel) Tone.Accent else Tone.Quiet, compact = true) {
                        if (busy == null && i != sel) {
                            sel = i
                            status = null
                        }
                    }
                }
            }
            NxField(dest, { dest = it; status = null }, tr("Destination path on server"), mono = true)
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NxButton(tr("Check path"), icon = NxIcon.Check, compact = true) { check() }
                NxButton(tr("Copy"), tone = if (!move) Tone.Accent else Tone.Quiet, compact = true) { move = false }
                NxButton(tr("Move"), tone = if (move) Tone.Danger else Tone.Quiet, compact = true) { move = true }
            }
            NxField(skips, { skips = it }, tr("Never send (names or patterns, comma separated)"), mono = true, lines = 2)
            val parsed = TreeScan.parse(skips)
            NxFlow(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                for (word in quickSkips) {
                    NxButton(word, tone = if (word in parsed) Tone.Accent else Tone.Quiet, compact = true) { toggleSkip(word) }
                }
            }
            val p = preview
            if (p != null) {
                Text(
                    tr("{0} files  ·  {1}  ·  {2} skipped", p.files, humanSize(p.bytes), p.skipped),
                    Modifier.padding(bottom = 10.dp),
                    fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxAccent,
                )
            }
            if (move) {
                Text(
                    tr("Only files that were sent are removed. Skipped items stay on this device."),
                    Modifier.padding(bottom = 10.dp), fontSize = 12.sp, color = NxDim,
                )
            }
        }
        val s = status
        if (s != null) {
            Text(s, Modifier.padding(bottom = 12.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (statusOk) NxLive else NxDanger)
        }
        if (busy != null) {
            Text("$busy...", Modifier.padding(bottom = 12.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxAccent)
        }
    }
}
