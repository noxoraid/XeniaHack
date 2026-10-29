package com.uplink.app

import android.content.Context

object OneTool {
    private const val HEAD = "nx"
    private const val READ_LIMIT = 3600
    private const val PAGE_LIMIT = 4000
    private const val PAGE_TEXT = "document.body?document.body.innerText:''"

    fun isCall(line: String): Boolean {
        val text = line.trim()
        return text == HEAD || text.startsWith("$HEAD ")
    }

    fun spec(target: AgentTarget): String {
        val shell = if (target is VpsAgentTarget) {
            "runs on the real server shell (sh -c): apt, npm, git, docker, pipes and && work. Use non-interactive flags and never start servers in the foreground. Destructive commands are blocked or wait for the user."
        } else {
            "runs on the phone's built-in shell: no pipes, no chaining, no npm or apt. rm waits for the user."
        }
        return """
One tool, many actions. Put "nx <action> <args>" as a line in commands. Any other line still runs as a plain shell command.
nx env  what works right now (run it first in every session)  |  nx help  every nx command and what it needs
nx read <path> [from] [to]  file text, optional 1-based line range
nx ls [path] [-n]  |  nx find <glob> [path]  |  nx grep <text> [path]
nx du [path]  |  nx tree [path] [depth]  |  nx stat <path>  |  nx head|tail <path> [n]  |  nx hash <path>  |  nx diff <a> <b>  |  nx json <path> [key]
nx write <path> '<text>'  short file, \n is a new line, asks before replacing  |  nx edit <path> '<old>' '<new>'  patch text that appears exactly once
nx mkdir <path>  |  nx cp <src> <dst>  |  nx mv <src> <dst>  |  nx rm [-r] <path>...  names are confirmed by the user, no wildcards  |  nx zip <source> <target.zip>  |  nx unzip <zip> [folder]
nx node <file> [args]  |  nx py <file> [args]  run a script file on the server, no input, stopped after 10 minutes, VPS only  |  nx install [apt|npm|pip] <package>...  install on the server, always waits for the user, apt by default, npm installs in the current folder
nx sh <line>  $shell
nx fetch <url> [offset]  a public web page as text, http and https only, local addresses refused, a new domain waits for the user; long pages continue with the offset it prints
nx web <url or search>  open in the in-app browser  |  nx text  visible page text  |  nx js <code>  run JavaScript on the page, waits for the user
Quote arguments with single quotes, or double quotes when the text holds a single quote. Long files go in the files list. A wrong call returns its correct usage.
Current location: ${target.place}
""".trim()
    }

    fun credit(app: Context, target: AgentTarget) {
        val gain = (target.toolbox.length - spec(target).length) / 4
        if (gain > 0) Cfg.addOneToolSaved(app, gain.toLong())
    }

    fun forModel(text: String): String = NxCondense.forModel(text, PAGE_LIMIT, nxSay)

    fun run(target: AgentTarget, app: Context, line: String, stopped: () -> Boolean, retry: NxRetry? = null, onOut: (String) -> Unit): ConsoleOut {
        if (retry != null) {
            val gate = retry.check(line, nxSay)
            if (gate is NxGate.Refuse) return ConsoleOut(gate.message, false)
        }
        val out = execute(target, app, line, stopped, onOut)
        retry?.record(line, out.ok, out.text)
        return out
    }

    private fun execute(target: AgentTarget, app: Context, line: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        if (!isCall(line)) return target.run(line, stopped, onOut)
        val body = line.trim().removePrefix(HEAD).trim()
        val action = body.substringBefore(' ').lowercase()
        val rest = body.substringAfter(' ', "").trim()
        return try {
            dispatch(target, app, action, rest, stopped, onOut)
        } catch (e: TaskStopped) {
            throw e
        } catch (e: Exception) {
            ConsoleOut(tr("nx {0}: {1}", action.ifEmpty { "?" }, e.message?.ifBlank { null } ?: e.toString()) + "\n" + usageOf(action), false)
        }
    }

