package com.uplink.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class CashKind { Income, Expense }

class CashEntry(val id: Long, val kind: CashKind, val amount: Long, val category: String, val note: String, val at: Long)

class Bucket(val label: String, val income: Long, val expense: Long)

class Slice(val name: String, val amount: Long)

class Digest(val income: Long, val expense: Long, val buckets: List<Bucket>, val slices: List<Slice>, val rows: List<CashEntry>)

enum class Span(private val key: String) {
    Week("7 days"),
    Month("This month"),
    Year("This year"),
    All("All time");

    val label: String get() = tr(key)
}

private const val DAY_MS = 24L * 60 * 60 * 1000

fun rupiah(value: Long): String {
    val body = NumberFormat.getIntegerInstance(Locale("id", "ID")).format(Math.abs(value))
    return (if (value < 0) "-Rp " else "Rp ") + body
}

private fun startOf(cal: Calendar, ms: Long, monthly: Boolean): Long {
    cal.timeInMillis = ms
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (monthly) cal.set(Calendar.DAY_OF_MONTH, 1)
    return cal.timeInMillis
}

fun dayStart(ms: Long): Long = startOf(Calendar.getInstance(), ms, false)

fun digest(all: List<CashEntry>, span: Span, now: Long = System.currentTimeMillis()): Digest {
    val cal = Calendar.getInstance()
    val monthly = span == Span.Year || span == Span.All
    val first = when (span) {
        Span.Week -> {
            startOf(cal, now, false)
            cal.add(Calendar.DAY_OF_YEAR, -6)
            cal.timeInMillis
        }
        Span.Month -> startOf(cal, now, true)
        Span.Year -> {
            startOf(cal, now, true)
            cal.set(Calendar.MONTH, 0)
            cal.timeInMillis
        }
        Span.All -> {
            startOf(cal, now, true)
            cal.add(Calendar.MONTH, -11)
            cal.timeInMillis
        }
    }
    val lower = if (span == Span.All) Long.MIN_VALUE else first
    val upper = dayStart(now) + DAY_MS - 1
    val rows = all.filter { it.at in lower..upper }.sortedByDescending { it.at }

    val keys = ArrayList<Long>()
    var t = first
    while (t <= now) {
        keys.add(t)
        cal.timeInMillis = t
        cal.add(if (monthly) Calendar.MONTH else Calendar.DAY_OF_YEAR, 1)
        t = cal.timeInMillis
    }
    val slot = HashMap<Long, Int>()
    for ((i, k) in keys.withIndex()) slot[k] = i
    val inc = LongArray(keys.size)
    val exp = LongArray(keys.size)
    var income = 0L
    var expense = 0L
    val byCategory = HashMap<String, Long>()
    for (e in rows) {
        val at = slot[startOf(cal, e.at, monthly)]
        if (e.kind == CashKind.Income) {
            income += e.amount
            if (at != null) inc[at] += e.amount
        } else {
            expense += e.amount
            if (at != null) exp[at] += e.amount
            byCategory[e.category] = (byCategory[e.category] ?: 0L) + e.amount
        }
    }
    val label = SimpleDateFormat(if (monthly) "MMM" else "d", Lang.locale())
    val buckets = keys.indices.map { Bucket(label.format(Date(keys[it])), inc[it], exp[it]) }
    val ranked = byCategory.entries.sortedByDescending { it.value }.map { Slice(it.key, it.value) }
    val slices = if (ranked.size <= 5) ranked else ranked.take(5) + Slice(tr("Other"), ranked.drop(5).sumOf { it.amount })
    return Digest(income, expense, buckets, slices, rows)
}

object CashBook {
    val entries = mutableStateListOf<CashEntry>()
    var loaded by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)

    private val disk = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val writer = CoroutineScope(SupervisorJob() + disk)
    private var counter = 0L

    private fun file(): File = File(AppFolder.dir("flow"), "entries.json")

    suspend fun load() {
        if (loaded) return
        val found = withContext(Dispatchers.IO) {
            val f = file()
            if (!f.isFile) return@withContext emptyList<CashEntry>()
            try {
                val arr = JSONArray(f.readText())
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    CashEntry(
                        o.getLong("id"), CashKind.valueOf(o.getString("kind")), o.getLong("amount"),
                        o.optString("category"), o.optString("note"), o.getLong("at"),
                    )
                }
            } catch (_: Throwable) {
                f.renameTo(File(f.parentFile, "entries.damaged.json"))
                failure = tr("Entries file was unreadable and was moved to entries.damaged.json")
                emptyList<CashEntry>()
            }
        }
        entries.clear()
        entries.addAll(found)
        loaded = true
    }

    private fun nextId(): Long = System.currentTimeMillis() * 1000 + (counter++ % 1000)

    fun add(kind: CashKind, amount: Long, category: String, note: String, at: Long) {
        entries.add(CashEntry(nextId(), kind, amount, category, note, at))
        persist()
    }

    fun replace(old: CashEntry, kind: CashKind, amount: Long, category: String, note: String, at: Long) {
        val i = entries.indexOfFirst { it.id == old.id }
        if (i < 0) return
        entries[i] = CashEntry(old.id, kind, amount, category, note, at)
        persist()
    }

    fun remove(entry: CashEntry) {
        entries.removeAll { it.id == entry.id }
        persist()
    }

    private fun persist() {
        val snap = entries.toList()
        writer.launch {
            try {
                val arr = JSONArray()
                for (e in snap) {
                    arr.put(
                        JSONObject().put("id", e.id).put("kind", e.kind.name).put("amount", e.amount)
                            .put("category", e.category).put("note", e.note).put("at", e.at),
                    )
                }
                AppFolder.writeAtomic(file(), arr.toString())
                failure = null
            } catch (t: Throwable) {
                failure = tr("Cannot save: {0}", t.message ?: t)
            }
        }
    }

    fun exportCsv(): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val out = File(AppFolder.dir("flow"), "export-$stamp.csv")
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val text = StringBuilder("date,type,category,note,amount\n")
        for (e in entries.toList().sortedBy { it.at }) {
            text.append(day.format(Date(e.at))).append(',').append(e.kind.name).append(',')
                .append(cell(e.category)).append(',').append(cell(e.note)).append(',').append(e.amount).append('\n')
        }
        out.writeText(text.toString())
        return out
    }
}
