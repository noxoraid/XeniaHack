package com.uplink.app

import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun hideKeyboard(v: View) {
    (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(v.windowToken, 0)
}

@Composable
fun VpsScreen(vps: Vps) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var tab by remember { mutableIntStateOf(0) }
    var askExit by remember { mutableStateOf(false) }
    var aiOpen by remember { mutableStateOf(false) }
    val sessionSeen = rememberNxSeen(vps)

    fun newSession() {
        tab = 0
        scope.launch {
            var err: String? = null
            try {
                val s = withContext(Dispatchers.IO) { vps.openSession() }
                if (s == null) err = tr("Limit reached: {0} sessions", Vps.MAX)
            } catch (e: Throwable) {
                err = tr("Cannot open session: {0}", e.message ?: e)
            }
            err?.let { toast(ctx, it) }
        }
    }

    fun leaveVps() {
        askExit = false
        scope.launch(Dispatchers.IO) { Hub.closeAll() }
    }

    LaunchedEffect(vps) { if (vps.sessions.isEmpty()) newSession() }
    LaunchedEffect(drawer.currentValue) { if (drawer.currentValue == DrawerValue.Open) hideKeyboard(view) }

    BackHandler { Nav.route = Route.Home }
    BackHandler(enabled = drawer.currentValue == DrawerValue.Open) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.currentValue == DrawerValue.Open,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                drawerContainerColor = NxSurface,
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Sessions"), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${vps.sessions.size}/${Vps.MAX}", fontFamily = FontPrefs.mono.family, fontSize = 13.sp, color = NxDim)
                }
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    NxButton(tr("New session"), icon = NxIcon.Plus, tone = Tone.Accent, wide = true) {
                        scope.launch { drawer.close() }
                        newSession()
                    }
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(vps.sessions.toList()) { index, s ->
                        val current = s === vps.current
                        NxListRow(
                            s.title, NxIcon.Terminal, Modifier.nxRow(sessionSeen, s, index),
                            meta = if (current) tr("Active") else null,
                            trailing = {
                                if (current) Box(Modifier.padding(end = 4.dp).size(8.dp).clip(CircleShape).background(NxLive))
                                NxIconButton(NxIcon.Close, tr("Close session"), NxDim) {
                                    scope.launch(Dispatchers.IO) { vps.closeSession(s) }
                                }
                            },
                        ) { vps.current = s; tab = 0; scope.launch { drawer.close() } }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
                Box(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                    NxButton(tr("Home"), icon = NxIcon.Home, wide = true) {
                        scope.launch { drawer.close() }
                        Nav.route = Route.Home
                    }
                }
                Box(Modifier.padding(16.dp)) {
                    NxButton(tr("Exit VPS"), icon = NxIcon.Logout, tone = Tone.Danger, wide = true) {
                        scope.launch { drawer.close() }
                        askExit = true
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            NxBar(
                vps.profile.name,
                subtitle = "${vps.profile.user}@${vps.profile.host}",
                nav = { NxIconButton(NxIcon.Back, tr("Back")) { Nav.route = Route.Home } },
                actions = {
                    NxIconButton(NxIcon.Menu, tr("Sessions")) { scope.launch { drawer.open() } }
                    NxButton(tr("Exit"), icon = NxIcon.Logout, tone = Tone.Danger, compact = true) { askExit = true }
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == 0) {
                    Column(Modifier.fillMaxSize()) {
                        SessionBar(vps, onNew = { newSession() }, onAi = { aiOpen = !aiOpen })
                        val cur = vps.current
                        if (aiOpen) {
                            SshAiPanel(vps, Modifier.weight(1f).fillMaxWidth()) { aiOpen = false }
                        } else if (cur == null) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                NxButton(tr("New session"), icon = NxIcon.Plus, tone = Tone.Accent) { newSession() }
                            }
                        } else {
                            Box(Modifier.weight(1f).fillMaxWidth()) { key(cur) { TerminalPane(cur) } }
                        }
                    }
                } else if (tab == 1) {
                    FilesPane(vps)
                } else if (tab == 2) {
                    SystemPane(vps)
                } else {
                    TransferPane(vps)
                }
            }
            NxBottomBar(
                listOf(
                    NxBarItem(tr("Terminal"), NxIcon.Terminal, tab == 0) { hideKeyboard(view); tab = 0 },
                    NxBarItem(tr("Files"), NxIcon.Folder, tab == 1) { hideKeyboard(view); tab = 1 },
                    NxBarItem(tr("System"), NxIcon.Pulse, tab == 2) { hideKeyboard(view); tab = 2 },
                    NxBarItem(tr("Transfer"), NxIcon.Swap, tab == 3) { hideKeyboard(view); tab = 3 },
                ),
            )
        }
    }

    if (askExit) {
        NxConfirm(tr("Exit {0}? All sessions on this server will be closed.", vps.profile.name), tr("Exit"), onDismiss = { askExit = false }) { leaveVps() }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun RowScope.Key(
    label: String? = null,
    icon: ImageVector? = null,
    active: Boolean = false,
    onTap: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val ink = if (active) NxOnAccent else NxText
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .clip(shape)
            .background(if (active) NxAccent else if (down) NxLine else NxRaised)
            .border(1.dp, if (active) NxAccent else NxLine, shape)
            .clickable(interactionSource = source, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, label, Modifier.size(18.dp), tint = ink)
        if (label != null && icon == null) {
            Text(label, fontFamily = FontPrefs.mono.family, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
        }
    }
}

@Composable
private fun KeyPage(s: TermSession) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyRow {
            Key("ESC") { s.send("\u001b".toByteArray()) }
            Key("TAB") { s.send("\t".toByteArray()) }
            Key("CTRL", active = s.ctrl) { s.ctrl = !s.ctrl }
            Key("^C") { s.send(byteArrayOf(3)) }
            Key("^D") { s.send(byteArrayOf(4)) }
            Key("^Z") { s.send(byteArrayOf(26)) }
            Key("PgUp") { s.scroll((s.term.rows * 0.8).toInt()) }
            Key("PgDn") { s.scroll(-(s.term.rows * 0.8).toInt()) }
        }
        KeyRow {
            Key("Left", NxIcon.Left) { s.arrow('D') }
            Key("Up", NxIcon.Up) { s.arrow('A') }
            Key("Down", NxIcon.Down) { s.arrow('B') }
            Key("Right", NxIcon.Right) { s.arrow('C') }
            Key("/") { s.type("/") }
            Key("-") { s.type("-") }
            Key("|") { s.type("|") }
            Key("~") { s.type("~") }
        }
    }
}

