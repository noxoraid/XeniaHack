package com.uplink.app

import androidx.compose.animation.core.spring
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Cfg {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nx", Context.MODE_PRIVATE)

    fun sendPath(ctx: Context): String = prefs(ctx).getString("send_path", "/root") ?: "/root"

    fun setSendPath(ctx: Context, value: String) {
        prefs(ctx).edit().putString("send_path", value).apply()
    }

    fun excludes(ctx: Context): String = prefs(ctx).getString("send_excludes", ".git, node_modules") ?: ""

    fun setExcludes(ctx: Context, value: String) {
        prefs(ctx).edit().putString("send_excludes", value).apply()
    }

    fun adblock(ctx: Context): Boolean = prefs(ctx).getBoolean("web_adblock", true)

    fun adsBuffer(ctx: Context): Boolean = prefs(ctx).getBoolean("web_ads_buffer", false)

    fun setAdsBuffer(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("web_ads_buffer", value).apply()
    }

    fun mediaBuffer(ctx: Context): Boolean = prefs(ctx).getBoolean("web_media_buffer", false)

    fun setMediaBuffer(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("web_media_buffer", value).apply()
    }

    fun oneTool(ctx: Context): Boolean = prefs(ctx).getBoolean("one_tool", true)

    fun setOneTool(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("one_tool", value).apply()
    }

    fun oneToolSaved(ctx: Context): Long = prefs(ctx).getLong("one_tool_saved", 0L)

    fun addOneToolSaved(ctx: Context, tokens: Long) {
        prefs(ctx).edit().putLong("one_tool_saved", oneToolSaved(ctx) + tokens).apply()
    }

    fun mind(ctx: Context): Boolean = prefs(ctx).getBoolean("mind_on", true)

    fun setMind(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("mind_on", value).apply()
    }

    fun mindBudget(ctx: Context): Int = prefs(ctx).getInt("mind_budget", MindSummary.DEFAULT_BUDGET).coerceIn(MindSummary.MIN_BUDGET, MindSummary.MAX_BUDGET)

    fun setMindBudget(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("mind_budget", value.coerceIn(MindSummary.MIN_BUDGET, MindSummary.MAX_BUDGET)).apply()
    }

    fun language(ctx: Context): String = prefs(ctx).getString("language", "system") ?: "system"

    fun setLanguage(ctx: Context, value: String) {
        prefs(ctx).edit().putString("language", value).apply()
    }

    fun setAdblock(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("web_adblock", value).apply()
    }

    fun desktop(ctx: Context): Boolean = prefs(ctx).getBoolean("web_desktop", false)

    fun setDesktop(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("web_desktop", value).apply()
    }
}

@Composable
private fun Group(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    NxSection(title, Modifier.nxEnter(index))
    Column(Modifier.fillMaxWidth().nxEnter(index).nxGlass(20.dp).padding(16.dp), content = content)
}

@Composable
internal fun NxSwitch(on: Boolean, onToggle: () -> Unit) {
    val x by animateDpAsState(if (on) 20.dp else 2.dp, spring(0.55f, 520f), label = "knob")
    Box(
        Modifier.width(46.dp).height(28.dp).clip(CircleShape)
            .background(if (on) NxAccent else NxLine)
            .border(1.dp, if (on) NxAccent else NxLine, CircleShape)
            .nxTap(14.dp, onToggle),
    ) {
        Box(
            Modifier.offset(x = x, y = 2.dp).size(22.dp).clip(CircleShape)
                .background(if (on) NxOnAccent else NxSurface),
        )
    }
}

@Composable
private fun PaletteCard(palette: NxPalette, picked: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val skin = palette.skinFor(NxMode.dark)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.nxTap(14.dp, onPick).background(skin.bg)
            .border(if (picked) 2.dp else 1.dp, if (picked) NxAccent else NxLine, shape)
            .padding(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(skin.surface).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp).background(skin.accent, CircleShape))
            Box(Modifier.padding(start = 6.dp).weight(1f).height(5.dp).clip(CircleShape).background(skin.line))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(palette.title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = skin.text, maxLines = 1)
            if (picked) Icon(NxIcon.Check, tr("Selected"), Modifier.size(16.dp), tint = skin.accent)
        }
    }
}

@Composable
private fun Line(icon: ImageVector, title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        NxIconBox(icon, box = 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(sub, fontSize = 12.sp, color = NxDim)
        }
        trailing()
    }
}

@Composable
fun SettingsScreen() {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        NxSegment(
            listOf(tr("Settings") to NxIcon.Gear, "Changelog" to NxIcon.History),
            tab,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) { tab = it }
        if (tab == 0) SettingsPane() else ChangelogPane()
    }
}

