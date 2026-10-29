package com.uplink.app

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

enum class ErrorLevel(private val key: String, private val hintKey: String) {
    Low("Low", "Expected and recoverable, such as a network or file problem. The app kept working."),
    Mid("Mid", "A job failed, but the app and its other features were not affected."),
    High("High", "A bug in the app, such as an empty value or a bad index. The feature broke, the app survived."),
    Fatal("Fatal", "The runtime itself failed, such as running out of memory or a missing native library."),
    ForceCrash("Force crash", "The app was closed: an uncaught crash, a native crash, a freeze (ANR) or a kill by the system.");

    val label: String get() = tr(key)
    val hint: String get() = tr(hintKey)
}

class ErrorText(val key: String, val arg: String = "") {
    fun text(): String = if (arg.isEmpty()) tr(key) else tr(key, arg)
}

data class ErrorEntry(
    val id: Long,
    val at: Long,
    val level: ErrorLevel,
    val source: ErrorText,
    val title: ErrorText,
    val detail: String,
    val thread: String,
    val seen: Boolean,
)

object ErrorLog {
    private const val CAP = 150
    private const val DETAIL_HEAD = 5000
    private const val DETAIL_TAIL = 3000
    private const val FILE_LIMIT = 6_000_000L
    private const val CRASH_SOURCE = "Crash on thread {0}"

