package com.uplink.app

import android.content.Context

interface AgentTarget {
    val source: FsBackend
    val base: String
    val place: String
    val cwd: String
    val toolbox: String
    val rounds: Int
    fun listing(): String
    fun run(line: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut
    fun write(path: String, content: String)
    fun begin(line: String)
    fun end(out: ConsoleOut)
    fun enter(dir: String)
}

class PhoneAgentTarget(
    private val fs: FsBackend, app: Context, override val base: String, val shell: PhoneShell,
) : AgentTarget {
    private val appContext = app.applicationContext

    override fun begin(line: String) {
    }

    override fun end(out: ConsoleOut) {
    }

    override fun enter(dir: String) {
        shell.cwd = dir
    }

    override val source: FsBackend get() = fs

    override val place: String get() = fs.label

    override val cwd: String get() = shell.cwd

    override val rounds: Int get() = AgentLimits.phoneRounds

    override val toolbox: String = """
A lightweight built-in shell on the phone storage, not a real Linux shell. No pipes, no chaining, no npm, no apt.
Reading: pwd | cd [path] | ls [-l] [-a] [path...] | cat file... | head [-n N] file | tail [-n N] file | wc [-l] file... | stat path | du [path] | tree [-L N] [-a] [path] | find [path] [-name glob] [-iname glob] [-type f|d] [-maxdepth N] | grep [-r] [-i] [-n] [-l] [-c] [-F] pattern [path...]
Changing: mkdir path... | touch path... | rmdir path... | cp [-r] from to | mv from to | zip path... | unzip file.zip | echo text > file
rm works but waits for the user to approve it, so only use it when needed. To create or replace a file with content, use the files list instead of commands.
In commands quote with single quotes only. grep and find skip .git and node_modules.
""".trim()

    override fun listing(): String = try {
        val items = fs.list(shell.cwd)
        if (items.isEmpty()) "(empty)"
        else items.take(150).joinToString("\n") { if (it.dir) "d ${it.name}" else "f ${it.name} ${it.size}" }
    } catch (_: Exception) {
        "(unreadable)"
    }

    override fun run(line: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val name = ConsoleSession.nameOf(line)
        if (name == "rm" || name == "rmdir" || name == "mv") {
            val doomed = try { shell.plan(line).filter { it.op == AgentOp.Delete || it.op == AgentOp.Rename } } catch (_: Exception) { emptyList() }
            for (step in doomed) {
                DeletePolicy.refusal(step.path)?.let { return ConsoleOut(tr("{0}: {1}", step.path, refusalText(it, step.path)), false) }
            }
            val overwrites = doomed.any { it.to.isNotEmpty() && fs.kind(it.to) != 0 }
            if (doomed.isNotEmpty() && DeletePolicy.needsConfirm(name, overwrites)) {
                val targets = doomed.map { NxTarget(it.path, fs.kind(it.path) == 2) }
                if (!AgentConfirm.ask(appContext, place, line, false, targets, stopped, onOut)) {
                    return ConsoleOut(tr("Denied by you: {0}", line), false)
                }
            }
        }
        return shell.run(line, stopped)
    }

    override fun write(path: String, content: String) {
        val full = fixPath(path, shell.cwd)
        val verdict = agentGuard(fs, base, AgentOp.CreateFile, full, "", content)
        verdict.first?.let { throw Exception(tr("{0}: {1}", path, it)) }
        if (verdict.second && !AgentConfirm.ask(appContext, place, full, true, listOf(NxTarget(full, false)), { false }, { })) {
            throw Exception(tr("Denied by you: {0}", full))
        }
        agentPerform(fs, AgentAction(AgentOp.CreateFile, full, "", content, verdict.second, null))
    }
}

private val forbidden = listOf(
    Regex("""\brm\b[^;&|\n]*\s-[a-zA-Z]*[rR][^;&|\n]*\s(/|/\*|/etc|/usr|/var|/boot|/bin|/sbin|/lib|/lib64|/home|/root|/opt|~|\${'$'}HOME)/?\*?(\s|$)"""),
    Regex("""\bmkfs(\.\w+)?\b"""),
    Regex("""\bdd\b[^;&|\n]*\bof=/dev/"""),
    Regex(""">\s*/dev/(sd|vd|nvme|xvd)"""),
    Regex(""":\(\)\s*\{"""),
    Regex("""\bchmod\s+-R\s+\d+\s+/(\s|$)"""),
    Regex("""\bchown\s+-R\s+\S+\s+/(\s|$)"""),
)

private val needsApproval = listOf(
    Regex("""\brm\b"""),
    Regex("""\b(shutdown|reboot|poweroff|halt)\b"""),
    Regex("""\binit\s+[06]\b"""),
    Regex("""\b(userdel|passwd|deluser)\b"""),
    Regex("""\bufw\s+(disable|reset|delete)\b"""),
    Regex("""\biptables\s+-F\b"""),
    Regex("""\bapt(-get)?\s+(remove|purge|autoremove)\b"""),
    Regex("""\bdocker\s+(rm|rmi|system\s+prune|volume\s+rm)\b"""),
    Regex("""\bsystemctl\s+(stop|disable|mask)\b"""),
)

fun shieldReason(line: String): String? =
    if (forbidden.any { it.containsMatchIn(line) }) tr("Blocked: this command could break the server") else null

fun approvalReason(line: String): String? =
    if (needsApproval.any { it.containsMatchIn(line) }) tr("This command removes or changes things on the server") else null

class VpsAgentTarget(private val vps: Vps, private val app: Context) : AgentTarget {
    override val source: FsBackend get() = VpsFs(vps)

