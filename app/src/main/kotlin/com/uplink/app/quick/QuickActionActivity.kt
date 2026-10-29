package com.uplink.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.load(this)
        NxMode.load(this)
        FontPrefs.load(this)
        setContent {
            NxTheme {
                QuickActionPanel(onDismiss = { finish() })
            }
        }
    }
}

@Composable
fun QuickActionPanel(onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    val swallow = remember { MutableInteractionSource() }
    val sheetShape = if (full) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Box(
        Modifier.fillMaxSize().background(Color(0x22000000)).clickable(onClick = onDismiss).nxSafe(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (full) 1f else 0.82f)
                .clip(sheetShape)
                .background(NxBg)
                .clickable(interactionSource = swallow, indication = null) {}
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NxFlow(Modifier.weight(1f)) {
                NxButton(tr("Translate"), icon = NxIcon.Globe, tone = if (tab == 0) Tone.Accent else Tone.Quiet, compact = true) { tab = 0 }
                NxButton(tr("Device"), icon = NxIcon.Phone, tone = if (tab == 1) Tone.Accent else Tone.Quiet, compact = true) { tab = 1 }
                NxButton(tr("Tools"), icon = NxIcon.Bolt, tone = if (tab == 2) Tone.Accent else Tone.Quiet, compact = true) { tab = 2 }
                NxButton(tr("Calculator"), icon = NxIcon.Grid, tone = if (tab == 3) Tone.Accent else Tone.Quiet, compact = true) { tab = 3 }
                }
                NxIconButton(if (full) NxIcon.Down else NxIcon.Up, if (full) tr("Restore") else tr("Full screen")) { full = !full }
                NxIconButton(NxIcon.Close, tr("Close"), onTap = onDismiss)
            }
            when (tab) {
                0 -> TranslatePane()
                1 -> DevicePane()
                2 -> ToolsPane()
                else -> QuickCalculatorPane()
            }
        }
    }
}

private val languageChoices = listOf(
    "auto" to "Auto",
    "id" to "Indonesian",
    "en" to "English",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "ar" to "Arabic",
    "ms" to "Malay",
    "fr" to "French",
    "de" to "German",
    "es" to "Spanish",
    "ru" to "Russian",
)

@Composable
private fun TranslatePane() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("auto") }
    var target by remember { mutableStateOf("id") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    NxSection(tr("Translate"))
    NxCard(Modifier.fillMaxWidth()) {
        NxField(input, { input = it }, tr("Text"), lines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("Paste"), icon = NxIcon.Paste, compact = true) { input = clipText(ctx) }
            NxButton(tr("Clear"), icon = NxIcon.Close, compact = true) { input = ""; output = "" }
        }
        LanguageRow(tr("From"), source, withAuto = true) { source = it }
        LanguageRow(tr("To"), target, withAuto = false) { target = it }
        NxButton(
            if (busy) tr("Translating...") else tr("Translate"),
            icon = NxIcon.Send,
            tone = Tone.Accent,
            wide = true,
        ) {
            val text = input.trim()
            if (text.isEmpty()) {
                note = tr("Empty input")
            } else {
                busy = true
                note = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { TranslateChain.translate(text, source, target) }
                    }
                    busy = false
                    result.onSuccess {
                        output = it.text
                        note = tr("Translated by {0}", it.provider)
                    }.onFailure {
                        note = tr("Translation failed")
                    }
                }
            }
        }
    }
    if (output.isNotEmpty()) {
        NxCard(Modifier.fillMaxWidth()) {
            Text(output, color = NxText)
            NxButton(tr("Copy"), icon = NxIcon.Copy, compact = true) {
                copyText(ctx, output)
                Toast.makeText(ctx, tr("Copied"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (note.isNotEmpty()) Text(note, color = NxDim)
}

@Composable
private fun LanguageRow(label: String, selected: String, withAuto: Boolean, onPick: (String) -> Unit) {
    val choices = if (withAuto) languageChoices else languageChoices.drop(1)
    Text(label, color = NxDim)
    NxFlow(Modifier) {
        choices.forEach { (code, key) ->
            NxButton(tr(key), tone = if (selected == code) Tone.Accent else Tone.Quiet, compact = true) { onPick(code) }
        }
    }
}

@Composable
private fun DevicePane() {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf(DeviceReport.rows(ctx)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        rows = DeviceReport.rows(ctx)
    }

    NxSection(tr("Device"))
    NxCard(Modifier.fillMaxWidth()) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = NxDim)
                Text(value, color = NxText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("Refresh"), icon = NxIcon.Refresh, compact = true) { rows = DeviceReport.rows(ctx) }
            NxButton(tr("Allow location and Wi-Fi name"), icon = NxIcon.Shield, compact = true) {
                askPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }
}

@Composable
private fun ToolsPane() {
    val ctx = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (torchOn) Torch.set(ctx, false) }
    }

    NxSection(tr("Tools"))
    NxCard(Modifier.fillMaxWidth()) {
        NxButton(
            if (torchOn) tr("Flashlight off") else tr("Flashlight on"),
            icon = NxIcon.Bolt,
            tone = if (torchOn) Tone.Accent else Tone.Quiet,
            wide = true,
        ) {
            val next = !torchOn
            if (Torch.set(ctx, next)) {
                torchOn = next
            } else {
                Toast.makeText(ctx, tr("Flashlight not available"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun clipText(ctx: Context): String {
    val clip = ctx.getSystemService(ClipboardManager::class.java)
    return clip?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
}

private fun copyText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("UntilyFlow", text))
}
