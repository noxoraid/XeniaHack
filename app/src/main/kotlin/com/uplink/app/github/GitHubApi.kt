package com.uplink.app

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ProtocolException

class RepoRow(val full: String, val priv: Boolean, val branch: String, val about: String)

class EntryRow(val name: String, val path: String, val dir: Boolean, val size: Long)

class CommitRow(val sha: String, val message: String, val author: String, val date: String)

class RunRow(val id: Long, val title: String, val branch: String, val status: String, val conclusion: String, val number: Int, val date: String) {
    val live: Boolean get() = status == "in_progress" || status == "queued" || status == "waiting" || status == "pending"
}

class ArtifactRow(val id: Long, val name: String, val size: Long, val expired: Boolean, val branch: String, val date: String = "")

class BlobRef(val path: String, val sha: String, val size: Long, val mode: String = "100644")

class AssetRow(val id: Long, val name: String, val size: Long, val url: String)

class ReleaseRow(
    val id: Long, val tag: String, val name: String, val draft: Boolean, val pre: Boolean,
    val page: String, val date: String, val assets: List<AssetRow>,
)

class PullRow(
    val number: Int, val title: String, val state: String, val merged: Boolean, val head: String,
    val base: String, val author: String, val date: String, val body: String, val page: String,
)

class PullFile(val name: String, val status: String, val adds: Int, val dels: Int)

class RemoteText(val text: String, val sha: String)

class ApiReply(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299

    fun obj(): JSONObject = JSONObject(body)

    fun list(): JSONArray = JSONArray(body)
}

object GitHubApi {
    private const val BASE = "https://api.github.com"
    private const val JSON_TYPE = "application/vnd.github+json"
    private const val RAW_TYPE = "application/vnd.github.raw+json"
    const val BLOB_LIMIT = 95L * 1024 * 1024

    private fun enc(v: String): String = Uri.encode(v, "/")

