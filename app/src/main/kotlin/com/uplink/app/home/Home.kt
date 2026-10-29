package com.uplink.app

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Stats(val storeUsed: Long, val storeTotal: Long, val memUsed: Long, val memTotal: Long, val battery: Int)

private fun readStats(ctx: Context): Stats {
    val sd = StatFs(Environment.getExternalStorageDirectory().absolutePath)
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return Stats(
        sd.totalBytes - sd.availableBytes, sd.totalBytes,
        mi.totalMem - mi.availMem, mi.totalMem,
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
    )
}

@Composable
private fun UsageBar(label: String, used: String, total: String, frac: Float, ready: Boolean, tint: Color) {
    val fill = animateFloatAsState(if (ready) frac.coerceIn(0f, 1f) else 0f, tween(1100, easing = FastOutSlowInEasing), label = "heroFill")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, Modifier.weight(1f), fontSize = 12.sp, color = NxSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (ready) "$used / $total" else tr("reading..."),
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NxOnHead, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(NxOnHead.copy(alpha = 0.18f))) {
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .graphicsLayer { scaleX = fill.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                    .clip(CircleShape).background(tint),
            )
        }
    }
}

@Composable
private fun HeroCard(stats: Stats?, modifier: Modifier = Modifier) {
    val s = stats
    val sf = if (s != null && s.storeTotal > 0) s.storeUsed.toFloat() / s.storeTotal else 0f
    val mf = if (s != null && s.memTotal > 0) s.memUsed.toFloat() / s.memTotal else 0f
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 36.dp).height(56.dp)
                .offset(y = 16.dp).clip(RoundedCornerShape(24.dp)).background(NxPrimaryVariant),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp).height(56.dp)
                .offset(y = 8.dp).clip(RoundedCornerShape(24.dp)).background(NxRed),
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(NxHero)
                .drawBehind { drawCircle(NxBlue, 76.dp.toPx(), Offset(size.width - 18.dp.toPx(), 8.dp.toPx())) }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                Text(tr("My device"), fontSize = 12.sp, color = NxSoft)
                Text(
                    if (s != null) humanSize(s.storeTotal - s.storeUsed) else "--",
                    Modifier.padding(top = 2.dp), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = NxOnHead,
                )
                Text(tr("Storage"), fontSize = 11.sp, color = NxSoft)
            }
            UsageBar(
                tr("Storage"), if (s != null) humanSize(s.storeUsed) else "", if (s != null) humanSize(s.storeTotal) else "",
                sf, s != null, NxMint,
            )
            UsageBar(
                tr("Memory"), if (s != null) humanSize(s.memUsed) else "", if (s != null) humanSize(s.memTotal) else "",
                mf, s != null, NxYellow,
            )
        }
    }
}

@Composable
private fun InfoPill(modifier: Modifier, icon: ImageVector, label: String, value: String, ink: Color, pulse: Boolean, onTap: (() -> Unit)?) {
    Row(
        modifier.then(if (onTap != null) Modifier.nxPress(onTap) else Modifier).nxGlass(16.dp, pulse).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp).then(if (pulse) Modifier.nxPulse(0.3f, 600) else Modifier), tint = NxAccent)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(label, fontSize = 12.sp, color = NxDim, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> tr("Good night")
        hour < 12 -> tr("Good morning")
        hour < 18 -> tr("Good afternoon")
        else -> tr("Good evening")
    }
}

