package com.uplink.app

import android.content.Context
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

class TrackedIn(inp: InputStream, private val run: TaskRun) : FilterInputStream(inp) {
    override fun read(): Int {
        if (run.stop) throw TaskStopped()
        val b = super.read()
        if (b >= 0) run.bytes += 1
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (run.stop) throw TaskStopped()
        val n = super.read(b, off, len)
        if (n > 0) {
            run.bytes += n
            TaskBoard.publish()
        }
        return n
    }
}

class TrackedOut(out: OutputStream, private val run: TaskRun) : FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (run.stop) throw TaskStopped()
        out.write(b, off, len)
        run.bytes += len
        TaskBoard.publish()
    }
}

private fun pump(input: InputStream, output: OutputStream, run: TaskRun) {
    val buf = ByteArray(65536)
    while (true) {
        if (run.stop) throw TaskStopped()
        val n = input.read(buf)
        if (n < 0) break
        output.write(buf, 0, n)
        run.bytes += n
        TaskBoard.publish()
    }
}

fun copyTracked(
    src: FsBackend,
    from: String,
    dst: FsBackend,
    to: String,
    patterns: List<String>,
    run: TaskRun,
    removeSource: Boolean,
) {
    if (run.stop) throw TaskStopped()
    val name = from.trimEnd('/').substringAfterLast('/')
    if (TreeScan.excluded(name, patterns)) return
    when (src.kind(from)) {
        0 -> throw Exception(tr("Source not found: {0}", name))
        2 -> {
            dst.mkdirs(to)
            for (child in src.list(from)) {
                copyTracked(src, joinPath(from, child.name), dst, joinPath(to, child.name), patterns, run, removeSource)
            }
            if (removeSource && src.list(from).isEmpty()) src.delete(from)
        }
        else -> {
            run.detail = name
            src.open(from).use { input -> dst.create(to).use { output -> pump(input, output, run) } }
            run.done++
            TaskBoard.publish()
            if (removeSource) src.delete(from)
        }
    }
}

private fun sameServer(a: Profile, b: Profile) = a.host == b.host && a.port == b.port && a.user == b.user

fun startSend(
    ctx: Context,
    fs: FsBackend,
    dir: String,
    names: List<String>,
    profile: Profile,
    dest: String,
    move: Boolean,
    patterns: List<String>,
) {
    val appCtx = ctx.applicationContext
    val preview = names.take(2).joinToString(", ") + if (names.size > 2) " +${names.size - 2}" else ""
    val title = if (move) tr("Move {0} to {1}", preview, profile.name) else tr("Send {0} to {1}", preview, profile.name)
    TaskBoard.start(if (move) TaskKind.Move else TaskKind.Send, title) { run ->
        val live = Hub.vps
        val shared = live != null && sameServer(live.profile, profile)
        val vps = if (shared) live!! else Vps(profile).also { it.connect() }
        try {
            run.detail = tr("Preparing")
            val fixed = fixPath(dest, vps.home())
            if (vps.kind(fixed) == 1) throw Exception(tr("Destination is a file, pick a folder"))
            val target = VpsFs(vps)
            target.mkdirs(fixed)
            run.detail = tr("Scanning")
            var files = 0
            var bytes = 0L
            for (n in names) {
                val r = TreeScan.scan(fs, joinPath(dir, n), patterns)
                files += r.files
                bytes += r.bytes
                run.skipped += r.skipped
            }
            run.total = files
            run.totalBytes = bytes
            for (n in names) copyTracked(fs, joinPath(dir, n), target, joinPath(fixed, n), patterns, run, move)
            Cfg.setSendPath(appCtx, fixed)
        } finally {
            if (!shared) try { vps.close() } catch (_: Throwable) {}
        }
    }
}

fun startPaste(clip: FsClipData, fs: FsBackend, path: String, uniqueName: (String) -> String) {
    val same = clip.fs === fs
    val preview = clip.names.take(2).joinToString(", ") + if (clip.names.size > 2) " +${clip.names.size - 2}" else ""
    val title = if (clip.cut) tr("Move {0}", preview) else tr("Copy {0}", preview)
    TaskBoard.start(if (clip.cut) TaskKind.Move else TaskKind.Copy, title) { run ->
        val zipDst = fs as? ZipFs
        zipDst?.beginBatch()
        try {
        class PastePlan(val from: String, val to: String, val cut: Boolean, val files: Int)

        val plan = ArrayList<PastePlan>()
        run.detail = tr("Scanning")
        for (n in clip.names) {
            val from = joinPath(clip.dir, n)
            val inPlace = same && clip.dir == path
            if (inPlace && clip.cut) continue
            val to = if (inPlace) uniqueName(n) else joinPath(path, n)
            if (same && !inPlace && (to == from || to.startsWith("$from/"))) throw Exception(tr("Cannot paste a folder into itself"))
            val r = TreeScan.scan(clip.fs, from, emptyList())
            run.total += r.files
            run.totalBytes += r.bytes
            plan.add(PastePlan(from, to, clip.cut && !inPlace, r.files))
        }
        for (j in plan) {
            if (run.stop) throw TaskStopped()
            if (j.cut && same) {
                run.detail = j.from.substringAfterLast('/')
                fs.rename(j.from, j.to)
                run.done += j.files
                TaskBoard.publish()
            } else {
                copyTracked(clip.fs, j.from, fs, j.to, emptyList(), run, false)
                if (j.cut) clip.fs.delete(j.from)
            }
        }
        } finally {
            zipDst?.endBatch()
        }
    }
}

fun startDelete(fs: FsBackend, dir: String, names: List<String>) {
    TaskBoard.start(TaskKind.Delete, if (names.size == 1) tr("Delete 1 item") else tr("Delete {0} items", names.size)) { run ->
        run.total = names.size
        for (n in names) {
            if (run.stop) throw TaskStopped()
            run.detail = n
            fs.delete(joinPath(dir, n))
            run.done++
            TaskBoard.publish()
        }
    }
}