    override val base: String = "/"

    override val place: String get() = vps.profile.name + " (" + vps.profile.user + "@" + vps.profile.host + ")"

    override var cwd: String = vps.cwd
        private set

    override val rounds: Int get() = AgentLimits.vpsRounds

    override val toolbox: String = """
A real Linux server over SSH, run with sh -c, no terminal attached, so nothing can ask questions.
Everything works: apt, apt-get, npm, pnpm, yarn, pip, node, git, curl, wget, systemctl, docker, tar, sed, awk, pipes, chaining with && and ;.
Always use non-interactive flags: apt-get install -y, npm install --no-fund --no-audit, git with -c and tokens in the URL only if the user gave one, curl -fsSL.
DEBIAN_FRONTEND=noninteractive is already set. Do not start servers in the foreground, use nohup ... & or systemctl, otherwise the command never ends.
Long commands are fine, they are streamed and stopped after 15 minutes. cd carries over to the next commands.
To create or replace a file with content, use the files list instead of heredocs.
Commands that could destroy the server (rm -rf on system folders, mkfs, dd to disks) are blocked. rm, shutdown, reboot, passwd, firewall resets, apt remove, docker rm and systemctl stop wait for the user to approve; use them only when needed.
""".trim()

    override fun listing(): String = try {
        val items = vps.list(cwd)
        if (items.isEmpty()) "(empty)"
        else items.take(150).joinToString("\n") { if (it.dir) "d ${it.name}" else "f ${it.name} ${it.size}" }
    } catch (_: Exception) {
        "(unreadable)"
    }

    override fun run(line: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        shieldReason(line)?.let { return ConsoleOut(it, false) }
        NxShellRmParse.parse(line)?.let { rm ->
            val fs = source
            val paths = rm.paths.map { fixPath(it, cwd) }
            for (path in paths) {
                agentGuard(fs, base, AgentOp.Delete, path, "", "").first?.takeIf { it != tr("not found") }?.let {
                    return ConsoleOut(tr("{0}: {1}", path, it), false)
                }
            }
            val targets = paths.filter { fs.kind(it) != 0 }.map { NxTarget(it, fs.kind(it) == 2) }
            if (targets.isNotEmpty() && !AgentConfirm.ask(app, place, line, false, targets, stopped, onOut)) {
                return ConsoleOut(tr("Denied by you: {0}", line), false)
            }
            return execute(line, stopped, onOut)
        }
        approvalReason(line)?.let { why ->
            onOut(tr("Waiting for your approval"))
            if (!ApprovalBoard.request(app, place, line, why, stopped)) return ConsoleOut(tr("Denied by you: {0}", line), false)
        }
        return execute(line, stopped, onOut)
    }

    private fun execute(line: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        val script = "cd ${sq(cwd)} 2>/dev/null; export DEBIAN_FRONTEND=noninteractive; $line\n__nx_code=\$?\necho\necho __NXCODE__\$__nx_code\necho __NXCWD__\$(pwd)"
        val text = StringBuilder()
        var echoed = 0
        vps.stream(script, stopped) { piece ->
            text.append(piece)
            val body = visible(text)
            onOut(body)
            if (body.length > echoed) {
                vps.current?.show(body.substring(echoed).replace("\n", "\r\n"))
                echoed = body.length
            }
        }
        val code = Regex("__NXCODE__(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        Regex("__NXCWD__(\\S+)").find(text)?.groupValues?.get(1)?.let {
            cwd = it
            vps.cwd = it
        }
        val shown = visible(text).trim()
        return ConsoleOut(shown.ifEmpty { if (code == 0) tr("(no output)") else tr("Exit code {0}", code) }, code == 0)
    }

    private fun visible(text: CharSequence): String {
        val cut = text.indexOf("__NXCODE__")
        val body = if (cut >= 0) text.substring(0, cut) else text.toString()
        return body.trimEnd()
    }

    override fun enter(dir: String) {
        cwd = dir
    }

    override fun begin(line: String) {
        vps.current?.show("\u001b[36m▶ AI  $line\u001b[0m\n")
    }

    override fun end(out: ConsoleOut) {
        vps.current?.show(if (out.ok) "\u001b[32m✓\u001b[0m\n" else "\u001b[31m✗ ${out.text.lines().firstOrNull().orEmpty()}\u001b[0m\n")
    }

    override fun write(path: String, content: String) {
        val full = fixPath(path, cwd)
        vps.mkdirs(parentOf(full))
        vps.write(full, content.toByteArray(Charsets.UTF_8))
    }
}
