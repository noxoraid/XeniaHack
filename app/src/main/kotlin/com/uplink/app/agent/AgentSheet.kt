package com.uplink.app

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun AttachLine(agent: TermAgent) {
    val names = agent.attached.toList()
    if (names.isEmpty()) return
    Text(
        tr("Attached: {0}", names.joinToString(", ") { it.substringAfterLast('/') }),
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), fontSize = 12.sp, color = NxDim, maxLines = 2,
    )
}

class PhoneAi(val fs: FsBackend, app: Context, val base: String) {
    val shell = PhoneShell(fs, app, base)
    val agent = TermAgent(
        PhoneAgentTarget(fs, app, base, shell), StagePlan.phone,
        termChatFile("phone-" + fs.label + "-" + base),
    )

    companion object {
        private val open = HashMap<String, PhoneAi>()

        fun of(fs: FsBackend, app: Context, base: String): PhoneAi =
            open.getOrPut(fs.label + "|" + base) { PhoneAi(fs, app.applicationContext, base) }
    }
}

private sealed interface Block

private class UserBlock(val entry: TermEntry) : Block

private class ReplyBlock(val steps: List<TermEntry>, val tail: List<TermEntry>, val live: TermEntry?) : Block

private class SheetReq(val steps: List<TermEntry>, val first: TermEntry?)

private fun TermEntry.isStep(): Boolean =
    kind == EntryKind.Stage || kind == EntryKind.Thought || kind == EntryKind.Command || kind == EntryKind.Files

private fun blocksOf(entries: List<TermEntry>, hideStage: Boolean): List<Block> {
    val out = ArrayList<Block>()
    var body = ArrayList<TermEntry>()
    fun flush() {
        if (body.isEmpty()) return
        val live = body.lastOrNull { it.kind == EntryKind.Live }
        val rest = body.filter { it.kind != EntryKind.Live && !(hideStage && it.kind == EntryKind.Stage) }
        val cut = rest.indexOfLast { it.isStep() }
        out.add(ReplyBlock(rest.subList(0, cut + 1).toList(), rest.subList(cut + 1, rest.size).toList(), live))
        body = ArrayList()
    }
    for (e in entries) {
        if (e.kind == EntryKind.Prompt) {
            flush()
            out.add(UserBlock(e))
        } else {
            body.add(e)
        }
    }
    flush()
    return out
}

private fun tokensOf(line: String): List<String> =
    try { consoleTokens(line) } catch (_: Throwable) { line.trim().split(Regex("\\s+")) }

private fun shortName(path: String): String {
    val clean = path.trim().trimEnd('/')
    return if (clean.isEmpty() || clean == ".") tr("this folder") else clean.substringAfterLast('/').ifEmpty { clean }
}

private fun stepLook(e: TermEntry): Pair<String, ImageVector> {
    when (e.kind) {
        EntryKind.Files -> return tr("Write {0}", shortName(e.text)) to NxIcon.FilePlus
        EntryKind.Thought -> return tr("Thinking") to NxIcon.Bot
        EntryKind.Stage -> return e.text to NxIcon.Bot
        else -> {}
    }
    val tokens = tokensOf(e.text)
    val cmd = tokens.firstOrNull().orEmpty()
    val args = tokens.drop(1).filter { !it.startsWith("-") }
    val last = args.lastOrNull().orEmpty()
    return when (cmd) {
        "ls" -> tr("List {0}", shortName(last)) to NxIcon.Folder
        "cat", "head", "tail" -> tr("Read {0}", shortName(last)) to NxIcon.File
        "grep" -> tr("Search for {0}", args.firstOrNull().orEmpty().take(28)) to NxIcon.Search
        "find" -> tr("Find files") to NxIcon.Search
        "tree" -> tr("View folder tree") to NxIcon.Grid
        "cd" -> tr("Go to {0}", shortName(last)) to NxIcon.Folder
        "pwd" -> tr("Current folder") to NxIcon.Folder
        "wc", "stat", "du" -> tr("Inspect {0}", shortName(last)) to NxIcon.Info
        "mkdir" -> tr("Create folder {0}", shortName(last)) to NxIcon.FolderPlus
        "touch" -> tr("Create {0}", shortName(last)) to NxIcon.FilePlus
        "cp" -> tr("Copy {0}", shortName(args.firstOrNull().orEmpty())) to NxIcon.Copy
        "mv" -> tr("Move {0}", shortName(args.firstOrNull().orEmpty())) to NxIcon.Rename
        "rm" -> tr("Delete {0}", shortName(last)) to NxIcon.Trash
        "zip" -> tr("Zip {0}", shortName(last)) to NxIcon.Archive
        "unzip" -> tr("Unzip {0}", shortName(last)) to NxIcon.Unarchive
        "play", "show" -> tr("Show {0}", shortName(last)) to NxIcon.Image
        else -> tr("Run {0}", tokens.take(2).joinToString(" ").take(36)) to NxIcon.Terminal
    }
}

