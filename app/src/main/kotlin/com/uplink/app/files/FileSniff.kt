package com.uplink.app

enum class OpenKind { Text, Image, Audio, Video, Binary }

class LineTable(val truncated: Boolean, val size: Long, private val packed: LongArray) {
    val count: Int get() = packed.size - 2

    fun start(i: Int): Long = packed[i + 2]

    fun end(i: Int): Long = if (i + 1 < count) packed[i + 3] else size
}

object FileSniff {
    private val loaded: Boolean = try {
        System.loadLibrary("nxcore")
        true
    } catch (_: Throwable) {
        false
    }

    private external fun nativeKind(head: ByteArray, count: Int): Int

    private external fun nativeLines(path: String, maxLines: Int, maxChunk: Int, maxBytes: Long): LongArray?

    const val LINE_CHUNK = 4096
    const val LINE_LIMIT = 4_000_000
    const val SCAN_LIMIT = 768L * 1024 * 1024

    private val stills = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val sounds = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "mka")
    private val movies = setOf("mp4", "m4v", "mkv", "webm", "3gp", "mov", "avi", "ts", "flv")

    fun byName(name: String): OpenKind? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in stills -> OpenKind.Image
            in sounds -> OpenKind.Audio
            in movies -> OpenKind.Video
            else -> null
        }
    }

    fun head(fs: FsBackend, path: String): ByteArray {
        val buf = ByteArray(8192)
        var filled = 0
        fs.open(path).use { s ->
            while (filled < buf.size) {
                val n = s.read(buf, filled, buf.size - filled)
                if (n < 0) break
                filled += n
            }
        }
        return buf.copyOf(filled)
    }

    fun kindOf(head: ByteArray): OpenKind {
        val raw = if (loaded) {
            try { nativeKind(head, head.size) } catch (_: Throwable) { plainKind(head) }
        } else {
            plainKind(head)
        }
        return when (raw) {
            1 -> OpenKind.Text
            2 -> OpenKind.Image
            3 -> OpenKind.Audio
            4 -> OpenKind.Video
            else -> OpenKind.Binary
        }
    }

    private fun plainKind(head: ByteArray): Int = if (head.any { it.toInt() == 0 }) 0 else 1

    fun lines(path: String): LineTable? {
        if (!loaded) return null
        val packed = try {
            nativeLines(path, LINE_LIMIT, LINE_CHUNK, SCAN_LIMIT)
        } catch (_: Throwable) {
            null
        } ?: return null
        return LineTable(packed[0] == 1L, packed[1], packed)
    }
}
