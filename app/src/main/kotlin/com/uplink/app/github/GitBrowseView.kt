package com.uplink.app

import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class BrowseAsk(val title: String, val label: String, val onOk: (String) -> Unit)

private var initialCopy = ""

private fun below(where: String, name: String): String = if (where.isEmpty()) name else "$where/$name"

@Composable
fun BrowseView(s: GitSession) {
    val at = s.link ?: return
    var where by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<EntryRow>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<BrowseAsk?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val seen = rememberNxSeen(where, at.branch)

    fun reload() = s.load(tr("Loading"), { GitHubApi.entries(at.repo, where, at.branch) }) { rows = it }

    fun edit(item: EntryRow) = s.load(tr("Loading"), { GitHubApi.readText(at.repo, item.path, at.branch) }) {
        s.editPath = item.path
        s.editSha = it.sha
        s.editText = it.text
        s.view = GitView.Edit
    }

    LaunchedEffect(where, at.repo, at.branch, s.version) { reload() }
    LaunchedEffect(where, at.repo, at.branch) {
        while (true) {
            delay(8000)
            if (s.busy == null) s.quiet({ GitHubApi.entries(at.repo, where, at.branch) }) { rows = it }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (where.isNotEmpty()) NxButton(tr("Up"), icon = NxIcon.Up, compact = true) { where = where.substringBeforeLast('/', "") }
            NxButton(tr("Refresh"), icon = NxIcon.Refresh, compact = true) { reload() }
            NxButton(at.branch, icon = NxIcon.Git, tone = Tone.Accent, compact = true) { picking = true }
            NxButton(tr("New file"), icon = NxIcon.FilePlus, compact = true) {
                ask = BrowseAsk(tr("New file"), tr("File name")) { n ->
                    if (n.isNotEmpty()) {
                        s.editPath = below(where, n)
                        s.editSha = null
                        s.editText = ""
                        s.view = GitView.Edit
                    }
                }
            }
            NxButton(tr("New folder"), icon = NxIcon.FolderPlus, compact = true) {
                ask = BrowseAsk(tr("New folder"), tr("Folder name")) { n ->
                    if (n.isNotEmpty()) {
                        s.load(
                            tr("Creating folder"),
                            { GitHubApi.saveText(at.repo, below(below(where, n), ".gitkeep"), at.branch, "\n", tr("Create folder {0}", n), null) },
                        ) { reload() }
                    }
                }
            }
            NxButton(tr("Download all"), icon = NxIcon.Download, compact = true) { s.downloadTree("") }
            NxButton(tr("ZIP all"), icon = NxIcon.Archive, compact = true) { s.downloadZip("") }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).nxGlass(14.dp).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NxIcon.Folder, null, Modifier.size(16.dp), tint = NxAccent)
            Text(
                at.repo + "/" + where, Modifier.padding(start = 8.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp,
                color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty() && s.busy == null) {
                Text(tr("Empty"), Modifier.align(Alignment.Center), color = NxDim)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows, key = { _, r -> r.path }) { index, item ->
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().nxRow(seen, item.path, index)
                            .nxTap(0.dp) { if (item.dir) where = item.path else edit(item) }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NxIconBox(if (item.dir) NxIcon.Folder else NxIcon.File, box = 36.dp, tint = if (item.dir) NxAccent else NxDim)
                        Column(Modifier.weight(1f).padding(start = 14.dp, top = 9.dp, bottom = 9.dp)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!item.dir) Text(humanSize(item.size), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                        }
                        Box {
                            NxIconButton(NxIcon.More, tr("Menu"), NxDim) { menu = true }
                            DropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false },
                                modifier = Modifier.background(NxRaised).border(1.dp, NxLine, RoundedCornerShape(12.dp)),
                            ) {
                                val options = buildList {
                                    if (!item.dir) add(Triple("edit", tr("Edit"), NxIcon.Edit))
                                    add(Triple("rename", tr("Rename"), NxIcon.Rename))
                                    add(Triple("copy", tr("Copy"), NxIcon.Copy))
                                    if (!item.dir) add(Triple("clear", tr("Clear"), NxIcon.Undo))
                                    add(Triple("download", tr("Download"), NxIcon.Download))
                                    add(Triple("zip", tr("ZIP"), NxIcon.Archive))
                                    add(Triple("delete", tr("Delete"), NxIcon.Trash))
                                }
                                for ((key, text, glyph) in options) {
                                    val ink = if (key == "delete") NxDanger else NxText
                                    DropdownMenuItem(
                                        text = { Text(text, color = ink) },
                                        leadingIcon = { Icon(glyph, null, Modifier.size(18.dp), tint = ink) },
                                        onClick = {
                                            menu = false
                                            when (key) {
                                                "edit" -> edit(item)
                                                "rename" -> ask = BrowseAsk(tr("Rename"), tr("New name")) { n ->
                                                    if (n.isNotEmpty() && n != item.name) s.relocate(item.path, below(where, n), false)
                                                }
                                                "copy" -> {
                                                    val dot = item.name.lastIndexOf('.')
                                                    val fresh = if (dot > 0) item.name.substring(0, dot) + " copy" + item.name.substring(dot) else item.name + " copy"
                                                    ask = BrowseAsk(tr("Copy"), tr("Path of the copy")) { n ->
                                                        if (n.isNotEmpty()) s.relocate(item.path, n.trim('/'), true)
                                                    }.also { initialCopy = below(where, fresh) }
                                                }
                                                "clear" -> confirm = tr("Clear {0}?", item.name) to { s.clear(item.path) }
                                                "zip" -> s.downloadZip(item.path)
                                                "download" -> if (item.dir) s.downloadTree(item.path) else s.downloadFile(item.path, item.size)
                                                else -> confirm = tr("Delete {0}?", item.name) to {
                                                    s.load(
                                                        tr("Deleting"),
                                                        { GitHubApi.removePaths(at.repo, at.branch, listOf(item.path), tr("Delete {0}", item.name)) },
                                                    ) { reload() }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(NxLine))
                }
            }
        }
    }

    if (picking) {
        BranchDialog(s, at.repo, at.branch, false, onPick = { picked ->
            if (picked != null) s.bind(RepoLink(at.repo, picked, at.subdir))
            where = ""
            picking = false
        }, onDismiss = { picking = false })
    }
    ask?.let { a ->
        NxAsk(a.title, if (a.title == tr("Copy")) initialCopy else "", a.label, 1, onDismiss = { ask = null }) { text -> ask = null; a.onOk(text) }
    }
    confirm?.let { (msg, yes) ->
        NxConfirm(msg, tr("Yes"), onDismiss = { confirm = null }) { confirm = null; yes() }
    }
}

@Composable
fun EditView(s: GitSession) {
    val at = s.link ?: return
    val name = s.editPath.substringAfterLast('/')
    var message by remember { mutableStateOf(if (s.editSha == null) tr("Create {0}", name) else tr("Update {0}", name)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            at.repo + "@" + at.branch + ":" + s.editPath, Modifier.padding(bottom = 12.dp),
            fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim,
        )
        NxField(s.editText, { s.editText = it }, tr("Content"), mono = true, lines = 16)
        NxField(message, { message = it }, tr("Commit message"))
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("Copy"), icon = NxIcon.Copy, compact = true) { copyOutput(s.ctx, s.editText) }
            NxButton(tr("Clear"), icon = NxIcon.Undo, compact = true) { s.editText = "" }
        }
        NxButton(tr("Save to GitHub"), icon = NxIcon.Save, tone = Tone.Accent, wide = true) {
            s.load(
                tr("Saving"),
                { GitHubApi.saveText(at.repo, s.editPath, at.branch, s.editText, message.ifBlank { s.autoMessage() }, s.editSha) },
            ) {
                toast(s.ctx, tr("Saved"))
                s.view = GitView.Browse
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun HistoryView(s: GitSession) {
    val at = s.link ?: return
    var rows by remember { mutableStateOf<List<CommitRow>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    val seen = rememberNxSeen(at.branch, rows.size)

    fun reload() = s.load(tr("Loading"), { GitHubApi.commits(at.repo, at.branch) }) { rows = it }

    LaunchedEffect(at.repo, at.branch) { reload() }

    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            NxButton(tr("Refresh"), icon = NxIcon.Refresh, compact = true) { reload() }
            NxButton(at.branch, icon = NxIcon.Git, tone = Tone.Accent, compact = true) { picking = true }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(rows, key = { _, c -> c.sha }) { index, commit ->
                Column(Modifier.fillMaxWidth().nxRow(seen, commit.sha, index).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    Text(commit.message.ifEmpty { "-" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(commit.sha.take(7), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxAccent)
                        Text(
                            commit.author + "  ·  " + commit.date, Modifier.padding(start = 10.dp),
                            fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(NxLine))
            }
        }
    }

    if (picking) {
        BranchDialog(s, at.repo, at.branch, false, onPick = { picked ->
            if (picked != null) s.bind(RepoLink(at.repo, picked, at.subdir))
            picking = false
        }, onDismiss = { picking = false })
    }
}