private fun stepSub(e: TermEntry): String = when (e.kind) {
    EntryKind.Command -> e.text
    EntryKind.Files -> e.text + if (e.output.isNotEmpty()) "  ·  " + e.output else ""
    EntryKind.Thought -> e.text.trim().replace('\n', ' ')
    else -> ""
}

private fun openPath(e: TermEntry, cwd: String): String? {
    val raw = when (e.kind) {
        EntryKind.Files -> e.text
        EntryKind.Command -> {
            val tokens = tokensOf(e.text)
            if (tokens.firstOrNull() !in listOf("cat", "head", "tail")) return null
            tokens.drop(1).filter { !it.startsWith("-") }.lastOrNull().orEmpty()
        }
        else -> return null
    }
    if (raw.isBlank()) return null
    return fixPath(raw, cwd)
}

private fun openFile(ctx: Context, fs: FsBackend, path: String) {
    CoroutineScope(Dispatchers.IO).launch {
        val size = try {
            fs.list(parentOf(path)).firstOrNull { it.name == path.substringAfterLast('/') && !it.dir }?.size ?: 0L
        } catch (_: Throwable) {
            0L
        }
        withContext(Dispatchers.Main) { FileOpen.start(ctx, fs, path, size) }
    }
}

@Composable
private fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 320f)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * rise.value))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.88f)
                    .graphicsLayer { translationY = (1f - rise.value) * size.height }
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(NxSurface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(NxLine))
                content()
            }
        }
    }
}

@Composable
private fun CodeBox(text: String, ink: Color) {
    SelectionContainer {
        Text(
            text,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NxBg)
                .border(1.dp, NxLine, RoundedCornerShape(16.dp)).padding(16.dp),
            fontFamily = FontPrefs.mono.family, fontSize = 13.sp, color = ink,
        )
    }
}

