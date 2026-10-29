package com.uplink.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date

private val quickNames = setOf("cd", "pwd", "ls", "echo", "date", "cat", "head", "tail", "stat", "help", "clear", "touch", "mkdir")

fun hasPipe(line: String): Boolean {
    var quote = '\u0000'
    for (c in line) {
        when {
            quote != '\u0000' -> if (c == quote) quote = '\u0000'
            c == '"' || c == '\'' -> quote = c
            c == '|' || c == ';' || c == '&' -> return true
        }
    }
    return false
}

class PhoneShell(private val fs: FsBackend, app: Context, private val base: String) {
    private val console = ConsoleSession(fs, app.applicationContext, base, base)

    var cwd: String
        get() = console.cwd
        set(value) {
            console.cwd = value
        }

    fun plan(line: String): List<ConsoleStep> = console.steps(line)

    fun heavy(line: String): Boolean = ConsoleSession.nameOf(line) !in quickNames

    fun run(line: String, stopped: () -> Boolean, onMedia: (AgentMedia) -> Unit = {}): ConsoleOut {
        val text = line.trim()
        if (text.isEmpty()) return ConsoleOut("", true)
        if (hasPipe(text)) return ConsoleOut(tr("Pipes and chained commands are not supported"), false)
        val tokens = consoleTokens(text)
        return try {
            when (tokens[0]) {
                "help" -> ConsoleOut(helpText(), true)
                "date" -> ConsoleOut(SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Lang.locale()).format(Date()), true)
                "echo" -> echo(tokens.drop(1))
                in ConsoleSession.writes -> change(text)
                else -> console.run(text, stopped, onMedia)
            }
        } catch (e: TaskStopped) {
            throw e
        } catch (e: Exception) {
            ConsoleOut(e.message?.ifBlank { null } ?: e.toString(), false)
        }
    }

    private fun change(line: String): ConsoleOut {
        val steps = console.steps(line)
        if (steps.isEmpty()) return ConsoleOut(tr("Nothing to do"), false)
        val report = ArrayList<String>()
        var failed = false
        for (s in steps) {
            val verdict = agentGuard(fs, base, s.op, s.path, s.to, "")
            val why = s.blocked ?: verdict.first
            if (why != null) {
                failed = true
                report.add(tr("{0}: {1}", s.path.substringAfterLast('/'), why))
                continue
            }
            try {
                agentPerform(fs, AgentAction(s.op, s.path, s.to, "", verdict.second, null, line))
                report.add(tr("{0}: {1}", s.op.title, s.path))
            } catch (e: Exception) {
                failed = true
                report.add(tr("{0}: {1}", s.path.substringAfterLast('/'), e.message ?: e.toString()))
            }
        }
        return ConsoleOut(report.joinToString("\n"), !failed)
    }

    private fun readCapped(path: String): String {
        val out = java.io.ByteArrayOutputStream()
        fs.open(path).use { input ->
            val buf = ByteArray(8192)
            while (out.size() < 1_000_000) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        return out.toString("UTF-8")
    }

    private fun echo(words: List<String>): ConsoleOut {
        val mark = words.indexOfFirst { it == ">" || it == ">>" }
        if (mark < 0) return ConsoleOut(words.joinToString(" "), true)
        val target = words.getOrNull(mark + 1) ?: return ConsoleOut(tr("Missing argument"), false)
        val path = fixPath(target, cwd)
        val append = words[mark] == ">>"
        var content = words.take(mark).joinToString(" ") + "\n"
        if (append && fs.kind(path) == 1) {
            content = readCapped(path) + content
        }
        val verdict = agentGuard(fs, base, AgentOp.CreateFile, path, "", content)
        verdict.first?.let { return ConsoleOut(tr("{0}: {1}", target, it), false) }
        agentPerform(fs, AgentAction(AgentOp.CreateFile, path, "", content, verdict.second, null))
        return ConsoleOut(tr("Written {0}", path), true)
    }

    private fun helpText(): String = listOf(
        "pwd  cd [path]  ls [-l] [-a] [path]  tree [-L N] [path]",
        "cat file  head [-n N] file  tail [-n N] file  wc [-l] file  stat path  du [path]",
        "find [path] [-name glob] [-iname glob] [-type f|d] [-maxdepth N]",
        "grep [-r] [-i] [-n] [-l] [-c] [-F] pattern [path...]",
        "mkdir path  touch path  cp [-r] a b  mv a b  rm [-r] path  rmdir path  zip path  unzip file.zip",
        "echo text > file  echo text >> file  date  clear  play path  show path",
    ).joinToString("\n")
}
