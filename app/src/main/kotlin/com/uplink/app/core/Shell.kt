package com.uplink.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class Route { Home, Tools, Tasks, Errors, Settings, Ssh, Files, Web, Cash, Ai, Mind }

object Nav {
    var route by mutableStateOf(Route.Home)
    var viewing by mutableStateOf<ViewTarget?>(null)
}

private fun titleOf(r: Route) = when (r) {
    Route.Home -> tr("Home")
    Route.Tools -> tr("Tools")
    Route.Ssh -> tr("Terminal")
    Route.Files -> tr("Files")
    Route.Web -> tr("Browser")
    Route.Cash -> tr("Cashflow")
    Route.Tasks -> tr("Tasks")
    Route.Errors -> tr("Task Error")
    Route.Ai -> tr("AI Agent")
    Route.Mind -> tr("Mind")
    Route.Settings -> tr("Settings")
}

private fun subtitleOf(r: Route): String? = when (r) {
    Route.Tools -> tr("All tools in one place")
    Route.Tasks -> tr("Transfers & jobs")
    Route.Errors -> tr("Crashes & failures")
    Route.Settings -> tr("Theme & access")
    Route.Ai -> tr("Chat and workflows")
    Route.Mind -> tr("Project memory")
    Route.Ssh -> tr("SSH sessions")
    Route.Web -> tr("Google & DevTools")
    Route.Cash -> tr("Income & expense")
    else -> null
}

@Composable
private fun DrawerItem(
    label: String,
    sub: String,
    icon: ImageVector,
    active: Boolean,
    index: Int,
    open: Boolean,
    badge: String? = null,
    onTap: () -> Unit,
) {
    val ink by animateColorAsState(if (active) NxAccent else NxText, tween(180), label = "drawerInk")
    val mark = animateFloatAsState(if (active) 1f else 0f, spring(0.7f, 400f), label = "drawerMark")
    val tint = NxAccent
    Row(
        Modifier.fillMaxWidth()
            .nxStagger(index, open)
            .drawBehind {
                val k = mark.value.coerceIn(0f, 1f)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(tint.copy(alpha = 0.16f * k), Color.Transparent)),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
                drawRoundRect(
                    tint, Offset(0f, size.height * (1f - 0.5f * k) / 2f), Size(3.dp.toPx(), size.height * 0.5f * k),
                    CornerRadius(2.dp.toPx()),
                )
            }
            .nxTap(14.dp, onTap)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).drawBehind {
                drawRoundRect(tint.copy(alpha = 0.08f + 0.12f * mark.value.coerceIn(0f, 1f)), cornerRadius = CornerRadius(11.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(21.dp).then(if (active && open) Modifier.nxBreathe(0.08f, 1600) else Modifier), tint = ink)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = ink, maxLines = 1)
            Text(sub, fontSize = 12.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) {
            Box(
                Modifier.then(if (open) Modifier.nxPulse(0.6f, 900) else Modifier).clip(CircleShape).background(NxAccent).padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) { Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NxOnAccent) }
        }
    }
}

@Composable
private fun DrawerLabel(text: String, index: Int, open: Boolean) {
    Text(
        text, Modifier.nxStagger(index, open).padding(start = 24.dp, top = 18.dp, bottom = 6.dp),
        fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
    )
}

