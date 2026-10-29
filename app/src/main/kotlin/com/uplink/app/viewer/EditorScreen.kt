package com.uplink.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(path: String, load: () -> ByteArray, save: (ByteArray) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var tf by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(true) }
    val undo = remember { ArrayList<TextFieldValue>() }
    var lastPush by remember { mutableStateOf(0L) }

    BackHandler { onBack() }

    LaunchedEffect(path) {
        try {
            val data = withContext(Dispatchers.IO) { load() }
            tf = TextFieldValue(String(data, Charsets.UTF_8))
        } catch (e: Throwable) {
            toast(ctx, tr("Cannot open file: {0}", e.message ?: e))
        }
        loading = false
    }

    fun change(v: TextFieldValue) {
        val now = System.currentTimeMillis()
        if (v.text != tf.text && now - lastPush > 800) {
            undo.add(tf)
            if (undo.size > 50) undo.removeAt(0)
            lastPush = now
        }
        tf = v
    }

    Column(Modifier.fillMaxSize()) {
        NxBar(
            path.substringAfterLast('/'),
            subtitle = path.substringBeforeLast('/', "/"),
            nav = { NxIconButton(NxIcon.Back, tr("Back"), onTap = onBack) },
            actions = {
                NxButton(tr("Save"), icon = NxIcon.Save, tone = Tone.Accent, compact = true) {
                    val bytes = tf.text.toByteArray(Charsets.UTF_8)
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { save(bytes) }
                            toast(ctx, tr("Saved"))
                        } catch (e: Throwable) {
                            toast(ctx, tr("Cannot save: {0}", e.message ?: e))
                        }
                    }
                }
            },
        )
        NxFlow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            NxButton(tr("Paste"), icon = NxIcon.Paste, compact = true) {
                val pasted = clip.getText()?.text ?: return@NxButton
                val sel = tf.selection
                val text = tf.text.replaceRange(sel.min, sel.max, pasted)
                change(TextFieldValue(text, TextRange(sel.min + pasted.length)))
            }
            NxButton(tr("Copy all"), icon = NxIcon.Copy, compact = true) { clip.setText(AnnotatedString(tf.text)); toast(ctx, tr("Copied")) }
            NxButton(tr("Undo"), icon = NxIcon.Undo, compact = true) { if (undo.isNotEmpty()) tf = undo.removeAt(undo.size - 1) }
            NxButton(tr("Clear"), icon = NxIcon.Trash, tone = Tone.Danger, compact = true) { change(TextFieldValue("")) }
            val at = tf.selection.start.coerceIn(0, tf.text.length)
            var row = 1
            var lineStart = 0
            for (i in 0 until at) if (tf.text[i] == '\n') { row++; lineStart = i + 1 }
            Text(tr("Line {0}, column {1}", row, at - lineStart + 1), Modifier.padding(start = 4.dp, top = 8.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim, maxLines = 1)
        }
        val shape = RoundedCornerShape(16.dp)
        val code = TextStyle(fontFamily = FontPrefs.mono.family, fontSize = 13.sp, lineHeight = 19.sp, color = NxText)
        val lineCount = tf.text.count { it == '\n' } + 1
        val numbers = remember(lineCount) { (1..lineCount).joinToString("\n") }
        val vertical = rememberScrollState()
        val sideways = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (loading) {
                Text(tr("Opening file..."), Modifier.align(Alignment.Center), color = NxDim)
            } else {
                Row(Modifier.fillMaxSize().clip(shape).background(NxSurface).border(1.dp, NxLine, shape).verticalScroll(vertical)) {
                    Text(
                        numbers,
                        Modifier.background(NxRaised).padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        style = code.copy(color = NxDim),
                        textAlign = TextAlign.End,
                    )
                    Box(Modifier.weight(1f).horizontalScroll(sideways)) {
                        BasicTextField(
                            value = tf,
                            onValueChange = { change(it) },
                            textStyle = code,
                            cursorBrush = SolidColor(NxAccent),
                            keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Ascii),
                            modifier = Modifier.widthIn(min = 260.dp).heightIn(min = 320.dp).padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
