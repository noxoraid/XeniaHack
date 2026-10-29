package com.uplink.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewTarget(val fs: FsBackend, val path: String, val size: Long, val kind: OpenKind, val readOnly: Boolean)

object FileOpen {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var busy = false

    fun start(ctx: Context, fs: FsBackend, path: String, size: Long, asText: Boolean = false) {
        if (!asText && path.endsWith(".apk", ignoreCase = true)) {
            ApkInstall.start(ctx, fs, path)
            return
        }
        if (busy) return
        busy = true
        val app = ctx.applicationContext
        scope.launch {
            try {
                val real = withContext(Dispatchers.IO) { classify(fs, path) }
                Nav.viewing = if (asText) {
                    ViewTarget(fs, path, size, OpenKind.Text, real != OpenKind.Text)
                } else {
                    ViewTarget(fs, path, size, real, false)
                }
            } catch (e: Throwable) {
                toast(app, tr("Cannot open: {0}", e.message ?: e))
            }
            busy = false
        }
    }

    private fun classify(fs: FsBackend, path: String): OpenKind {
        FileSniff.byName(path.substringAfterLast('/'))?.let { return it }
        return FileSniff.kindOf(FileSniff.head(fs, path))
    }
}
