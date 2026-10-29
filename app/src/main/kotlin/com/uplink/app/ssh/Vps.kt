package com.uplink.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Profile(val name: String, val host: String, val port: Int, val user: String, val pass: String, val key: String = "")

fun loadProfiles(ctx: Context): MutableList<Profile> {
    val arr = JSONArray(ctx.getSharedPreferences("nx", 0).getString("profiles", "[]"))
    var legacy = false
    val list = MutableList(arr.length()) { i ->
        arr.getJSONObject(i).let {
            val name = it.getString("name")
            val rawPass = it.getString("pass")
            val rawKey = it.optString("key")
            if (SecretVault.needsMigration(rawPass) || SecretVault.needsMigration(rawKey)) legacy = true
            Profile(
                name, it.getString("host"), it.getInt("port"), it.getString("user"),
                SecretVault.value("vps:pass:$name", rawPass), SecretVault.value("vps:key:$name", rawKey),
            )
        }
    }
    if (legacy && SecretVault.available) saveProfiles(ctx, list)
    return list
}

fun saveProfiles(ctx: Context, list: List<Profile>) {
    val arr = JSONArray()
    for (p in list) {
        arr.put(
            JSONObject().put("name", p.name).put("host", p.host).put("port", p.port).put("user", p.user)
                .put("pass", SecretVault.seal("vps:pass:${p.name}", p.pass)).put("key", SecretVault.seal("vps:key:${p.name}", p.key)),
        )
    }
    ctx.getSharedPreferences("nx", 0).edit().putString("profiles", arr.toString()).apply()
}

fun sq(v: String) = "'" + v.replace("'", "'\\''") + "'"

fun joinPath(dir: String, name: String) = if (dir == "/") "/$name" else "$dir/$name"

class RemoteItem(val name: String, val dir: Boolean, val size: Long)

object Hub {
    var vps by mutableStateOf<Vps?>(null)
    val count = MutableStateFlow(0)
    val wake = MutableStateFlow(false)

    fun closeAll() {
        val v = vps
        vps = null
        count.value = 0
        v?.close()
    }
}

private class NoCloseOut(out: OutputStream) : FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
    override fun close() { flush() }
}

private class NoCloseIn(inp: InputStream) : FilterInputStream(inp) {
    override fun close() {}
}

class TermSession(val title: String, val channel: ChannelShell, private val io: ExecutorService) {
    val term = Term(80, 24)
    var ctrl by mutableStateOf(false)
    @Volatile var scrollOff = 0
    @Volatile var onChange: (() -> Unit)? = null

    private val input = channel.inputStream
    private val output = channel.outputStream

    init {
        term.reply = { raw(it.toByteArray()) }
    }

    private fun raw(b: ByteArray) {
        io.execute { try { output.write(b); output.flush() } catch (_: Exception) {} }
    }

    fun send(b: ByteArray) { scrollOff = 0; raw(b) }

    fun type(text: String) {
        if (ctrl && text.length == 1) {
            val c = text[0].lowercaseChar()
            if (c in 'a'..'z') { send(byteArrayOf((c - 'a' + 1).toByte())); ctrl = false; return }
        }
        send(text.replace('\n', '\r').toByteArray())
    }

    fun arrow(c: Char) = send((if (term.appCursor) "\u001bO$c" else "\u001b[$c").toByteArray())

    fun paste(text: String) {
        val body = text.replace("\r\n", "\r").replace('\n', '\r')
        send((if (term.bracketed) "\u001b[200~$body\u001b[201~" else body).toByteArray())
    }

    fun show(text: String) {
        val chars = text.replace("\r\n", "\n").replace("\n", "\r\n").toCharArray()
        synchronized(term) { term.feed(chars, chars.size) }
        onChange?.invoke()
    }

    fun scroll(older: Int) {
        scrollOff = (scrollOff + older).coerceIn(0, term.history.size)
        onChange?.invoke()
    }

    fun resize(cols: Int, rows: Int) {
        if (cols == term.cols && rows == term.rows) return
        synchronized(term) { term.resize(cols, rows) }
        io.execute { try { channel.setPtySize(cols, rows, 0, 0) } catch (_: Exception) {} }
        onChange?.invoke()
    }

