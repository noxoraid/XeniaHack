package com.uplink.app

class GrepLine(val number: Int, val text: String)

object TextGrep {
    private val loaded: Boolean = try {
        System.loadLibrary("nxcore")
        true
    } catch (_: Throwable) {
        false
    }

    private external fun nativeGrep(data: ByteArray, len: Int, needle: ByteArray, ignoreCase: Boolean, maxHits: Int): LongArray

    fun binary(data: ByteArray, len: Int): Boolean {
        val n = minOf(len, 4096)
        for (i in 0 until n) if (data[i].toInt() == 0) return true
        return false
    }

    fun literal(data: ByteArray, len: Int, needle: String, ignoreCase: Boolean, maxHits: Int): List<GrepLine> {
        val key = needle.toByteArray(Charsets.UTF_8)
        if (len <= 0 || key.isEmpty() || maxHits <= 0) return emptyList()
        val packed = if (loaded) {
            try { nativeGrep(data, len, key, ignoreCase, maxHits) } catch (_: Throwable) { null }
        } else {
            null
        } ?: return literalSlow(data, len, key, ignoreCase, maxHits)
        val out = ArrayList<GrepLine>(packed.size / 3)
        var i = 0
        while (i + 2 < packed.size) {
            val start = packed[i + 1].toInt()
            val end = packed[i + 2].toInt()
            out.add(GrepLine(packed[i].toInt(), String(data, start, end - start, Charsets.UTF_8)))
            i += 3
        }
        return out
    }

    fun pattern(data: ByteArray, len: Int, regex: Regex, maxHits: Int): List<GrepLine> {
        val out = ArrayList<GrepLine>()
        var number = 1
        var pos = 0
        while (pos < len && out.size < maxHits) {
            var end = pos
            while (end < len && data[end] != '\n'.code.toByte()) end++
            var stop = end
            if (stop > pos && data[stop - 1] == '\r'.code.toByte()) stop--
            val text = String(data, pos, stop - pos, Charsets.UTF_8)
            if (regex.containsMatchIn(text)) out.add(GrepLine(number, text))
            pos = end + 1
            number++
        }
        return out
    }

    private fun fold(b: Byte): Int {
        val c = b.toInt() and 0xff
        return if (c in 65..90) c + 32 else c
    }

    private fun literalSlow(data: ByteArray, len: Int, key: ByteArray, ignoreCase: Boolean, maxHits: Int): List<GrepLine> {
        val folded = IntArray(key.size) { if (ignoreCase) fold(key[it]) else key[it].toInt() and 0xff }
        val out = ArrayList<GrepLine>()
        var number = 1
        var pos = 0
        while (pos < len && out.size < maxHits) {
            var end = pos
            while (end < len && data[end] != '\n'.code.toByte()) end++
            var stop = end
            if (stop > pos && data[stop - 1] == '\r'.code.toByte()) stop--
            var hit = false
            var i = pos
            while (i + key.size <= stop && !hit) {
                var k = 0
                while (k < key.size) {
                    val c = if (ignoreCase) fold(data[i + k]) else data[i + k].toInt() and 0xff
                    if (c != folded[k]) break
                    k++
                }
                if (k == key.size) hit = true
                i++
            }
            if (hit) out.add(GrepLine(number, String(data, pos, stop - pos, Charsets.UTF_8)))
            pos = end + 1
            number++
        }
        return out
    }
}
