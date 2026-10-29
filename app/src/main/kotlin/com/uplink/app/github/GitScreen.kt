package com.uplink.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private class HubTool(val icon: ImageVector, val title: String, val body: String, val go: GitView)

private fun gitTitle(view: GitView): String = when (view) {
    GitView.Hub -> "GitHub"
    GitView.Setup -> tr("Connect GitHub")
    GitView.Repos -> tr("Repositories")
    GitView.Upload -> tr("Upload to GitHub")
    GitView.Browse -> tr("Browse GitHub")
    GitView.Edit -> tr("Edit file")
    GitView.History -> tr("History and branches")
    GitView.Actions -> tr("GitHub Actions")
    GitView.Releases -> tr("Releases")
    GitView.Pulls -> tr("Pull requests")
    GitView.Engine -> tr("Git engine")
}

@Composable
private fun RepoStrip(s: GitSession) {
    val at = s.link ?: return
    NxFlow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        NxChip(at.repo, icon = NxIcon.Git, hot = true)
        NxChip(at.branch)
        if (at.subdir.isNotEmpty()) NxChip(at.subdir, icon = NxIcon.Folder)
    }
}

@Composable
private fun HubView(s: GitSession) {
    val at = s.link
    val tools = listOf(
        HubTool(NxIcon.Upload, tr("Upload to GitHub"), tr("Push this folder to a repository"), GitView.Upload),
        HubTool(NxIcon.Folder, tr("Browse GitHub"), tr("Edit, delete, download, create files and folders"), GitView.Browse),
        HubTool(NxIcon.Bolt, tr("GitHub Actions"), tr("Runs and artifacts from any branch"), GitView.Actions),
        HubTool(NxIcon.History, tr("History and branches"), tr("Recent commits, switch or create a branch"), GitView.History),
        HubTool(NxIcon.Archive, tr("Releases"), tr("Create tags, upload files, get links"), GitView.Releases),
        HubTool(NxIcon.Swap, tr("Pull requests"), tr("View and open pull requests"), GitView.Pulls),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        NxCard(Modifier.fillMaxWidth().nxEnter(0), hot = GitAccount.ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(NxAccent.copy(alpha = 0.16f))
                        .border(1.dp, NxAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(GitAccount.user.take(1).uppercase().ifEmpty { "G" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NxAccent)
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(GitAccount.user, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        GitAccount.email, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                NxButton(tr("Account"), compact = true) { s.view = GitView.Setup }
            }
            Spacer(Modifier.height(14.dp))
            NxSegment(
                listOf(tr("Token (PAT)") to NxIcon.Shield, tr("Git") to NxIcon.Git),
                if (GitAccount.mode == GitMode.Token) 0 else 1,
                Modifier.fillMaxWidth(),
            ) { i ->
                if (i == 0) {
                    GitAccount.pickMode(s.ctx, GitMode.Token)
                } else {
                    GitAccount.pickMode(s.ctx, GitMode.Engine)
                    s.view = GitView.Engine
                }
            }
            Text(
                if (GitAccount.mode == GitMode.Token) tr("Token mode syncs the folder with GitHub: new, changed, renamed and deleted files all follow.")
                else tr("Git mode commits locally and pushes. If it does not work, use a Personal Access Token instead."),
                Modifier.padding(top = 10.dp), color = NxDim, fontSize = 12.sp,
            )
        }
        NxSection(tr("REPOSITORY"), Modifier.nxEnter(1))
        NxCard(Modifier.fillMaxWidth().nxEnter(1), hot = at != null) {
            if (at == null) {
                Text(tr("No repository linked to this folder"), color = NxDim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                NxButton(tr("Choose repository"), icon = NxIcon.Git, tone = Tone.Accent, wide = true) { s.view = GitView.Repos }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NxIconBox(NxIcon.Git, box = 40.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(at.repo, fontFamily = FontPrefs.mono.family, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        NxFlow(Modifier.padding(top = 6.dp)) {
                            NxChip(at.branch, icon = NxIcon.Git, hot = true)
                            if (at.subdir.isNotEmpty()) NxChip(at.subdir, icon = NxIcon.Folder)
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NxButton(tr("Change"), compact = true) { s.view = GitView.Repos }
                    NxButton(tr("Unlink"), compact = true, tone = Tone.Danger) { s.unbind() }
                }
            }
        }
        NxSection(tr("TOOLS"), Modifier.nxEnter(2))
        for ((i, tool) in tools.withIndex()) {
            NxListRow(
                tool.title, tool.icon,
                Modifier.padding(bottom = 10.dp).nxEnter(3 + i).graphicsLayer { alpha = if (at == null) 0.62f else 1f },
                meta = tool.body,
            ) { s.open(tool.go) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun GitSheet(fs: FsBackend, dir: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val finishedTick = TaskBoard.finished
    val session = remember(fs, dir) {
        GitAccount.load(ctx)
        GitSession(ctx.applicationContext, fs, dir, scope)
    }
    LaunchedEffect(finishedTick) { if (finishedTick != 0) session.version++ }
    val leave = { if (!session.back()) onDismiss() }

    Dialog(onDismissRequest = leave, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.statusBarColor = NxHead.toArgb()
            window?.navigationBarColor = NxBg.toArgb()
        }
        Column(Modifier.fillMaxSize().background(NxBg).nxSafe()) {
            NxBar(
                gitTitle(session.view),
                subtitle = session.busy?.let { "$it..." } ?: dir,
                nav = { NxIconButton(NxIcon.Back, tr("Back"), onTap = leave) },
            )
            TaskStrip()
            if (session.view != GitView.Hub && session.view != GitView.Setup && session.view != GitView.Repos && session.view != GitView.Engine) {
                RepoStrip(session)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = session.view,
                    transitionSpec = {
                        if (targetState != GitView.Hub) {
                            (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 8 }) togetherWith
                                (fadeOut(tween(90)) + slideOutHorizontally(tween(200)) { -it / 10 })
                        } else {
                            (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 8 }) togetherWith
                                (fadeOut(tween(90)) + slideOutHorizontally(tween(200)) { it / 10 })
                        }
                    },
                    label = "gitView",
                ) { view ->
                    when (view) {
                        GitView.Hub -> HubView(session)
                        GitView.Setup -> SetupView(session)
                        GitView.Repos -> ReposView(session)
                        GitView.Upload -> UploadView(session)
                        GitView.Browse -> BrowseView(session)
                        GitView.Edit -> EditView(session)
                        GitView.History -> HistoryView(session)
                        GitView.Actions -> ActionsView(session)
                        GitView.Releases -> ReleasesView(session)
                        GitView.Pulls -> PullsView(session)
                        GitView.Engine -> EngineView(session)
                    }
                }
            }
            if (session.view != GitView.Setup) GitConsole(session)
        }
    }
}
