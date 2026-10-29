package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
private fun BranchRow(label: String, on: Boolean, onTap: () -> Unit) {
    NxListRow(
        label, NxIcon.Git, Modifier.padding(bottom = 6.dp),
        trailing = { if (on) Icon(NxIcon.Check, null, Modifier.size(18.dp), tint = NxAccent) },
        onTap = onTap,
    )
}

@Composable
fun BranchDialog(s: GitSession, repo: String, current: String?, allowAll: Boolean, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var fresh by remember { mutableStateOf("") }
    LaunchedEffect(repo) { s.load(tr("Loading"), { GitHubApi.branches(repo) }) { names = it } }
    NxDialog(
        tr("Branch"), onDismiss, tr("Use branch"),
        {
            val n = fresh.trim()
            if (n.isNotEmpty()) {
                if (n in names) onPick(n)
                else s.load(tr("Creating branch"), { GitHubApi.createBranch(repo, n, current ?: names.firstOrNull() ?: "main") }) { onPick(n) }
            } else {
                onDismiss()
            }
        },
    ) {
        if (allowAll) BranchRow(tr("All branches"), current == null) { onPick(null) }
        for (b in names) BranchRow(b, b == current) { onPick(b) }
        Box(Modifier.height(10.dp))
        NxField(fresh, { fresh = it }, tr("Other or new branch name"), mono = true)
    }
}

@Composable
fun ReposView(s: GitSession) {
    var repos by remember { mutableStateOf<List<RepoRow>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var making by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val seen = rememberNxSeen(repos.size)

    fun reload() = s.load(tr("Loading"), { GitHubApi.repos() }) { repos = it }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            NxButton(tr("Refresh"), icon = NxIcon.Refresh, compact = true) { reload() }
            NxButton(tr("New repository"), icon = NxIcon.FolderPlus, tone = Tone.Accent, compact = true) { making = true }
            NxButton(tr("Enter name"), icon = NxIcon.Edit, compact = true) { typing = true }
        }
        NxSearch(filter, { filter = it }, tr("Filter"), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val shown = repos.filter { it.full.contains(filter.trim(), ignoreCase = true) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(shown, key = { _, r -> r.full }) { index, repo ->
                val on = s.link?.repo == repo.full
                NxListRow(
                    repo.full, if (repo.priv) NxIcon.Shield else NxIcon.Globe, Modifier.nxRow(seen, repo.full, index),
                    meta = repo.about.ifEmpty { null },
                    trailing = { if (on) Icon(NxIcon.Check, null, Modifier.size(18.dp), tint = NxAccent) },
                ) { s.bind(RepoLink(repo.full, repo.branch, s.link?.subdir.orEmpty())); s.view = GitView.Hub }
            }
        }
    }

    if (making) RepoCreateDialog(s, onDismiss = { making = false })
    if (typing) {
        NxAsk(tr("Repository"), "", tr("owner/name"), 1, onDismiss = { typing = false }) { text ->
            typing = false
            if (text.contains('/')) {
                s.load(tr("Loading"), { GitHubApi.repoInfo(text) }) {
                    s.bind(RepoLink(it.full, it.branch, ""))
                    s.view = GitView.Hub
                }
            } else {
                toast(s.ctx, tr("Use the form owner/name"))
            }
        }
    }
}

@Composable
private fun RepoCreateDialog(s: GitSession, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var priv by remember { mutableStateOf(true) }
    NxDialog(
        tr("New repository"), onDismiss, tr("Create"),
        {
            val n = name.trim()
            if (n.isNotEmpty()) {
                onDismiss()
                s.load(tr("Creating repository"), { GitHubApi.createRepo(n, priv) }) {
                    s.bind(RepoLink(it.full, it.branch, ""))
                    s.view = GitView.Hub
                }
            }
        },
    ) {
        NxField(name, { name = it }, tr("Repository name"), mono = true)
        Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("Private"), icon = NxIcon.Shield, tone = if (priv) Tone.Accent else Tone.Quiet, compact = true) { priv = true }
            NxButton(tr("Public"), icon = NxIcon.Globe, tone = if (!priv) Tone.Accent else Tone.Quiet, compact = true) { priv = false }
        }
    }
}
