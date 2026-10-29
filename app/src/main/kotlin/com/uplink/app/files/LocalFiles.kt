package com.uplink.app

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FsClipData(val fs: FsBackend, val dir: String, val names: List<String>, val cut: Boolean)

object FsClip {
    var current by mutableStateOf<FsClipData?>(null)
}

object Access {
    fun base(): String = Environment.getExternalStorageDirectory().absolutePath

    fun granted(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun request(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        } else {
            (ctx as? Activity)?.requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 2,
            )
        }
    }
}

object LocalState {
    var tab by mutableIntStateOf(0)
    var internalPath by mutableStateOf(Access.base())
    var rootPath by mutableStateOf("/")
    var noxoraPath by mutableStateOf("")
}

fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024.0))
    else -> "%.2f GB".format(b / (1024.0 * 1024.0 * 1024.0))
}

private class FsPrompt(val title: String, val initial: String, val label: String, val onOk: (String) -> Unit)

@Composable
private fun SegTab(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val ink = if (active) NxAccent else NxDim
    Column(modifier.nxTap(0.dp, onTap), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = ink)
            Text(label, Modifier.weight(1f, fill = false).padding(start = 6.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) NxAccent else NxLine))
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, modifier: Modifier, tint: androidx.compose.ui.graphics.Color = NxText, onTap: () -> Unit) {
    Column(modifier.nxTap(12.dp, onTap).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, Modifier.size(20.dp), tint = tint)
        Text(label, Modifier.padding(top = 2.dp), fontSize = 10.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Gate(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(NxRaised).border(1.dp, NxLine, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(30.dp), tint = NxAccent) }
            Text(title, Modifier.padding(top = 18.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(body, Modifier.padding(top = 6.dp, bottom = 20.dp), color = NxDim, fontSize = 13.sp)
            NxButton(action, tone = Tone.Accent, onTap = onAction)
        }
    }
}

private fun shortPath(path: String): String {
    val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "/"
        parts.size <= 2 -> "/" + parts.joinToString("/")
        else -> ".../" + parts.takeLast(2).joinToString("/")
    }
}

private fun crumbsOf(base: String, path: String): List<Pair<String, String>> {
    val root = if (path.startsWith(base)) base.trimEnd('/') else ""
    val out = ArrayList<Pair<String, String>>()
    out.add("" to (if (root.isEmpty()) "/" else root))
    var acc = root
    for (seg in path.removePrefix(root).split('/')) {
        if (seg.isEmpty()) continue
        acc = "$acc/$seg"
        out.add(seg to acc)
    }
    return out
}

private val imageExt = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "avif")
private val audioExt = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")
private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")
private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2", "tgz")
private val codeExt = setOf(
    "kt", "java", "js", "ts", "py", "c", "cpp", "h", "sh", "json", "xml", "html", "css", "md", "txt",
    "yml", "yaml", "toml", "sql", "log", "gradle", "kts", "go", "rs", "php",
)

private fun lookOf(item: FsItem): Pair<ImageVector, Color> {
    if (item.dir) return NxIcon.Folder to NxYellow
    return when (item.name.substringAfterLast('.', "").lowercase()) {
        in imageExt -> NxIcon.Image to NxMint
        in audioExt -> NxIcon.Music to NxOrange
        in videoExt -> NxIcon.Video to NxRed
        in archiveExt -> NxIcon.Archive to NxPrimaryVariant
        in codeExt -> NxIcon.File to NxBlue
        "apk" -> NxIcon.Phone to NxMint
        else -> NxIcon.File to NxDim
    }
}

private class HeadAction(val label: String, val icon: ImageVector, val run: () -> Unit)

@Composable
private fun HeadMenu(items: List<HeadAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        NxIconButton(NxIcon.More, tr("Menu")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(NxSurface)) {
            for (action in items) {
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, null, Modifier.size(18.dp), tint = NxText) },
                    onClick = { open = false; action.run() },
                )
            }
        }
    }
}

