package com.uplink.app

import android.content.Context
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.BatchingProgressMonitor
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.security.MessageDigest

object GitEngine {
    const val VERSION = "5.13.3"

    fun store(ctx: Context): File = File(NoxoraFs.home(ctx), ".nxgit").also { it.mkdirs() }

    fun installed(ctx: Context): Boolean = File(store(ctx), "engine.json").isFile

    private fun prepare(ctx: Context) {
        if (System.getProperty("user.home").isNullOrEmpty()) System.setProperty("user.home", ctx.filesDir.absolutePath)
    }

    fun install(ctx: Context, run: TaskRun, log: (String) -> Unit) {
        prepare(ctx)
        run.detail = tr("Preparing")
        log("$ git --version")
        Class.forName("org.eclipse.jgit.api.Git")
        log("git version $VERSION (built in, no Termux)")
        val dir = store(ctx)
        log(tr("Storage: {0}", dir.path))
        File(dir, "engine.json").writeText("{\"engine\":\"jgit\",\"version\":\"$VERSION\"}")
        log(tr("Git is ready"))
    }

    fun forget(ctx: Context, folder: String) {
        gitDirOf(ctx, folder).deleteRecursively()
    }

    fun gitDirOf(ctx: Context, folder: String): File {
        val id = MessageDigest.getInstance("SHA-1").digest(folder.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        return File(store(ctx), "$id.git")
    }

    private fun open(ctx: Context, folder: String): Repository {
        prepare(ctx)
        val gitDir = gitDirOf(ctx, folder)
        val repo = FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(File(folder)).build()
        if (!File(gitDir, "HEAD").isFile) repo.create()
        return repo
    }

    fun history(ctx: Context, folder: String): List<String> {
        if (!File(gitDirOf(ctx, folder), "HEAD").isFile) return emptyList()
        open(ctx, folder).use { repo ->
            if (repo.resolve(Constants.HEAD) == null) return emptyList()
            Git(repo).use { git ->
                return git.log().setMaxCount(20).call().map { it.abbreviate(7).name() + "  " + it.shortMessage }
            }
        }
    }

    fun push(
        ctx: Context, folder: String, link: RepoLink, message: String,
        patterns: List<String>, prune: Boolean, run: TaskRun, log: (String) -> Unit,
    ): String {
        if (link.subdir.isNotEmpty()) throw Exception(tr("A folder inside the repository needs the token mode"))
        val url = "https://github.com/${link.repo}.git"
        val creds = UsernamePasswordCredentialsProvider(GitAccount.user, GitAccount.token)
        val branch = link.branch
        open(ctx, folder).use { repo ->
            Git(repo).use { git ->
                val exclude = File(repo.directory, "info/exclude")
                exclude.parentFile?.mkdirs()
                exclude.writeText((patterns + ".git").distinct().joinToString("\n") + "\n")
                repo.updateRef(Constants.HEAD).link("refs/heads/$branch")
                run.detail = tr("Connecting to GitHub")
                log("$ git fetch origin $branch")
                try {
                    git.fetch().setRemote(url)
                        .setRefSpecs(RefSpec("+refs/heads/$branch:refs/remotes/origin/$branch"))
                        .setCredentialsProvider(creds).call()
                } catch (e: TransportException) {
                    if (e.message.orEmpty().contains("does not have")) log(tr("Branch {0} is new on GitHub", branch)) else throw e
                }
                if (run.stop) throw TaskStopped()
                val remote = repo.resolve("refs/remotes/origin/$branch")
                if (remote != null) {
                    val move = repo.updateRef("refs/heads/$branch")
                    move.setNewObjectId(remote)
                    move.setForceUpdate(true)
                    move.update()
                    git.reset().setMode(ResetCommand.ResetType.MIXED).setRef("refs/heads/$branch").call()
                }
                run.detail = tr("Scanning")
                log("$ git add .")
                git.add().addFilepattern(".").call()
                if (prune) {
                    log("$ git add -u")
                    git.add().setUpdate(true).addFilepattern(".").call()
                }
                val status = git.status().call()
                val added = status.added.size
                val changed = status.changed.size
                val removed = status.removed.size
                log(tr("{0} new, {1} changed, {2} removed", added, changed, removed))
                if (added + changed + removed == 0) throw Exception(tr("Nothing changed"))
                run.detail = tr("Committing")
                val who = PersonIdent(GitAccount.user, GitAccount.email)
                log("$ git commit -m \"$message\"")
                val commit = git.commit().setMessage(message).setAuthor(who).setCommitter(who).call()
                run.detail = tr("Uploading")
                log("$ git push origin $branch")
                val monitor = object : BatchingProgressMonitor() {
                    override fun onUpdate(taskName: String, workCurr: Int) {}

                    override fun onEndTask(taskName: String, workCurr: Int) {}

                    override fun onUpdate(taskName: String, workCurr: Int, workTotal: Int, percentDone: Int) {
                        run.detail = "$taskName $percentDone%"
                        TaskBoard.publish()
                    }

                    override fun onEndTask(taskName: String, workCurr: Int, workTotal: Int, percentDone: Int) {
                        log("$taskName $workCurr/$workTotal")
                    }

                    override fun isCancelled(): Boolean = run.stop
                }
                val results = git.push().setRemote(url)
                    .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
                    .setCredentialsProvider(creds)
                    .setProgressMonitor(monitor).call()
                for (r in results) {
                    for (u in r.remoteUpdates) {
                        if (u.status != RemoteRefUpdate.Status.OK && u.status != RemoteRefUpdate.Status.UP_TO_DATE) {
                            throw Exception(u.status.name + (u.message?.let { ": $it" } ?: ""))
                        }
                    }
                }
                log(tr("Pushed {0}", commit.abbreviate(7).name()))
                return commit.abbreviate(7).name()
            }
        }
    }
}
