package com.uplink.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun blankProfile(): AiProfile = AiProfile(AiStore.newId(), "", AiApi.OpenAi, "", "", "", "", "", "")

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0 until 3) {
            val level by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 160),
                ),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).graphicsLayer { alpha = level }.clip(CircleShape).background(NxAccent))
        }
    }
}

@Composable
private fun AiText(text: String, ink: Color) {
    NxMarkdown(text, ink)
}

@Composable
private fun Bubble(turn: AiTurn, modifier: Modifier) {
    val pop = remember { Animatable(if (turn.settled) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!turn.settled) {
            pop.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            turn.settled = true
        }
    }
    val shape = if (turn.mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    val fill = when {
        turn.failed -> NxDanger.copy(alpha = 0.12f)
        turn.mine -> NxAccent
        else -> NxRaised
    }
    val ink = when {
        turn.failed -> NxDanger
        turn.mine -> NxOnAccent
        else -> NxText
    }
    Row(
        modifier.fillMaxWidth().graphicsLayer {
            alpha = pop.value.coerceIn(0f, 1f)
            translationY = (1f - pop.value) * 24f
        }.padding(start = if (turn.mine) 40.dp else 0.dp, end = if (turn.mine) 0.dp else 40.dp),
        horizontalArrangement = if (turn.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.weight(1f, fill = false).clip(shape).background(fill).padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (!turn.mine && turn.text.isEmpty()) {
                TypingDots()
                AiThought(turn.thought, turn.status, true)
            } else {
                if (!turn.mine && turn.thought.isNotEmpty()) AiThought(turn.thought, tr("Thinking"), false, Modifier.padding(bottom = 8.dp))
                SelectionContainer { AiText(turn.text, ink) }
            }
        }
    }
}

@Composable
private fun AiEmpty(icon: ImageVector, title: String, body: String, action: String?, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.nxEnter(0), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(40.dp).nxPulse(0.4f, 1400), tint = NxAccent)
            Text(title, Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(body, Modifier.padding(top = 6.dp, bottom = 18.dp), color = NxDim, fontSize = 13.sp)
            if (action != null) NxButton(action, icon = NxIcon.Plus, tone = Tone.Accent, onTap = onAction)
        }
    }
}