    fun startReading(onEnd: () -> Unit) {
        Thread {
            try {
                val reader = InputStreamReader(input, Charsets.UTF_8)
                val buf = CharArray(8192)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    synchronized(term) { term.feed(buf, n) }
                    onChange?.invoke()
                }
            } catch (_: Exception) {}
            onEnd()
        }.apply { isDaemon = true; start() }
    }
}

class Vps(val profile: Profile) {
    val sessions = mutableStateListOf<TermSession>()
    var current by mutableStateOf<TermSession?>(null)
    var cwd by mutableStateOf("/root")

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var ssh: Session
    private var sftpChannel: ChannelSftp? = null
    private val lock = Any()
    private var counter = 0

    val alive: Boolean get() = ::ssh.isInitialized && ssh.isConnected

    fun connect() {
        val passwords = LinkedHashSet<String>()
        passwords.add(profile.pass)
        if (profile.pass != profile.pass.trim()) passwords.add(profile.pass.trim())
        var rejected: JSchException? = null
        for (word in passwords) {
            val s = try { dial(word) } catch (e: JSchException) {
                if (e.message.orEmpty().startsWith("Auth")) { rejected = e; continue }
                throw e
            }
            ssh = s
            return
        }
        val user = profile.user.trim()
        val host = profile.host.trim()
        throw Exception(tr("Login rejected for {0}@{1}. Check the password, or add a private key if the server only accepts keys. ({2})", user, host, rejected?.message.orEmpty()))
    }

    private fun dial(word: String): Session {
        val jsch = JSch()
        val pem = profile.key.trim()
        if (pem.isNotEmpty()) {
            val phrase = if (profile.pass.isEmpty()) null else profile.pass.toByteArray(Charsets.UTF_8)
            try {
                jsch.addIdentity("nxkey", (pem + "\n").toByteArray(Charsets.UTF_8), null, phrase)
            } catch (e: JSchException) {
                throw Exception(tr("Private key rejected: {0}", e.message.orEmpty()))
            }
        }
        val s = jsch.getSession(profile.user.trim(), profile.host.trim(), profile.port)
        if (word.isNotEmpty()) s.setPassword(word)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
        s.setServerAliveInterval(30000)
        s.setServerAliveCountMax(10)
        try {
            s.connect(15000)
        } catch (e: JSchException) {
            try { s.disconnect() } catch (_: Exception) {}
            throw e
        }
        return s
    }

    fun forwardLoopback(remotePort: Int, localPort: Int) {
        ssh.setPortForwardingR("127.0.0.1", remotePort, "127.0.0.1", localPort)
    }

    fun unforwardLoopback(remotePort: Int) {
        try {
            ssh.delPortForwardingR("127.0.0.1", remotePort)
        } catch (_: Exception) {
        }
    }

    fun close() {
        for (s in sessions.toList()) try { s.channel.disconnect() } catch (_: Exception) {}
        sessions.clear()
        current = null
        try { sftpChannel?.disconnect() } catch (_: Exception) {}
        try { ssh.disconnect() } catch (_: Exception) {}
        io.shutdown()
    }

    fun openSession(): TermSession? {
        if (sessions.size >= MAX) return null
        val ch = ssh.openChannel("shell") as ChannelShell
        ch.setPtyType("xterm-256color")
        ch.setPtySize(80, 24, 0, 0)
        val s = TermSession(tr("Session {0}", ++counter), ch, io)
        ch.connect(15000)
        s.startReading { drop(s) }
        sessions.add(s)
        current = s
        Hub.count.value = sessions.size
        return s
    }

    private fun drop(s: TermSession) {
        if (sessions.remove(s)) {
            if (current === s) current = sessions.lastOrNull()
            Hub.count.value = sessions.size
        }
    }

    fun closeSession(s: TermSession) {
        try { s.channel.disconnect() } catch (_: Exception) {}
        drop(s)
    }

    fun sh(cmd: String) {
        val ch = ssh.openChannel("exec") as ChannelExec
        ch.setCommand(cmd)
        val err = ByteArrayOutputStream()
        ch.setErrStream(err)
        val out = ch.inputStream
        ch.connect(15000)
        out.readBytes()
        while (!ch.isClosed) Thread.sleep(10)
        val code = ch.exitStatus
        ch.disconnect()
        if (code != 0) throw Exception(err.toString().trim().ifEmpty { tr("Command failed") })
    }