@Composable
private fun InputPage(draft: String, onDraft: (String) -> Unit, onPaste: () -> Unit, onSend: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = onDraft,
            maxLines = 3,
            textStyle = TextStyle(fontFamily = FontPrefs.mono.family, fontSize = 14.sp, color = NxText),
            cursorBrush = SolidColor(NxAccent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
            modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(NxBg).border(1.dp, NxLine, shape).padding(horizontal = 12.dp),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) Text(tr("Type or paste, then send"), color = NxDim, fontSize = 14.sp)
                    field()
                }
            },
        )
        NxIconButton(NxIcon.Paste, tr("Paste"), NxDim, onPaste)
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(NxAccent).clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) { Icon(NxIcon.Send, tr("Send"), Modifier.size(20.dp), tint = NxOnAccent) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalPane(s: TermSession) {
    val ctx = LocalContext.current
    val pager = rememberPagerState(pageCount = { 2 })
    var draft by remember { mutableStateOf("") }

    fun clipboardText(): String? {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip: ClipData = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(ctx)?.toString()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(FontPrefs.mono.key) {
                AndroidView(
                    factory = { TerminalView(it) },
                    update = { it.session = s; it.setSizeSp(TermZoom.sp) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
        Column(Modifier.fillMaxWidth().background(NxSurface)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(98.dp)) { page ->
                if (page == 0) {
                    KeyPage(s)
                } else {
                    InputPage(
                        draft = draft,
                        onDraft = { draft = it },
                        onPaste = { clipboardText()?.let { draft += it } },
                        onSend = {
                            if (draft.isNotEmpty()) {
                                s.paste(draft)
                                s.send("\r".toByteArray())
                                draft = ""
                            }
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..1) {
                    val on = pager.currentPage == i
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(4.dp).width(if (on) 14.dp else 4.dp)
                            .clip(CircleShape).background(if (on) NxAccent else NxLine),
                    )
                }
            }
        }
    }
}
