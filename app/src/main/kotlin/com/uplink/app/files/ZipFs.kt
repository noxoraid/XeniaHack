package com.uplink.app

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

private val ZIP_EXTENSIONS = setOf("zip", "apk", "jar", "aar", "war", "xapk", "apks", "apkm", "obb", "epub", "docx", "xlsx", "pptx", "odt", "ods", "odp", "mcpack", "mcaddon", "geode")

fun isZipName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in ZIP_EXTENSIONS

class ZipFs(
    val file: File,
    private val password: String,
    val writable: Boolean,
    private val scratch: File,
    private val method: EncryptionMethod = EncryptionMethod.AES,
) : FsBackend {
    override val label: String = file.name

    private val zip: ZipFile = if (password.isEmpty()) ZipFile(file) else ZipFile(file, password.toCharArray())
    private val lock = Any()
    private val stage = File(scratch, "zipstage_" + System.nanoTime().toString(16))
    private var batch = 0

    val needsPassword: Boolean
        get() = synchronized(lock) { zip.isEncrypted }

    fun passwordOk(): Boolean {
        val probe = synchronized(lock) {
            zip.fileHeaders.filter { it.isEncrypted && !it.isDirectory }.minByOrNull { it.uncompressedSize }
        } ?: return true
        return try {
            zip.getInputStream(probe).use { s ->
                val buf = ByteArray(65536)
                var left = if (probe.uncompressedSize <= 8L * 1024 * 1024) Long.MAX_VALUE else 65536L
                while (left > 0) {
                    val n = s.read(buf)
                    if (n < 0) break
                    left -= n
                }
            }
            true
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD || e.type == ZipException.Type.CHECKSUM_MISMATCH) false else throw e
        }
    }

    private fun entry(path: String): String = path.trim('/').replace('\\', '/')

    private fun writeGuard() {
        if (!writable) throw Exception(tr("This archive is open read-only"))
    }

    private class ZipIndex(val kinds: HashMap<String, Int>, val kids: HashMap<String, ArrayList<FsItem>>)

    private var cached: ZipIndex? = null

    private fun index(): ZipIndex = synchronized(lock) { cached ?: build().also { cached = it } }

    private fun build(): ZipIndex {
        val kinds = HashMap<String, Int>()
        val kids = HashMap<String, ArrayList<FsItem>>()
        fun adopt(full: String, dir: Boolean, size: Long) {
            kinds[full] = if (dir) 2 else 1
            kids.getOrPut(full.substringBeforeLast('/', "")) { ArrayList() }.add(FsItem(full.substringAfterLast('/'), dir, size))
        }
        for (h in zip.fileHeaders) {
            val full = h.fileName.replace('\\', '/').trimEnd('/')
            if (full.isEmpty()) continue
            var cut = full.indexOf('/')
            while (cut > 0) {
                val above = full.substring(0, cut)
                if (kinds[above] == null) adopt(above, true, 0L)
                cut = full.indexOf('/', cut + 1)
            }
            if (kinds[full] == null) adopt(full, h.isDirectory, if (h.isDirectory) 0L else h.uncompressedSize)
        }
        for ((k, v) in kids) kids[k] = ArrayList(sortItems(v))
        return ZipIndex(kinds, kids)
    }

    private fun forget() {
        synchronized(lock) { cached = null }
    }

    override fun list(path: String): List<FsItem> = index().kids[entry(path)] ?: emptyList()

    override fun kind(path: String): Int {
        val e = entry(path)
        return if (e.isEmpty()) 2 else index().kinds[e] ?: 0
    }

    override fun open(path: String): InputStream {
        val name = entry(path)
        val head: FileHeader = synchronized(lock) { zip.getFileHeader(name) }
            ?: throw Exception(tr("Source not found: {0}", name.substringAfterLast('/')))
        return BufferedInputStream(zip.getInputStream(head), 65536)
    }

    private fun stageFile(name: String): File {
        val target = File(stage, name)
        if (!target.canonicalPath.startsWith(stage.canonicalPath + File.separator)) throw Exception(tr("Invalid name"))
        return target
    }

    override fun create(path: String): OutputStream {
        writeGuard()
        val name = entry(path)
        if (name.isEmpty()) throw Exception(tr("Invalid name"))
        val target = stageFile(name)
        target.parentFile?.mkdirs()
        var closed = false
        return object : FilterOutputStream(BufferedOutputStream(FileOutputStream(target), 65536)) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }

            override fun close() {
                if (closed) return
                closed = true
                super.close()
                settle()
            }
        }
    }

    override fun mkdirs(path: String) {
        writeGuard()
        val name = entry(path)
        if (name.isEmpty() || kind(path) == 2) return
        val target = stageFile(name)
        if (!target.isDirectory && !target.mkdirs()) throw Exception(tr("Cannot create folder"))
        settle()
    }

    override fun touch(path: String) {
        if (kind(path) != 0) return
        create(path).close()
    }

    override fun rename(from: String, to: String) {
        writeGuard()
        val a = entry(from)
        val b = entry(to)
        if (kind(to) != 0) throw Exception(tr("Name already exists"))
        synchronized(lock) {
            val map = LinkedHashMap<String, String>()
            for (h in zip.fileHeaders) {
                val n = h.fileName
                if (n == a) map[n] = b
                else if (n == "$a/") map[n] = "$b/"
                else if (n.startsWith("$a/")) map[n] = b + "/" + n.substring(a.length + 1)
            }
            if (map.isEmpty()) throw Exception(tr("Source not found: {0}", a.substringAfterLast('/')))
            zip.renameFiles(map)
            forget()
        }
    }

    override fun delete(path: String) {
        writeGuard()
        val e = entry(path)
        synchronized(lock) {
            val hit = ArrayList<String>()
            for (h in zip.fileHeaders) {
                val n = h.fileName
                if (n == e || n == "$e/" || n.startsWith("$e/")) hit.add(n)
            }
            if (hit.isEmpty()) throw Exception(tr("Cannot delete"))
            val folders = hit.filter { it.endsWith("/") }
            val top = hit.filter { n -> folders.none { d -> d != n && n.startsWith(d) } }
            zip.removeFiles(top)
            forget()
        }
    }

    fun beginBatch() {
        synchronized(lock) { batch++ }
    }

    fun endBatch() {
        synchronized(lock) { if (batch > 0) batch-- }
        settle()
    }

    private fun settle() {
        synchronized(lock) {
            if (batch > 0) return
            val staged = stage.list()
            if (staged == null || staged.isEmpty()) return
            val p = ZipParameters()
            p.isIncludeRootFolder = false
            p.compressionMethod = CompressionMethod.DEFLATE
            if (password.isNotEmpty()) {
                p.isEncryptFiles = true
                p.encryptionMethod = method
                if (method == EncryptionMethod.AES) p.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            try {
                zip.addFolder(stage, p)
            } finally {
                stage.deleteRecursively()
                forget()
            }
        }
    }

    fun discard() {
        synchronized(lock) { stage.deleteRecursively() }
    }
}