    fun exec(script: String): String {
        val ch = ssh.openChannel("exec") as ChannelExec
        ch.setCommand("sh -c " + sq(script))
        val out = ch.inputStream
        ch.connect(15000)
        val text = out.readBytes().toString(Charsets.UTF_8)
        ch.disconnect()
        return text
    }


    fun stream(script: String, stopped: () -> Boolean, onChunk: (String) -> Unit) {
        val ch = ssh.openChannel("exec") as ChannelExec
        ch.setCommand("sh -c " + sq(script) + " 2>&1")
        ch.setPty(false)
        val input = ch.inputStream
        ch.connect(15000)
        val started = System.currentTimeMillis()
        val pending = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var quietSince = started
        fun flush(all: Boolean) {
            val bytes = pending.toByteArray()
            var upto = bytes.size
            if (!all) {
                upto = bytes.lastIndexOf(10.toByte()) + 1
                if (upto <= 0) return
            }
            onChunk(String(bytes, 0, upto, Charsets.UTF_8))
            pending.reset()
            pending.write(bytes, upto, bytes.size - upto)
        }
        try {
            while (true) {
                if (stopped()) throw TaskStopped()
                if (System.currentTimeMillis() - started > STREAM_LIMIT) throw Exception(tr("Command timed out"))
                if (input.available() > 0) {
                    val n = input.read(buf)
                    if (n < 0) break
                    pending.write(buf, 0, n)
                    quietSince = System.currentTimeMillis()
                    flush(false)
                } else if (ch.isClosed) {
                    while (input.available() > 0) {
                        val n = input.read(buf)
                        if (n < 0) break
                        pending.write(buf, 0, n)
                    }
                    break
                } else {
                    if (pending.size() > 0 && System.currentTimeMillis() - quietSince > 300) flush(true)
                    Thread.sleep(40)
                }
            }
            flush(true)
        } finally {
            try { ch.disconnect() } catch (_: Exception) {}
        }
    }

    private fun openSftp(): ChannelSftp = (ssh.openChannel("sftp") as ChannelSftp).also { it.connect(15000) }

    private fun sftp(): ChannelSftp {
        sftpChannel?.let { if (it.isConnected) return it }
        return openSftp().also { sftpChannel = it }
    }

    fun list(path: String): List<RemoteItem> = synchronized(lock) {
        val items = ArrayList<RemoteItem>()
        for (e in sftp().ls(path)) {
            val en = e as ChannelSftp.LsEntry
            if (en.filename == "." || en.filename == "..") continue
            items.add(RemoteItem(en.filename, en.attrs.isDir, en.attrs.size))
        }
        items.sortedWith(compareBy<RemoteItem>({ !it.dir }, { it.name.lowercase() }))
    }

    fun read(path: String): ByteArray = synchronized(lock) {
        val b = ByteArrayOutputStream()
        sftp().get(path, b)
        b.toByteArray()
    }

    fun write(path: String, data: ByteArray) = synchronized(lock) {
        sftp().put(ByteArrayInputStream(data), path, ChannelSftp.OVERWRITE)
    }

    fun home(): String = exec("echo \$HOME").trim().ifEmpty { "/root" }

    fun kind(path: String): Int = synchronized(lock) {
        try {
            if (sftp().stat(path).isDir) 2 else 1
        } catch (e: com.jcraft.jsch.SftpException) {
            0
        }
    }

    fun meta(path: String): FsMeta? = synchronized(lock) {
        try {
            val a = sftp().stat(path)
            FsMeta(a.isDir, if (a.isDir) 0L else a.size, a.mTime * 1000L, a.permissions)
        } catch (e: com.jcraft.jsch.SftpException) {
            null
        }
    }

    fun mkdirs(path: String) = sh("mkdir -p -- ${sq(path)}")

    fun remove(path: String) = sh("rm -rf -- ${sq(path)}")

    fun renameTo(from: String, to: String) = sh("mv -- ${sq(from)} ${sq(to)}")

    fun touch(path: String) = sh("touch -- ${sq(path)}")