@Composable
fun AppShell() {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val route = Nav.route
    val vps = Hub.vps
    val viewing = Nav.viewing
    val sessions by Hub.count.collectAsState()
    val running = TaskBoard.active
    val open = drawer.targetValue == DrawerValue.Open
    val alive = open || drawer.currentValue != DrawerValue.Closed
    val crashed = ErrorLog.launchAlert

    BackHandler(enabled = drawer.currentValue == DrawerValue.Open && viewing == null) { scope.launch { drawer.close() } }
    BackHandler(enabled = route != Route.Home && drawer.currentValue == DrawerValue.Closed && viewing == null && !(route == Route.Ssh && vps != null)) {
        Nav.route = Route.Home
    }

    if (crashed != null) {
        NxConfirm(
            tr("The app closed unexpectedly last time: {0}", crashed.title.text()),
            tr("View"),
            { ErrorLog.dismissAlert() },
        ) {
            ErrorLog.dismissAlert()
            Nav.route = Route.Errors
        }
    }

    if (viewing != null) {
        ViewerScreen(viewing)
        return
    }

    if (route == Route.Ssh && vps != null) {
        VpsScreen(vps)
        return
    }

    fun go(r: Route) {
        Nav.route = r
        scope.launch { drawer.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = route != Route.Web || drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = NxSurface,
            ) {
                Column(Modifier.fillMaxWidth().nxStagger(0, open).padding(start = 16.dp, top = 22.dp, end = 20.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (alive) NxOrbit(Modifier.size(64.dp)) else Spacer(Modifier.size(64.dp))
                        Column(Modifier.padding(start = 10.dp)) {
                            Row {
                                Text("Untily", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Flow", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NxAccent)
                            }
                            Text(tr("Tools & utilities"), fontSize = 12.sp, color = NxDim)
                        }
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NxChip(tr("live {0}", sessions), icon = NxIcon.Server, hot = sessions > 0)
                        NxChip(tr("tasks {0}", running), icon = NxIcon.Tasks, hot = running > 0)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    DrawerLabel(tr("NAVIGATION"), 1, open)
                    DrawerItem(tr("Home"), tr("Overview"), NxIcon.Home, route == Route.Home, 2, open) { go(Route.Home) }
                    DrawerItem(tr("Tools"), tr("All tools in one place"), NxIcon.Grid, route == Route.Tools, 2, open) { go(Route.Tools) }
                    DrawerItem(tr("Tasks"), tr("Transfers & jobs"), NxIcon.Tasks, route == Route.Tasks, 3, open, if (running > 0) running.toString() else null) { go(Route.Tasks) }
                    DrawerItem(tr("Task Error"), tr("Crashes & failures"), NxIcon.Triangle, route == Route.Errors, 4, open, if (ErrorLog.unseen > 0) ErrorLog.unseen.toString() else null) { go(Route.Errors) }
                    DrawerItem(tr("Settings"), tr("Theme & access"), NxIcon.Gear, route == Route.Settings, 5, open) { go(Route.Settings) }
                    DrawerLabel(tr("STORAGE"), 6, open)
                    DrawerItem(tr("Files"), tr("Internal, Root & app data"), NxIcon.Phone, route == Route.Files, 7, open) { go(Route.Files) }
                    DrawerLabel(tr("AI"), 8, open)
                    DrawerItem(tr("AI Agent"), tr("Chat and workflows"), NxIcon.Bot, route == Route.Ai, 9, open) { go(Route.Ai) }
                    DrawerItem(tr("Mind"), tr("Project memory"), NxIcon.Bolt, route == Route.Mind, 9, open, if (MindGate.notice.isNotEmpty()) "!" else null) { go(Route.Mind) }
                    DrawerLabel(tr("TOOLS"), 10, open)
                    DrawerItem(tr("Terminal"), tr("SSH sessions"), NxIcon.Terminal, route == Route.Ssh, 11, open, if (sessions > 0) sessions.toString() else null) { go(Route.Ssh) }
                    DrawerItem(tr("Browser"), tr("Google & DevTools"), NxIcon.Globe, route == Route.Web, 12, open) { go(Route.Web) }
                    DrawerItem(tr("Cashflow"), tr("Income & expense"), NxIcon.Wallet, route == Route.Cash, 13, open) { go(Route.Cash) }
                }
                Row(Modifier.nxStagger(14, open).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(6.dp).then(if (alive) Modifier.nxPulse(0.25f, 1200) else Modifier).background(NxLive, CircleShape))
                    Text("UntilyFlow", Modifier.padding(start = 10.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                }
            }
        },
    ) {
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.fillMaxSize()) {
            if (route != Route.Files) {
                NxBar(
                    titleOf(route),
                    subtitle = if (route == Route.Home) greeting() else subtitleOf(route),
                    nav = {
                        if (route == Route.Home) Spacer(Modifier.width(8.dp))
                        else NxIconButton(NxIcon.Back, tr("Back")) { Nav.route = Route.Home }
                    },
                    actions = {
                        if (route == Route.Home) {
                            NxHeadAction(NxIcon.Bell, tr("Notifications"), running > 0 || ErrorLog.unseen > 0) {
                                Nav.route = if (running == 0 && ErrorLog.unseen > 0) Route.Errors else Route.Tasks
                            }
                        }
                        NxMenuMorph(open) { scope.launch { if (drawer.currentValue == DrawerValue.Open) drawer.close() else drawer.open() } }
                    },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 80)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 36 }) togetherWith
                            fadeOut(tween(120))
                    },
                    label = "route",
                ) { target ->
                    when (target) {
                        Route.Home -> HomeScreen()
                        Route.Tools -> ToolsScreen()
                        Route.Tasks -> TasksScreen()
                        Route.Errors -> ErrorsScreen()
                        Route.Settings -> SettingsScreen()
                        Route.Ssh -> ServerList()
                        Route.Files -> FilesScreen()
                        Route.Web -> WebScreen()
                        Route.Cash -> CashflowScreen()
                        Route.Ai -> AiScreen()
                        Route.Mind -> MindScreen()
                    }
                }
            }
            if (!keyboard) {
                val toolRoutes = listOf(Route.Tools, Route.Ssh, Route.Web, Route.Cash, Route.Tasks, Route.Errors)
                NxBottomBar(
                    listOf(
                        NxBarItem(tr("Home"), NxIcon.Home, route == Route.Home) { Nav.route = Route.Home },
                        NxBarItem(tr("Tools"), NxIcon.Grid, route in toolRoutes) { Nav.route = Route.Tools },
                        NxBarItem(tr("Files"), NxIcon.Folder, route == Route.Files) { Nav.route = Route.Files },
                        NxBarItem(tr("AI"), NxIcon.Bot, route == Route.Ai || route == Route.Mind) { Nav.route = Route.Ai },
                        NxBarItem(tr("Settings"), NxIcon.Gear, route == Route.Settings) { Nav.route = Route.Settings },
                    ),
                )
            }
        }
    }
}
