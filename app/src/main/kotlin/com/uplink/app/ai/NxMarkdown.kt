package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class MdKind { Para, Code, Heading, Bullet, Quote }

private class MdBlock(val kind: MdKind, val text: String, val tag: String = "")

private val bulletMark = "^\\s*([-*•]|\\d+[.)])\\s+(.*)$".toRegex()
private val headingMark = "^#{1,4}\\s+(.*)$".toRegex()
private val inlineMark = "(\\*\\*[^*\\n]+\\*\\*|`[^`\\n]+`)".toRegex()

private fun blocksOf(text: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out.add(MdBlock(MdKind.Para, para.toString().trim()))
        para.setLength(0)
    }
    var code: StringBuilder? = null
    var lang = ""
    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            val open = code
            if (open == null) {
                flush()
                code = StringBuilder()
                lang = trimmed.removePrefix("```").trim()
            } else {
                out.add(MdBlock(MdKind.Code, open.toString().trimEnd('\n'), lang))
                code = null
            }
            continue
        }
        val open = code
        if (open != null) {
            open.append(line).append('\n')
            continue
        }
        val head = headingMark.find(trimmed)
        val bullet = bulletMark.find(line)
        when {
            trimmed.isEmpty() -> flush()
            head != null -> {
                flush()
                out.add(MdBlock(MdKind.Heading, head.groupValues[1]))
            }
            bullet != null -> {
                flush()
                out.add(MdBlock(MdKind.Bullet, bullet.groupValues[2], if (bullet.groupValues[1][0].isDigit()) bullet.groupValues[1] else "•"))
            }
            trimmed.startsWith(">") -> {
                flush()
                out.add(MdBlock(MdKind.Quote, trimmed.removePrefix(">").trim()))
            }
            else -> para.append(line).append('\n')
        }
    }
    code?.let { out.add(MdBlock(MdKind.Code, it.toString().trimEnd('\n'), lang)) }
    flush()
    return out
}

private fun inline(text: String, accent: Color, chip: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in inlineMark.findAll(text)) {
        append(text.substring(at, m.range.first))
        val body = m.value
        if (body.startsWith("**")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(body.substring(2, body.length - 2)) }
        } else {
            withStyle(SpanStyle(fontFamily = FontPrefs.mono.family, color = accent, background = chip)) { append(body.substring(1, body.length - 1)) }
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

@Composable
fun NxMarkdown(raw: String, ink: Color = NxText, size: TextUnit = 15.sp) {
    val ctx = LocalContext.current
    val blocks = remember(raw) { blocksOf(AiClean.clean(raw)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b.kind) {
                MdKind.Para -> Text(inline(b.text, NxAccent, NxRaised), color = ink, fontSize = size, lineHeight = size * 1.4f)
                MdKind.Heading -> Text(inline(b.text, NxAccent, NxRaised), color = ink, fontSize = size * 1.1f, fontWeight = FontWeight.Bold)
                MdKind.Quote -> Text(
                    inline(b.text, NxAccent, NxRaised), Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(NxRaised).padding(10.dp),
                    color = NxDim, fontSize = size,
                )
                MdKind.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Text(b.tag, Modifier.padding(end = 8.dp), color = NxAccent, fontSize = size, fontWeight = FontWeight.Bold)
                    Text(inline(b.text, NxAccent, NxRaised), Modifier.weight(1f), color = ink, fontSize = size, lineHeight = size * 1.4f)
                }
                MdKind.Code -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NxBg)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.tag.ifEmpty { tr("Code") }, Modifier.weight(1f), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                        NxIconButton(NxIcon.Copy, tr("Copy"), NxDim) { copyOutput(ctx, b.text) }
                    }
                    Text(
                        b.text, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxText, softWrap = false,
                    )
                }
            }
        }
    }
}
