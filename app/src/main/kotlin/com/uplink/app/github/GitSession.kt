package com.uplink.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class GitView { Hub, Setup, Repos, Upload, Browse, Edit, History, Actions, Releases, Pulls, Engine }

private fun freePath(fs: FsBackend, parent: String, name: String): String {
    var candidate = joinPath(parent, name)
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var n = 2
    while (fs.kind(candidate) != 0) {
        candidate = joinPath(parent, "$stem ($n)$ext")
        n++
    }
    return candidate
}

class GitSession(val ctx: Context, val fs: FsBackend, val dir: String, private val scope: CoroutineScope) {
    var view by mutableStateOf(if (GitAccount.ready) GitView.Hub else GitView.Setup)
    var link by mutableStateOf(GitAccount.linkOf(dir))
    var busy by mutableStateOf<String?>(null)
    var version by mutableIntStateOf(0)
    var editPath by mutableStateOf("")
    var editSha by mutableStateOf<String?>(null)
    var editText by mutableStateOf("")
    val console = mutableStateListOf<String>()
    var consoleOpen by mutableStateOf(true)

    fun <T> load(label: String, job: () -> T, done: (T) -> Unit) {
        scope.launch {
            busy = label
            try {
                done(withContext(Dispatchers.IO) { job() })
            } catch (e: Throwable) {
                toast(ctx, e.message ?: e.toString())
            }
            busy = null
        }
    }

    fun <T> quiet(job: () -> T, done: (T) -> Unit) {
        scope.launch {
            try {
                done(withContext(Dispatchers.IO) { job() })
            } catch (_: Throwable) {
            }
        }
    }

    fun say(line: String) {
        val clock = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        synchronized(console) {
            for (part in line.lines()) {
                if (part.isBlank()) continue
                if (console.size >= 600) repeat(100) { console.removeAt(0) }
                console.add("$clock  $part")
            }
        }
    }

    fun autoMessage(): String = tr("Update {0}", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

    fun downloadZip(path: String) {
        val at = link ?: return
        val label = if (path.isEmpty()) at.repo.substringAfter('/') else path.substringAfterLast('/')
        TaskBoard.start(TaskKind.Git, tr("Zip {0}", label)) { run ->
            val prefix = if (path.isEmpty()) "" else "$path/"
            say(tr("Listing {0}", at.repo))
            val files = GitHubApi.remoteBlobs(at.repo, at.branch).filter { it.path.startsWith(prefix) }
            if (files.isEmpty()) throw Exception(tr("Nothing to download"))
            run.total = files.size
            run.totalBytes = files.sumOf { it.size }
            val dest = freePath(fs, dir, "$label.zip")
            ZipOutputStream(fs.create(dest)).use { zip ->
                for (f in files) {
                    if (run.stop) throw TaskStopped()
                    val rel = f.path.removePrefix(prefix)
                    run.detail = rel
                    zip.putNextEntry(ZipEntry("$label/$rel"))
                    GitHubApi.blobStream(at.repo, f.sha).use { input -> input.copyTo(TrackedOut(zip, run), 65536) }
                    zip.closeEntry()
                    run.done++
                    TaskBoard.publish()
                }
            }
            say(tr("Saved {0}", dest))
        }
        toast(ctx, tr("Running in background, see Tasks"))
    }

    fun relocate(from: String, to: String, keep: Boolean) {
        val at = link ?: return
        val name = from.substringAfterLast('/')
        load(if (keep) tr("Copying") else tr("Renaming"), {
            GitHubApi.relocate(at.repo, at.branch, from, to, keep, if (keep) tr("Copy {0}", name) else tr("Rename {0}", name))
        }) {
            say(if (keep) tr("Copied {0} to {1}", from, to) else tr("Renamed {0} to {1}", from, to))
            version++
        }
    }

    fun clear(path: String) {
        val at = link ?: return
        load(tr("Clearing"), { GitHubApi.clearFile(at.repo, at.branch, path, tr("Clear {0}", path.substringAfterLast('/'))) }) {
            say(tr("Cleared {0}", path))
            version++
        }
    }

    fun bind(next: RepoLink) {
        link = next
        GitAccount.link(ctx, dir, next)
    }

    fun unbind() {
        link = null
        GitAccount.unlink(ctx, dir)
    }

    fun open(next: GitView) {
        if (link == null && next != GitView.Repos && next != GitView.Setup) {
            toast(ctx, tr("Choose a repository first"))
            view = GitView.Repos
        } else {
            view = next
        }
    }

    fun back(): Boolean = when (view) {
        GitView.Hub -> false
        GitView.Edit -> {
            view = GitView.Browse
            true
        }
        GitView.Setup -> if (GitAccount.ready) {
            view = GitView.Hub
            true
        } else {
            false
        }
        else -> {
            view = GitView.Hub
            true
        }
    }

    fun downloadFile(path: String, size: Long) {
        val at = link ?: return
        val name = path.substringAfterLast('/')
        TaskBoard.start(TaskKind.Git, tr("Download {0}", name)) { run ->
            val dest = freePath(fs, dir, name)
            run.detail = name
            run.totalBytes = size
            GitHubApi.rawFile(at.repo, path, at.branch).use { input ->
                fs.create(dest).use { out -> input.copyTo(TrackedOut(out, run), 65536) }
            }
        }
        toast(ctx, tr("Running in background, see Tasks"))
    }

    fun downloadTree(path: String) {
        val at = link ?: return
        val label = if (path.isEmpty()) at.repo.substringAfter('/') else path.substringAfterLast('/')
        TaskBoard.start(TaskKind.Git, tr("Download {0}", label)) { run ->
            val prefix = if (path.isEmpty()) "" else "$path/"
            val files = GitHubApi.remoteBlobs(at.repo, at.branch).filter { it.path.startsWith(prefix) }
            if (files.isEmpty()) throw Exception(tr("Nothing to download"))
            run.total = files.size
            run.totalBytes = files.sumOf { it.size }
            val root = freePath(fs, dir, label)
            for (f in files) {
                if (run.stop) throw TaskStopped()
                val rel = f.path.removePrefix(prefix)
                run.detail = rel
                val dest = joinPath(root, rel)
                fs.mkdirs(parentOf(dest))
                GitHubApi.blobStream(at.repo, f.sha).use { input ->
                    fs.create(dest).use { out -> input.copyTo(TrackedOut(out, run), 65536) }
                }
                run.done++
                TaskBoard.publish()
            }
        }
        toast(ctx, tr("Running in background, see Tasks"))
    }

    fun downloadArtifact(art: ArtifactRow, saveTo: String, extract: Boolean) {
        val at = link ?: return
        TaskBoard.start(TaskKind.Git, tr("Download {0}", art.name)) { run ->
            val safe = art.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = joinPath(fixPath(saveTo, dir), safe)
            val zipName = "$safe.zip"
            fs.mkdirs(target)
            run.detail = art.name
            run.totalBytes = art.size
            GitHubApi.artifactStream(at.repo, art.id).use { input ->
                fs.create(joinPath(target, zipName)).use { out -> input.copyTo(TrackedOut(out, run), 65536) }
            }
            if (extract) {
                run.detail = tr("Extracting")
                unzipItem(fs, target, zipName)
                fs.delete(joinPath(target, zipName))
            }
        }
        toast(ctx, tr("Running in background, see Tasks"))
    }
}
