package com.uplink.app

import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class ReleaseDraft(val tag: String, val title: String, val notes: String, val branch: String, val pre: Boolean, val draft: Boolean)

@Composable
private fun ReleaseCreateDialog(s: GitSession, branch: String, onDismiss: () -> Unit, onMake: (ReleaseDraft) -> Unit) {
    var tag by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(branch) }
    var pre by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(false) }
    NxDialog(
        tr("New release tag"), onDismiss, tr("Create"),
        {
            if (tag.isBlank()) toast(s.ctx, tr("Enter a tag name")) else onMake(ReleaseDraft(tag.trim(), title.trim(), notes.trim(), target.trim().ifEmpty { branch }, pre, draft))
        },
    ) {
        NxField(tag, { tag = it }, tr("Tag name, for example v1.0.0"), mono = true)
        NxField(title, { title = it }, tr("Release title (optional)"))
        NxField(notes, { notes = it }, tr("Notes (optional)"), lines = 3)
        NxField(target, { target = it }, tr("Branch to tag"), mono = true)
        Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("Pre-release"), tone = if (pre) Tone.Accent else Tone.Quiet, compact = true) { pre = !pre }
            NxButton(tr("Draft"), tone = if (draft) Tone.Accent else Tone.Quiet, compact = true) { draft = !draft }
        }
    }
}

