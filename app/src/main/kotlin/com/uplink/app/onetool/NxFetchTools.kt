package com.uplink.app

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

private class NxFetched(val key: String, val head: String, val text: String, val at: Long)

object NxFetchTools {
    val names = setOf("fetch")

    private const val CONNECT_MS = 10_000
    private const val READ_MS = 15_000
    private const val HOP_MS = 30_000L
    private const val BODY_CAP = 2_000_000
    private const val HOPS = 5
    private const val PAGE_LIMIT = 4000
    private const val KEEP_MS = 5 * 60_000L
    private const val ACCEPT = "text/html,application/xhtml+xml,application/json,text/plain;q=0.9,*/*;q=0.1"
    private val say: NxSay = { key, args -> tr(key, *args) }

    private var last: NxFetched? = null

    @Synchronized
    private fun cachedFor(key: String): NxFetched? =
        last?.takeIf { it.key == key && System.currentTimeMillis() - it.at < KEEP_MS }

    @Synchronized
    private fun remember(page: NxFetched) {
        last = page
    }

    fun run(app: Context, rest: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val words = consoleTokens(rest)
        if (words.isEmpty()) throw NxFetchError(tr("Missing argument"))
        if (words.size > 2) throw NxFetchError(tr("too many arguments"))
        val offset = if (words.size > 1) {
            words[1].toIntOrNull()?.takeIf { it >= 0 } ?: throw NxFetchError(tr("offset must be a whole number, 0 or more"))
        } else {
            0
        }
        val first = NxFetchUrl.check(words[0], say)
        val page = (if (offset > 0) cachedFor(first.url) else null) ?: download(app, first, stopped, onOut)
        val body = NxFetchPage.slice(page.text, offset, PAGE_LIMIT, first.url, say)
        return ConsoleOut(if (offset == 0) page.head + "\n" + body else body, true)
    }

    private fun download(app: Context, first: NxFetchTarget, stopped: () -> Boolean, onOut: (String) -> Unit): NxFetched {
        var page: NxFetched? = null
        var refusal: String? = null
        NxTask.await(TaskKind.Fetch, tr("Fetch {0}", first.host), stopped) { run ->
            try {
                page = follow(app, first, run, onOut)
            } catch (e: NxFetchError) {
                refusal = e.message
            }
        }
        refusal?.let { throw NxFetchError(it) }
        val done = page ?: throw NxFetchError(tr("Command failed"))
        remember(done)
        return done
    }

    private fun follow(app: Context, first: NxFetchTarget, run: TaskRun, onOut: (String) -> Unit): NxFetched {
        var target = first
        var hops = 0
        while (true) {
            if (run.stop) throw TaskStopped()
            run.detail = target.host
            TaskBoard.publish()
            refuseLocalAddress(target)
            askDomain(app, target, run, onOut)
            val conn = open(target)
            try {
                val code = conn.responseCode
                if (NxFetchRedirect.isRedirect(code)) {
                    val to = conn.getHeaderField("Location") ?: throw NxFetchError(tr("the redirect has no address"))
                    hops++
                    if (hops > HOPS) throw NxFetchError(tr("more than {0} redirects, stopped", HOPS))
                    target = NxFetchRedirect.next(target, to, say)
                    continue
                }
                if (code !in 200..299) throw NxFetchError(tr("HTTP {0} {1}", code, conn.responseMessage.orEmpty()))
                return read(conn, target, first.url, run)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun refuseLocalAddress(target: NxFetchTarget) {
        if (target.literal) return
        val found = try {
            InetAddress.getAllByName(target.host)
        } catch (_: UnknownHostException) {
            throw NxFetchError(tr("cannot resolve {0}", target.host))
        }
        if (found.isEmpty() || found.any { NxFetchAddress.blocked(it.address) }) {
            throw NxFetchError(tr("{0} points to a local or private address, refused", target.host))
        }
    }

    private fun askDomain(app: Context, target: NxFetchTarget, run: TaskRun, onOut: (String) -> Unit) {
        if (NxFetchDomains.isAllowed(target.host)) return
        onOut(tr("Waiting for your approval"))
        val line = "nx fetch " + target.url
        val allowed = ApprovalBoard.request(
            app, tr("Network"), line,
            tr("The AI wants to download a page from {0}. If you allow it, {0} stays allowed until the app closes.", target.host),
        ) { run.stop }
        if (!allowed) throw NxFetchError(tr("Denied by you: {0}", "nx fetch " + target.host))
        NxFetchDomains.allow(target.host)
    }

    private fun open(target: NxFetchTarget): HttpURLConnection {
        val conn = URL(target.url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        conn.connectTimeout = CONNECT_MS
        conn.readTimeout = READ_MS
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", AiWire.BROWSER)
        conn.setRequestProperty("Accept", ACCEPT)
        return conn
    }

    private fun read(conn: HttpURLConnection, target: NxFetchTarget, key: String, run: TaskRun): NxFetched {
        val type = conn.contentType
        if (NxFetchBody.declaredBinary(type)) {
            throw NxFetchError(tr("not a text page ({0}), nothing downloaded", NxFetchBody.typeOf(type)))
        }
        val declared = conn.contentLengthLong
        if (declared > 0) run.totalBytes = declared
        val buffer = ByteArray(8192)
        val bytes = ByteArrayOutputStream()
        val end = System.currentTimeMillis() + HOP_MS
        var cut = false
        conn.inputStream.use { input ->
            while (true) {
                if (run.stop) throw TaskStopped()
                if (System.currentTimeMillis() > end) throw NxFetchError(tr("the download took longer than {0} seconds, stopped", HOP_MS / 1000))
                val n = input.read(buffer)
                if (n < 0) break
                val room = BODY_CAP - bytes.size()
                if (n >= room) {
                    bytes.write(buffer, 0, room)
                    cut = true
                    break
                }
                bytes.write(buffer, 0, n)
                run.bytes = bytes.size().toLong()
                TaskBoard.publish()
            }
        }
        val data = bytes.toByteArray()
        val kind = NxFetchBody.kind(type, data.copyOf(minOf(data.size, 512)))
        if (kind == NxFetchKind.Binary) {
            throw NxFetchError(tr("not a text page ({0}, {1})", NxFetchBody.typeOf(type).ifEmpty { "?" }, NxFormat.size(data.size.toLong())))
        }
        val decoded = NxFetchBody.decode(data, type)
        val page = if (kind == NxFetchKind.Html) NxHtmlText.convert(decoded) else NxPageText("", decoded.replace("\r\n", "\n").trim())
        val head = StringBuilder(tr("Fetched {0} ({1}, {2})", target.url, NxFetchBody.typeOf(type).ifEmpty { "?" }, NxFormat.size(data.size.toLong())))
        if (page.title.isNotEmpty()) head.append('\n').append(tr("Title: {0}", page.title))
        if (cut) head.append('\n').append(tr("[download stopped at {0}, the page is longer]", NxFormat.size(BODY_CAP.toLong())))
        return NxFetched(key, head.toString(), page.text, System.currentTimeMillis())
    }
}