@Composable
private fun ProviderEditor(initial: AiProfile, onDismiss: () -> Unit, onSave: (AiProfile) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.baseUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var forced by remember { mutableStateOf(if (initial.pinned) initial.model else "") }
    var system by remember { mutableStateOf(initial.system) }
    var headers by remember { mutableStateOf(initial.headers) }
    var body by remember { mutableStateOf(initial.body) }
    var advanced by remember { mutableStateOf(false) }
    var run by remember { mutableStateOf<TaskRun?>(null) }
    var found by remember { mutableStateOf<AiProbeResult?>(null) }
    val state = run?.state
    val working = state == TaskState.Running

    LaunchedEffect(state) {
        val result = found
        if (state == TaskState.Done && result != null) {
            val host = try { java.net.URL(result.baseUrl).host } catch (_: Exception) { result.api.label }
            onSave(
                AiProfile(
                    initial.id, name.trim().ifEmpty { host }, result.api, result.baseUrl, key.trim(), result.model,
                    system, headers, body, result.models, forced.isNotBlank(),
                ),
            )
        }
    }

    NxDialog(
        tr("AI provider"),
        { run?.stop = true; onDismiss() },
        if (working) tr("Detecting...") else tr("Connect"),
        {
            if (!working) {
                found = null
                run = TaskBoard.start(TaskKind.Detect, tr("Detect AI provider")) { job ->
                    found = AiProbe.detect(url, key, forced, job)
                }
            }
        },
    ) {
        NxField(name, { name = it }, tr("Name (optional)"))
        NxField(url, { url = it }, tr("API URL"), mono = true)
        NxField(key, { key = it }, tr("API key"), obscure = true, mono = true)
        Text(
            tr("Paste the API URL and key. The API type and the model are detected for you."),
            Modifier.padding(bottom = 12.dp), fontSize = 12.sp, color = NxDim,
        )
        if (working) {
            Text(tr("Detecting API and models..."), Modifier.padding(bottom = 12.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxAccent)
        }
        val failure = run?.error
        if (state == TaskState.Failed && failure != null) {
            Text(failure, Modifier.padding(bottom = 12.dp), fontSize = 13.sp, color = NxDanger)
        }
        if (!working && initial.models.isNotEmpty() && state == null) {
            Text(
                tr("Detected: {0} ({1} models)", initial.model, initial.models.size),
                Modifier.padding(bottom = 12.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, color = NxDim,
            )
        }
        NxButton(
            if (advanced) tr("Hide advanced") else tr("Advanced"),
            Modifier.padding(bottom = 14.dp), icon = NxIcon.Gear, compact = true,
        ) { advanced = !advanced }
        if (advanced) {
            NxField(forced, { forced = it }, tr("Model (leave empty to detect)"), mono = true)
            NxField(system, { system = it }, tr("System prompt"), lines = 3)
            NxField(headers, { headers = it }, tr("Extra headers (Name: value per line)"), mono = true, lines = 2)
            NxField(body, { body = it }, tr("Extra body (JSON)"), mono = true, lines = 3)
            Text(
                tr("Extra body is merged into the request, for example temperature or max_tokens."),
                Modifier.padding(bottom = 14.dp), fontSize = 12.sp, color = NxDim,
            )
        }
    }
}

@Composable
private fun ProviderList(onDismiss: () -> Unit, onAdd: () -> Unit, onEdit: (AiProfile) -> Unit) {
    val ctx = LocalContext.current
    var removing by remember { mutableStateOf<AiProfile?>(null) }
    NxDialog(tr("AI providers"), onDismiss, tr("Add provider"), onAdd) {
        if (AiStore.profiles.isEmpty()) {
            Text(tr("No providers yet"), Modifier.padding(bottom = 14.dp), color = NxDim)
        }
        for (p in AiStore.profiles) {
            val on = p.id == AiStore.active?.id
            NxListRow(
                p.name, if (on) NxIcon.Check else NxIcon.Bot, Modifier.padding(bottom = 8.dp), meta = p.model,
                trailing = {
                    NxIconButton(NxIcon.Edit, tr("Edit")) { onEdit(p) }
                    NxIconButton(NxIcon.Trash, tr("Remove"), NxDanger) { removing = p }
                },
            ) { AiStore.pick(ctx, p.id); onDismiss() }
        }
    }
    val target = removing
    if (target != null) {
        NxConfirm(tr("Remove {0}?", target.name), tr("Remove"), { removing = null }) {
            AiStore.remove(ctx, target.id)
            removing = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiScreen() {
    val ctx = LocalContext.current
    var listing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<AiProfile?>(null) }
    var draft by remember { mutableStateOf("") }
    val profile = AiStore.active
    val turns = AiChat.turns
    val busy = AiChat.busy

    LaunchedEffect(Unit) {
        AiStore.load(ctx)
        AiChat.load()
    }

    Column(Modifier.fillMaxSize()) {
        NxSegment(
            listOf(tr("Chat") to NxIcon.Bot, tr("Workflows") to NxIcon.Bolt), mode,
            Modifier.fillMaxWidth().padding(start = 18.dp, top = 4.dp, end = 18.dp), height = 40.dp,
        ) { mode = it }
        if (mode == 1) {
            Box(Modifier.weight(1f).fillMaxWidth()) { FlowScreen() }
        } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, top = 8.dp, end = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier.weight(1f).nxPress { picking = true }.clip(shape).background(NxRaised)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(8.dp).then(if (busy) Modifier.nxPulse(0.3f, 600) else Modifier).clip(CircleShape)
                        .background(if (busy) NxAccent else if (profile != null) NxLive else NxLine),
                )
                Text(
                    if (profile != null) "${profile.name}  ·  ${profile.model}" else tr("No provider"),
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                    fontFamily = FontPrefs.mono.family, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(NxIcon.Down, null, Modifier.size(18.dp), tint = NxDim)
            }
            NxIconButton(NxIcon.Gear, tr("AI providers")) { listing = true }
            NxIconButton(NxIcon.Trash, tr("Clear chat")) { AiChat.clear() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                profile == null -> AiEmpty(
                    NxIcon.Bot, tr("Add an AI provider"),
                    tr("Paste an API URL and key. The model is detected for you."),
                    tr("Add provider"),
                ) { editing = blankProfile() }
                turns.isEmpty() -> AiEmpty(NxIcon.Bot, tr("Ask anything"), tr("Replies stream in live."), null) { }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(turns.asReversed(), key = { it.id }) { Bubble(it, Modifier.animateItemPlacement(tween(220))) }
                }
            }
        }

        if (profile != null) {
            NxComposer(
                draft, { draft = it }, tr("Message"), busy,
                onSend = {
                    if (draft.isNotBlank()) {
                        AiChat.ask(draft)
                        draft = ""
                    }
                },
                onStop = { AiChat.stop() },
            )
        }
        }
    }

    if (picking) {
        ModelPicker(
            selectedId = AiStore.active?.id.orEmpty(), selectedModel = AiStore.active?.model.orEmpty(), followActive = false,
            onPick = { p, m ->
                if (p != null) AiStore.choose(ctx, p.id, m)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    if (listing) {
        ProviderList(
            onDismiss = { listing = false },
            onAdd = { listing = false; editing = blankProfile() },
            onEdit = { listing = false; editing = it },
        )
    }
    val target = editing
    if (target != null) {
        ProviderEditor(target, onDismiss = { editing = null }) {
            AiStore.save(ctx, it)
            editing = null
        }
    }
}
