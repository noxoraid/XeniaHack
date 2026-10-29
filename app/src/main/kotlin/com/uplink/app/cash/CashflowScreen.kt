package com.uplink.app

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun incomeTags() = listOf(tr("Salary"), tr("Business"), tr("Gift"), tr("Bonus"), tr("Other"))
private fun expenseTags() = listOf(tr("Food"), tr("Transport"), tr("Bills"), tr("Shopping"), tr("Health"), tr("Fun"), tr("Other"))
private const val NOON_MS = 12L * 60 * 60 * 1000

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().nxGlass(20.dp).padding(16.dp), content = content)
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 11.sp, color = NxDim, fontWeight = FontWeight.Bold)
}

@Composable
fun CashflowScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var span by remember { mutableStateOf(Span.Month) }
    var editing by remember { mutableStateOf<CashEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf<CashEntry?>(null) }
    val snapshot = CashBook.entries.toList()
    val view = remember(span, snapshot) { digest(snapshot, span) }
    val balance = snapshot.sumOf { if (it.kind == CashKind.Income) it.amount else -it.amount }
    val dayLabel = remember { SimpleDateFormat("EEE, d MMM yyyy", Lang.locale()) }
    val groups = remember(view) { view.rows.groupBy { dayStart(it.at) } }
    val seen = rememberNxSeen()

    LaunchedEffect(Unit) { CashBook.load() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NxSegment(Span.values().map { it.label to null }, span.ordinal, Modifier.fillMaxWidth()) { span = Span.values()[it] }
        }
        item {
            Panel {
                Caption(tr("BALANCE"))
                Text(
                    rupiah(balance), Modifier.padding(top = 6.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = if (balance < 0) NxDanger else NxText,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NxCard(Modifier.weight(1f)) {
                    NxIconBox(NxIcon.Down, box = 34.dp, tint = NxLive)
                    Caption(tr("INCOME  /  {0}", span.label.uppercase()), Modifier.padding(top = 10.dp))
                    Text(rupiah(view.income), Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold, color = NxLive, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NxCard(Modifier.weight(1f)) {
                    NxIconBox(NxIcon.Up, box = 34.dp, tint = NxDanger)
                    Caption(tr("EXPENSE"), Modifier.padding(top = 10.dp))
                    Text(rupiah(view.expense), Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold, color = NxDanger, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            NxFlow(Modifier.fillMaxWidth()) {
                NxButton(tr("Add"), icon = NxIcon.Plus, tone = Tone.Accent, compact = true) { adding = true }
                NxButton(tr("Export CSV"), icon = NxIcon.Download, compact = true) {
                    scope.launch {
                        try {
                            val out = withContext(Dispatchers.IO) { CashBook.exportCsv() }
                            toast(ctx, tr("Saved {0}", out.path))
                        } catch (t: Throwable) {
                            toast(ctx, tr("Cannot export: {0}", t.message ?: t))
                        }
                    }
                }
            }
        }
        CashBook.failure?.let { item { Text(it, color = NxDanger, fontSize = 12.sp) } }
        if (!Access.granted(ctx)) {
            item { Text(tr("Storage access is off, data is kept in the app folder instead."), color = NxDim, fontSize = 12.sp) }
        }
        item {
            Panel {
                Caption(tr("INCOME VS EXPENSE"))
                Spacer(Modifier.height(12.dp))
                BarsChart(view.buckets)
            }
        }
        if (view.slices.isNotEmpty()) {
            item {
                Panel {
                    Caption(tr("EXPENSE BY CATEGORY"))
                    Spacer(Modifier.height(12.dp))
                    CategoryDonut(view.slices)
                }
            }
        }
        item { Caption(tr("TRANSACTIONS"), Modifier.padding(top = 6.dp)) }
        if (view.rows.isEmpty()) {
            item { Text(tr("Nothing recorded in this period."), color = NxDim, fontSize = 13.sp) }
        }
        for ((day, list) in groups) {
            item(key = "day$day") { Caption(dayLabel.format(Date(day)).uppercase(), Modifier.padding(top = 6.dp)) }
            itemsIndexed(list, key = { _, row -> row.id }) { index, e ->
                val gain = e.kind == CashKind.Income
                Row(
                    Modifier.fillMaxWidth().nxRow(seen, e.id, index).nxGlass(16.dp)
                        .clickable { editing = e }.padding(start = 12.dp, top = 6.dp, end = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NxIconBox(if (gain) NxIcon.Down else NxIcon.Up, box = 36.dp, tint = if (gain) NxLive else NxDanger)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(e.category.ifBlank { tr("Uncategorized") }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.note.isNotBlank()) Text(e.note, fontSize = 12.sp, color = NxDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        (if (gain) "+" else "-") + rupiah(e.amount), fontFamily = FontPrefs.mono.family, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = if (gain) NxLive else NxDanger,
                    )
                    NxIconButton(NxIcon.Trash, tr("Delete"), NxDim) { dropping = e }
                }
            }
        }
    }

    if (adding) {
        EntryDialog(null, onDismiss = { adding = false }) { kind, amount, category, note, at ->
            adding = false
            CashBook.add(kind, amount, category, note, at)
        }
    }
    editing?.let { old ->
        EntryDialog(old, onDismiss = { editing = null }) { kind, amount, category, note, at ->
            editing = null
            CashBook.replace(old, kind, amount, category, note, at)
        }
    }
    dropping?.let { e ->
        NxConfirm(tr("Delete {0} {1}?", e.category.ifBlank { tr("this entry") }, rupiah(e.amount)), tr("Delete"), onDismiss = { dropping = null }) {
            dropping = null
            CashBook.remove(e)
        }
    }
}

@Composable
private fun EntryDialog(
    initial: CashEntry?,
    onDismiss: () -> Unit,
    onSave: (CashKind, Long, String, String, Long) -> Unit,
) {
    val format = remember { SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { isLenient = false } }
    var kind by remember { mutableStateOf(initial?.kind ?: CashKind.Expense) }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(format.format(Date(initial?.at ?: System.currentTimeMillis()))) }
    var problem by remember { mutableStateOf<String?>(null) }
    val tags = if (kind == CashKind.Income) incomeTags() else expenseTags()

    NxDialog(
        title = if (initial == null) tr("New entry") else tr("Edit entry"),
        onDismiss = onDismiss,
        confirm = tr("Save"),
        onConfirm = {
            val value = amount.toLongOrNull() ?: 0L
            val moment = try { format.parse(date.trim())?.time } catch (_: Throwable) { null }
            when {
                value <= 0L -> problem = tr("Enter an amount above zero")
                moment == null -> problem = tr("Date must look like 31-12-2026")
                else -> onSave(kind, value, category.trim(), note.trim(), moment + NOON_MS)
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxButton(tr("Income"), Modifier.weight(1f), tone = if (kind == CashKind.Income) Tone.Accent else Tone.Quiet, compact = true) { kind = CashKind.Income }
            NxButton(tr("Expense"), Modifier.weight(1f), tone = if (kind == CashKind.Expense) Tone.Accent else Tone.Quiet, compact = true) { kind = CashKind.Expense }
        }
        NxField(amount, { amount = it.filter(Char::isDigit).take(15) }, tr("Amount (Rp)"), mono = true)
        NxField(category, { category = it }, tr("Category"))
        NxFlow(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            for (t in tags) NxChip(t, hot = t == category, onTap = { category = t })
        }
        NxField(note, { note = it }, tr("Note (optional)"))
        NxField(date, { date = it }, tr("Date (dd-MM-yyyy)"), mono = true)
        problem?.let { Text(it, color = NxDanger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)) }
    }
}