    private fun dispatch(
        target: AgentTarget, app: Context, action: String, rest: String, stopped: () -> Boolean, onOut: (String) -> Unit,
    ): ConsoleOut {
        val words = consoleTokens(rest)
        val choice = NxPick.forCommand(action, NxEnvReport.facts(app))
        if (choice is NxChoice.Unavailable && choice.reason != "unknown action") {
            return ConsoleOut(tr("nx {0}: {1}. Run nx env to see what is available.", action, tr(choice.reason)), false)
        }
        return when (action) {
            "help" -> ConsoleOut(helpText(), true)
            "env" -> ConsoleOut(NxEnvReport.text(app, target), true)
            "read" -> read(target, words, stopped, onOut)
            in NxReadTools.names -> NxReadTools.run(target, action, words, stopped)
            in NxChangeTools.names -> NxChangeTools.run(target, app, action, rest, stopped, onOut)
            in NxArchiveTools.names -> NxArchiveTools.run(target, app, action, rest, stopped, onOut)
            in NxFetchTools.names -> NxFetchTools.run(app, rest, stopped, onOut)
            in NxRunTools.names -> NxRunTools.run(target, app, action, rest, stopped, onOut)
            "find" -> {
                if (words.isEmpty()) throw Exception(tr("Missing argument"))
                grouped(target.run("find ${quote(words.getOrElse(1) { "." })} -name ${quote(words[0])}", stopped, onOut)) { NxGroup.find(it, 200, nxSay) }
            }
            "grep" -> {
                if (words.isEmpty()) throw Exception(tr("Missing argument"))
                grouped(target.run("grep -r -n ${quote(words[0])} ${quote(words.getOrElse(1) { "." })}", stopped, onOut)) { NxGroup.grep(it, 5, 60, nxSay) }
            }
            "sh" -> {
                if (rest.isEmpty()) throw Exception(tr("Missing argument"))
                target.run(rest, stopped, onOut)
            }
            "web" -> {
                if (rest.isEmpty()) throw Exception(tr("Missing argument"))
                ConsoleOut(WebHost.open(rest, stopped), true)
            }
            "text" -> ConsoleOut(WebHost.script(PAGE_TEXT).take(PAGE_LIMIT), true)
            "js" -> script(target, app, rest, stopped, onOut)
            else -> throw Exception(tr("unknown action"))
        }
    }

    private val nxSay: NxSay = { key, args -> tr(key, *args) }

    private fun grouped(out: ConsoleOut, regroup: (List<String>) -> List<String>): ConsoleOut {
        if (!out.ok || out.text == tr("No matches") || out.text.isBlank()) return out
        return ConsoleOut(regroup(out.text.lines()).joinToString("\n"), true)
    }

    private fun helpText(): String {
        fun line(e: NxEntry) = "${e.usage}  -  ${tr(e.summary)}  [${tr("needs")}: ${tr(e.need.label)}]"
        val now = NxCatalog.all.filter { it.ready }.joinToString("\n") { line(it) }
        val later = NxCatalog.all.filter { !it.ready }.joinToString("\n") { line(it) }
        val head = tr("Available now:") + "\n" + now
        return if (later.isEmpty()) head else head + "\n\n" + tr("Planned, not available yet (do not call):") + "\n" + later
    }

    private fun read(target: AgentTarget, words: List<String>, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        if (words.isEmpty()) throw Exception(tr("Missing argument"))
        val out = target.run("cat " + quote(words[0]), stopped, onOut)
        if (!out.ok) return out
        val lines = out.text.lines()
        val from = words.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val end = minOf(words.getOrNull(2)?.toIntOrNull() ?: Int.MAX_VALUE, lines.size)
        if (from > lines.size) throw Exception(tr("file has {0} lines", lines.size))
        val body = StringBuilder()
        var last = from - 1
        for (i in from - 1 until end) {
            val row = lines[i]
            if (body.isNotEmpty() && body.length + row.length + 1 > READ_LIMIT) break
            body.append(if (body.isEmpty()) row.take(READ_LIMIT) else row).append('\n')
            last = i + 1
        }
        if (last < end) body.append(tr("[cut after line {0} of {1}, continue with: nx read {2} {3}]", last, lines.size, words[0], last + 1))
        return ConsoleOut(body.toString().trimEnd(), true)
    }

    private fun script(target: AgentTarget, app: Context, code: String, stopped: () -> Boolean, onOut: (String) -> Unit): ConsoleOut {
        if (code.isEmpty()) throw Exception(tr("Missing argument"))
        onOut(tr("Waiting for your approval"))
        val allowed = ApprovalBoard.request(app, tr("Browser page"), "nx js $code", tr("This script runs on the open web page and can act as you"), stopped)
        if (!allowed) return ConsoleOut(tr("Denied by you: {0}", "nx js"), false)
        return ConsoleOut(WebHost.script(code).take(PAGE_LIMIT), true)
    }

    private fun quote(value: String): String {
        if (value.contains('\'')) throw Exception(tr("single quotes cannot be used inside an argument"))
        val plain = value.isNotEmpty() && value.none { it.isWhitespace() || it in "*?[]{}()<>|&;$\"\\" }
        return if (plain) value else "'$value'"
    }

    private fun usageOf(action: String): String = when (action) {
        "read" -> "usage: nx read <path> [from] [to]"
        "ls" -> "usage: nx ls [path] [-n]"
        "find" -> "usage: nx find <glob> [path]"
        "grep" -> "usage: nx grep <text> [path]"
        "sh" -> "usage: nx sh <line>"
        "web" -> "usage: nx web <url or search>"
        "text" -> "usage: nx text"
        "js" -> "usage: nx js <code>"
        "help" -> "usage: nx help"
        "env" -> "usage: nx env"
        else -> NxCatalog.all.firstOrNull { it.name == action && it.ready }?.let { "usage: " + it.usage }
            ?: ("actions: " + NxCatalog.readyNames().joinToString(", "))
    }
}
