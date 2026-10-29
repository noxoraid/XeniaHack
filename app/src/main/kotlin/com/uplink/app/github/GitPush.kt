package com.uplink.app

import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

object GitPush {
    private const val PARALLEL = 4

    fun target(subdir: String, name: String): String {
        val base = subdir.trim('/')
        return if (base.isEmpty()) name else "$base/$name"
    }

    private class Local(val full: String, val place: String, val size: Long)

    /** SHA-1 gaya git ("blob <size>\0" + isi). Native dulu, kalau tidak ada pakai Kotlin (juga untuk Root fs). */
    private fun hashOf(fs: FsBackend, f: Local): String? {
        if (isLocal(fs)) GitHash.blob(f.full)?.let { return it }
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            md.update("blob ${f.size}\u0000".toByteArray())
            fs.open(f.full).use { input ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Path remote yang cocok dengan daftar skip (di segmen manapun) tidak ikut dihapus. */
    private fun skippedRemote(path: String, skip: List<String>): Boolean =
        path.split('/').any { TreeScan.excluded(it, skip) }

    fun upload(
        fs: FsBackend, dir: String, link: RepoLink, message: String,
        patterns: List<String>, run: TaskRun, prune: Boolean = true, log: (String) -> Unit = {},
    ) {
        val skip = patterns + ".git"
        val scan = TreeScan.scan(fs, dir, skip)
        run.detail = tr("Connecting to GitHub")
        log(tr("Connecting to {0} ({1})", link.repo, link.branch))
        val parent = GitHubApi.ensureHead(link.repo, link.branch)

        // 1. Daftar file remote (path -> sha). Kalau gagal, jangan menghapus apa pun.
        var listed = true
        val remote: Map<String, String> = try {
            GitHubApi.blobList(link.repo, parent.second).associate { it.path to it.sha }
        } catch (_: Exception) {
            listed = false
            emptyMap()
        }
        val remoteShas: Map<String, String> = HashMap<String, String>().also { m ->
            for ((p, s) in remote) m.putIfAbsent(s, p)
        }

        // 2. Kumpulkan file lokal.
        val files = ArrayList<Local>()
        fun walk(path: String, rel: String) {
            for (child in fs.list(path)) {
                if (run.stop) throw TaskStopped()
                if (TreeScan.excluded(child.name, skip)) continue
                val full = joinPath(path, child.name)
                val name = if (rel.isEmpty()) child.name else "$rel/${child.name}"
                if (child.dir) {
                    walk(full, name)
                    continue
                }
                if (child.size > GitHubApi.BLOB_LIMIT) {
                    run.skipped++
                    continue
                }
                files.add(Local(full, target(link.subdir, name), child.size))
            }
        }
        run.detail = tr("Scanning")
        walk(dir, "")
        if (files.isEmpty()) throw Exception(tr("Nothing changed"))
        log(tr("Found {0} files, skipping {1}", files.size, skip.joinToString(", ")))
        run.total = files.size
        run.totalBytes = scan.bytes

        // 3. Bandingkan hash: sama = lewati, sha sudah ada di remote (rename/copy) = pakai ulang tanpa upload.
        val entries = ArrayList<JSONObject>()
        val toUpload = ArrayList<Local>()
        val localPaths = HashSet<String>()
        for (f in files) {
            if (run.stop) throw TaskStopped()
            localPaths.add(f.place)
            run.detail = f.place
            val sha = if (remote.isEmpty()) null else hashOf(fs, f)
            if (sha != null && remote[f.place] == sha) {
                run.bytes += f.size
                run.done++
                continue
            }
            if (sha != null && remoteShas.containsKey(sha)) {
                entries.add(GitHubApi.blobEntry(f.place, sha))
                run.bytes += f.size
                run.done++
                continue
            }
            toUpload.add(f)
        }

        // 4. Upload paralel hanya untuk file yang benar-benar baru/berubah.
        if (toUpload.isNotEmpty()) {
            val lock = Any()
            val threads = if (isLocal(fs)) PARALLEL else 1
            val pool = Executors.newFixedThreadPool(threads)
            val jobs = ArrayList<Future<JSONObject>>()
            try {
                for (f in toUpload) {
                    jobs.add(pool.submit<JSONObject> {
                        if (run.stop) throw TaskStopped()
                        val sha = fs.open(f.full).use { GitHubApi.blob(link.repo, it) }
                        synchronized(lock) {
                            run.bytes += f.size
                            run.done++
                            run.detail = f.place
                        }
                        log(tr("Uploaded {0}", f.place))
                        TaskBoard.publish()
                        GitHubApi.blobEntry(f.place, sha)
                    })
                }
                for (j in jobs) {
                    try {
                        entries.add(j.get())
                    } catch (e: ExecutionException) {
                        throw e.cause ?: e
                    }
                }
            } finally {
                pool.shutdownNow()
            }
        }

        // 5. File yang sudah dihapus lokal ikut dihapus di remote (hanya di dalam subfolder tujuan).
        var removed = 0
        if (prune && listed) {
            val base = link.subdir.trim('/')
            val prefix = if (base.isEmpty()) "" else "$base/"
            for (path in remote.keys) {
                if (prefix.isNotEmpty() && !path.startsWith(prefix)) continue
                if (path in localPaths) continue
                if (skippedRemote(path.removePrefix(prefix), skip)) continue
                entries.add(GitHubApi.deleteEntry(path))
                log(tr("Removed from GitHub: {0}", path))
                removed++
            }
        }

        if (entries.isEmpty()) throw Exception(tr("Nothing changed"))
        run.detail = tr("Committing")
        val sha = GitHubApi.commit(link.repo, link.branch, parent, entries, message)
        run.detail = sha.take(7) + if (removed > 0) "  -$removed" else ""
        log(tr("Committed {0}: {1} changes, {2} removed", sha.take(7), entries.size - removed, removed))
    }
}