@Composable
private fun LiveBeacon(live: Boolean) {
    val tint = if (live) NxLive else NxLine
    Spacer(
        Modifier.size(22.dp).then(
            if (live) Modifier.nxRipple(tint) else Modifier.drawBehind { drawCircle(tint, size.minDimension * 0.15f) },
        ),
    )
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by Hub.count.collectAsState()
    var stats by remember { mutableStateOf<Stats?>(null) }
    var root by remember { mutableStateOf<Boolean?>(null) }
    var rootBusy by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf<String?>(null) }
    val profiles = remember { loadProfiles(ctx) }
    val live = Hub.vps

    LaunchedEffect(Unit) {
        while (true) {
            stats = try { withContext(Dispatchers.IO) { readStats(ctx) } } catch (_: Throwable) { null }
            delay(4000)
        }
    }

    fun checkRoot() {
        if (rootBusy) return
        rootBusy = true
        scope.launch {
            root = withContext(Dispatchers.IO) { RootFs.available(true) }
            rootBusy = false
        }
    }

    fun connect(p: Profile) {
        if (connecting != null) return
        connecting = p.name
        scope.launch {
            val err = openVps(p)
            connecting = null
            if (err != null) toast(ctx, tr("Cannot connect: {0}", err))
        }
    }

    val tiles = listOf(
        Triple(tr("Files"), NxIcon.Folder, NxAccent) to Route.Files,
        Triple(tr("AI Agent"), NxIcon.Bot, NxRed) to Route.Ai,
        Triple(tr("Terminal"), NxIcon.Terminal, NxBlue) to Route.Ssh,
        Triple(tr("Browser"), NxIcon.Globe, NxYellow) to Route.Web,
        Triple("GitHub", NxIcon.Git, NxPrimaryVariant) to Route.Files,
        Triple("Localhost", NxIcon.Monitor, NxOrange) to Route.Files,
        Triple(tr("Cashflow"), NxIcon.Wallet, NxRed) to Route.Cash,
        Triple(tr("Tasks"), NxIcon.Tasks, NxAccent) to Route.Tasks,
        Triple(tr("Mind"), NxIcon.Bolt, NxLive) to Route.Mind,
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(6.dp))
        HeroCard(stats, Modifier.nxEnter(0))
        Spacer(Modifier.height(30.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((r, row) in tiles.chunked(3).withIndex()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((c, entry) in row.withIndex()) {
                        val (look, route) = entry
                        NxGridTile(look.first, look.second, look.third, Modifier.weight(1f).nxEnter(1 + r * 3 + c)) { Nav.route = route }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HomeMusicCard(Modifier.nxEnter(10))

        val level = stats?.battery ?: -1
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().nxEnter(10), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoPill(
                Modifier.weight(1f), NxIcon.Bolt, tr("Battery"), if (level in 0..100) "$level%" else "--",
                if (level in 0..15) NxDanger else NxText, false, null,
            )
            InfoPill(
                Modifier.weight(1f), NxIcon.Shield, "Root",
                when {
                    rootBusy -> tr("Checking")
                    root == true -> tr("Granted")
                    root == false -> tr("Denied")
                    else -> tr("Tap to check")
                },
                when {
                    root == true -> NxLive
                    root == false -> NxDanger
                    else -> NxText
                },
                rootBusy,
            ) { checkRoot() }
        }

        NxSection("SSH")
        NxCard(Modifier.fillMaxWidth().nxEnter(11), hot = live != null, onTap = { Nav.route = Route.Ssh }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBeacon(live != null)
                Text(
                    if (live != null) tr("Connected") else tr("No active connection"),
                    Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.SemiBold,
                )
                Icon(NxIcon.Right, null, Modifier.size(18.dp).nxNudge(4.dp, 1400), tint = NxDim)
            }
            Text(
                if (live != null) "${live.profile.user}@${live.profile.host}:${live.profile.port}" else tr("Open SSH to connect to a server"),
                Modifier.padding(top = 6.dp),
                fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (profiles.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            for ((i, p) in profiles.take(3).withIndex()) {
                val busy = connecting == p.name
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).nxEnter(12 + i)
                        .nxPress { connect(p) }
                        .nxGlass(16.dp, busy)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NxIconBox(NxIcon.Server, box = 40.dp, tint = NxBlue)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (busy) tr("Connecting...") else "${p.user}@${p.host}",
                            fontFamily = FontPrefs.mono.family, fontSize = 11.sp,
                            color = if (busy) NxAccent else NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(NxIcon.Bolt, null, Modifier.size(18.dp).then(if (busy) Modifier.nxPulse(0.3f, 450) else Modifier), tint = if (busy) NxAccent else NxDim)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