@Composable
private fun DialPill(label: String, icon: ImageVector, tint: Color, onTap: () -> Unit) {
    Row(
        Modifier.nxPress(onTap).nxGlass(24.dp).padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, Modifier.padding(start = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun FileTabs(tab: Int) {
    NxSegment(
        listOf("Internal" to null, "Root" to null, "NoxoraStorage" to null), tab, Modifier.fillMaxWidth(), height = 36.dp,
    ) { LocalState.tab = it }
}

@Composable
private fun FilesFrame(tabs: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        NxBar(tr("Files"), nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.route = Route.Home } })
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) { tabs() }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
fun FilesScreen() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Access.granted(ctx)) }
    var tick by remember { mutableIntStateOf(0) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var rootTry by remember { mutableIntStateOf(0) }
    val tab = LocalState.tab

    LaunchedEffect(Unit) {
        while (true) {
            val g = Access.granted(ctx)
            if (g != granted) {
                granted = g
                tick++
            }
            delay(800)
        }
    }

    LaunchedEffect(Unit) {
        if (LocalState.noxoraPath.isEmpty()) LocalState.noxoraPath = NoxoraFs.home(ctx)
    }

    LaunchedEffect(tab, rootTry) {
        if (tab == 1 && rootOk != true) {
            rootOk = null
            rootOk = withContext(Dispatchers.IO) { RootFs.available(true) }
        }
    }

    val tabs: @Composable () -> Unit = { FileTabs(tab) }
    val zip = ZipOpen.fs
    if (zip != null && ZipOpen.tab == tab) {
        FsExplorer(
            fs = zip, base = "/", path = ZipOpen.path, reload = tick, onPath = { ZipOpen.path = it }, tabs = tabs,
            banner = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NxIcon.Archive, null, Modifier.size(18.dp), tint = NxAccent)
                    Text(zip.label, Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NxButton(tr("Close zip"), icon = NxIcon.Close, compact = true) { ZipOpen.leave() }
                }
            },
        )
    } else if (tab == 2) {
        if (LocalState.noxoraPath.isNotEmpty()) {
            FsExplorer(
                fs = NoxoraFs, base = NoxoraFs.base(), path = LocalState.noxoraPath, reload = tick,
                onPath = { LocalState.noxoraPath = it }, tabs = tabs,
            )
        } else {
            FilesFrame(tabs) { }
        }
    } else if (tab == 0) {
        if (!granted) {
            FilesFrame(tabs) {
                Gate(
                    NxIcon.Folder, tr("Storage access needed"),
                    tr("Allow file access so UntilyFlow can browse and edit internal storage."),
                    tr("Grant access"),
                ) { Access.request(ctx) }
            }
        } else {
            FsExplorer(
                fs = InternalFs, base = Access.base(), path = LocalState.internalPath, reload = tick,
                onPath = { LocalState.internalPath = it }, tabs = tabs,
            )
        }
    } else {
        when (rootOk) {
            null -> FilesFrame(tabs) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("Checking root access..."), color = NxDim)
                }
            }
            false -> FilesFrame(tabs) {
                Gate(
                    NxIcon.Shield, tr("Root not available"),
                    tr("This device did not grant root. Allow UntilyFlow in your root manager, then try again."),
                    tr("Try again"),
                ) { rootTry++ }
            }
            true -> FsExplorer(
                fs = RootFs, base = "/", path = LocalState.rootPath, reload = rootTry,
                onPath = { LocalState.rootPath = it }, tabs = tabs,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FsExplorer(
    fs: FsBackend,
    base: String,
    path: String,
    reload: Int = 0,
    onPath: (String) -> Unit,
    tabs: @Composable () -> Unit = {},
    banner: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember(fs) { mutableStateOf<List<FsItem>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var selecting by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<FsPrompt?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var sendNames by remember { mutableStateOf<List<String>?>(null) }
    var agentOpen by remember { mutableStateOf(false) }
    var gitOpen by remember { mutableStateOf(false) }
    var localOpen by remember { mutableStateOf(false) }
    var zipAsk by remember { mutableStateOf<PasswordAsk?>(null) }
    var zipJob by remember { mutableStateOf<ZipMaker?>(null) }
    val phoneAi = remember(fs, base) { PhoneAi.of(fs, ctx, base) }
    val clip = FsClip.current
    val seen = rememberNxSeen(path)
    var query by remember(path) { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var dial by remember { mutableStateOf(false) }
    var kind by remember(path) { mutableIntStateOf(0) }
    val needle = query.trim()
    val shown = entries.filter { (kind == 0 || (kind == 1 && it.dir) || (kind == 2 && !it.dir)) && it.name.contains(needle, ignoreCase = true) }

    fun load() {
        scope.launch {
            busy = tr("Loading")
            problem = null
            try {
                entries = withContext(Dispatchers.IO) { fs.list(path) }
            } catch (e: Throwable) {
                entries = emptyList()
                problem = e.message ?: e.toString()
            }
            busy = null
        }
    }

    fun act(label: String, job: () -> Unit) {
        scope.launch {
            busy = label
            try {
                withContext(Dispatchers.IO) { job() }
            } catch (e: Throwable) {
                toast(ctx, e.message ?: e.toString())
            }
            try {
                entries = withContext(Dispatchers.IO) { fs.list(path) }
                problem = null
            } catch (e: Throwable) {
                problem = e.message ?: e.toString()
            }
            busy = null
        }
    }

    LaunchedEffect(path, fs, reload) {
        picked.clear()
        selecting = false
        load()
    }

    val finishedTick = TaskBoard.finished
    LaunchedEffect(finishedTick) {
        if (finishedTick != 0) load()
    }

    LaunchedEffect(path, fs) {
        if (fs === RootFs) return@LaunchedEffect
        while (true) {
            delay(1200)
            if (busy != null) continue
            try {
                val fresh = withContext(Dispatchers.IO) { fs.list(path) }
                if (watchSign(fresh) != watchSign(entries)) entries = fresh
            } catch (_: Throwable) {
            }
        }
    }

    fun goUp() { onPath(parentOf(path)) }

    fun toggle(n: String) { if (n in picked) picked.remove(n) else picked.add(n) }

    fun uniqueIn(dir: String, name: String): String {
        var candidate = joinPath(dir, name)
        if (fs.kind(candidate) == 0) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            candidate = joinPath(dir, if (i == 1) "$stem copy$ext" else "$stem copy $i$ext")
            if (fs.kind(candidate) == 0) return candidate
            i++
        }
    }

    fun paste() {
        val c = clip ?: return
        if (c.cut) FsClip.current = null
        startPaste(c, fs, path) { n -> uniqueIn(path, n) }
        toast(ctx, tr("Running in background, see Tasks"))
    }

    fun deleteNames(names: List<String>) {
        startDelete(fs, path, names)
        picked.clear()
        selecting = false
    }

    fun browseZip(item: FsItem, full: String) {
        openZipFile(ctx, scope, fs, full, item.size, { zipAsk = it }) { z -> ZipOpen.enter(z, LocalState.tab) }
    }

    fun onAction(item: FsItem, action: String) {
        val full = joinPath(path, item.name)
        when (action) {
            "open" -> if (isZipName(item.name)) browseZip(item, full) else FileOpen.start(ctx, fs, full, item.size)
            "browse" -> browseZip(item, full)
            "text" -> FileOpen.start(ctx, fs, full, item.size, asText = true)
            "rename" -> ask = FsPrompt(tr("Rename"), item.name, tr("Name")) { next ->
                if (next.isNotEmpty() && next != item.name) act(tr("Renaming")) { fs.rename(full, joinPath(path, next)) }
            }
            "copy" -> { FsClip.current = FsClipData(fs, path, listOf(item.name), false); toast(ctx, tr("Copied")) }
            "cut" -> { FsClip.current = FsClipData(fs, path, listOf(item.name), true); toast(ctx, tr("Cut")) }
            "send" -> sendNames = listOf(item.name)
            "zip" -> if (isLocal(fs)) zipJob = ZipMaker(path, listOf(item.name)) else TaskBoard.start(TaskKind.Zip, tr("Zip {0}", item.name), false) { run ->
                run.detail = item.name
                zipItem(fs, path, item.name)
            }
            "unzip" -> TaskBoard.start(TaskKind.Unzip, tr("Unzip {0}", item.name), false) { run ->
                run.detail = item.name
                unzipItem(fs, path, item.name)
            }
            "delete" -> confirm = tr("Delete {0}?", item.name) to { deleteNames(listOf(item.name)) }
        }
    }

    val marking = selecting || picked.isNotEmpty()

    fun newFolder() {
        ask = FsPrompt(tr("New folder"), "", tr("Folder name")) { n ->
            if (n.isNotEmpty()) act(tr("Creating folder")) { fs.mkdirs(joinPath(path, n)) }
        }
    }

    fun newFile() {
        ask = FsPrompt(tr("New file"), "", tr("File name")) { n ->
            if (n.isNotEmpty()) act(tr("Creating file")) { fs.touch(joinPath(path, n)) }
        }
    }

    fun goTo() {
        ask = FsPrompt(tr("Go to path"), path, tr("Absolute path")) { text ->
            val target = fixPath(text, base)
            scope.launch {
                val k = try { withContext(Dispatchers.IO) { fs.kind(target) } } catch (_: Throwable) { 0 }
                if (k == 2) onPath(target) else toast(ctx, tr("Not a folder"))
            }
        }
    }

    fun endSelect() {
        picked.clear()
        selecting = false
    }

    Column(Modifier.fillMaxSize()) {
        if (marking) {
            NxBar(
                tr("{0} selected", picked.size),
                nav = { NxIconButton(NxIcon.Close, tr("Done")) { endSelect() } },
                actions = {
                    if (picked.isNotEmpty()) {
                        NxIconButton(NxIcon.Copy, tr("Copy")) {
                            FsClip.current = FsClipData(fs, path, picked.toList(), false)
                            endSelect()
                            toast(ctx, tr("Copied"))
                        }
                        NxIconButton(NxIcon.Cut, tr("Cut")) {
                            FsClip.current = FsClipData(fs, path, picked.toList(), true)
                            endSelect()
                            toast(ctx, tr("Cut"))
                        }
                        NxIconButton(NxIcon.Trash, tr("Delete")) {
                            val names = picked.toList()
                            confirm = tr("Delete {0} items?", names.size) to { deleteNames(names) }
                        }
                    }
                    HeadMenu(
                        buildList<HeadAction> {
                            if (picked.size < entries.size) add(HeadAction(tr("Select all"), NxIcon.Check) { picked.clear(); picked.addAll(entries.map { it.name }) })
                            if (picked.isNotEmpty()) {
                                add(HeadAction(tr("Deselect all"), NxIcon.Close) { picked.clear() })
                                add(HeadAction(tr("Send to VPS"), NxIcon.Send) { sendNames = picked.toList() })
                                if (isLocal(fs)) add(HeadAction(tr("Zip"), NxIcon.Archive) { zipJob = ZipMaker(path, picked.toList()); endSelect() })
                            }
                        },
                    )
                },
            )
        } else {
            NxBar(
                tr("Files"),
                subtitle = busy?.let { "$it..." } ?: shortPath(path),
                nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.route = Route.Home } },
                actions = {
                    NxIconButton(NxIcon.Search, tr("Search")) {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }
                    HeadMenu(
                        listOf(
                            HeadAction(tr("Home"), NxIcon.Home) { onPath(base) },
                            HeadAction(tr("Refresh"), NxIcon.Refresh) { load() },
                            HeadAction(tr("Select"), NxIcon.Check) { selecting = true },
                            HeadAction(tr("Go to path"), NxIcon.Right) { goTo() },
                            HeadAction(tr("New folder"), NxIcon.FolderPlus) { newFolder() },
                            HeadAction(tr("New file"), NxIcon.FilePlus) { newFile() },
                        ),
                    )
                },
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (searchOpen && !marking) NxSearch(query, { query = it }, tr("Search files or folders..."), Modifier.padding(top = 4.dp))
            tabs()
            banner?.invoke()
            Row(
                Modifier.fillMaxWidth().height(32.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NxChip(tr("All"), hot = kind == 0) { kind = 0 }
                NxChip(tr("Folders"), hot = kind == 1) { kind = 1 }
                NxChip(tr("Files"), hot = kind == 2) { kind = 2 }
                Box(Modifier.width(1.dp).height(16.dp).background(NxLine))
                NxChip(tr("AI"), icon = NxIcon.Bot, tint = NxRed) { agentOpen = true }
                NxChip("GitHub", icon = NxIcon.Git, tint = NxPrimaryVariant) { gitOpen = true }
                if (isLocal(fs)) NxChip(tr("Localhost"), icon = NxIcon.Globe, tint = NxOrange) { localOpen = true }
                if (clip != null) NxChip(tr("Paste"), icon = NxIcon.Paste, tint = NxAccent) { paste() }
            }
            val crumbScroll = rememberScrollState()
            val crumbs = crumbsOf(base, path)
            LaunchedEffect(path) { crumbScroll.animateScrollTo(crumbScroll.maxValue) }
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                if (path != "/") {
                    Box(Modifier.size(36.dp).nxPress { goUp() }, contentAlignment = Alignment.Center) {
                        Icon(NxIcon.Up, tr("Go back"), Modifier.size(20.dp), tint = NxAccent)
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(crumbScroll), verticalAlignment = Alignment.CenterVertically) {
                    for ((i, crumb) in crumbs.withIndex()) {
                        val last = i == crumbs.lastIndex
                        if (i > 0) Icon(NxIcon.Right, null, Modifier.size(12.dp), tint = NxDim)
                        Row(
                            Modifier.heightIn(min = 36.dp).nxPress { onPath(crumb.second) }.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (i == 0) {
                                Icon(NxIcon.Home, tr("Home"), Modifier.size(16.dp), tint = if (last) NxText else NxDim)
                            } else {
                                Text(
                                    crumb.first, fontSize = 12.sp, fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (last) NxText else NxDim, maxLines = 1,
                                )
                            }
                        }
                    }
                }
                TaskTicker(Modifier.widthIn(max = 110.dp))
            }
        }
        TaskStrip()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val err = problem
            if (err != null) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(NxIcon.Folder, null, Modifier.size(36.dp), tint = NxDanger)
                    Text(tr("Cannot open folder"), Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
                    Text(err, color = NxDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                    if (path != "/") NxButton(tr("Go back"), icon = NxIcon.Up, compact = true) { goUp() }
                }
            } else if (entries.isEmpty() && busy == null && path == "/") {
                Text(tr("Empty"), Modifier.align(Alignment.Center), color = NxDim)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 96.dp)) {
                    if (entries.isEmpty() && busy == null) item {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(NxIcon.Folder, null, Modifier.size(36.dp).nxFloat(5.dp, 2200), tint = NxYellow)
                            Text(tr("Empty folder"), Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
                            Text(tr("Create a file or paste something."), color = NxDim, fontSize = 13.sp)
                        }
                    }
                    if (shown.isEmpty() && entries.isNotEmpty()) item {
                        Text(tr("No matches"), Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = NxDim)
                    }
                    itemsIndexed(shown, key = { _, row -> row.name }) { index, item ->
                        var menu by remember { mutableStateOf(false) }
                        val on = item.name in picked
                        val source = remember { MutableInteractionSource() }
                        val look = lookOf(item)
                        Row(
                            Modifier.fillMaxWidth().height(64.dp)
                                .nxRow(seen, item.name, index)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (on) NxAccent.copy(alpha = 0.10f) else Color.Transparent)
                                .combinedClickable(
                                    interactionSource = source, indication = null,
                                    onClick = {
                                        if (marking) toggle(item.name)
                                        else if (item.dir) onPath(joinPath(path, item.name))
                                        else onAction(item, "open")
                                    },
                                    onLongClick = { toggle(item.name) },
                                )
                                .padding(start = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (marking) {
                                Box(
                                    Modifier.padding(end = 10.dp).size(22.dp).clip(RoundedCornerShape(7.dp))
                                        .background(if (on) NxAccent else NxRaised)
                                        .border(1.dp, if (on) NxAccent else NxLine, RoundedCornerShape(7.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Icon(NxIcon.Check, null, Modifier.size(14.dp), tint = NxOnAccent)
                                }
                            }
                            NxIconBox(look.first, box = 40.dp, tint = look.second)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (item.dir) tr("Folder") else tr("File · {0}", humanSize(item.size)),
                                    Modifier.padding(top = 2.dp), fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box {
                                NxIconButton(NxIcon.More, tr("Menu"), NxDim) { menu = true }
                                DropdownMenu(
                                    expanded = menu,
                                    onDismissRequest = { menu = false },
                                    modifier = Modifier.background(NxSurface),
                                ) {
                                    val options = buildList {
                                        if (!item.dir) {
                                            if (isZipName(item.name)) add(Triple("browse", tr("Browse zip"), NxIcon.Archive))
                                            add(Triple("open", tr("Open"), NxIcon.File))
                                            add(Triple("text", tr("Read as text"), NxIcon.Edit))
                                        }
                                        add(Triple("rename", tr("Rename"), NxIcon.Rename))
                                        add(Triple("send", tr("Send to VPS"), NxIcon.Send))
                                        add(Triple("copy", tr("Copy"), NxIcon.Copy))
                                        add(Triple("cut", tr("Cut"), NxIcon.Cut))
                                        add(Triple("zip", tr("Zip"), NxIcon.Archive))
                                        if (!item.dir && item.name.lowercase().endsWith(".zip")) add(Triple("unzip", tr("Unzip"), NxIcon.Unarchive))
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
                    }
                }
            }
            if (!marking && err == null) {
                if (dial) {
                    Box(
                        Modifier.fillMaxSize().background(NxBg.copy(alpha = 0.7f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dial = false },
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnimatedVisibility(dial, enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 3 }, exit = fadeOut(tween(120))) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialPill(tr("New folder"), NxIcon.FolderPlus, NxYellow) { dial = false; newFolder() }
                            DialPill(tr("New file"), NxIcon.FilePlus, NxBlue) { dial = false; newFile() }
                        }
                    }
                    NxFab(NxIcon.Plus, tr("New"), turned = dial) { dial = !dial }
                }
            }
        }
    }

    zipAsk?.let { a -> PasswordDialog(a, onDismiss = { zipAsk = null }, onOpened = { z -> zipAsk = null; a.onReady(z) }) }
    zipJob?.let { job ->
        ZipDialog(job, onDismiss = { zipJob = null }) { name, secret, strong ->
            zipJob = null
            startZipBuild(ctx.applicationContext, fs, job.dir, job.names, name, secret, strong)
            toast(ctx, tr("Running in background, see Tasks"))
        }
    }
    if (agentOpen) AgentSheet(phoneAi.agent, StagePlan.phone, path, fs) { agentOpen = false }
    if (gitOpen) GitSheet(fs, path) { gitOpen = false }
    if (localOpen) LocalhostSheet(path) { localOpen = false }
    ask?.let { a ->
        NxAsk(a.title, a.initial, a.label, 1, onDismiss = { ask = null }) { text -> ask = null; a.onOk(text) }
    }
    confirm?.let { (msg, yes) ->
        NxConfirm(msg, tr("Delete"), onDismiss = { confirm = null }) { confirm = null; yes() }
    }
    sendNames?.let { names ->
        SendDialog(
            fs = fs, dir = path, names = names,
            onDismiss = { sendNames = null },
            onDone = { moved ->
                sendNames = null
                picked.clear()
                selecting = false
                if (moved) load()
            },
        )
    }
}
