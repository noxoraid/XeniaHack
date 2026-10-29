package com.uplink.app

import android.content.Context

object NxRunTools {
    val names = setOf("node", "py", "install")

    private val say: NxSay = { key, args -> tr(key, *args) }

    fun run(target: AgentTarget, app: Context, action: String, rest: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val vps = (target.source as? VpsFs)?.vps
            ?: return ConsoleOut(tr("nx {0} runs on a connected server, switch the target to the VPS", action), false)
        val words = consoleTokens(rest)
        return try {
            if (action == "install") install(target, vps, app, words, stopped, onOut) else execute(target, vps, app, action, words, stopped, onOut)
        } catch (e: NxRunError) {
            ConsoleOut(e.message.orEmpty(), false)
        }
    }

    private fun readScript(fs: FsBackend, path: String): String {
        val out = java.io.ByteArrayOutputStream()
        fs.open(path).use { input ->
            val buf = ByteArray(8192)
            while (out.size() < NxRun.MAX_SCRIPT) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun stream(vps: Vps, script: String, stopped: () -> Boolean, run: TaskRun, onOut: (String) -> Unit): String {
        val text = StringBuilder()
        vps.stream(NxRun.withCode(script), stopped) { piece ->
            text.append(piece)
            val body = NxRun.visible(text)
            run.detail = body.lines().lastOrNull { it.isNotBlank() }.orEmpty().take(80)
            onOut(body.takeLast(NxRun.OUTPUT_LIMIT))
        }
        if (stopped()) throw TaskStopped()
        return text.toString()
    }

    private fun execute(
        target: AgentTarget, vps: Vps, app: Context, action: String, words: List<String>, stopped: () -> Boolean, onOut: (String) -> Unit,
    ): ConsoleOut {
        val runtime = NxRuntime.values().first { it.action == action }
        val tools = NxEnvReport.vpsTools(app)
        if (tools != null && tools[runtime.binary] != true) {
            return ConsoleOut(tr("{0} is not installed on this server, nx {1} is not available here", runtime.binary, action), false)
        }
        val plan = NxRun.plan(runtime, words, say)
        val fs = target.source
        val file = fixPath(plan.file, target.cwd)
        when (fs.kind(file)) {
            0 -> return ConsoleOut(tr("{0}: not found", file), false)
            2 -> return ConsoleOut(tr("{0}: is a folder", file), false)
        }
        val size = try { fs.meta(file)?.size ?: 0L } catch (_: Exception) { 0L }
        if (size > NxRun.MAX_SCRIPT) {
            return ConsoleOut(tr("{0} is larger than {1}, nx {2} runs script files, not bundles", file, NxFormat.size(NxRun.MAX_SCRIPT), action), false)
        }
        val content = readScript(fs, file)
        shieldReason(content)?.let { return ConsoleOut(it, false) }
        val line = "nx $action " + words.joinToString(" ")
        if (approvalReason(content) != null) {
            onOut(tr("Waiting for your approval"))
            val allowed = ApprovalBoard.request(app, target.place, line, tr("This script contains commands that remove or change things on the server"), stopped)
            if (!allowed) return ConsoleOut(tr("Denied by you: {0}", line), false)
        }
        val script = NxRun.command(runtime, file, plan.args, target.cwd)
        var output = ""
        NxTask.await(TaskKind.Run, tr("Run {0}", file.substringAfterLast('/')), stopped) { r ->
            r.detail = tr("Running on the server")
            output = stream(vps, script, stopped, r, onOut)
        }
        val code = NxRun.codeOf(output)
        return ConsoleOut(NxRun.report(NxRun.visible(output), code, say), code == 0)
    }

    private fun install(
        target: AgentTarget, vps: Vps, app: Context, words: List<String>, stopped: () -> Boolean, onOut: (String) -> Unit,
    ): ConsoleOut {
        val plan = NxInstall.plan(words, say)
        val shown = NxInstall.display(plan, target.cwd)
        val line = "nx install " + words.joinToString(" ")
        onOut(tr("Waiting for your approval"))
        val allowed = ApprovalBoard.request(app, target.place, line, tr("This installs software on the server: {0}", shown), stopped)
        if (!allowed) return ConsoleOut(tr("Denied by you: {0}", line), false)
        val script = NxInstall.command(plan, target.cwd)
        var output = ""
        NxTask.await(TaskKind.Package, tr("Install {0}", plan.packages.joinToString(" ").take(40)), stopped) { r ->
            r.detail = tr("Running on the server")
            output = stream(vps, script, stopped, r, onOut)
        }
        val code = NxRun.codeOf(output)
        return ConsoleOut(NxInstall.report(plan, NxRun.visible(output), code, say), code == 0)
    }
}
