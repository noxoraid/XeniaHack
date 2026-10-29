package com.uplink.app

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object DevTools {
    private const val SOURCE = "https://cdn.jsdelivr.net/npm/eruda@3.0.1/eruda.js"
    private const val MIN_BYTES = 100_000L
    private const val MAX_BYTES = 4_000_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun script(): File = File(AppFolder.dir("browser"), "eruda.js")

    fun ready(): Boolean {
        val f = script()
        return f.isFile && f.length() > MIN_BYTES
    }

    fun fetch(): TaskRun = TaskBoard.start(TaskKind.Download, tr("DevTools engine"), true) { run ->
        val dest = script()
        val temp = File(dest.parentFile, dest.name + ".part")
        try {
            val conn = URL(SOURCE).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            try {
                run.totalBytes = conn.contentLengthLong.coerceAtLeast(0L)
                conn.inputStream.use { input ->
                    temp.outputStream().use { out -> input.copyTo(TrackedOut(out, run), 65536) }
                }
            } finally {
                conn.disconnect()
            }
            if (temp.length() < MIN_BYTES) throw IOException(tr("Incomplete download"))
            dest.delete()
            if (!temp.renameTo(dest)) throw IOException(tr("Cannot save engine"))
        } finally {
            temp.delete()
        }
    }

    fun attach(view: WebView) {
        scope.launch {
            val code = withContext(Dispatchers.IO) {
                script().takeIf { it.isFile && it.length() in MIN_BYTES..MAX_BYTES }?.readText()
            } ?: return@launch
            view.evaluateJavascript(
                "(function(){if(window.eruda&&window.eruda._isInit){eruda.show();return}" + code +
                    "\n;eruda.init({defaults:{displaySize:55,transparency:0.95}});eruda.show()})()",
                null,
            )
        }
    }

    fun detach(view: WebView) {
        view.evaluateJavascript("if(window.eruda){eruda.destroy()}", null)
    }
}
