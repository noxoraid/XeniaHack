package com.uplink.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FsItem(val name: String, val dir: Boolean, val size: Long)

class FsMeta(val dir: Boolean, val size: Long, val modified: Long, val mode: Int)

interface FsBackend {
    val label: String
    fun list(path: String): List<FsItem>
    fun open(path: String): InputStream
    fun create(path: String): OutputStream
    fun mkdirs(path: String)
    fun touch(path: String)
    fun rename(from: String, to: String)
    fun delete(path: String)
    fun kind(path: String): Int
    fun meta(path: String): FsMeta? = null
}

fun sortItems(items: List<FsItem>): List<FsItem> =
    items.sortedWith(compareBy<FsItem>({ !it.dir }, { it.name.lowercase() }))

fun parentOf(path: String): String {
    val cut = path.trimEnd('/').lastIndexOf('/')
    return if (cut <= 0) "/" else path.substring(0, cut)
}

fun fixPath(raw: String, home: String): String {
    var p = raw.trim().replace('\\', '/')
    if (p.isEmpty()) p = home
    if (p == "~") p = home
    else if (p.startsWith("~/")) p = home.trimEnd('/') + "/" + p.substring(2)
    else if (!p.startsWith("/")) p = home.trimEnd('/') + "/" + p
    val out = ArrayList<String>()
    for (seg in p.split('/')) {
        when (seg) {
            "", "." -> {}
            ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
            else -> out.add(seg)
        }
    }
    return "/" + out.joinToString("/")
}

object InternalFs : FsBackend {
    override val label = "Internal"

    override fun list(path: String): List<FsItem> {
        val arr = File(path).listFiles() ?: throw Exception(tr("Cannot read folder"))
        return sortItems(arr.map { FsItem(it.name, it.isDirectory, if (it.isDirectory) 0L else it.length()) })
    }

    override fun open(path: String): InputStream = BufferedInputStream(FileInputStream(File(path)), 65536)

    override fun create(path: String): OutputStream {
        val f = File(path)
        f.parentFile?.mkdirs()
        return BufferedOutputStream(FileOutputStream(f), 65536)
    }

    override fun mkdirs(path: String) {
        val f = File(path)
        if (!f.isDirectory && !f.mkdirs()) throw Exception(tr("Cannot create folder"))
    }

    override fun touch(path: String) {
        val f = File(path)
        f.parentFile?.mkdirs()
        if (!f.exists() && !f.createNewFile()) throw Exception(tr("Cannot create file"))
    }

    override fun rename(from: String, to: String) {
        val a = File(from)
        val b = File(to)
        if (b.exists()) throw Exception(tr("Name already exists"))
        if (!a.renameTo(b)) {
            copyTree(this, from, this, to) { }
            a.deleteRecursively()
        }
    }

    override fun delete(path: String) {
        if (!File(path).deleteRecursively()) throw Exception(tr("Cannot delete"))
    }

    override fun kind(path: String): Int {
        val f = File(path)
        return if (f.isDirectory) 2 else if (f.exists()) 1 else 0
    }

    override fun meta(path: String): FsMeta? {
        val f = File(path)
        if (!f.exists()) return null
        val mode = try {
            android.system.Os.stat(path).st_mode
        } catch (_: Exception) {
            -1
        }
        return FsMeta(f.isDirectory, if (f.isDirectory) 0L else f.length(), f.lastModified(), mode)
    }
}

object NoxoraFs : FsBackend by InternalFs {
    override val label = "NoxoraStorage"

    @Volatile
    private var data: String = ""

    fun init(ctx: android.content.Context) {
        data = ctx.applicationInfo.dataDir
        java.io.File(ctx.filesDir, "home").mkdirs()
    }

    fun base(): String = data.ifEmpty { "/data/data/com.uplink.app" }

    fun home(ctx: android.content.Context): String = java.io.File(ctx.filesDir, "home").also { it.mkdirs() }.absolutePath
}

fun isLocal(fs: FsBackend): Boolean = fs === InternalFs || fs === NoxoraFs

fun watchSign(items: List<FsItem>): Int =
    items.fold(7) { h, i -> 31 * h + i.name.hashCode() * 3 + i.size.hashCode() + (if (i.dir) 1 else 0) }

@JvmName("watchSignRemote")
fun watchSign(items: List<RemoteItem>): Int =
    items.fold(7) { h, i -> 31 * h + i.name.hashCode() * 3 + i.size.hashCode() + (if (i.dir) 1 else 0) }

object RootFs : FsBackend {
    override val label = "Root"

    @Volatile
    private var checked: Boolean? = null

    fun available(force: Boolean = false): Boolean {
        if (!force) checked?.let { return it }
        val ok = try {
            val p = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            p.waitFor()
            out == "0"
        } catch (_: Throwable) {
            false
        }
        checked = ok
        return ok
    }

    private fun need() {
        if (!available()) throw Exception(tr("Root access is not available"))
    }

    private fun run(cmd: String): String {
        need()
        val p = ProcessBuilder("su", "-c", cmd).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        val err = p.errorStream.readBytes().toString(Charsets.UTF_8).trim()
        val code = p.waitFor()
        if (code != 0) throw Exception(err.ifEmpty { tr("Command failed") })
        return out
    }