@Composable
private fun AssetPickDialog(s: GitSession, release: ReleaseRow, onDismiss: () -> Unit) {
    var files by remember { mutableStateOf<List<FsItem>>(emptyList()) }
    val picked = remember { mutableStateListOf<String>() }
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(s.dir) { s.load(tr("Loading"), { s.fs.list(s.dir).filter { !it.dir } }) { files = it } }
    NxDialog(
        tr("Upload files to {0}", release.tag), onDismiss, tr("Upload"),
        {
            val paths = picked.map { joinPath(s.dir, it) } + (if (typed.isBlank()) emptyList() else listOf(fixPath(typed, s.dir)))
            if (paths.isEmpty()) {
                toast(s.ctx, tr("Select at least one file"))
            } else {
                onDismiss()
                val at = s.link
                if (at != null) {
                    TaskBoard.start(TaskKind.Git, tr("Upload to {0}", release.tag)) { run ->
                        run.total = paths.size
                        run.totalBytes = paths.sumOf { p -> s.fs.list(parentOf(p)).firstOrNull { it.name == p.substringAfterLast('/') }?.size ?: 0L }
                        for (p in paths) {
                            if (run.stop) throw TaskStopped()
                            val name = p.substringAfterLast('/')
                            val size = s.fs.list(parentOf(p)).firstOrNull { it.name == name }?.size ?: throw Exception(tr("{0}: not found", p))
                            run.detail = name
                            s.say(tr("Uploading {0} ({1})", name, humanSize(size)))
                            val asset = s.fs.open(p).use { input ->
                                GitHubApi.uploadAsset(at.repo, release.id, name, size, input) { n ->
                                    run.bytes += n
                                    TaskBoard.publish()
                                }
                            }
                            s.say(asset.url)
                            run.done++
                            TaskBoard.publish()
                        }
                        s.version++
                    }
                    toast(s.ctx, tr("Running in background, see Tasks"))
                }
            }
        },
    ) {
        Text(s.dir, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, modifier = Modifier.padding(bottom = 10.dp))
        if (files.isEmpty()) Text(tr("No files in this folder"), color = NxDim, modifier = Modifier.padding(bottom = 10.dp))
        for (f in files.take(80)) {
            val on = f.name in picked
            Row(
                Modifier.fillMaxWidth().nxTap(8.dp) { if (on) picked.remove(f.name) else picked.add(f.name) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(if (on) NxAccent else NxRaised),
                    contentAlignment = Alignment.Center,
                ) { if (on) Icon(NxIcon.Check, null, Modifier.size(12.dp), tint = NxOnAccent) }
                Text(f.name, Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(humanSize(f.size), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
            }
        }
        Box(Modifier.height(8.dp))
        NxField(typed, { typed = it }, tr("Or a file path"), mono = true)
    }
}

@Composable
fun ReleasesView(s: GitSession) {
    val at = s.link ?: return
    var rows by remember { mutableStateOf<List<ReleaseRow>>(emptyList()) }
    var open by remember { mutableStateOf(0L) }
    var making by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf<ReleaseRow?>(null) }
    var doomed by remember { mutableStateOf<ReleaseRow?>(null) }
    val seen = rememberNxSeen(at.repo, rows.size)

    fun reload() = s.load(tr("Loading"), { GitHubApi.releases(at.repo) }) { rows = it }

    LaunchedEffect(at.repo, s.version) { reload() }
    LaunchedEffect(at.repo) {
        while (true) {
            delay(8000)
            if (s.busy == null) s.quiet({ GitHubApi.releases(at.repo) }) { rows = it }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            NxButton(tr("Refresh"), icon = NxIcon.Refresh, compact = true) { reload() }
            NxButton(tr("New release tag"), icon = NxIcon.Plus, tone = Tone.Accent, compact = true) { making = true }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty() && s.busy == null) Text(tr("No releases yet"), Modifier.align(Alignment.Center), color = NxDim)
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows, key = { _, r -> r.id }) { index, r ->
                    val shown = open == r.id
                    Column(Modifier.fillMaxWidth().nxRow(seen, r.id, index)) {
                        Row(
                            Modifier.fillMaxWidth().nxTap(0.dp) { open = if (shown) 0L else r.id }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(NxIcon.Archive, null, Modifier.size(20.dp), tint = NxAccent)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(r.tag, fontFamily = FontPrefs.mono.family, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    listOfNotNull(
                                        r.name.takeIf { it.isNotBlank() && it != r.tag },
                                        if (r.draft) tr("Draft") else null,
                                        if (r.pre) tr("Pre-release") else null,
                                        r.date,
                                        tr("{0} files", r.assets.size),
                                    ).joinToString("  ·  "),
                                    color = NxDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(if (shown) NxIcon.Up else NxIcon.Down, null, Modifier.size(16.dp), tint = NxDim)
                        }
                        if (shown) {
                            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                Text(r.page, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxAccent)
                                NxFlow(Modifier.padding(top = 8.dp)) {
                                    NxButton(tr("Upload files"), icon = NxIcon.Upload, tone = Tone.Accent, compact = true) { uploading = r }
                                    NxButton(tr("Copy link"), icon = NxIcon.Copy, compact = true) { copyOutput(s.ctx, r.page) }
                                    NxButton(tr("Source zip link"), icon = NxIcon.Archive, compact = true) {
                                        val link = "https://github.com/${at.repo}/archive/refs/tags/${r.tag}.zip"
                                        s.say(link)
                                        copyOutput(s.ctx, link)
                                    }
                                    NxButton(tr("Delete"), icon = NxIcon.Trash, tone = Tone.Danger, compact = true) { doomed = r }
                                }
                                for (a in r.assets) {
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(NxIcon.File, null, Modifier.size(16.dp), tint = NxDim)
                                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                            Text(a.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(humanSize(a.size), fontFamily = FontPrefs.mono.family, fontSize = 10.sp, color = NxDim)
                                        }
                                        NxIconButton(NxIcon.Copy, tr("Copy link"), NxDim) {
                                            s.say(a.url)
                                            copyOutput(s.ctx, a.url)
                                        }
                                        NxIconButton(NxIcon.Trash, tr("Delete"), NxDim) {
                                            s.load(tr("Deleting"), { GitHubApi.deleteAsset(at.repo, a.id) }) {
                                                s.say(tr("Removed {0}", a.name))
                                                s.version++
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(NxLine))
                }
            }
        }
    }

    if (making) {
        ReleaseCreateDialog(s, at.branch, onDismiss = { making = false }) { d ->
            making = false
            s.say("$ release ${d.tag}  ->  ${at.repo}@${d.branch}")
            s.load(tr("Creating release"), { GitHubApi.createRelease(at.repo, d.tag, d.title, d.notes, d.branch, d.draft, d.pre) }) { made ->
                s.say(tr("Created {0}", made.tag))
                s.say(made.page)
                s.version++
                open = made.id
            }
        }
    }
    uploading?.let { AssetPickDialog(s, it, onDismiss = { uploading = null }) }
    doomed?.let { r ->
        NxConfirm(tr("Delete release {0} and its tag?", r.tag), tr("Delete"), onDismiss = { doomed = null }) {
            doomed = null
            s.load(tr("Deleting"), { GitHubApi.deleteRelease(at.repo, r.id, r.tag, true) }) {
                s.say(tr("Deleted {0}", r.tag))
                s.version++
            }
        }
    }
}
