package com.uplink.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashTrail {
    @Volatile
    private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        AppFolder.init(ctx)
        NoxoraFs.init(ctx)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(AppFolder.dir("crash"), "last.txt").writeText("$stamp  ${thread.name}\n$trace")
            } catch (_: Throwable) {
            }
            try {
                ErrorLog.crash(thread, error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