    override fun list(path: String): List<FsItem> {
        val script = "cd ${sq(path)} || exit 1; for f in .[!.]* ..?* *; do " +
            "if [ -d \"\$f\" ]; then echo \"d|0|\$f\"; " +
            "elif [ -e \"\$f\" ] || [ -L \"\$f\" ]; then echo \"f|\$(stat -c %s -- \"\$f\" 2>/dev/null || echo 0)|\$f\"; fi; done"
        val out = run(script)
        val items = ArrayList<FsItem>()
        for (line in out.lines()) {
            if (line.length < 5) continue
            val a = line.indexOf('|')
            val b = line.indexOf('|', a + 1)
            if (a < 0 || b < 0) continue
            val name = line.substring(b + 1)
            if (name.isEmpty()) continue
            items.add(FsItem(name, line[0] == 'd', line.substring(a + 1, b).toLongOrNull() ?: 0L))
        }
        return sortItems(items)
    }

    override fun open(path: String): InputStream {
        need()
        val p = ProcessBuilder("su", "-c", "cat -- ${sq(path)}").start()
        return object : FilterInputStream(BufferedInputStream(p.inputStream, 65536)) {
            override fun close() {
                try { super.close() } finally { p.destroy() }
            }
        }
    }

    override fun create(path: String): OutputStream {
        need()
        run("mkdir -p -- ${sq(parentOf(path))}")
        val p = ProcessBuilder("su", "-c", "cat > ${sq(path)}").start()
        Thread { try { p.inputStream.readBytes() } catch (_: Throwable) {} }.apply { isDaemon = true; start() }
        val errBuf = java.io.ByteArrayOutputStream()
        Thread { try { p.errorStream.copyTo(errBuf) } catch (_: Throwable) {} }.apply { isDaemon = true; start() }
        return object : FilterOutputStream(BufferedOutputStream(p.outputStream, 65536)) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() {
                try { super.close() } catch (_: Throwable) {}
                val code = p.waitFor()
                if (code != 0) throw Exception(errBuf.toString().trim().ifEmpty { tr("Cannot write file") })
            }
        }
    }

    override fun mkdirs(path: String) { run("mkdir -p -- ${sq(path)}") }

    override fun touch(path: String) { run("touch -- ${sq(path)}") }

    override fun rename(from: String, to: String) { run("mv -- ${sq(from)} ${sq(to)}") }

    override fun delete(path: String) { run("rm -rf -- ${sq(path)}") }

    override fun kind(path: String): Int {
        val out = run("if [ -d ${sq(path)} ]; then echo 2; elif [ -e ${sq(path)} ] || [ -L ${sq(path)} ]; then echo 1; else echo 0; fi")
        return out.trim().toIntOrNull() ?: 0
    }
}

class VpsFs(val vps: Vps) : FsBackend {
    override val label = vps.profile.name

    override fun list(path: String): List<FsItem> = vps.list(path).map { FsItem(it.name, it.dir, it.size) }

    override fun open(path: String): InputStream = vps.openIn(path)

    override fun create(path: String): OutputStream = vps.openOut(path)

    override fun mkdirs(path: String) = vps.mkdirs(path)

    override fun touch(path: String) = vps.touch(path)

    override fun rename(from: String, to: String) = vps.renameTo(from, to)

    override fun delete(path: String) = vps.remove(path)

    override fun kind(path: String): Int = vps.kind(path)

    override fun meta(path: String): FsMeta? = vps.meta(path)
}

fun copyTree(src: FsBackend, from: String, dst: FsBackend, to: String, onFile: (String) -> Unit) {
    when (src.kind(from)) {
        0 -> throw Exception(tr("Source not found: {0}", from.substringAfterLast('/')))
        2 -> {
            dst.mkdirs(to)
            for (child in src.list(from)) {
                copyTree(src, joinPath(from, child.name), dst, joinPath(to, child.name), onFile)
            }
        }
        else -> {
            onFile(from.substringAfterLast('/'))
            src.open(from).use { input ->
                dst.create(to).use { output -> input.copyTo(output, 65536) }
            }
        }
    }
}

fun countFiles(fs: FsBackend, path: String): Int = when (fs.kind(path)) {
    2 -> fs.list(path).sumOf { countFiles(fs, joinPath(path, it.name)) }
    1 -> 1
    else -> 0
}

private class KeepOpenOut(out: OutputStream) : FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
    override fun close() { flush() }
}

private class KeepOpenIn(inp: InputStream) : FilterInputStream(inp) {
    override fun close() {}
}

private fun zipInto(fs: FsBackend, z: ZipOutputStream, path: String, entry: String) {
    if (fs.kind(path) == 2) {
        z.putNextEntry(ZipEntry("$entry/"))
        z.closeEntry()
        for (child in fs.list(path)) zipInto(fs, z, joinPath(path, child.name), "$entry/${child.name}")
    } else {
        z.putNextEntry(ZipEntry(entry))
        fs.open(path).use { it.copyTo(KeepOpenOut(z), 65536) }
        z.closeEntry()
    }
}

fun zipItem(fs: FsBackend, parent: String, name: String) {
    ZipOutputStream(BufferedOutputStream(fs.create(joinPath(parent, "$name.zip")), 65536)).use { z ->
        zipInto(fs, z, joinPath(parent, name), name)
    }
}

fun unzipItem(fs: FsBackend, parent: String, name: String) {
    ZipInputStream(BufferedInputStream(fs.open(joinPath(parent, name)), 65536)).use { z ->
        while (true) {
            val e = z.nextEntry ?: break
            val rel = e.name.trimStart('/')
            if (rel.isEmpty() || rel.split('/').contains("..")) continue
            val target = joinPath(parent, rel.trimEnd('/'))
            if (e.isDirectory) {
                fs.mkdirs(target)
            } else {
                fs.mkdirs(parentOf(target))
                fs.create(target).use { out -> KeepOpenIn(z).copyTo(out, 65536) }
            }
        }
    }
}
