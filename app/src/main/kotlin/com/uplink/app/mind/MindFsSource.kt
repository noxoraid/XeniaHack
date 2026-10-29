package com.uplink.app

import java.io.File

class MindFsSource(private val fs: FsBackend) : MindSource {
    private val local = isLocal(fs)
    private val shared = ByteArray(262144)

    override fun list(dir: String): List<MindEntry> =
        fs.list(dir).map {
            val stamp = if (local && !it.dir) File(MindWalk.join(dir, it.name)).lastModified() else 0L
            MindEntry(it.name, it.dir, it.size, stamp)
        }

    override fun read(path: String, limit: Int): String {
        val buf = if (limit <= shared.size) shared else ByteArray(limit)
        fs.open(path).use { input ->
            var n = 0
            while (n < limit) {
                val got = input.read(buf, n, limit - n)
                if (got < 0) break
                n += got
            }
            return String(buf, 0, n, Charsets.UTF_8)
        }
    }
}
