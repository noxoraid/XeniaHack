package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun StageBar(plan: StagePlan) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf(false) }
    val stages = plan.stages
    val multi = stages.size > 1
    Column(Modifier.fillMaxWidth().background(NxSurface).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NxButton(tr("Single"), icon = NxIcon.Bot, tone = if (!multi) Tone.Accent else Tone.Quiet, compact = true) {
                if (multi) {
                    val keep = stages[0]
                    stages.clear()
                    stages.add(StageSpec(keep.profileId, keep.model, StageRole.Whole))
                    plan.save(ctx)
                }
            }
            NxButton(tr("Multi"), icon = NxIcon.Grid, tone = if (multi) Tone.Accent else Tone.Quiet, compact = true) {
                if (!multi) {
                    val first = stages.firstOrNull() ?: StageSpec("", "", StageRole.Whole)
                    stages.clear()
                    stages.add(StageSpec(first.profileId, first.model, StageRole.Structure))
                    stages.add(StageSpec("", "", StageRole.Code))
                    plan.save(ctx)
                }
            }
            if (multi && stages.size < 4) {
                NxButton(tr("Add model"), icon = NxIcon.Plus, compact = true) {
                    stages.add(StageSpec("", "", StageRole.Review))
                    plan.save(ctx)
                }
            }
        }
        NxFlow(Modifier.fillMaxWidth()) {
            for ((i, spec) in stages.withIndex()) {
                val label = plan.resolve(spec)?.model ?: tr("No provider")
                val lead = if (multi) "${i + 1}  " else ""
                val tail = if (multi) "  ·  " + spec.role.title else ""
                NxButton(lead + label + tail, icon = NxIcon.Bot, tone = Tone.Accent, compact = true) { editing = i }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(NxLine))
    val at = editing
    if (at != null && at < stages.size) {
        val spec = stages[at]
        NxDialog(if (multi) tr("Model {0}", at + 1) else tr("Model"), { editing = null }, tr("Done"), { editing = null }) {
            Text(tr("Model"), color = NxDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            NxButton(plan.resolve(spec)?.let { it.name + " · " + it.model } ?: tr("Choose model"), icon = NxIcon.Bot, wide = true) { picking = true }
            if (multi) {
                Text(tr("Role"), color = NxDim, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (role in StageRole.entries) {
                        NxButton(
                            role.title, tone = if (role == spec.role) Tone.Accent else Tone.Quiet, wide = true,
                        ) {
                            stages[at] = StageSpec(spec.profileId, spec.model, role)
                            plan.save(ctx)
                        }
                    }
                }
                if (stages.size > 2) {
                    Spacer(Modifier.size(12.dp))
                    NxButton(tr("Remove this model"), icon = NxIcon.Trash, tone = Tone.Danger, wide = true) {
                        stages.removeAt(at)
                        plan.save(ctx)
                        editing = null
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
        }
        if (picking) {
            ModelPicker(
                selectedId = spec.profileId,
                selectedModel = spec.model,
                followActive = true,
                onPick = { profile, model ->
                    stages[at] = StageSpec(profile?.id.orEmpty(), if (profile == null) "" else model, spec.role)
                    plan.save(ctx)
                    picking = false
                },
                onDismiss = { picking = false },
            )
        }
    }
}

@Composable
fun TermPrompt(busy: Boolean, hint: String, onSend: (String) -> Unit, onStop: () -> Unit, onAttach: (() -> Unit)? = null) {
    var draft by remember { mutableStateOf("") }
    NxComposer(
        draft, { draft = it }, hint, busy,
        onSend = {
            if (draft.isNotBlank()) {
                onSend(draft)
                draft = ""
            }
        },
        onStop = onStop,
        modifier = Modifier.background(NxBg),
        onAttach = onAttach,
    )
}

@Composable
fun TermGate(onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(NxIcon.Bot, null, Modifier.size(36.dp), tint = NxDim)
        Text(tr("Add an AI provider first"), Modifier.padding(top = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(tr("Set up your API key and models in AI Agent, then pick them here."), Modifier.padding(top = 6.dp, bottom = 20.dp), color = NxDim, fontSize = 13.sp)
        NxButton(tr("Open AI Agent"), icon = NxIcon.Bot, tone = Tone.Accent, onTap = onOpen)
    }
}

@Composable
fun ApprovalCards() {
    for (ask in ApprovalBoard.pending.toList()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).nxGlass(16.dp, true).padding(14.dp)) {
            Text(tr("AI asks permission"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(ask.reason, Modifier.padding(top = 2.dp), color = NxDim, fontSize = 12.sp)
            Text(
                ask.command, Modifier.padding(top = 8.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp,
                color = NxAccent, maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NxButton(tr("Allow"), icon = NxIcon.Check, tone = Tone.Accent, compact = true) { ApprovalBoard.decide(ask.id, true) }
                NxButton(tr("Deny"), icon = NxIcon.Close, tone = Tone.Danger, compact = true) { ApprovalBoard.decide(ask.id, false) }
            }
        }
    }
}