@Composable
private fun StepDetail(e: TermEntry, openable: String?, onOpen: (String) -> Unit) {
    val failed = e.state == AgentStep.Failed
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
        when (e.kind) {
            EntryKind.Thought -> {
                Text(tr("Thinking"), color = NxDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                CodeBox(e.text, NxDim)
            }
            EntryKind.Files -> {
                Text(tr("Request"), color = NxDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                CodeBox("+ " + e.text, NxAccent)
                Text(tr("File content"), Modifier.padding(top = 18.dp, bottom = 8.dp), color = NxDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                CodeBox(e.body.ifEmpty { tr("(no output)") }, NxText)
                if (failed) {
                    Text(tr("Result"), Modifier.padding(top = 18.dp, bottom = 8.dp), color = NxDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    CodeBox(e.output, NxDanger)
                }
            }
            else -> {
                Text(tr("Request"), color = NxDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                CodeBox("$ " + e.text, NxAccent)
                Text(tr("Result"), Modifier.padding(top = 18.dp, bottom = 8.dp), color = NxDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                CodeBox(
                    e.output.ifEmpty { if (e.state == AgentStep.Running) tr("Running") else tr("(no output)") },
                    if (failed) NxDanger else NxText,
                )
            }
        }
        if (openable != null) {
            Spacer(Modifier.height(18.dp))
            NxButton(tr("Open file"), icon = NxIcon.File, tone = Tone.Accent, wide = true) { onOpen(openable) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TimelineNode(e: TermEntry, onStep: (TermEntry) -> Unit) {
    val step = e.kind == EntryKind.Command || e.kind == EntryKind.Files
    val ink = when (e.state) {
        AgentStep.Failed -> NxDanger
        AgentStep.Done -> NxLive
        else -> NxAccent
    }
    val look = if (step) stepLook(e) else null
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .then(if (step) Modifier.nxTap(12.dp) { onStep(e) } else Modifier)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier.width(40.dp).fillMaxHeight().drawBehind {
                drawLine(NxLine, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2.dp.toPx())
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            if (step && look != null) {
                Box(
                    Modifier.padding(top = 4.dp).size(32.dp).clip(RoundedCornerShape(10.dp)).background(NxBg)
                        .border(1.dp, NxLine, RoundedCornerShape(10.dp))
                        .then(if (e.state == AgentStep.Running) Modifier.nxPulse(0.4f, 600) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Icon(look.second, null, Modifier.size(17.dp), tint = ink) }
            } else {
                Box(Modifier.padding(top = 14.dp).size(10.dp).clip(CircleShape).background(NxLine))
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, top = if (step) 8.dp else 6.dp, bottom = 14.dp)) {
            if (step && look != null) {
                Text(look.first, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (e.state == AgentStep.Failed) NxDanger else NxText)
                if (e.state == AgentStep.Failed) {
                    Text(tr("Failed"), Modifier.padding(top = 2.dp), fontSize = 12.sp, color = NxDanger)
                }
            } else {
                Text(
                    stepSub(e).ifEmpty { e.text }.trim().replace('\n', ' ').take(200),
                    fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 23.sp, color = NxDim,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActivitySheet(
    req: SheetReq, cwd: String, canOpen: Boolean,
    onOpen: (String) -> Unit, onDismiss: () -> Unit,
) {
    var detail by remember { mutableStateOf(req.first) }
    val deep = req.first == null
    SheetFrame(onDismiss) {
        val current = detail
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NxIconButton(if (current != null && deep) NxIcon.Back else NxIcon.Close, tr("Back")) {
                if (current != null && deep) detail = null else onDismiss()
            }
            Text(
                if (current != null) stepLook(current).first else tr("Summary"),
                Modifier.weight(1f).padding(end = 44.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (current != null) {
            StepDetail(current, if (canOpen) openPath(current, cwd) else null, onOpen)
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp)) {
                for (e in req.steps) TimelineNode(e) { detail = it }
            }
        }
    }
}

@Composable
private fun StepRow(e: TermEntry, onTap: () -> Unit) {
    val look = stepLook(e)
    val ink = when (e.state) {
        AgentStep.Failed -> NxDanger
        AgentStep.Done -> NxLive
        else -> NxAccent
    }
    Row(
        Modifier.fillMaxWidth().nxTap(14.dp, onTap).padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(ink.copy(alpha = 0.10f))
                .then(if (e.state == AgentStep.Running) Modifier.nxPulse(0.4f, 600) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(look.second, null, Modifier.size(15.dp), tint = ink) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(look.first, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = stepSub(e)
            if (sub.isNotEmpty()) {
                Text(sub, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(NxIcon.Right, null, Modifier.size(16.dp), tint = NxDim)
    }
}

@Composable
private fun ActivityCard(steps: List<TermEntry>, running: Boolean, onSummary: () -> Unit, onStep: (TermEntry) -> Unit) {
    val real = steps.filter { it.kind == EntryKind.Command || it.kind == EntryKind.Files }
    val shown = real.takeLast(3)
    Column(Modifier.fillMaxWidth().nxGlass(18.dp, running).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().nxTap(12.dp, onSummary).padding(horizontal = 6.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (real.isEmpty()) NxIcon.Bot else NxIcon.Terminal, null,
                Modifier.size(18.dp).then(if (running) Modifier.nxPulse(0.3f, 600) else Modifier), tint = NxAccent,
            )
            Text(
                if (real.isEmpty()) tr("Thinking") else tr("Summary") + "  ·  " + tr("{0} steps", real.size),
                Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Icon(NxIcon.Right, null, Modifier.size(16.dp), tint = NxDim)
        }
        if (real.size > shown.size) {
            Text(tr("{0} earlier steps", real.size - shown.size), Modifier.padding(start = 12.dp, bottom = 2.dp), fontSize = 11.sp, color = NxDim)
        }
        for (e in shown) StepRow(e) { onStep(e) }
    }
}

@Composable
private fun LiveRow(e: TermEntry) {
    val clock by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(NxIcon.Bot, null, Modifier.size(16.dp).nxPulse(0.3f, 600), tint = NxAccent)
            Text(
                e.status.ifEmpty { tr("Thinking") } + "  ·  " + ((clock - e.startedAt) / 1000) + "s" +
                    if (e.received > 0) "  ·  " + e.received + " " + tr("chars") else "",
                Modifier.weight(1f).padding(start = 8.dp),
                fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (e.thought.isNotEmpty()) {
            Text(
                e.thought.takeLast(260).trim(), Modifier.padding(top = 6.dp),
                fontFamily = FontFamily.Serif, fontSize = 13.sp, color = NxDim, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        if (e.text.isNotEmpty()) Box(Modifier.padding(top = 6.dp)) { NxMarkdown(e.text, NxText, 14.sp) }
        if (e.output.isNotEmpty()) {
            Text(
                e.output.takeLast(500), Modifier.padding(top = 6.dp),
                fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxAccent, maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReplyView(block: ReplyBlock, running: Boolean, onSheet: (SheetReq) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (block.steps.isNotEmpty()) {
            ActivityCard(
                block.steps, running && block.tail.isEmpty(),
                { onSheet(SheetReq(block.steps, null)) }, { onSheet(SheetReq(block.steps, it)) },
            )
        }
        block.live?.let { LiveRow(it) }
        for (e in block.tail) {
            when (e.kind) {
                EntryKind.Say -> Box(Modifier.fillMaxWidth().nxGlass(18.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    NxMarkdown(e.text, NxText, 14.sp)
                }
                EntryKind.Done -> Row(Modifier.fillMaxWidth().nxGlass(14.dp, true).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NxIcon.Check, null, Modifier.size(18.dp), tint = NxLive)
                    Text(e.text, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
                }
                EntryKind.Failed -> Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NxDanger.copy(alpha = 0.08f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(NxIcon.Info, null, Modifier.size(16.dp), tint = NxDanger)
                    Text(e.text, Modifier.padding(start = 8.dp), color = NxDanger, fontSize = 13.sp)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun UserView(e: TermEntry) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), horizontalArrangement = Arrangement.End) {
        val shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        Text(
            e.text,
            Modifier.widthIn(max = 300.dp).clip(shape).background(NxAccent.copy(alpha = 0.14f))
                .border(1.dp, NxAccent.copy(alpha = 0.3f), shape).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AgentEmpty(profile: AiProfile, hints: List<String>, onHint: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NxIconBox(NxIcon.Bot, Modifier.nxFloat(5.dp, 2400), box = 64.dp)
        Text(tr("Tell the AI what to do with your files"), Modifier.padding(top = 18.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            tr("The AI runs commands on this storage and every step stays visible. Deleting asks you first."),
            Modifier.padding(top = 6.dp), color = NxDim, fontSize = 13.sp,
        )
        for ((i, hint) in hints.withIndex()) {
            NxChip(hint, Modifier.padding(top = if (i == 0) 18.dp else 8.dp), icon = NxIcon.Bolt, hot = true, onTap = { onHint(hint) })
        }
        Text("${profile.name}  ·  ${profile.model}", Modifier.padding(top = 22.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
    }
}

@Composable
fun AgentChat(
    agent: TermAgent,
    plan: StagePlan,
    fs: FsBackend?,
    hints: List<String>,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit,
) {
    val ctx = LocalContext.current
    val profile = AiStore.active
    val state = rememberLazyListState()
    val seen = rememberNxSeen(agent)
    val entries = agent.entries
    var sheet by remember { mutableStateOf<SheetReq?>(null) }

    LaunchedEffect(Unit) {
        AiStore.load(ctx)
        plan.load(ctx)
        agent.restore()
        agent.focus()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) AgentAttach.save(ctx, agent, uris)
    }

    val blocks = blocksOf(entries.toList(), plan.stages.size <= 1)
    val last = entries.lastOrNull()
    val tick = (last?.text?.length ?: 0) + (last?.output?.length ?: 0) + (last?.thought?.length ?: 0)
    LaunchedEffect(entries.size, tick) {
        if (blocks.isEmpty()) return@LaunchedEffect
        val visible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (visible < 0 || visible >= blocks.size - 2) {
            state.scrollToItem(blocks.size - 1)
            state.scrollBy(1_000_000f)
        }
    }

    Column(modifier) {
        if (profile != null) StageBar(plan)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                profile == null -> TermGate { onLeave(); Nav.route = Route.Ai }
                blocks.isEmpty() -> AgentEmpty(profile, hints) { agent.send(ctx, it) }
                else -> LazyColumn(Modifier.fillMaxSize(), state = state) {
                    items(blocks.size, key = { it }) { index ->
                        Box(Modifier.nxRow(seen, index, index)) {
                            when (val b = blocks[index]) {
                                is UserBlock -> UserView(b.entry)
                                is ReplyBlock -> ReplyView(b, agent.busy && index == blocks.size - 1) { sheet = it }
                            }
                        }
                    }
                }
            }
        }
        if (profile != null) {
            ApprovalCards()
            AttachLine(agent)
            TermPrompt(agent.busy, tr("Tell the AI what to do..."), { agent.send(ctx, it) }, { agent.stop() }, { picker.launch(AgentAttach.kinds) })
        }
    }

    sheet?.let { req ->
        ActivitySheet(
            req, agent.target.cwd, fs != null,
            onOpen = { path ->
                sheet = null
                if (fs != null) {
                    onLeave()
                    openFile(ctx, fs, path)
                }
            },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
fun AgentSheet(agent: TermAgent, plan: StagePlan, dir: String, fs: FsBackend?, onDismiss: () -> Unit) {
    remember(dir) {
        if (agent.openedDir != dir) {
            agent.openedDir = dir
            agent.target.enter(dir)
        }
    }
    var cwd by remember { mutableStateOf(agent.target.cwd) }
    LaunchedEffect(agent.entries.size, agent.busy) { cwd = agent.target.cwd }
    val hints = listOf(
        tr("Create a file named alok in storage"),
        tr("Find where login is used"),
        tr("Show the photos in DCIM"),
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.statusBarColor = NxHead.toArgb()
            window?.navigationBarColor = NxBg.toArgb()
        }
        Column(Modifier.fillMaxSize().background(NxBg).nxSafe()) {
            NxBar(
                tr("File AI"),
                subtitle = cwd,
                nav = { NxIconButton(NxIcon.Back, tr("Back"), onTap = onDismiss) },
                actions = { NxIconButton(NxIcon.Trash, tr("Clear chat")) { agent.clear() } },
            )
            AgentChat(agent, plan, fs, hints, Modifier.weight(1f).fillMaxWidth(), onDismiss)
        }
    }
}
