package com.uplink.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FlowScreen() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<FlowDef?>(null) }

    LaunchedEffect(Unit) {
        AiStore.load(ctx)
        FlowStore.load()
    }

    val flow = open
    if (flow == null) FlowList { open = it } else FlowEditor(flow) { open = null }
}

@Composable
private fun FlowList(onOpen: (FlowDef) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<FlowDef?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            tr("Chain several models like n8n: one reads, another writes code, then save the result."),
            color = NxDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("New workflow"), icon = NxIcon.Plus, tone = Tone.Accent, compact = true) { naming = true }
            NxButton(tr("Add example"), icon = NxIcon.Bolt, compact = true) {
                val sample = FlowStore.example()
                FlowStore.save(sample)
                onOpen(sample)
            }
        }
        NxSection(tr("YOUR WORKFLOWS"))
        if (FlowStore.flows.isEmpty()) {
            Text(tr("No workflows yet"), color = NxDim, modifier = Modifier.padding(bottom = 12.dp))
        }
        for ((i, f) in FlowStore.flows.toList().withIndex()) {
            NxListRow(
                f.name, NxIcon.Bolt, Modifier.padding(bottom = 10.dp).nxEnter(i), meta = tr("{0} steps", f.steps.size),
                trailing = { NxIconButton(NxIcon.Trash, tr("Delete"), NxDanger) { removing = f } },
            ) { onOpen(f) }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (naming) {
        NxAsk(tr("New workflow"), "", tr("Workflow name"), 1, onDismiss = { naming = false }) { text ->
            naming = false
            if (text.isNotEmpty()) {
                val made = FlowDef(FlowStore.newId(), text, listOf(FlowStep(FlowStore.newId(), FlowKind.Ask, tr("Step 1"))))
                FlowStore.save(made)
                onOpen(made)
            }
        }
    }
    removing?.let { target ->
        NxConfirm(tr("Delete {0}?", target.name), tr("Delete"), onDismiss = { removing = null }) {
            FlowStore.remove(target.id)
            removing = null
        }
    }
}

