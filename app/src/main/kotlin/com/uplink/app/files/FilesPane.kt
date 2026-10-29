package com.uplink.app

import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.itemsIndexed
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Ask(val title: String, val initial: String, val label: String, val lines: Int, val onOk: (String) -> Unit)

private fun displayName(ctx: Context, uri: Uri): String {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) return it.getString(0)
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

private fun sizeLabel(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / (1024.0 * 1024.0))
}

@Composable
fun FilesPane(vps: Vps) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val home = "/root"
    val path = vps.cwd
    var entries by remember { mutableStateOf<List<RemoteItem>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var exclusions by remember { mutableStateOf(Cfg.excludes(ctx)) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var pendingSave by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    var agentOpen by remember { mutableStateOf(false) }
    val agent = remember(vps) { vpsAgent(vps, ctx.applicationContext) }

    fun run(label: String, job: () -> Unit) {
        scope.launch {
            busy = label
            try {
                withContext(Dispatchers.IO) { job() }
            } catch (e: Throwable) {
                toast(ctx, e.message ?: e.toString())
            }
            busy = null
        }
    }

    fun refresh() = run(tr("Loading")) { entries = vps.list(path) }
    fun act(label: String, job: () -> Unit) = run(label) { job(); entries = vps.list(path) }
    fun open(next: String) { vps.cwd = next }
    fun goUp() {
        val cut = path.lastIndexOf('/')
        open(if (cut <= 0) "/" else path.substring(0, cut))
    }

    LaunchedEffect(path) { refresh() }

    val finishedTick = TaskBoard.finished
    LaunchedEffect(finishedTick) {
        if (finishedTick != 0) refresh()
    }

    LaunchedEffect(path) {
        while (true) {
            delay(2500)
            if (busy != null) continue
            try {
                val fresh = withContext(Dispatchers.IO) { vps.list(path) }
                if (watchSign(fresh) != watchSign(entries)) entries = fresh
            } catch (_: Throwable) {
            }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val target = path
            TaskBoard.start(TaskKind.Upload, if (uris.size == 1) tr("Upload 1 file") else tr("Upload {0} files", uris.size)) { run ->
                run.total = uris.size
                for (u in uris) {
                    if (run.stop) throw TaskStopped()
                    val name = displayName(ctx, u)
                    run.detail = name
                    ctx.contentResolver.openInputStream(u)?.use { vps.upload(TrackedIn(it, run), joinPath(target, name)) }
                    run.done++
                    TaskBoard.publish()
                }
            }
        }
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val tree = uri?.let { DocumentFile.fromTreeUri(ctx, it) }
        if (tree != null) {
            val skip = TreeScan.parse(exclusions)
            val target = joinPath(path, tree.name ?: "folder")
            TaskBoard.start(TaskKind.Upload, tr("Upload {0}", tree.name ?: "folder")) { run ->
                vps.uploadTree(ctx, tree, target, skip, run)
            }
        }
    }
    val saveDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) pendingSave?.invoke(uri)
    }

    fun onAction(item: RemoteItem, action: String) {
        val full = joinPath(path, item.name)
        when (action) {
            "open" -> FileOpen.start(ctx, VpsFs(vps), full, item.size)
            "text" -> FileOpen.start(ctx, VpsFs(vps), full, item.size, asText = true)
            "phone" -> {
                val remote = VpsFs(vps)
                val label = vps.profile.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                TaskBoard.start(TaskKind.Download, tr("Save {0} to phone", item.name)) { run ->
                    val dest = AppFolder.unique(AppFolder.dir("vps/$label"), item.name)
                    val scan = TreeScan.scan(remote, full, emptyList())
                    run.total = scan.files
                    run.totalBytes = scan.bytes
                    copyTracked(remote, full, InternalFs, dest.path, emptyList(), run, false)
                    run.detail = dest.path
                }
            }
            "rename" -> ask = Ask(tr("Rename"), item.name, tr("Name"), 1) { next ->
                if (next.isNotEmpty() && next != item.name) act(tr("Renaming")) { vps.sh("mv -- ${sq(full)} ${sq(joinPath(path, next))}") }
            }
            "delete" -> confirm = tr("Delete {0}?", item.name) to {
                act(tr("Deleting")) { vps.sh("rm -rf -- ${sq(full)}") }
            }
            "download" -> {
                pendingSave = { uri ->
                    val parent = path
                    TaskBoard.start(TaskKind.Download, tr("Download {0}", item.name)) { run ->
                        run.detail = item.name
                        if (!item.dir) run.totalBytes = item.size
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            val tracked = TrackedOut(out, run)
                            if (item.dir) vps.zipTo(parent, item.name, true, tracked) else vps.download(full, tracked)
                        }
                        toast(ctx, tr("Downloaded {0}", item.name))
                    }
                }
                saveDoc.launch(if (item.dir) "${item.name}.zip" else item.name)
            }
            "zip" -> {
                val parent = path
                TaskBoard.start(TaskKind.Zip, tr("Zip {0}", item.name), false) { run ->
                    run.detail = item.name
                    vps.zipHere(parent, item.name, item.dir)
                }
            }
            "unzip" -> {
                val parent = path
                TaskBoard.start(TaskKind.Unzip, tr("Unzip {0}", item.name), false) { run ->
                    run.detail = item.name
                    vps.unzipHere(parent, item.name)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NxTile(tr("AI"), NxIcon.Bot, Modifier.weight(1f)) { agentOpen = true }
                NxTile(tr("Upload"), NxIcon.Upload, Modifier.weight(1f)) { pickFiles.launch(arrayOf("*/*")) }
                NxTile(tr("Upload folder"), NxIcon.FolderUp, Modifier.weight(1f)) {
                    ask = Ask(tr("Skip these names"), exclusions, tr("Names or patterns, comma separated"), 3) { text ->
                        exclusions = text
                        Cfg.setExcludes(ctx, text)
                        pickTree.launch(null)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NxIconButton(NxIcon.Home, tr("Home")) { open(home) }
            NxIconButton(NxIcon.Refresh, tr("Refresh")) { refresh() }
            NxIconButton(NxIcon.FolderPlus, tr("New folder")) {
                ask = Ask(tr("New folder"), "", tr("Folder name"), 1) { n ->
                    if (n.isNotEmpty()) act(tr("Creating folder")) { vps.sh("mkdir -p ${sq(joinPath(path, n))}") }
                }
            }
            NxIconButton(NxIcon.FilePlus, tr("New file")) {
                ask = Ask(tr("New file"), "", tr("File name"), 1) { n ->
                    if (n.isNotEmpty()) act(tr("Creating file")) { vps.sh("touch ${sq(joinPath(path, n))}") }
                }
            }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(NxSurface)
                .border(1.dp, NxLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NxIcon.Folder, null, Modifier.size(16.dp), tint = if (busy == null) NxDim else NxAccent)
            Text(
                if (busy == null) path else "$busy...",
                Modifier.weight(1f).padding(start = 8.dp),
                fontFamily = FontPrefs.mono.family, fontSize = 12.sp,
                color = if (busy == null) NxDim else NxAccent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            TaskTicker(Modifier.widthIn(max = 150.dp))
        }
        TaskStrip()
        val atHome = path == home
        val seen = rememberNxSeen(path)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty() && atHome && busy == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(NxIcon.Folder, null, Modifier.size(36.dp).nxFloat(5.dp, 2200), tint = NxDim)
                    Text(tr("Empty folder"), Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                    Text(tr("Create a file or upload one."), color = NxDim, fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!atHome) item {
                        Row(
                            Modifier.fillMaxWidth().nxTap(0.dp) { goUp() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(NxIcon.Up, null, Modifier.size(20.dp), tint = NxDim)
                            Text("..", Modifier.padding(start = 14.dp), fontFamily = FontPrefs.mono.family, color = NxDim)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
                    }
                    itemsIndexed(entries) { index, item ->
                        var menu by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth()
                                .nxRow(seen, item.name, index)
                                .nxTap(0.dp) { if (item.dir) open(joinPath(path, item.name)) else onAction(item, "open") }
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NxIconBox(if (item.dir) NxIcon.Folder else NxIcon.File, tint = if (item.dir) NxAccent else NxDim)
                            Column(Modifier.weight(1f).padding(start = 14.dp, top = 9.dp, bottom = 9.dp)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (item.dir) tr("Folder") else tr("File · {0}", sizeLabel(item.size)),
                                    Modifier.padding(top = 2.dp), fontSize = 12.sp, color = NxDim, maxLines = 1,
                                )
                            }
                            Box {
                                NxIconButton(NxIcon.More, tr("Menu"), NxDim) { menu = true }
                                DropdownMenu(
                                    expanded = menu,
                                    onDismissRequest = { menu = false },
                                    modifier = Modifier.background(NxRaised).border(1.dp, NxLine, RoundedCornerShape(12.dp)),
                                ) {
                                    val options = if (item.dir) {
                                        listOf(
                                            Triple("rename", tr("Rename"), NxIcon.Rename),
                                            Triple("phone", tr("Save to phone"), NxIcon.Phone),
                                            Triple("download", tr("Download as zip"), NxIcon.Download),
                                            Triple("zip", tr("Zip"), NxIcon.Archive),
                                            Triple("delete", tr("Delete"), NxIcon.Trash),
                                        )
                                    } else buildList {
                                        add(Triple("open", tr("Open"), NxIcon.File))
                                        add(Triple("text", tr("Read as text"), NxIcon.Edit))
                                        add(Triple("rename", tr("Rename"), NxIcon.Rename))
                                        add(Triple("phone", tr("Save to phone"), NxIcon.Phone))
                                        add(Triple("download", tr("Download"), NxIcon.Download))
                                        add(Triple("zip", tr("Zip"), NxIcon.Archive))
                                        if (item.name.lowercase().endsWith(".zip")) add(Triple("unzip", tr("Unzip"), NxIcon.Unarchive))
                                        add(Triple("delete", tr("Delete"), NxIcon.Trash))
                                    }
                                    for ((key, text, glyph) in options) {
                                        val ink = if (key == "delete") NxDanger else NxText
                                        DropdownMenuItem(
                                            text = { Text(text, color = ink) },
                                            leadingIcon = { Icon(glyph, null, Modifier.size(18.dp), tint = ink) },
                                            onClick = { menu = false; onAction(item, key) },
                                        )
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 74.dp).height(1.dp).background(NxLine))
                    }
                }
            }
        }
    }

    if (agentOpen) AgentSheet(agent, StagePlan.ssh, path, null) { agentOpen = false }
    ask?.let { a ->
        NxAsk(a.title, a.initial, a.label, a.lines, onDismiss = { ask = null }) { text -> ask = null; a.onOk(text) }
    }
    confirm?.let { (msg, yes) ->
        NxConfirm(msg, tr("Delete"), onDismiss = { confirm = null }) { confirm = null; yes() }
    }
}
