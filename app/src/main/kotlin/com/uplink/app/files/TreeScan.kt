package com.uplink.app

class ScanResult(val files: Int, val bytes: Long, val skipped: Int)

object TreeScan {
    private val loaded: Boolean = try {
        System.loadLibrary("nxcore")
        true
    } catch (_: Throwable) {
        false
    }

    val nativeReady: Boolean get() = loaded

    private external fun nativeScan(root: String, patterns: Array<String>): LongArray

    fun parse(raw: String): List<String> =
        raw.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun excluded(name: String, patterns: List<String>): Boolean {
        for (p in patterns) if (glob(p, name)) return true
        return false
    }

    fun scan(fs: FsBackend, path: String, patterns: List<String>): ScanResult {
        if (loaded && isLocal(fs)) {
            try {
                val r = nativeScan(path, patterns.toTypedArray())
                return ScanResult(r[0].toInt(), r[1], r[2].toInt())
            } catch (_: Throwable) {
            }
        }
        return scanSlow(fs, path, patterns)
    }

    private fun scanSlow(fs: FsBackend, path: String, patterns: List<String>): ScanResult {
        var files = 0
        var bytes = 0L
        var skipped = 0

        fun visit(p: String, name: String, dir: Boolean, size: Long) {
            if (excluded(name, patterns)) {
                skipped++
                return
            }
            if (dir) {
                for (c in fs.list(p)) visit(joinPath(p, c.name), c.name, c.dir, c.size)
            } else {
                files++
                bytes += size
            }
        }

        val trimmed = path.trimEnd('/')
        val name = trimmed.substringAfterLast('/')
        when (fs.kind(path)) {
            2 -> visit(path, name, true, 0L)
            1 -> {
                val size = fs.list(parentOf(trimmed)).firstOrNull { it.name == name }?.size ?: 0L
                visit(path, name, false, size)
            }
        }
        return ScanResult(files, bytes, skipped)
    }

    private fun glob(pattern: String, text: String): Boolean {
        var p = 0
        var t = 0
        var star = -1
        var mark = 0
        while (t < text.length) {
            if (p < pattern.length && pattern[p] == '*') {
                star = p++
                mark = t
            } else if (p < pattern.length && (pattern[p] == '?' || pattern[p] == text[t])) {
                p++
                t++
            } else if (star >= 0) {
                p = star + 1
                t = ++mark
            } else {
                return false
            }
        }
        while (p < pattern.length && pattern[p] == '*') p++
        return p == pattern.length
    }
}
