package com.uplink.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object AgentAttach {
    val kinds = arrayOf("image/*", "video/*", "application/zip", "application/x-zip-compressed", "application/octet-stream")

    private fun nameOf(ctx: Context, uri: Uri): String {
        val shown = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        val raw = shown ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        return raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { "file" }.take(120)
    }

    private fun free(source: FsBackend, folder: String, name: String): String {
        var path = fixPath(name, folder)
        if (source.kind(path) == 0) return path
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            path = fixPath("$stem ($n)$ext", folder)
            if (source.kind(path) == 0) return path
            n++
        }
    }

    fun save(ctx: Context, agent: TermAgent, uris: List<Uri>) {
        val app = ctx.applicationContext
        val target = agent.target
        val folder = target.cwd
        val source = target.source
        TaskBoard.start(TaskKind.Upload, tr("Attach files"), true) { run ->
            run.total = uris.size
            for (uri in uris) {
                if (run.stop) throw TaskStopped()
                val name = nameOf(app, uri)
                run.detail = name
                TaskBoard.publish()
                val dest = free(source, folder, name)
                val input = app.contentResolver.openInputStream(uri) ?: throw java.io.IOException(tr("Cannot read {0}", name))
                input.use { src ->
                    source.create(dest).use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            if (run.stop) throw TaskStopped()
                            val n = src.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            run.bytes += n
                            TaskBoard.publish()
                        }
                    }
                }
                synchronized(agent.attached) { agent.attached.add(dest) }
                run.done++
            }
        }
    }
}
