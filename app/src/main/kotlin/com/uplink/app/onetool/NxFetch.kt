package com.uplink.app

import java.net.IDN
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale

class NxFetchError(message: String) : Exception(message)

class NxFetchTarget(val url: String, val host: String, val port: Int, val secure: Boolean, val literal: Boolean)

class NxPageText(val title: String, val text: String)

enum class NxFetchKind { Html, Text, Binary }

private val NO_ARGS: Array<Any?> = arrayOf()

object NxFetchAddress {
    private val numericHost = Regex("^(0[xX][0-9a-fA-F]+|[0-9]+)(\\.(0[xX][0-9a-fA-F]+|[0-9]+))*$")

    fun looksNumeric(host: String): Boolean = numericHost.matches(host)

    private fun number(part: String): Long? = when {
        part.length > 2 && (part.startsWith("0x") || part.startsWith("0X")) -> part.substring(2).toLongOrNull(16)
        part.length > 1 && part[0] == '0' -> part.toLongOrNull(8)
        else -> part.toLongOrNull()
    }

    fun parseV4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size !in 1..4) return null
        val values = LongArray(parts.size)
        for ((i, p) in parts.withIndex()) values[i] = number(p) ?: return null
        var total = 0L
        for (i in 0 until parts.size - 1) {
            if (values[i] > 255L) return null
            total = total or (values[i] shl (8 * (3 - i)))
        }
        val tailBits = 8 * (4 - (parts.size - 1))
        val last = values[parts.size - 1]
        if (last >= (1L shl tailBits)) return null
        total = total or last
        return byteArrayOf((total shr 24).toByte(), (total shr 16).toByte(), (total shr 8).toByte(), total.toByte())
    }

    private fun strictV4(text: String): ByteArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            if (p.isEmpty() || p.length > 3 || p.any { !it.isDigit() }) return null
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v.toByte()
        }
        return out
    }

    private fun groups(text: String): List<String>? {
        if (text.isEmpty()) return emptyList()
        val g = text.split(':')
        return if (g.any { it.isEmpty() || it.length > 4 }) null else g
    }

    fun parseV6(text: String): ByteArray? {
        if (text.isEmpty() || text.any { !(it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.') }) return null
        var body = text
        var tail: ByteArray? = null
        if (body.indexOf('.') >= 0) {
            val cut = body.lastIndexOf(':')
            if (cut < 0) return null
            tail = strictV4(body.substring(cut + 1)) ?: return null
            body = body.substring(0, cut + 1) + "0:0"
        }
        val doubleAt = body.indexOf("::")
        if (doubleAt >= 0 && body.indexOf("::", doubleAt + 1) >= 0) return null
        val head: List<String>
        val rear: List<String>
        if (doubleAt >= 0) {
            head = groups(body.substring(0, doubleAt)) ?: return null
            rear = groups(body.substring(doubleAt + 2)) ?: return null
            if (head.size + rear.size > 7) return null
        } else {
            head = groups(body) ?: return null
            rear = emptyList()
            if (head.size != 8) return null
        }
        val out = ByteArray(16)
        for ((i, g) in head.withIndex()) {
            val v = g.toInt(16)
            out[i * 2] = (v shr 8).toByte()
            out[i * 2 + 1] = v.toByte()
        }
        for ((i, g) in rear.withIndex()) {
            val v = g.toInt(16)
            val slot = 8 - rear.size + i
            out[slot * 2] = (v shr 8).toByte()
            out[slot * 2 + 1] = v.toByte()
        }
        if (tail != null) for (k in 0 until 4) out[12 + k] = tail[k]
        return out
    }

    fun blocked(address: ByteArray): Boolean = when (address.size) {
        4 -> blockedV4(address)
        16 -> blockedV6(address)
        else -> true
    }

    private fun blockedV4(a: ByteArray): Boolean {
        val b0 = a[0].toInt() and 255
        val b1 = a[1].toInt() and 255
        val b2 = a[2].toInt() and 255
        return when {
            b0 == 0 || b0 == 10 || b0 == 127 -> true
            b0 == 100 && b1 in 64..127 -> true
            b0 == 169 && b1 == 254 -> true
            b0 == 172 && b1 in 16..31 -> true
            b0 == 192 && b1 == 168 -> true
            b0 == 192 && b1 == 0 && b2 == 0 -> true
            b0 == 198 && b1 in 18..19 -> true
            b0 >= 224 -> true
            else -> false
        }
    }

    private fun blockedV6(a: ByteArray): Boolean {
        val u = IntArray(16) { a[it].toInt() and 255 }
        if ((0 until 15).all { u[it] == 0 }) return true
        val embedded = a.copyOfRange(12, 16)
        if ((0 until 10).all { u[it] == 0 } && u[10] == 255 && u[11] == 255) return blockedV4(embedded)
        if ((0 until 12).all { u[it] == 0 }) return blockedV4(embedded)
        if (u[0] == 0 && u[1] == 0x64 && u[2] == 0xff && u[3] == 0x9b && (4 until 12).all { u[it] == 0 }) return blockedV4(embedded)
        if (u[0] == 0x20 && u[1] == 0x02) return blockedV4(a.copyOfRange(2, 6))
        if (u[0] == 0x20 && u[1] == 0x01 && u[2] == 0 && u[3] == 0) return true
        if ((u[0] and 0xfe) == 0xfc) return true
        if (u[0] == 0xfe && (u[1] and 0xc0) >= 0x80) return true
        if (u[0] == 0xff) return true
        return false
    }

    fun dotted(address: ByteArray): String = address.joinToString(".") { (it.toInt() and 255).toString() }
}

object NxFetchUrl {
    private val localSuffixes = listOf(".localhost", ".local", ".internal", ".lan", ".home.arpa", ".localdomain", ".intranet", ".corp")
    private const val SAFE_MARKS = "-._~:/?#[]@!$&()*+,;=%"
    private val hostShape = Regex("^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)*$")

    fun encodeLoose(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp < 128 && (Character.isLetterOrDigit(cp) || SAFE_MARKS.indexOf(cp.toChar()) >= 0)) {
                out.append(cp.toChar())
            } else {
                for (b in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)) {
                    out.append('%').append(String.format(Locale.ROOT, "%02X", b.toInt() and 255))
                }
            }
        }
        return out.toString()
    }

    private fun invalid(say: NxSay): NxFetchError = NxFetchError(say("the address is not valid", NO_ARGS))

    fun check(raw: String, say: NxSay): NxFetchTarget {
        val text = raw.trim()
        if (text.isEmpty()) throw NxFetchError(say("Missing argument", NO_ARGS))
        if (text.any { it == '\\' || it.code < 32 || it.code == 127 }) throw NxFetchError(say("the address has characters that are not allowed", NO_ARGS))
        val at = text.indexOf("://")
        if (at < 1) throw NxFetchError(say("an address must start with http:// or https://", NO_ARGS))
        val scheme = text.substring(0, at).lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") throw NxFetchError(say("only http and https addresses are allowed, not {0}", arrayOf<Any?>(scheme)))
        val secure = scheme == "https"
        val rest = text.substring(at + 3)
        val end = rest.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (end < 0) rest else rest.substring(0, end)
        val tail = (if (end < 0) "" else rest.substring(end)).substringBefore('#')
        if (authority.isEmpty()) throw NxFetchError(say("the address has no host", NO_ARGS))
        if (authority.contains('@')) throw NxFetchError(say("addresses with a user name or password are not allowed", NO_ARGS))
        val hostText: String
        var portText = ""
        val bracketed = authority.startsWith("[")
        if (bracketed) {
            val close = authority.indexOf(']')
            if (close < 0) throw invalid(say)
            hostText = authority.substring(1, close)
            val after = authority.substring(close + 1)
            if (after.isNotEmpty()) {
                if (!after.startsWith(":")) throw invalid(say)
                portText = after.substring(1)
            }
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0) {
                hostText = authority.substring(0, colon)
                portText = authority.substring(colon + 1)
            } else {
                hostText = authority
            }
        }
        val port: Int
        if (portText.isEmpty()) {
            port = if (secure) 443 else 80
        } else {
            if (portText.any { !it.isDigit() } || portText.length > 5) throw invalid(say)
            port = portText.toInt()
            if (port !in 1..65535) throw invalid(say)
        }
        val shown: String
        val literal: Boolean
        if (bracketed) {
            val bytes = NxFetchAddress.parseV6(hostText) ?: throw invalid(say)
            if (NxFetchAddress.blocked(bytes)) throw NxFetchError(say("{0} is a local or private address, refused", arrayOf<Any?>(hostText)))
            shown = "[" + hostText.lowercase(Locale.ROOT) + "]"
            literal = true
        } else {
            var name = hostText.lowercase(Locale.ROOT)
            if (name.endsWith(".")) name = name.dropLast(1)
            if (name.isEmpty()) throw invalid(say)
            if (NxFetchAddress.looksNumeric(name)) {
                val bytes = NxFetchAddress.parseV4(name) ?: throw invalid(say)
                if (NxFetchAddress.blocked(bytes)) throw NxFetchError(say("{0} is a local or private address, refused", arrayOf<Any?>(name)))
                shown = NxFetchAddress.dotted(bytes)
                literal = true
            } else {
                name = try {
                    IDN.toASCII(name, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
                } catch (_: Exception) {
                    throw invalid(say)
                }
                if (name == "localhost" || localSuffixes.any { name.endsWith(it) } || !name.contains('.')) {
                    throw NxFetchError(say("{0} is a local network name, refused", arrayOf<Any?>(name)))
                }
                if (name.length > 253 || !hostShape.matches(name)) throw invalid(say)
                shown = name
                literal = false
            }
        }
        val defaultPort = if (secure) 443 else 80
        val authorityOut = if (port == defaultPort) shown else "$shown:$port"
        val path = if (tail.isEmpty() || tail[0] == '?') "/$tail" else tail
        return NxFetchTarget("$scheme://$authorityOut${encodeLoose(path)}", shown, port, secure, literal)
    }
}

