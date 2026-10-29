package com.uplink.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

object ExitReport {
    private const val TRACE_LIMIT = 24_000

    fun scan(ctx: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val exits = ctx.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null, 0, 12)
            val mark = ErrorLog.exitMark()
            val expected = expectedExit(ctx)
            var newest = mark
            for (info in exits.sortedBy { it.timestamp }) {
                if (info.timestamp <= mark) continue
                if (info.timestamp > newest) newest = info.timestamp
                record(info, expected)
            }
            if (newest > mark) ErrorLog.setExitMark(newest)
        } catch (_: Throwable) {
        }
    }

    fun expectExit(ctx: Context) {
        ctx.getSharedPreferences("nx_exit", Context.MODE_PRIVATE).edit().putLong("expected", System.currentTimeMillis()).commit()
    }

    private fun expectedExit(ctx: Context): Long = ctx.getSharedPreferences("nx_exit", Context.MODE_PRIVATE).getLong("expected", 0L)

    @RequiresApi(30)
    private fun record(info: ApplicationExitInfo, expected: Long) {
        if (expected > 0 && info.timestamp >= expected - 3_000L && info.timestamp <= expected + 60_000L) return
        val quiet = info.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        val verdict = classify(info, quiet) ?: return
        if (info.reason == ApplicationExitInfo.REASON_CRASH && ErrorLog.crashNear(info.timestamp)) return
        val detail = "reason ${info.reason}  importance ${info.importance}  status ${info.status}\n" +
            "rss ${info.rss shr 10} MB  pss ${info.pss shr 10} MB\n" +
            "description: ${info.description.orEmpty()}\n\n" + traceOf(info)
        ErrorLog.exit(verdict.first, verdict.second, detail, info.timestamp)
    }

    @RequiresApi(30)
    private fun classify(info: ApplicationExitInfo, quiet: Boolean): Pair<ErrorLevel, String>? = when (info.reason) {
        ApplicationExitInfo.REASON_CRASH -> ErrorLevel.ForceCrash to "Java crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ErrorLevel.ForceCrash to "Native crash"
        ApplicationExitInfo.REASON_ANR -> ErrorLevel.ForceCrash to "App not responding"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> ErrorLevel.ForceCrash to "Failed to start"
        ApplicationExitInfo.REASON_LOW_MEMORY -> if (quiet) null else ErrorLevel.ForceCrash to "Killed: low memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> if (quiet) null else ErrorLevel.ForceCrash to "Killed: too many resources"
        ApplicationExitInfo.REASON_SIGNALED -> signaled(info, quiet)
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> if (quiet) null else ErrorLevel.High to "Closed: a dependency died"
        ApplicationExitInfo.REASON_UNKNOWN -> if (quiet) null else ErrorLevel.Low to "Closed for an unknown reason"
        else -> null
    }

    @RequiresApi(30)
    private fun signaled(info: ApplicationExitInfo, quiet: Boolean): Pair<ErrorLevel, String>? {
        val why = info.description.orEmpty().lowercase()
        if (why.contains("lmk") || why.contains("oom") || why.contains("memory")) {
            return if (quiet) null else ErrorLevel.ForceCrash to "Killed: low memory"
        }
        if (info.status == 9 || quiet) return null
        return ErrorLevel.High to "Killed by a signal"
    }

    @RequiresApi(30)
    private fun traceOf(info: ApplicationExitInfo): String {
        if (info.reason != ApplicationExitInfo.REASON_ANR) return ""
        return try {
            val stream = info.traceInputStream ?: return ""
            stream.use {
                val buffer = ByteArray(TRACE_LIMIT)
                var filled = 0
                while (filled < buffer.size) {
                    val got = it.read(buffer, filled, buffer.size - filled)
                    if (got < 0) break
                    filled += got
                }
                String(buffer, 0, filled, Charsets.UTF_8)
            }
        } catch (_: Throwable) {
            ""
        }
    }
}