@Composable
private fun FlowEditor(start: FlowDef, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var name by remember { mutableStateOf(start.name) }
    val steps = remember { mutableStateListOf<FlowStep>().also { it.addAll(start.steps) } }
    var input by remember { mutableStateOf("") }
    var choosing by remember { mutableStateOf<Int?>(null) }

    BackHandler {
        FlowStore.save(FlowDef(start.id, name.ifBlank { start.name }, steps.toList()))
        onBack()
    }

    fun commit(): FlowDef {
        val next = FlowDef(start.id, name.ifBlank { start.name }, steps.toList())
        FlowStore.save(next)
        return next
    }

    fun swap(a: Int, b: Int) {
        if (a < 0 || b < 0 || a >= steps.size || b >= steps.size) return
        val keep = steps[a]
        steps[a] = steps[b]
        steps[b] = keep
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NxIconButton(NxIcon.Back, tr("Back")) { commit(); onBack() }
            Text(tr("Edit workflow"), Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        NxField(name, { name = it }, tr("Workflow name"))
        Text(
            tr("In prompts and paths use {{input}} for your input, {{previous}} for the last step, {{1}}, {{2}} for a numbered step."),
            color = NxDim, fontSize = 12.sp,
        )
        NxSection(tr("STEPS"))
        for ((i, step) in steps.toList().withIndex()) {
            NxCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontFamily = FontPrefs.mono.family, color = NxAccent, fontWeight = FontWeight.Bold)
                    Text(step.kind.title, Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.Bold)
                    NxIconButton(NxIcon.Up, tr("Move up"), NxDim) { swap(i, i - 1) }
                    NxIconButton(NxIcon.Down, tr("Move down"), NxDim) { swap(i, i + 1) }
                    NxIconButton(NxIcon.Trash, tr("Delete"), NxDanger) { steps.removeAt(i) }
                }
                NxField(step.title, { steps[i] = step.copy(title = it) }, tr("Step name"))
                when (step.kind) {
                    FlowKind.Ask -> {
                        val chosen = AiStore.profileOf(step.profileId)
                        val label = if (chosen == null) tr("Chat model (active)") else chosen.name + "  ·  " + step.model.ifBlank { chosen.model }
                        NxButton(label, icon = NxIcon.Bot, compact = true, modifier = Modifier.padding(bottom = 12.dp)) { choosing = i }
                        NxField(step.prompt, { steps[i] = step.copy(prompt = it) }, tr("Prompt"), lines = 5)
                    }
                    FlowKind.ReadFile -> NxField(step.path, { steps[i] = step.copy(path = it) }, tr("File to read (absolute path)"), mono = true)
                    FlowKind.SaveFile -> {
                        NxField(step.path, { steps[i] = step.copy(path = it) }, tr("File to write (absolute path)"), mono = true)
                        NxField(step.prompt, { steps[i] = step.copy(prompt = it) }, tr("Content (empty = previous step)"), lines = 2)
                        NxButton(
                            if (step.codeOnly) tr("Code block only: on") else tr("Code block only: off"),
                            icon = NxIcon.File, tone = if (step.codeOnly) Tone.Accent else Tone.Quiet, compact = true,
                        ) { steps[i] = step.copy(codeOnly = !step.codeOnly) }
                    }
                }
            }
        }
        NxFlow(Modifier.fillMaxWidth()) {
            for (kind in FlowKind.entries) {
                NxChip(kind.title, icon = NxIcon.Plus, hot = true, onTap = {
                    steps.add(FlowStep(FlowStore.newId(), kind, tr("Step {0}", steps.size + 1)))
                })
            }
        }

        NxSection(tr("RUN"))
        NxField(input, { input = it }, tr("Input"), lines = 4)
        val busy = FlowRun.busy
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) {
                NxButton(tr("Stop"), icon = NxIcon.Close, tone = Tone.Danger) { FlowRun.stop() }
            } else {
                NxButton(tr("Run"), icon = NxIcon.Play, tone = Tone.Accent) {
                    if (steps.isEmpty()) toast(ctx, tr("Add a step first")) else FlowRun.start(ctx.applicationContext, commit(), input)
                }
            }
            NxButton(tr("Save"), icon = NxIcon.Save) { commit(); toast(ctx, tr("Saved")) }
        }

        if (FlowRun.results.isNotEmpty()) {
            NxSection(tr("OUTPUT"))
            for ((i, row) in FlowRun.results.toList().withIndex()) {
                NxCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), hot = row.state == StepResult.RUNNING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontFamily = FontPrefs.mono.family, color = NxAccent, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(row.step.title.ifBlank { row.step.kind.title }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (row.model.isNotEmpty()) Text(row.model, fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            when (row.state) {
                                StepResult.RUNNING -> tr("RUNNING")
                                StepResult.DONE -> tr("DONE")
                                StepResult.FAILED -> tr("FAILED")
                                else -> tr("WAITING")
                            },
                            fontFamily = FontPrefs.mono.family, fontSize = 11.sp,
                            color = when (row.state) {
                                StepResult.DONE -> NxLive
                                StepResult.FAILED -> NxDanger
                                StepResult.RUNNING -> NxAccent
                                else -> NxDim
                            },
                        )
                    }
                    if (row.state == StepResult.RUNNING || row.thought.isNotEmpty()) {
                        AiThought(row.thought, if (row.state == StepResult.RUNNING) row.status else tr("Thinking"), row.state == StepResult.RUNNING, Modifier.padding(top = 10.dp))
                    }
                    if (row.text.isNotEmpty()) {
                        SelectionContainer {
                            Box(Modifier.padding(top = 10.dp)) {
                                NxMarkdown(row.text, if (row.state == StepResult.FAILED) NxDanger else NxText, 13.sp)
                            }
                        }
                    }
                }
            }
            val last = FlowRun.finalText()
            if (last.isNotEmpty()) {
                NxButton(tr("Copy final output"), icon = NxIcon.Copy, compact = true) {
                    clip.setText(AnnotatedString(last))
                    toast(ctx, tr("Copied"))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    choosing?.let { at ->
        val step = steps.getOrNull(at)
        if (step == null) {
            choosing = null
        } else {
            ModelPicker(
                selectedId = step.profileId, selectedModel = step.model, followActive = true,
                onPick = { p, m ->
                    steps[at] = if (p == null) step.copy(profileId = "", model = "") else step.copy(profileId = p.id, model = m)
                    choosing = null
                },
                onDismiss = { choosing = null },
            )
        }
    }
}