object NxFetchRedirect {
    fun next(current: NxFetchTarget, location: String, say: NxSay): NxFetchTarget {
        val loc = location.trim()
        if (loc.isEmpty()) throw NxFetchError(say("the redirect has no address", NO_ARGS))
        val resolved = try {
            URL(URL(current.url), NxFetchUrl.encodeLoose(loc)).toString()
        } catch (_: Exception) {
            throw NxFetchError(say("the redirect address is not valid", NO_ARGS))
        }
        val target = NxFetchUrl.check(resolved, say)
        if (current.secure && !target.secure) throw NxFetchError(say("redirect from https to http refused", NO_ARGS))
        return target
    }

    fun isRedirect(code: Int): Boolean = code == 301 || code == 302 || code == 303 || code == 307 || code == 308
}

object NxFetchDomains {
    private val allowed = HashSet<String>()

    @Synchronized
    fun isAllowed(host: String): Boolean = allowed.contains(host)

    @Synchronized
    fun allow(host: String) {
        allowed.add(host)
    }

    @Synchronized
    fun clear() {
        allowed.clear()
    }
}

object NxFetchBody {
    private val declaredCharset = Regex("charset\\s*=\\s*\"?([A-Za-z0-9_.:-]+)", RegexOption.IGNORE_CASE)
    private val metaCharset = Regex("<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_.:-]+)", RegexOption.IGNORE_CASE)

