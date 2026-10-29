package com.uplink.app

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun WebScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val made = remember { WebHost.obtain(ctx) }
    if (made == null) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(NxIcon.Globe, null, Modifier.size(40.dp), tint = NxDim)
                Text(tr("WebView is not available on this device"), Modifier.padding(top = 12.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(WebHost.failure ?: "", Modifier.padding(top = 6.dp), color = NxDim, fontSize = 12.sp)
            }
        }
        return
    }
    val web = made
    var typed by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var askWipe by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val loading = WebHost.progress in 0..99

    BackHandler(enabled = showHistory) { showHistory = false }
    BackHandler(enabled = !showHistory && WebHost.canBack) { web.goBack() }

    fun go() {
        typing = false
        focus.clearFocus()
        WebHost.load(typed)
    }

    fun toggleTools() {
        if (WebHost.devtools) {
            WebHost.devtools = false
            DevTools.detach(web)
            return
        }
        scope.launch {
            if (!DevTools.ready()) {
                toast(ctx, tr("Downloading DevTools engine..."))
                val run = DevTools.fetch()
                snapshotFlow { run.state }.first { it != TaskState.Running }
                if (!DevTools.ready()) {
                    toast(ctx, run.error ?: tr("DevTools download failed"))
                    return@launch
                }
            }
            WebHost.devtools = true
            DevTools.attach(web)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NxIconButton(NxIcon.Back, tr("Back"), if (WebHost.canBack) NxText else NxDim) { if (WebHost.canBack) web.goBack() }
            NxIconButton(NxIcon.Right, tr("Forward"), if (WebHost.canForward) NxText else NxDim) { if (WebHost.canForward) web.goForward() }
            NxIconButton(if (loading) NxIcon.Close else NxIcon.Refresh, if (loading) tr("Stop") else tr("Reload")) {
                if (loading) web.stopLoading() else web.reload()
            }
            BasicTextField(
                value = if (typing) typed else WebHost.url,
                onValueChange = { typed = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontPrefs.mono.family, fontSize = 13.sp, color = NxText),
                cursorBrush = SolidColor(NxAccent),
                keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier.weight(1f)
                    .onFocusChanged {
                        if (it.isFocused && !typing) {
                            typed = WebHost.url
                            typing = true
                        }
                    }
                    .clip(shape).background(NxSurface).border(1.dp, NxLine, shape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                decorationBox = { field ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(NxIcon.Search, null, Modifier.size(18.dp), tint = NxDim)
                        Box(Modifier.weight(1f).padding(start = 10.dp)) { field() }
                    }
                },
            )
        }
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            NxChip(tr("Google"), icon = NxIcon.Globe, onTap = { showHistory = false; WebHost.load(WebHost.HOME) })
            NxChip(tr("Desktop"), icon = NxIcon.Monitor, hot = WebHost.desktop, onTap = { WebHost.setDesktop(ctx, !WebHost.desktop) })
            NxChip(tr("DevTools"), icon = NxIcon.Terminal, hot = WebHost.devtools, onTap = { toggleTools() })
            NxChip(tr("Adblock"), icon = NxIcon.Shield, hot = WebHost.adblock, onTap = { WebHost.setAdblock(ctx, !WebHost.adblock) })
            NxChip(
                if (WebHost.mediaBuffer) tr("Media: Buffer") else tr("Media: Render"),
                icon = NxIcon.Image, hot = WebHost.mediaBuffer, onTap = { WebHost.setMediaBuffer(ctx, !WebHost.mediaBuffer) },
            )
            NxChip(
                if (WebHost.adsBuffer) tr("Ads: Buffer") else tr("Ads: Render"),
                icon = NxIcon.Bolt, hot = WebHost.adsBuffer, onTap = { WebHost.setAdsBuffer(ctx, !WebHost.adsBuffer) },
            )
            if (WebHost.held > 0) {
                NxButton(tr("Load {0} buffered", WebHost.held), icon = NxIcon.Download, tone = Tone.Accent, compact = true) {
                    WebHost.unlock()
                }
            }
            NxChip(tr("History"), icon = NxIcon.History, hot = showHistory, onTap = { showHistory = !showHistory })
            NxButton(tr("Clear data"), icon = NxIcon.Trash, tone = Tone.Danger, compact = true) { askWipe = true }
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(NxLine).drawBehind {
                if (loading) drawRect(NxAccent, size = Size(size.width * WebHost.progress / 100f, size.height))
            },
        )
        if (showHistory) {
            HistoryList(onPick = { showHistory = false; WebHost.load(it) })
        } else {
            AndroidView(
                factory = {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web.onResume()
                    web
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onRelease = { WebHost.detach(it, ctx) },
            )
        }
    }

    if (askWipe) {
        NxConfirm(tr("Clear cookies, cache and site data?"), tr("Clear"), onDismiss = { askWipe = false }) {
            askWipe = false
            WebHost.wipe()
            toast(ctx, tr("Cleared"))
        }
    }
}

@Composable
private fun HistoryList(onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var askClear by remember { mutableStateOf(false) }
    val all = WebHistory.entries
    val shown = remember(query, all.size) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all.toList() else all.filter { it.url.lowercase().contains(q) || it.title.lowercase().contains(q) }
    }

    LaunchedEffect(Unit) { WebHistory.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            NxSearch(query, { query = it }, tr("Search history"), Modifier.weight(1f))
            Box(Modifier.padding(start = 8.dp, top = 8.dp)) {
                NxButton(tr("Clear all"), tone = Tone.Danger, compact = true) { askClear = true }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.at.toString() + it.url }) { v ->
                NxListRow(
                    v.title.ifBlank { v.url }, NxIcon.Globe, meta = v.url,
                    trailing = { NxIconButton(NxIcon.Trash, tr("Delete"), NxDim) { WebHistory.remove(v) } },
                ) { onPick(v.url) }
            }
        }
    }

    if (askClear) {
        NxConfirm(tr("Delete all browsing history?"), tr("Delete"), onDismiss = { askClear = false }) {
            askClear = false
            WebHistory.clear()
        }
    }
}
