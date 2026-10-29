package com.uplink.app

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder

const val LOCALHOST_MARKER = ".localhost_task_"

private const val RELOAD_PATH = "/__nx/reload"
private const val INJECT_LIMIT = 8L * 1024 * 1024
private const val HEAD_LIMIT = 8192
private const val TEXT = "text/plain; charset=utf-8"
private val SKIP_NAMES = listOf(".git", "node_modules")
private val RELOAD_SCRIPT =
    "<script>(function(){var es=new EventSource('$RELOAD_PATH');" +
        "es.addEventListener('change',function(){location.reload()});})();</script>"

private val MIME = mapOf(
    "html" to "text/html; charset=utf-8",
    "htm" to "text/html; charset=utf-8",
    "css" to "text/css; charset=utf-8",
    "js" to "text/javascript; charset=utf-8",
    "mjs" to "text/javascript; charset=utf-8",
    "json" to "application/json; charset=utf-8",
    "txt" to TEXT,
    "md" to TEXT,
    "csv" to "text/csv; charset=utf-8",
    "xml" to "application/xml; charset=utf-8",
    "svg" to "image/svg+xml",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "avif" to "image/avif",
    "ico" to "image/x-icon",
    "mp4" to "video/mp4",
    "webm" to "video/webm",
    "mp3" to "audio/mpeg",
    "ogg" to "audio/ogg",
    "wav" to "audio/wav",
    "woff" to "font/woff",
    "woff2" to "font/woff2",
    "ttf" to "font/ttf",
    "pdf" to "application/pdf",
    "wasm" to "application/wasm",
)

class LocalhostServer(
    private val root: File,
    private val port: Int,
    private val autoReload: Boolean,
    private val run: TaskRun,
) {
    @Volatile private var stamp = 0L

    fun serve() {
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32)
                soTimeout = 500
            }
        } catch (_: BindException) {
            throw IllegalStateException(tr("Port {0} is busy", port))
        }
        val marker = File(root, LOCALHOST_MARKER + run.id)
        try {
            marker.writeText("http://localhost:$port\n")
            stamp = fingerprint()
            if (autoReload) Thread({ watch() }, "nx-watch").apply { isDaemon = true }.start()
            while (!run.stop) {
                val client = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                Thread({ handle(client) }, "nx-client").apply { isDaemon = true }.start()
            }
        } finally {
            server.close()
            marker.delete()
        }
    }

    private fun fingerprint(): Long = TreePrint.of(root.path, SKIP_NAMES, LOCALHOST_MARKER)

    private fun watch() {
        while (!run.stop) {
            try {
                stamp = fingerprint()
            } catch (_: Throwable) {
            }
            Thread.sleep(800)
        }
    }

    private fun handle(client: Socket) {
        try {
            client.use { sock ->
                sock.soTimeout = 15_000
                val out = sock.getOutputStream()
                val head = readHead(BufferedInputStream(sock.getInputStream())) ?: return
                val parts = head.lineSequence().firstOrNull()?.split(' ') ?: return
                if (parts.size < 2) return
                if (parts[0] != "GET" && parts[0] != "HEAD") {
                    reply(out, 405, TEXT, "Method not allowed".toByteArray())
                    return
                }
                run.done++
                TaskBoard.publish()
                val path = URLDecoder.decode(parts[1].substringBefore('?').replace("+", "%2B"), "UTF-8")
                if (path == RELOAD_PATH) {
                    events(sock, out)
                    return
                }
                serveFile(out, path)
            }
        } catch (_: Throwable) {
        }
    }

    private fun readHead(input: InputStream): String? {
        val buf = StringBuilder()
        while (buf.length < HEAD_LIMIT) {
            val c = input.read()
            if (c < 0) break
            buf.append(c.toChar())
            if (buf.endsWith("\r\n\r\n")) return buf.toString()
        }
        return if (buf.isEmpty()) null else buf.toString()
    }

    private fun serveFile(out: OutputStream, path: String) {
        val target = resolve(path)
        if (target == null) {
            reply(out, 403, TEXT, "Forbidden".toByteArray())
            return
        }
        if (target.isDirectory) {
            val index = File(target, "index.html")
            if (index.isFile) sendFile(out, index) else listing(out, target, path)
            return
        }
        if (!target.isFile || target.name.startsWith(LOCALHOST_MARKER)) {
            reply(out, 404, TEXT, "Not found".toByteArray())
            return
        }
        sendFile(out, target)
    }

    private fun sendFile(out: OutputStream, file: File) {
        val type = MIME[file.name.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"
        if (autoReload && type.startsWith("text/html") && file.length() <= INJECT_LIMIT) {
            reply(out, 200, type, inject(file.readText(Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
        } else {
            head(out, 200, type, file.length())
            file.inputStream().use { it.copyTo(out) }
        }
    }

    private fun listing(out: OutputStream, dir: File, path: String) {
        val base = if (path.endsWith("/")) path else "$path/"
        val rows = dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
        val items = rows.joinToString("") { f ->
            val suffix = if (f.isDirectory) "/" else ""
            "<li><a href=\"${escape(base + f.name + suffix)}\">${escape(f.name + suffix)}</a></li>"
        }
        val page = "<!doctype html><meta charset=\"utf-8\"><title>${escape(path)}</title>" +
            "<h3>${escape(path)}</h3><ul>$items</ul>"
        reply(out, 200, "text/html; charset=utf-8", (if (autoReload) inject(page) else page).toByteArray(Charsets.UTF_8))
    }

    private fun events(sock: Socket, out: OutputStream) {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-store\r\nConnection: keep-alive\r\n\r\n".toByteArray())
        out.flush()
        sock.soTimeout = 0
        var seen = stamp
        var idle = 0
        while (!run.stop) {
            Thread.sleep(400)
            val now = stamp
            if (now != seen) {
                seen = now
                out.write("event: change\ndata: $now\n\n".toByteArray())
            } else if (++idle >= 40) {
                idle = 0
                out.write(": ping\n\n".toByteArray())
            }
            out.flush()
        }
    }

    private fun resolve(path: String): File? {
        val base = root.canonicalFile
        val target = File(base, path.trimStart('/')).canonicalFile
        val inside = target.path == base.path || target.path.startsWith(base.path + File.separator)
        return if (inside) target else null
    }

    private fun inject(html: String): String {
        val at = html.lastIndexOf("</body>", ignoreCase = true)
        return if (at < 0) html + RELOAD_SCRIPT else html.substring(0, at) + RELOAD_SCRIPT + html.substring(at)
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun head(out: OutputStream, code: Int, type: String, length: Long) {
        val reason = when (code) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val text = "HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: $length\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(text.toByteArray())
    }

    private fun reply(out: OutputStream, code: Int, type: String, body: ByteArray) {
        head(out, code, type, body.size.toLong())
        out.write(body)
        out.flush()
    }
}
