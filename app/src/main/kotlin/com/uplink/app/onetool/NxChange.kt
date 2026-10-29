package com.uplink.app

class NxChangeError(message: String) : Exception(message)

object NxEscape {
    fun decode(text: String): String {
        if (text.indexOf('\\') < 0) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n' -> { out.append('\n'); i += 2; continue }
                    't' -> { out.append('\t'); i += 2; continue }
                    '\\' -> { out.append('\\'); i += 2; continue }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}

class NxWriteArgs(val path: String, val content: String)

object NxWriteParse {
    fun parse(rest: String, maxChars: Int, say: NxSay): NxWriteArgs {
        val text = rest.trim()
        if (text.isEmpty()) throw NxChangeError(say("Missing argument", arrayOf<Any?>()))
        val path: String
        val after: Int
        val first = text[0]
        if (first == '\'' || first == '"') {
            val close = text.indexOf(first, 1)
            if (close < 0) throw NxChangeError(say("unclosed quote", arrayOf<Any?>()))
            path = text.substring(1, close)
            after = close + 1
        } else {
            var end = 0
            while (end < text.length && !text[end].isWhitespace()) end++
            path = text.substring(0, end)
            after = end
        }
        if (path.isEmpty()) throw NxChangeError(say("Missing argument", arrayOf<Any?>()))
        val tail = text.substring(after).trimStart()
        if (tail.isEmpty()) throw NxChangeError(say("nx write needs the text after the path, use '' for an empty file", arrayOf<Any?>()))
        var body = tail
        if (body.length >= 2 && (body[0] == '\'' || body[0] == '"') && body.last() == body[0]) body = body.substring(1, body.length - 1)
        val content = NxEscape.decode(body)
        if (content.length > maxChars) throw NxChangeError(say("text is {0} characters, nx write takes at most {1}. Put long files in the files list", arrayOf(content.length, maxChars)))
        return NxWriteArgs(path, content)
    }
}

class NxEditResult(val text: String, val line: Int)

object NxEdit {
    fun count(text: String, piece: String): Int {
        if (piece.isEmpty()) return 0
        var n = 0
        var at = text.indexOf(piece)
        while (at >= 0) {
            n++
            at = text.indexOf(piece, at + 1)
        }
        return n
    }

    fun apply(text: String, old: String, replacement: String, name: String, say: NxSay): NxEditResult {
        if (old.isEmpty()) throw NxChangeError(say("old text is empty", arrayOf<Any?>()))
        val n = count(text, old)
        if (n == 0) {
            val crlf = text.contains("\r\n") && old.contains('\n') && !old.contains("\r\n")
            val hint = if (crlf) " " + say("The file uses CRLF line endings, include \\r before each \\n in the old text", arrayOf<Any?>()) else ""
            throw NxChangeError(say("old text not found in {0} (0 occurrences)", arrayOf(name)) + hint)
        }
        if (n > 1) throw NxChangeError(say("old text appears {0} times in {1}, it must appear exactly once. Add surrounding text to make it unique", arrayOf(n, name)))
        val at = text.indexOf(old)
        val line = text.substring(0, at).count { it == '\n' } + 1
        return NxEditResult(text.substring(0, at) + replacement + text.substring(at + old.length), line)
    }
}

sealed class NxMove {
    class Go(val to: String, val replaces: Boolean) : NxMove()
    class Refuse(val reason: String) : NxMove()
}

object NxTransfer {
    fun resolve(src: String, srcKind: Int, dst: String, dstKind: Int, kindOf: (String) -> Int, say: NxSay): NxMove {
        if (srcKind == 0) return NxMove.Refuse(say("{0}: not found", arrayOf(src)))
        val name = src.trimEnd('/').substringAfterLast('/')
        val target = if (dstKind == 2) NxFormat.join(dst.trimEnd('/').ifEmpty { "/" }, name) else dst
        val from = DeletePolicy.canonical(src)
        val to = DeletePolicy.canonical(target)
        if (from == to) return NxMove.Refuse(say("source and target are the same: {0}", arrayOf(src)))
        if (srcKind == 2 && to.startsWith("$from/")) return NxMove.Refuse(say("a folder cannot go inside itself: {0}", arrayOf(src)))
        val have = if (target == dst) dstKind else kindOf(target)
        return when {
            have == 0 -> NxMove.Go(target, false)
            have == 2 -> NxMove.Refuse(say("{0} is an existing folder, pick another name", arrayOf(target)))
            srcKind == 2 -> NxMove.Refuse(say("{0} is a file, a folder cannot replace it", arrayOf(target)))
            else -> NxMove.Go(target, true)
        }
    }
}

class NxTarget(val path: String, val folder: Boolean)

object NxConfirm {
    fun distinct(targets: List<NxTarget>): List<NxTarget> {
        val seen = LinkedHashMap<String, NxTarget>()
        for (t in targets) seen.putIfAbsent(DeletePolicy.canonical(t.path), t)
        return seen.values.toList()
    }

    fun head(replace: Boolean, count: Int, say: NxSay): String = when {
        replace && count == 1 -> say("Replace 1 item", arrayOf<Any?>())
        replace -> say("Replace {0} items", arrayOf(count))
        count == 1 -> say("Delete 1 item", arrayOf<Any?>())
        else -> say("Delete {0} items", arrayOf(count))
    }

    fun lines(targets: List<NxTarget>): List<String> =
        distinct(targets).map { "- " + DeletePolicy.canonical(it.path) + if (it.folder) "/" else "" }
}
