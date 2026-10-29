package com.uplink.app

import kotlinx.coroutines.delay
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun leave(path: String): String = if (path == "/") "/" else parentOf(path)

@Composable
private fun SidePane(
    title: String,
    icon: ImageVector,
    fs: FsBackend,
    path: String,
    picked: SnapshotStateList<String>,
    reload: Int,
    modifier: Modifier,
    onPath: (String) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    var rows by remember(fs, path) { mutableStateOf<List<FsItem>>(emptyList()) }
    var problem by remember(fs, path) { mutableStateOf<String?>(null) }
    var spin by remember { mutableStateOf(0) }
    val seen = rememberNxSeen(fs, path)

    LaunchedEffect(fs, path, reload, spin) {
        problem = null
        try {
            rows = withContext(Dispatchers.IO) { fs.list(path) }
        } catch (e: Throwable) {
            rows = emptyList()
            problem = e.message ?: e.toString()
        }
    }

    LaunchedEffect(fs, path) {
        while (true) {
            delay(2000)
            try {
                val fresh = withContext(Dispatchers.IO) { fs.list(path) }
                if (watchSign(fresh) != watchSign(rows)) rows = fresh
            } catch (_: Throwable) {
            }
        }
    }

    Column(modifier.nxGlass(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            NxIconBox(icon, box = 32.dp)
            Text(title, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                path, Modifier.weight(1f).padding(horizontal = 8.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp,
                color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            extra()
            NxIconButton(NxIcon.Up, tr("Up"), NxDim) { onPath(leave(path)) }
            NxIconButton(NxIcon.Refresh, tr("Refresh"), NxDim) { spin++ }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (problem != null) Text(problem.orEmpty(), Modifier.align(Alignment.Center).padding(16.dp), color = NxDanger, fontSize = 12.sp)
            else if (rows.isEmpty()) Text(tr("Empty"), Modifier.align(Alignment.Center), color = NxDim, fontSize = 13.sp)
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows, key = { _, r -> r.name }) { index, item ->
                    val on = item.name in picked
                    Row(
                        Modifier.fillMaxWidth().nxRow(seen, item.name, index)
                            .nxTap(0.dp) {
                                if (item.dir) onPath(joinPath(path, item.name))
                                else if (on) picked.remove(item.name) else picked.add(item.name)
                            }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NxIconBox(if (item.dir) NxIcon.Folder else NxIcon.File, box = 36.dp, tint = if (item.dir) NxAccent else NxDim)
                        Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                            Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!item.dir) Text(humanSize(item.size), fontFamily = FontPrefs.mono.family, fontSize = 10.sp, color = NxDim)
                        }
                        Box(Modifier.size(40.dp).nxTap(0.dp) { if (on) picked.remove(item.name) else picked.add(item.name) }, contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(20.dp).clip(CircleShape)
                                    .background(if (on) NxAccent else NxRaised)
                                    .border(1.dp, if (on) NxAccent else NxLine, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(NxIcon.Check, null, Modifier.size(14.dp), tint = NxOnAccent) }
                        }
                    }
                }
            }
        }
    }
}

private fun startTransfer(
    src: FsBackend, from: String, dst: FsBackend, to: String, names: List<String>, patterns: List<String>, move: Boolean, label: String,
) {
    val preview = names.take(2).joinToString(", ") + if (names.size > 2) " +${names.size - 2}" else ""
    val title = if (move) tr("Move {0} to {1}", preview, label) else tr("Copy {0} to {1}", preview, label)
    TaskBoard.start(if (move) TaskKind.Move else TaskKind.Copy, title) { run ->
        run.detail = tr("Scanning")
        var files = 0
        var bytes = 0L
        for (n in names) {
            val r = TreeScan.scan(src, joinPath(from, n), patterns)
            files += r.files
            bytes += r.bytes
            run.skipped += r.skipped
        }
        run.total = files
        run.totalBytes = bytes
        dst.mkdirs(to)
        for (n in names) copyTracked(src, joinPath(from, n), dst, joinPath(to, n), patterns, run, move)
    }
}

@Composable
fun TransferPane(vps: Vps) {
    val ctx = LocalContext.current
    val remote = remember(vps) { VpsFs(vps) }
    var useNoxora by remember { mutableStateOf(false) }
    val phone: FsBackend = if (useNoxora) NoxoraFs else InternalFs
    val phoneRoot = if (useNoxora) NoxoraFs.home(ctx) else Environment.getExternalStorageDirectory().absolutePath
    var phonePath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var vpsPath by remember(vps) { mutableStateOf(vps.cwd) }
    val phonePicked = remember { mutableStateListOf<String>() }
    val vpsPicked = remember { mutableStateListOf<String>() }
    var skip by remember { mutableStateOf(Cfg.excludes(ctx)) }
    var showSkip by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }

    LaunchedEffect(vps) {
        val home = withContext(Dispatchers.IO) { try { vps.home() } catch (_: Throwable) { "/" } }
        if (vpsPath == "/root" || vpsPath.isEmpty()) vpsPath = home
    }

    fun go(fromPhone: Boolean) {
        val names = (if (fromPhone) phonePicked else vpsPicked).toList()
        if (names.isEmpty()) {
            toast(ctx, tr("Select files on the {0} side first", if (fromPhone) tr("Phone") else tr("VPS")))
            return
        }
        Cfg.setExcludes(ctx, skip)
        val patterns = TreeScan.parse(skip)
        if (fromPhone) startTransfer(phone, phonePath, remote, vpsPath, names, patterns, move, vps.profile.name)
        else startTransfer(remote, vpsPath, phone, phonePath, names, patterns, move, tr("Phone"))
        (if (fromPhone) phonePicked else vpsPicked).clear()
        toast(ctx, tr("Running in background, see Tasks"))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskStrip()
        SidePane(
            tr("Phone"), NxIcon.Phone, phone, phonePath, phonePicked, TaskBoard.finished, Modifier.weight(1f).fillMaxWidth(),
            onPath = { phonePath = it; phonePicked.clear() },
        ) {
            NxButton(if (useNoxora) "Noxora" else tr("Storage"), compact = true) {
                useNoxora = !useNoxora
                phonePath = if (useNoxora) NoxoraFs.home(ctx) else Environment.getExternalStorageDirectory().absolutePath
                phonePicked.clear()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NxButton(tr("To VPS"), icon = NxIcon.Down, tone = Tone.Accent, compact = true, modifier = Modifier.weight(1f)) { go(true) }
            NxButton(tr("To phone"), icon = NxIcon.Up, tone = Tone.Accent, compact = true, modifier = Modifier.weight(1f)) { go(false) }
            NxButton(if (move) tr("Move") else tr("Copy"), icon = if (move) NxIcon.Cut else NxIcon.Copy, compact = true) { move = !move }
            NxButton(tr("Skip"), icon = NxIcon.Shield, compact = true) { showSkip = !showSkip }
        }
        if (showSkip) NxField(skip, { skip = it }, tr("Skip these names"), mono = true)
        SidePane(
            vps.profile.name, NxIcon.Server, remote, vpsPath, vpsPicked, TaskBoard.finished, Modifier.weight(1f).fillMaxWidth(),
            onPath = { vpsPath = it; vpsPicked.clear(); vps.cwd = it },
        )
    }
}