    private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key)

    private fun stamp(iso: String): String {
        val at = iso.replace('T', ' ').removeSuffix("Z").take(19)
        return try {
            val zone = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            zone.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val moment = zone.parse(at) ?: return at.take(16)
            val local = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            local.timeZone = java.util.TimeZone.getDefault()
            local.format(moment)
        } catch (_: Exception) {
            at.take(16)
        }
    }

    private fun connect(path: String, method: String, accept: String): HttpURLConnection {
        val c = AiWire.connect(if (path.startsWith("http")) path else BASE + path)
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer ${GitAccount.token}")
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            c.requestMethod = method
        } catch (_: ProtocolException) {
            c.requestMethod = "POST"
            c.setRequestProperty("X-HTTP-Method-Override", method)
        }
        return c
    }

    private fun readLimited(stream: InputStream?): String {
        if (stream == null) return ""
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        stream.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > 16 shl 20) throw Exception(tr("Response too large"))
                out.write(buf, 0, n)
            }
        }
        return out.toString("UTF-8")
    }

    private fun failure(code: Int, body: String): Exception {
        val why = AiWire.reason(body)
        return Exception(
            when {
                code == 401 -> tr("GitHub rejected the token")
                code == 404 -> tr("Not found on GitHub")
                why.isNotEmpty() -> why
                else -> tr("GitHub error {0}", code)
            },
        )
    }

    fun call(method: String, path: String, body: String? = null, accept: String = JSON_TYPE): ApiReply {
        val c = connect(path, method, accept)
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            return ApiReply(code, readLimited(if (code >= 400) c.errorStream else c.inputStream))
        } finally {
            c.disconnect()
        }
    }

    private fun need(r: ApiReply): ApiReply {
        if (!r.ok) throw failure(r.code, r.body)
        return r
    }

    fun fetch(path: String, accept: String = JSON_TYPE): InputStream {
        var url = if (path.startsWith("http")) path else BASE + path
        var signed = true
        repeat(6) {
            val c = AiWire.connect(url)
            c.instanceFollowRedirects = false
            c.connectTimeout = 20000
            c.readTimeout = 60000
            if (signed) {
                c.setRequestProperty("Authorization", "Bearer ${GitAccount.token}")
                c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            c.setRequestProperty("Accept", accept)
            val code = c.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val next = c.getHeaderField("Location") ?: throw Exception(tr("GitHub error {0}", code))
                c.disconnect()
                url = next
                signed = false
            } else if (code in 200..299) {
                return c.inputStream
            } else {
                throw failure(code, readLimited(c.errorStream))
            }
        }
        throw Exception(tr("Too many redirects"))
    }

    fun login(): String = need(call("GET", "/user")).obj().getString("login")

    fun repos(): List<RepoRow> {
        val rows = ArrayList<RepoRow>()
        for (page in 1..5) {
            val arr = need(call("GET", "/user/repos?per_page=100&sort=updated&page=$page")).list()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                rows.add(RepoRow(o.getString("full_name"), o.optBoolean("private"), o.optString("default_branch", "main"), text(o, "description")))
            }
            if (arr.length() < 100) break
        }
        return rows
    }

    fun createRepo(name: String, priv: Boolean): RepoRow {
        val body = JSONObject().put("name", name).put("private", priv).toString()
        val o = need(call("POST", "/user/repos", body)).obj()
        return RepoRow(o.getString("full_name"), o.optBoolean("private"), o.optString("default_branch", "main"), "")
    }

    fun repoInfo(repo: String): RepoRow {
        val o = need(call("GET", "/repos/$repo")).obj()
        return RepoRow(o.getString("full_name"), o.optBoolean("private"), o.optString("default_branch", "main"), text(o, "description"))
    }

    fun branches(repo: String): List<String> {
        val arr = need(call("GET", "/repos/$repo/branches?per_page=100")).list()
        return List(arr.length()) { arr.getJSONObject(it).getString("name") }
    }

    private fun head(repo: String, branch: String): Pair<String, String>? {
        val r = call("GET", "/repos/$repo/git/ref/heads/${enc(branch)}")
        if (r.code == 404 || r.code == 409) return null
        val commit = need(r).obj().getJSONObject("object").getString("sha")
        val tree = need(call("GET", "/repos/$repo/git/commits/$commit")).obj().getJSONObject("tree").getString("sha")
        return commit to tree
    }

    fun ensureHead(repo: String, branch: String): Pair<String, String> {
        head(repo, branch)?.let { return it }
        val fallback = repoInfo(repo).branch
        var base = head(repo, fallback)
        if (base == null) {
            val seed = JSONObject()
                .put("message", "Initial commit")
                .put("content", Base64.encodeToString("\n".toByteArray(), Base64.NO_WRAP))
            need(call("PUT", "/repos/$repo/contents/.gitkeep", seed.toString()))
            base = head(repo, fallback) ?: throw Exception(tr("Repository is empty"))
        }
        if (branch != fallback) {
            val ref = JSONObject().put("ref", "refs/heads/$branch").put("sha", base.first)
            need(call("POST", "/repos/$repo/git/refs", ref.toString()))
        }
        return base
    }

    fun createBranch(repo: String, name: String, from: String) {
        val base = head(repo, from) ?: throw Exception(tr("Branch not found"))
        val ref = JSONObject().put("ref", "refs/heads/$name").put("sha", base.first)
        need(call("POST", "/repos/$repo/git/refs", ref.toString()))
    }

    fun blob(repo: String, input: InputStream): String {
        val c = connect("/repos/$repo/git/blobs", "POST", JSON_TYPE)
        try {
            c.doOutput = true
            c.setChunkedStreamingMode(65536)
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { out ->
                out.write("{\"encoding\":\"base64\",\"content\":\"".toByteArray())
                val buf = ByteArray(3 * 16384)
                while (true) {
                    val n = fill(input, buf)
                    if (n <= 0) break
                    out.write(Base64.encode(buf, 0, n, Base64.NO_WRAP))
                    if (n < buf.size) break
                }
                out.write("\"}".toByteArray())
            }
            val code = c.responseCode
            val body = readLimited(if (code >= 400) c.errorStream else c.inputStream)
            if (code !in 200..299) throw failure(code, body)
            return JSONObject(body).getString("sha")
        } finally {
            c.disconnect()
        }
    }

    private fun fill(input: InputStream, buf: ByteArray): Int {
        var got = 0
        while (got < buf.size) {
            val n = input.read(buf, got, buf.size - got)
            if (n < 0) break
            got += n
        }
        return got
    }

    fun blobList(repo: String, treeSha: String): List<BlobRef> {
        val o = need(call("GET", "/repos/$repo/git/trees/$treeSha?recursive=1")).obj()
        if (o.optBoolean("truncated")) throw Exception(tr("Repository is too large to list"))
        val arr = o.getJSONArray("tree")
        val out = ArrayList<BlobRef>()
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            if (t.optString("type") == "blob") out.add(BlobRef(t.getString("path"), t.getString("sha"), t.optLong("size"), t.optString("mode", "100644")))
        }
        return out
    }

    fun remoteBlobs(repo: String, branch: String): List<BlobRef> {
        val h = head(repo, branch) ?: throw Exception(tr("Branch not found"))
        return blobList(repo, h.second)
    }

    fun commit(repo: String, branch: String, parent: Pair<String, String>, entries: List<JSONObject>, message: String): String {
        var tree = parent.second
        for (chunk in entries.chunked(300)) {
            val body = JSONObject().put("base_tree", tree).put("tree", JSONArray(chunk)).toString()
            tree = need(call("POST", "/repos/$repo/git/trees", body)).obj().getString("sha")
        }
        if (tree == parent.second) throw Exception(tr("Nothing changed"))
        val who = JSONObject().put("name", GitAccount.user).put("email", GitAccount.email)
        val body = JSONObject()
            .put("message", message)
            .put("tree", tree)
            .put("parents", JSONArray().put(parent.first))
            .put("author", who)
            .toString()
        val sha = need(call("POST", "/repos/$repo/git/commits", body)).obj().getString("sha")
        need(call("PATCH", "/repos/$repo/git/refs/heads/${enc(branch)}", JSONObject().put("sha", sha).toString()))
        return sha
    }

    fun deleteEntry(path: String): JSONObject =
        JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL)

    fun blobEntry(path: String, sha: String, mode: String = "100644"): JSONObject =
        JSONObject().put("path", path).put("mode", mode).put("type", "blob").put("sha", sha)

    fun entries(repo: String, path: String, branch: String): List<EntryRow> {
        val r = need(call("GET", "/repos/$repo/contents/${enc(path)}?ref=${Uri.encode(branch)}"))
        val arr = r.list()
        val rows = List(arr.length()) {
            val o = arr.getJSONObject(it)
            EntryRow(o.getString("name"), o.getString("path"), o.getString("type") == "dir", o.optLong("size"))
        }
        return rows.sortedWith(compareBy<EntryRow>({ !it.dir }, { it.name.lowercase() }))
    }

    fun readText(repo: String, path: String, branch: String): RemoteText {
        val o = need(call("GET", "/repos/$repo/contents/${enc(path)}?ref=${Uri.encode(branch)}")).obj()
        if (o.optLong("size") > 1_000_000L) throw Exception(tr("File is too large to edit here"))
        val bytes = Base64.decode(o.optString("content"), Base64.DEFAULT)
        for (b in bytes) if (b.toInt() == 0) throw Exception(tr("This file is not text"))
        return RemoteText(String(bytes, Charsets.UTF_8), o.getString("sha"))
    }

    fun saveText(repo: String, path: String, branch: String, content: String, message: String, sha: String?) {
        val who = JSONObject().put("name", GitAccount.user).put("email", GitAccount.email)
        val body = JSONObject()
            .put("message", message)
            .put("branch", branch)
            .put("committer", who)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        if (sha != null) body.put("sha", sha)
        need(call("PUT", "/repos/$repo/contents/${enc(path)}", body.toString()))
    }

    fun removePaths(repo: String, branch: String, paths: List<String>, message: String) {
        val parent = head(repo, branch) ?: throw Exception(tr("Branch not found"))
        val doomed = blobList(repo, parent.second).filter { b -> paths.any { b.path == it || b.path.startsWith("$it/") } }
        if (doomed.isEmpty()) throw Exception(tr("Nothing changed"))
        val entries = doomed.map {
            JSONObject().put("path", it.path).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL)
        }
        commit(repo, branch, parent, entries, message)
    }

    fun blobStream(repo: String, sha: String): InputStream = fetch("/repos/$repo/git/blobs/$sha", RAW_TYPE)

    fun rawFile(repo: String, path: String, branch: String): InputStream =
        fetch("/repos/$repo/contents/${enc(path)}?ref=${Uri.encode(branch)}", RAW_TYPE)

    fun commits(repo: String, branch: String): List<CommitRow> {
        val arr = need(call("GET", "/repos/$repo/commits?sha=${Uri.encode(branch)}&per_page=40")).list()
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            val c = o.getJSONObject("commit")
            CommitRow(
                o.getString("sha"),
                c.getString("message").lineSequence().firstOrNull().orEmpty(),
                c.getJSONObject("author").optString("name"),
                stamp(c.getJSONObject("author").optString("date")),
            )
        }
    }

    fun runs(repo: String, branch: String?): List<RunRow> {
        val filter = if (branch.isNullOrEmpty()) "" else "&branch=${Uri.encode(branch)}"
        val arr = need(call("GET", "/repos/$repo/actions/runs?per_page=30$filter")).obj().getJSONArray("workflow_runs")
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            RunRow(
                o.getLong("id"),
                text(o, "display_title").ifEmpty { text(o, "name") },
                text(o, "head_branch"),
                text(o, "status"),
                text(o, "conclusion"),
                o.optInt("run_number"),
                stamp(text(o, "created_at")),
            )
        }
    }

    private fun artifactRows(arr: JSONArray): List<ArtifactRow> = List(arr.length()) {
        val o = arr.getJSONObject(it)
        ArtifactRow(
            o.getLong("id"),
            o.getString("name"),
            o.optLong("size_in_bytes"),
            o.optBoolean("expired"),
            o.optJSONObject("workflow_run")?.let { run -> text(run, "head_branch") }.orEmpty(),
            stamp(text(o, "created_at")),
        )
    }

    fun runArtifacts(repo: String, runId: Long): List<ArtifactRow> =
        artifactRows(need(call("GET", "/repos/$repo/actions/runs/$runId/artifacts")).obj().getJSONArray("artifacts"))

    fun allArtifacts(repo: String): List<ArtifactRow> =
        artifactRows(need(call("GET", "/repos/$repo/actions/artifacts?per_page=50")).obj().getJSONArray("artifacts"))

    fun artifactStream(repo: String, id: Long): InputStream = fetch("/repos/$repo/actions/artifacts/$id/zip")

    fun rerun(repo: String, runId: Long) {
        need(call("POST", "/repos/$repo/actions/runs/$runId/rerun"))
    }

    fun cancel(repo: String, runId: Long) {
        need(call("POST", "/repos/$repo/actions/runs/$runId/cancel"))
    }

    fun relocate(repo: String, branch: String, from: String, to: String, keep: Boolean, message: String) {
        val parent = head(repo, branch) ?: throw Exception(tr("Branch not found"))
        val all = blobList(repo, parent.second)
        val hits = all.filter { it.path == from || it.path.startsWith("$from/") }
        if (hits.isEmpty()) throw Exception(tr("Nothing changed"))
        if (all.any { it.path == to || it.path.startsWith("$to/") }) throw Exception(tr("Name already exists"))
        val entries = ArrayList<JSONObject>()
        for (b in hits) {
            entries.add(blobEntry(to + b.path.removePrefix(from), b.sha, b.mode))
            if (!keep) entries.add(deleteEntry(b.path))
        }
        commit(repo, branch, parent, entries, message)
    }

    fun clearFile(repo: String, branch: String, path: String, message: String) {
        val parent = head(repo, branch) ?: throw Exception(tr("Branch not found"))
        val empty = blob(repo, java.io.ByteArrayInputStream(ByteArray(0)))
        commit(repo, branch, parent, listOf(blobEntry(path, empty)), message)
    }

    private fun assetRows(arr: JSONArray?): List<AssetRow> {
        if (arr == null) return emptyList()
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            AssetRow(o.getLong("id"), o.getString("name"), o.optLong("size"), text(o, "browser_download_url"))
        }
    }

    private fun releaseOf(o: JSONObject): ReleaseRow = ReleaseRow(
        o.getLong("id"), text(o, "tag_name"), text(o, "name"), o.optBoolean("draft"), o.optBoolean("prerelease"),
        text(o, "html_url"), stamp(text(o, "created_at")), assetRows(o.optJSONArray("assets")),
    )

    fun releases(repo: String): List<ReleaseRow> {
        val arr = need(call("GET", "/repos/$repo/releases?per_page=30")).list()
        return List(arr.length()) { releaseOf(arr.getJSONObject(it)) }
    }

    fun createRelease(repo: String, tag: String, name: String, notes: String, target: String, draft: Boolean, pre: Boolean): ReleaseRow {
        val body = JSONObject()
            .put("tag_name", tag)
            .put("target_commitish", target)
            .put("name", name.ifBlank { tag })
            .put("body", notes)
            .put("draft", draft)
            .put("prerelease", pre)
            .toString()
        return releaseOf(need(call("POST", "/repos/$repo/releases", body)).obj())
    }

    fun deleteRelease(repo: String, id: Long, tag: String, withTag: Boolean) {
        need(call("DELETE", "/repos/$repo/releases/$id"))
        if (withTag && tag.isNotEmpty()) call("DELETE", "/repos/$repo/git/refs/tags/${enc(tag)}")
    }

    fun deleteAsset(repo: String, id: Long) {
        need(call("DELETE", "/repos/$repo/releases/assets/$id"))
    }

    fun uploadAsset(repo: String, releaseId: Long, name: String, length: Long, input: InputStream, onBytes: (Int) -> Unit): AssetRow {
        val c = connect("https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=${Uri.encode(name)}", "POST", JSON_TYPE)
        c.connectTimeout = 20000
        c.readTimeout = 300000
        try {
            c.doOutput = true
            c.setFixedLengthStreamingMode(length)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { out ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    onBytes(n)
                }
            }
            val code = c.responseCode
            val body = readLimited(if (code >= 400) c.errorStream else c.inputStream)
            if (code !in 200..299) throw failure(code, body)
            return assetRows(JSONArray().put(JSONObject(body)))[0]
        } finally {
            c.disconnect()
        }
    }

    fun tags(repo: String): List<String> {
        val arr = need(call("GET", "/repos/$repo/tags?per_page=50")).list()
        return List(arr.length()) { arr.getJSONObject(it).getString("name") }
    }

    private fun pullOf(o: JSONObject): PullRow = PullRow(
        o.getInt("number"), text(o, "title"), text(o, "state"), !o.isNull("merged_at"),
        o.optJSONObject("head")?.let { text(it, "ref") }.orEmpty(),
        o.optJSONObject("base")?.let { text(it, "ref") }.orEmpty(),
        o.optJSONObject("user")?.let { text(it, "login") }.orEmpty(),
        stamp(text(o, "created_at")), text(o, "body"), text(o, "html_url"),
    )

    fun pulls(repo: String, state: String): List<PullRow> {
        val arr = need(call("GET", "/repos/$repo/pulls?state=$state&per_page=40&sort=created&direction=desc")).list()
        return List(arr.length()) { pullOf(arr.getJSONObject(it)) }
    }

    fun pullFiles(repo: String, number: Int): List<PullFile> {
        val arr = need(call("GET", "/repos/$repo/pulls/$number/files?per_page=100")).list()
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            PullFile(o.getString("filename"), text(o, "status"), o.optInt("additions"), o.optInt("deletions"))
        }
    }

    fun createPull(repo: String, title: String, head: String, base: String, notes: String): PullRow {
        val body = JSONObject().put("title", title).put("head", head).put("base", base).put("body", notes).toString()
        return pullOf(need(call("POST", "/repos/$repo/pulls", body)).obj())
    }
}