    private val lock = Any()
    private val store = ArrayList<ErrorEntry>()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "error-log") }
    private val main = Handler(Looper.getMainLooper())
    private var loaded = false
    private var lastId = 0L
    private var exitMark = 0L

    var entries by mutableStateOf<List<ErrorEntry>>(emptyList())
        private set

    var launchAlert by mutableStateOf<ErrorEntry?>(null)

    val unseen: Int get() = entries.count { !it.seen }

    fun load() {
        val snapshot = synchronized(lock) {
            ensureLoaded()
            store.toList()
        }
        publish(snapshot)
    }

    fun benign(error: Throwable): Boolean {
        var cursor: Throwable? = error
        var depth = 0
        while (cursor != null && depth < 8) {
            val text = cursor.message.orEmpty().lowercase()
            val hit = cursor is kotlinx.coroutines.CancellationException || cursor is InterruptedException ||
                cursor is java.io.InterruptedIOException && cursor !is java.net.SocketTimeoutException ||
                cursor is java.nio.channels.ClosedByInterruptException || cursor is java.nio.channels.AsynchronousCloseException ||
                cursor is TaskStopped ||
                (cursor is java.net.SocketException && (text.contains("socket closed") || text.contains("socket is closed") ||
                    text.contains("software caused connection abort") || text.contains("broken pipe") || text.contains("connection reset")))
            if (hit) return true
            val next = cursor.cause
            cursor = if (next === cursor) null else next
            depth++
        }
        return false
    }

    fun report(source: ErrorText, error: Throwable, level: ErrorLevel = levelOf(error)) {
        try {
            if (benign(error)) return
            val thread = Thread.currentThread().name
            worker.execute {
                try {
                    val title = titleOf(error)
                    val detail = traceOf(error)
                    add(level, source, title, detail, thread, System.currentTimeMillis())
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun crash(thread: Thread, error: Throwable) {
        add(
            ErrorLevel.ForceCrash, ErrorText(CRASH_SOURCE, thread.name), titleOf(error),
            traceOf(error), thread.name, System.currentTimeMillis(),
        )
    }

    fun exit(level: ErrorLevel, titleKey: String, detail: String, at: Long) {
        add(level, ErrorText("Previous session ended"), ErrorText(titleKey), squeeze(detail), "", at)
    }

    fun crashNear(at: Long): Boolean = synchronized(lock) {
        ensureLoaded()
        store.any { it.level == ErrorLevel.ForceCrash && it.source.key == CRASH_SOURCE && Math.abs(it.at - at) < 20_000L }
    }

    fun exitMark(): Long = synchronized(lock) {
        ensureLoaded()
        exitMark
    }

    fun setExitMark(stamp: Long) {
        val snapshot = synchronized(lock) {
            ensureLoaded()
            exitMark = stamp
            save()
            store.toList()
        }
        publish(snapshot)
    }

    fun alertIfCrashed() {
        val newest = synchronized(lock) {
            ensureLoaded()
            store.firstOrNull { it.level == ErrorLevel.ForceCrash && !it.seen }
        }
        if (newest != null) main.post { launchAlert = newest }
    }

    fun dismissAlert() {
        launchAlert = null
        markSeen()
    }

    fun markSeen() {
        worker.execute {
            val snapshot: List<ErrorEntry>? = synchronized(lock) {
                ensureLoaded()
                if (store.none { !it.seen }) return@synchronized null
                for (i in store.indices) store[i] = store[i].copy(seen = true)
                save()
                store.toList()
            }
            if (snapshot != null) publish(snapshot)
        }
    }

    fun remove(entry: ErrorEntry) {
        worker.execute {
            val snapshot = synchronized(lock) {
                ensureLoaded()
                store.removeAll { it.id == entry.id }
                save()
                store.toList()
            }
            publish(snapshot)
        }
    }

    fun clear() {
        worker.execute {
            val snapshot = synchronized(lock) {
                ensureLoaded()
                store.clear()
                save()
                store.toList()
            }
            publish(snapshot)
        }
    }

    fun levelOf(error: Throwable): ErrorLevel {
        var worst = ErrorLevel.Low
        var cursor: Throwable? = error
        var depth = 0
        while (cursor != null && depth < 8) {
            val level = levelOfOne(cursor)
            if (level.ordinal > worst.ordinal) worst = level
            val next = cursor.cause
            cursor = if (next === cursor) null else next
            depth++
        }
        return worst
    }

    private fun levelOfOne(error: Throwable): ErrorLevel = when (error) {
        is Error -> ErrorLevel.Fatal
        is NumberFormatException -> ErrorLevel.Mid
        is NullPointerException, is IndexOutOfBoundsException, is ClassCastException, is IllegalStateException,
        is IllegalArgumentException, is ArithmeticException, is ConcurrentModificationException,
        is UnsupportedOperationException -> ErrorLevel.High
        is IOException, is TimeoutException -> ErrorLevel.Low
        else -> ErrorLevel.Mid
    }

    private fun add(level: ErrorLevel, source: ErrorText, title: ErrorText, detail: String, thread: String, at: Long) {
        val snapshot = synchronized(lock) {
            ensureLoaded()
            val twin = store.firstOrNull {
                it.level == level && it.source.key == source.key && it.source.arg == source.arg &&
                    it.title.key == title.key && it.title.arg == title.arg && it.detail.length == detail.length &&
                    kotlin.math.abs(it.at - at) < 60_000L
            }
            if (twin != null) return
            lastId = maxOf(at, lastId + 1)
            store.add(ErrorEntry(lastId, at, level, source, title, detail, thread, false))
            store.sortByDescending { it.at }
            while (store.size > CAP) store.removeAt(store.size - 1)
            save()
            store.toList()
        }
        publish(snapshot)
    }

    private fun publish(snapshot: List<ErrorEntry>) {
        main.post { entries = snapshot }
    }

    private fun titleOf(error: Throwable): ErrorText {
        val name = error.javaClass.simpleName.ifEmpty { error.javaClass.name }
        val message = error.message?.trim().orEmpty()
        return ErrorText("{0}", if (message.isEmpty()) name else name + ": " + message.take(300))
    }

    private fun traceOf(error: Throwable): String {
        val out = StringWriter()
        PrintWriter(out).use { error.printStackTrace(it) }
        return squeeze(contextLine() + "\n\n" + out)
    }

    private fun contextLine(): String {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) shr 20
        val max = runtime.maxMemory() shr 20
        val live = try {
            TaskBoard.runs.filter { it.state == TaskState.Running }.joinToString(" | ") { it.title }
        } catch (_: Throwable) {
            ""
        }
        val base = "android ${Build.VERSION.SDK_INT}  ${Build.MODEL}  heap $used/$max MB"
        return if (live.isEmpty()) base else base + "\nrunning: " + live
    }

    private fun squeeze(text: String): String {
        if (text.length <= DETAIL_HEAD + DETAIL_TAIL) return text
        return text.substring(0, DETAIL_HEAD) + "\n...\n" + text.substring(text.length - DETAIL_TAIL)
    }

    private fun file(): File = File(AppFolder.dir("errors"), "log.json")

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val source = file()
            if (source.isFile && source.length() < FILE_LIMIT) {
                val root = JSONObject(source.readText())
                exitMark = root.optLong("exitMark", 0L)
                val rows = root.optJSONArray("entries") ?: JSONArray()
                for (i in 0 until rows.length()) store.add(decode(rows.getJSONObject(i)))
                store.sortByDescending { it.at }
            }
        } catch (_: Throwable) {
        }
        lastId = store.maxOfOrNull { it.id } ?: 0L
    }

    private fun save() {
        try {
            val rows = JSONArray()
            for (entry in store) rows.put(encode(entry))
            AppFolder.writeAtomic(file(), JSONObject().put("exitMark", exitMark).put("entries", rows).toString())
        } catch (_: Throwable) {
        }
    }

    private fun encode(entry: ErrorEntry): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("at", entry.at)
        .put("level", entry.level.name)
        .put("sourceKey", entry.source.key)
        .put("sourceArg", entry.source.arg)
        .put("titleKey", entry.title.key)
        .put("titleArg", entry.title.arg)
        .put("detail", entry.detail)
        .put("thread", entry.thread)
        .put("seen", entry.seen)

    private fun decode(row: JSONObject): ErrorEntry = ErrorEntry(
        id = row.getLong("id"),
        at = row.getLong("at"),
        level = ErrorLevel.values().firstOrNull { it.name == row.optString("level") } ?: ErrorLevel.Mid,
        source = ErrorText(row.optString("sourceKey"), row.optString("sourceArg")),
        title = ErrorText(row.optString("titleKey"), row.optString("titleArg")),
        detail = row.optString("detail"),
        thread = row.optString("thread"),
        seen = row.optBoolean("seen", true),
    )
}