@Composable
private fun SettingsPane() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val wake by Hub.wake.collectAsState()
    var granted by remember { mutableStateOf(Access.granted(ctx)) }
    var root by remember { mutableStateOf<Boolean?>(null) }
    var rootBusy by remember { mutableStateOf(false) }
    var sendPath by remember { mutableStateOf(Cfg.sendPath(ctx)) }
    var skipList by remember { mutableStateOf(Cfg.excludes(ctx)) }
    var ignoring by remember { mutableStateOf(false) }
    var oneTool by remember { mutableStateOf(Cfg.oneTool(ctx)) }
    val oneToolSaved = remember { Cfg.oneToolSaved(ctx) }

    LaunchedEffect(Unit) {
        while (true) {
            granted = Access.granted(ctx)
            ignoring = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
            delay(800)
        }
    }

    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "4.0.0" } catch (_: Throwable) { "4.0.0" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        Group(tr("APPEARANCE"), 1) {
            if (NxPalette.values().size > 1) {
                Text(tr("Color theme"), Modifier.padding(start = 4.dp, bottom = 8.dp), fontSize = 12.sp, color = NxDim)
                for (pair in NxPalette.values().toList().chunked(2)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (p in pair) PaletteCard(p, p == NxMode.palette, Modifier.weight(1f)) { NxMode.pick(ctx, p) }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            Line(if (NxMode.dark) NxIcon.Moon else NxIcon.Sun, tr("Mode"), if (NxMode.dark) tr("Dark") else tr("Light")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NxButton(tr("Dark"), tone = if (NxMode.dark) Tone.Accent else Tone.Quiet, compact = true) { NxMode.set(ctx, true) }
                    NxButton(tr("Light"), tone = if (!NxMode.dark) Tone.Accent else Tone.Quiet, compact = true) { NxMode.set(ctx, false) }
                }
            }
            Text(tr("Interface font"), Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp), fontSize = 12.sp, color = NxDim)
            for (c in NxFonts.ui) FontRow(c, c.key == FontPrefs.ui.key) { FontPrefs.pickUi(ctx, c) }
            Text(tr("Terminal and code font"), Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp), fontSize = 12.sp, color = NxDim)
            for (c in NxFonts.mono) FontRow(c, c.key == FontPrefs.mono.key) { FontPrefs.pickMono(ctx, c) }
        }

        Group(tr("LANGUAGE"), 2) {
            LanguageRow(tr("System default"), Lang.choice == "system") { Lang.set(ctx, "system") }
            for (l in Lang.all) LanguageRow(l.label, Lang.choice == l.code) { Lang.set(ctx, l.code) }
        }

        Group(tr("ACCESS"), 3) {
            Line(NxIcon.Phone, tr("Internal storage"), if (granted) tr("Access granted") else tr("Access needed")) {
                if (!granted) NxButton(tr("Grant"), tone = Tone.Accent, compact = true) { Access.request(ctx) }
                else Icon(NxIcon.Check, null, Modifier.size(22.dp), tint = NxLive)
            }
            Line(
                NxIcon.Shield, "Root",
                when {
                    rootBusy -> tr("Checking...")
                    root == true -> tr("Root granted")
                    root == false -> tr("Root not available")
                    else -> tr("Not checked yet")
                },
            ) {
                NxButton(tr("Check"), compact = true) {
                    if (!rootBusy) {
                        rootBusy = true
                        scope.launch {
                            root = withContext(Dispatchers.IO) { RootFs.available(true) }
                            rootBusy = false
                        }
                    }
                }
            }
        }

        Group(tr("BACKGROUND"), 4) {
            Line(NxIcon.Bolt, tr("Keep CPU awake"), if (wake) tr("Wakelock held") else tr("Off")) {
                NxSwitch(wake) { ctx.startActivity(Intent(ctx, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Line(NxIcon.Pulse, tr("Battery optimization"), if (ignoring) tr("Unrestricted") else tr("Restricted")) {
                if (!ignoring) NxButton(tr("Allow"), tone = Tone.Accent, compact = true) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {
                    }
                } else Icon(NxIcon.Check, null, Modifier.size(22.dp), tint = NxLive)
            }
        }

        Group(tr("AI TOOLS"), 5) {
            Line(NxIcon.Bolt, tr("One Tool"), tr("{0} tokens saved so far", oneToolSaved)) {
                NxSwitch(oneTool) {
                    oneTool = !oneTool
                    Cfg.setOneTool(ctx, oneTool)
                }
            }
            Text(
                tr("One compact command (nx) for files, shell and the browser. Shorter AI instructions mean fewer tokens on every request."),
                fontSize = 12.sp, color = NxDim,
            )
        }

        Group(tr("MIND"), 6) {
            MindControls()
            NxButton(tr("Open Mind"), Modifier.padding(top = 10.dp), icon = NxIcon.Bolt, wide = true) { Nav.route = Route.Mind }
        }

        Group(tr("TRANSFER"), 6) {
            Text(tr("Default destination on server"), Modifier.padding(start = 4.dp, bottom = 8.dp), fontSize = 12.sp, color = NxDim)
            NxField(sendPath, { sendPath = it; Cfg.setSendPath(ctx, it) }, tr("Path"), mono = true)
            Text(
                tr("Used when sending files from Internal or Root storage to a VPS. The path is checked and fixed before every transfer."),
                fontSize = 12.sp, color = NxDim, modifier = Modifier.padding(bottom = 14.dp),
            )
            NxField(skipList, { skipList = it; Cfg.setExcludes(ctx, it) }, tr("Never send or upload"), mono = true, lines = 2)
            Text(
                tr("Names or patterns like *.log, matched at any depth. Applies to Send to VPS and Upload folder."),
                fontSize = 12.sp, color = NxDim,
            )
        }

        Group(tr("ABOUT"), 7) {
            Line(NxIcon.Info, "UntilyFlow", tr("Version {0}", version)) { }
            Text(ctx.packageName, Modifier.padding(start = 4.dp, top = 6.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
        }
        Box(Modifier.height(32.dp))
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().nxTap(10.dp, onPick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        if (selected) Icon(NxIcon.Check, tr("Selected"), Modifier.size(20.dp), tint = NxAccent)
    }
}