    fun typeOf(contentType: String?): String = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)

    fun declaredBinary(contentType: String?): Boolean {
        val type = typeOf(contentType)
        return type.isNotEmpty() && type != "application/octet-stream" && kind(contentType, ByteArray(0)) == NxFetchKind.Binary
    }

    fun kind(contentType: String?, head: ByteArray): NxFetchKind {
        val type = typeOf(contentType)
        return when {
            type == "text/html" || type == "application/xhtml+xml" -> NxFetchKind.Html
            type.startsWith("text/") || type == "application/json" || type == "application/xml" ||
                type == "application/javascript" || type == "application/x-javascript" ||
                type.endsWith("+json") || type.endsWith("+xml") -> NxFetchKind.Text
            type.isEmpty() || type == "application/octet-stream" -> sniff(head)
            else -> NxFetchKind.Binary
        }
    }

    private fun sniff(head: ByteArray): NxFetchKind {
        val size = minOf(head.size, 512)
        for (i in 0 until size) if (head[i].toInt() == 0) return NxFetchKind.Binary
        val start = String(head, 0, size, Charsets.ISO_8859_1).trimStart().lowercase(Locale.ROOT)
        return if (start.startsWith("<!doctype html") || start.startsWith("<html")) NxFetchKind.Html else NxFetchKind.Text
    }

    fun charset(contentType: String?, head: ByteArray): Charset {
        if (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) return Charsets.UTF_8
        if (head.size >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        if (head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        val name = declaredCharset.find(contentType.orEmpty())?.groupValues?.get(1)
            ?: metaCharset.find(String(head, 0, minOf(head.size, 2048), Charsets.ISO_8859_1))?.groupValues?.get(1)
            ?: return Charsets.UTF_8
        return try {
            Charset.forName(name)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    fun decode(bytes: ByteArray, contentType: String?): String {
        val head = bytes.copyOf(minOf(bytes.size, 2048))
        return String(bytes, charset(contentType, head)).removePrefix("\uFEFF")
    }
}

private class NxTextFlow {
    val out = StringBuilder()
    var gap = false
    var pre = 0

    fun lineBreak() {
        gap = false
        while (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
        if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append('\n')
    }

    fun text(value: String) {
        if (pre > 0) {
            out.append(value.replace("\r\n", "\n").replace('\r', '\n'))
            return
        }
        for (ch in value) {
            if (ch.isWhitespace() || ch == '\u00a0') {
                gap = true
            } else {
                if (gap && out.isNotEmpty() && out[out.length - 1] != '\n' && out[out.length - 1] != ' ') out.append(' ')
                gap = false
                out.append(ch)
            }
        }
    }

    fun word(value: String) {
        if (out.isNotEmpty() && out[out.length - 1] != '\n') lineBreak()
        out.append(value)
    }
}

object NxHtmlText {
    private val skipped = setOf("script", "style", "noscript", "template", "svg", "iframe", "object", "canvas")
    private val blocks = setOf(
        "p", "div", "section", "article", "header", "footer", "main", "nav", "aside", "ul", "ol", "li", "table", "tr", "caption",
        "thead", "tbody", "tfoot", "blockquote", "pre", "form", "fieldset", "figure", "figcaption", "dl", "dt", "dd", "hr", "br",
        "details", "summary", "address", "h1", "h2", "h3", "h4", "h5", "h6",
    )
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00a0",
        "copy" to "\u00a9", "reg" to "\u00ae", "trade" to "\u2122", "hellip" to "\u2026", "mdash" to "\u2014",
        "ndash" to "\u2013", "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201c", "rdquo" to "\u201d",
        "laquo" to "\u00ab", "raquo" to "\u00bb", "bull" to "\u2022", "middot" to "\u00b7", "euro" to "\u20ac",
        "times" to "\u00d7", "deg" to "\u00b0",
    )

    private fun entity(name: String): String? {
        if (name[0] == '#') {
            val code = if (name.length > 1 && (name[1] == 'x' || name[1] == 'X')) name.substring(2).toIntOrNull(16) else name.substring(1).toIntOrNull()
            if (code == null || code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) return null
            return String(Character.toChars(code))
        }
        return named[name]
    }

    fun decode(text: String): String {
        if (text.indexOf('&') < 0) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val semi = text.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 10)) {
                    val rep = entity(text.substring(i + 1, semi))
                    if (rep != null) {
                        out.append(rep)
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun tagStart(html: String, at: Int): Boolean {
        if (html[at] != '<' || at + 1 >= html.length) return false
        val next = html[at + 1]
        return next.isLetter() || next == '/' || next == '!' || next == '?'
    }

    private fun tagEnd(html: String, start: Int): Int {
        var quote = '\u0000'
        var j = start + 1
        while (j < html.length) {
            val ch = html[j]
            if (quote != '\u0000') {
                if (ch == quote) quote = '\u0000'
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '>') {
                return j
            }
            j++
        }
        return html.indexOf('>', start)
    }

    private fun afterClose(html: String, name: String, from: Int): Int {
        val close = html.indexOf("</$name", from, true)
        if (close < 0) return html.length
        val gt = html.indexOf('>', close)
        return if (gt < 0) html.length else gt + 1
    }

    fun convert(html: String): NxPageText {
        val flow = NxTextFlow()
        var title = ""
        var i = 0
        val n = html.length
        while (i < n) {
            if (html[i] == '<' && tagStart(html, i)) {
                if (html.startsWith("<!--", i)) {
                    val e = html.indexOf("-->", i + 4)
                    i = if (e < 0) n else e + 3
                    continue
                }
                val end = tagEnd(html, i)
                if (end < 0) break
                val raw = html.substring(i + 1, end)
                i = end + 1
                val first = raw[0]
                if (first == '!' || first == '?') continue
                val closing = first == '/'
                val name = raw.substring(if (closing) 1 else 0).takeWhile { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
                if (name.isEmpty()) continue
                val selfClosed = raw.endsWith("/")
                if (!closing && !selfClosed && name in skipped) {
                    i = afterClose(html, name, i)
                    continue
                }
                if (!closing && !selfClosed && name == "title") {
                    val stop = html.indexOf("</title", i, true).let { if (it < 0) n else it }
                    if (title.isEmpty()) title = decode(html.substring(i, stop)).trim().replace(Regex("\\s+"), " ")
                    i = afterClose(html, "title", i)
                    continue
                }
                if (name == "pre") {
                    if (!closing) flow.pre++ else if (flow.pre > 0) flow.pre--
                }
                if (name in blocks) {
                    flow.lineBreak()
                    if (!closing && name == "li") flow.word("- ")
                    if (!closing && name.length == 2 && name[0] == 'h' && name[1] in '1'..'6') flow.word("#".repeat(name[1] - '0') + " ")
                } else if (name == "td" || name == "th") {
                    flow.gap = true
                }
            } else {
                var j = i
                while (j < n && !(html[j] == '<' && j > i && tagStart(html, j))) j++
                flow.text(decode(html.substring(i, j)))
                i = j
            }
        }
        return NxPageText(title, flow.out.toString().trim())
    }
}

object NxFetchPage {
    fun slice(text: String, offset: Int, limit: Int, url: String, say: NxSay): String {
        if (text.isEmpty()) return say("the page has no readable text", NO_ARGS)
        if (offset >= text.length) throw NxFetchError(say("offset {0} is past the end, the page text has {1} characters", arrayOf<Any?>(offset, text.length)))
        var cut = minOf(text.length, offset + limit)
        if (cut < text.length) {
            val nl = text.lastIndexOf('\n', cut - 1)
            if (nl > offset + limit / 2) cut = nl + 1
            if (cut < text.length && cut > offset && Character.isLowSurrogate(text[cut])) cut--
        }
        val body = text.substring(offset, cut).trimEnd()
        if (cut >= text.length) return body
        return body + "\n" + say("[cut at character {0} of {1}, continue with: nx fetch '{2}' {0}]", arrayOf<Any?>(cut, text.length, url))
    }
}