    fun openIn(path: String): InputStream {
        val ch = openSftp()
        val raw = try { ch.get(path) } catch (e: Throwable) { ch.disconnect(); throw e }
        return object : FilterInputStream(BufferedInputStream(raw, 65536)) {
            override fun close() {
                try { super.close() } finally { ch.disconnect() }
            }
        }
    }

    fun openOut(path: String): OutputStream {
        val ch = openSftp()
        val raw = try { ch.put(path, ChannelSftp.OVERWRITE) } catch (e: Throwable) { ch.disconnect(); throw e }
        return object : FilterOutputStream(BufferedOutputStream(raw, 65536)) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() {
                try { super.close() } finally { ch.disconnect() }
            }
        }
    }

    fun upload(input: InputStream, remote: String) = synchronized(lock) {
        sftp().put(input, remote, ChannelSftp.OVERWRITE)
    }

    fun download(remote: String, out: OutputStream) = synchronized(lock) {
        sftp().get(remote, out)
    }

    fun uploadTree(ctx: Context, dir: DocumentFile, remoteDir: String, skip: List<String>, run: TaskRun) {
        if (run.stop) throw TaskStopped()
        sh("mkdir -p ${sq(remoteDir)}")
        for (f in dir.listFiles()) {
            val n = f.name ?: continue
            if (TreeScan.excluded(n, skip)) {
                run.skipped++
                continue
            }
            if (f.isDirectory) {
                uploadTree(ctx, f, joinPath(remoteDir, n), skip, run)
            } else {
                run.detail = n
                ctx.contentResolver.openInputStream(f.uri)?.use { upload(TrackedIn(it, run), joinPath(remoteDir, n)) }
                run.done++
                TaskBoard.publish()
            }
        }
    }


    private fun addToZip(rd: ChannelSftp, z: ZipOutputStream, remote: String, entry: String) {
        for (e in rd.ls(remote)) {
            val en = e as ChannelSftp.LsEntry
            val n = en.filename
            if (n == "." || n == "..") continue
            val full = joinPath(remote, n)
            val name = "$entry/$n"
            when {
                en.attrs.isDir -> {
                    z.putNextEntry(ZipEntry("$name/")); z.closeEntry()
                    addToZip(rd, z, full, name)
                }
                en.attrs.isLink -> {}
                else -> {
                    z.putNextEntry(ZipEntry(name))
                    rd.get(full, NoCloseOut(z))
                    z.closeEntry()
                }
            }
        }
    }

    fun zipTo(parent: String, name: String, dir: Boolean, out: OutputStream) {
        val rd = openSftp()
        try {
            ZipOutputStream(BufferedOutputStream(out, 65536)).use { z ->
                val full = joinPath(parent, name)
                if (dir) {
                    z.putNextEntry(ZipEntry("$name/")); z.closeEntry()
                    addToZip(rd, z, full, name)
                } else {
                    z.putNextEntry(ZipEntry(name))
                    rd.get(full, NoCloseOut(z))
                    z.closeEntry()
                }
            }
        } finally {
            rd.disconnect()
        }
    }

    fun zipHere(parent: String, name: String, dir: Boolean) {
        val wr = openSftp()
        try {
            zipTo(parent, name, dir, wr.put(joinPath(parent, "$name.zip"), ChannelSftp.OVERWRITE))
        } finally {
            wr.disconnect()
        }
    }

    fun unzipHere(parent: String, name: String) {
        val rd = openSftp()
        val wr = openSftp()
        val made = HashSet<String>()
        fun mk(d: String) { if (made.add(d)) sh("mkdir -p ${sq(d)}") }
        try {
            ZipInputStream(BufferedInputStream(rd.get(joinPath(parent, name)))).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val rel = e.name.trimStart('/')
                    if (rel.isEmpty() || rel.split('/').contains("..")) continue
                    val target = joinPath(parent, rel.trimEnd('/'))
                    if (e.isDirectory) mk(target)
                    else {
                        mk(target.substringBeforeLast('/', "/").ifEmpty { "/" })
                        wr.put(NoCloseIn(z), target, ChannelSftp.OVERWRITE)
                    }
                }
            }
        } finally {
            rd.disconnect(); wr.disconnect()
        }
    }

    companion object {
        const val MAX = 100
        const val STREAM_LIMIT = 15 * 60 * 1000L
    }
}
