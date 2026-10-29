package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size

private class ToolLink(val title: String, val sub: String, val icon: ImageVector, val group: Int, val route: Route, val badge: Int, val tint: Color)

@Composable
fun ToolsScreen() {
    val sessions by Hub.count.collectAsState()
    val running = TaskBoard.active
    val unseen = ErrorLog.unseen
    var query by remember { mutableStateOf("") }
    var group by remember { mutableIntStateOf(0) }
    val groups = listOf(tr("All"), tr("Network"), tr("Development"), tr("Productivity"))
    val links = listOf(
        ToolLink(tr("Terminal"), tr("SSH sessions"), NxIcon.Terminal, 1, Route.Ssh, sessions, NxBlue),
        ToolLink(tr("Browser"), tr("Google & DevTools"), NxIcon.Globe, 1, Route.Web, 0, NxYellow),
        ToolLink("GitHub", tr("Open from Files"), NxIcon.Git, 2, Route.Files, 0, NxPrimaryVariant),
        ToolLink("Localhost", tr("Open from Files"), NxIcon.Monitor, 2, Route.Files, 0, NxOrange),
        ToolLink(tr("Mind"), tr("Project memory"), NxIcon.Bolt, 2, Route.Mind, 0, NxLive),
        ToolLink(tr("Cashflow"), tr("Income & expense"), NxIcon.Wallet, 3, Route.Cash, 0, NxRed),
        ToolLink(tr("Tasks"), tr("Transfers & jobs"), NxIcon.Tasks, 3, Route.Tasks, running, NxAccent),
        ToolLink(tr("Task Error"), tr("Crashes & failures"), NxIcon.Triangle, 3, Route.Errors, unseen, NxRed),
    )
    val needle = query.trim()
    val shown = links.filter { (group == 0 || it.group == group) && it.title.contains(needle, ignoreCase = true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        NxSearch(query, { query = it }, tr("Search tools..."), Modifier.nxEnter(0))
        NxFlow(Modifier.padding(top = 14.dp).nxEnter(1)) {
            for ((i, name) in groups.withIndex()) NxChip(name, hot = i == group) { group = i }
        }
        NxSection(tr("Tools"))
        if (shown.isEmpty()) {
            Text(tr("No matching tools"), Modifier.padding(vertical = 24.dp), color = NxDim)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, link) in shown.withIndex()) {
                NxListRow(
                    link.title, link.icon, Modifier.nxEnter(2 + i), link.sub, tint = link.tint,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (link.badge > 0) {
                                Text(
                                    link.badge.toString(),
                                    Modifier.clip(CircleShape).background(NxAccent).padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NxOnAccent,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(NxIcon.Right, null, Modifier.size(18.dp), tint = NxDim)
                        }
                    },
                ) { Nav.route = link.route }
                if (i == shown.lastIndex) Spacer(Modifier.size(20.dp))
            }
        }
    }
}
