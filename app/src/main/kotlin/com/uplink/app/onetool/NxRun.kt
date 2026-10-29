package com.uplink.app

class NxRunError(message: String) : Exception(message)

enum class NxRuntime(val action: String, val binary: String, val extensions: List<String>) {
    Node("node", "node", listOf(".js", ".mjs", ".cjs")),
    Python("py", "python3", listOf(".py")),
}

class NxRunPlan(val runtime: NxRuntime, val file: String, val args: List<String>)

fun nxQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

object NxRun {
    const val MAX_ARGS = 20
    const val MAX_ARG = 500
    const val MAX_SCRIPT = 262_144L
    const val LIMIT_SECONDS = 600
    const val OUTPUT_LIMIT = 4000
    const val CODE_MARK = "__NXCODE__"

    fun plan(runtime: NxRuntime, words: List<String>, say: NxSay): NxRunPlan {
        if (words.isEmpty()) throw NxRunError(say("nx {0} needs a file", arrayOf(runtime.action)))
        val file = words[0]
        if (file.startsWith("-")) throw NxRunError(say("The file name cannot start with -", arrayOf<Any?>()))
        if (runtime.extensions.none { file.lowercase().endsWith(it) }) {
            throw NxRunError(say("nx {0} runs {1} files only", arrayOf(runtime.action, runtime.extensions.joinToString(" "))))
        }
        val args = words.drop(1)
        if (args.size > MAX_ARGS) throw NxRunError(say("At most {0} arguments are allowed", arrayOf(MAX_ARGS)))
        for (arg in args) {
            if (arg.length > MAX_ARG) throw NxRunError(say("An argument is longer than {0} characters", arrayOf(MAX_ARG)))
            if (arg.any { it == '\n' || it == '\r' || it == '\u0000' }) throw NxRunError(say("An argument has a line break or a null character", arrayOf<Any?>()))
        }
        return NxRunPlan(runtime, file, args)
    }

    fun command(runtime: NxRuntime, file: String, args: List<String>, cwd: String): String {
        val call = (listOf(runtime.binary, nxQuote(file)) + args.map { nxQuote(it) }).joinToString(" ")
        return "cd ${nxQuote(cwd)} 2>/dev/null\nT=\"\"\ncommand -v timeout >/dev/null 2>&1 && T=\"timeout $LIMIT_SECONDS\"\n\$T $call </dev/null"
    }

    fun withCode(script: String): String = "$script\n__nx_code=\$?\necho\necho $CODE_MARK\$__nx_code"

    fun codeOf(text: CharSequence): Int = Regex("$CODE_MARK(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    fun visible(text: CharSequence): String {
        val cut = text.indexOf(CODE_MARK)
        return (if (cut >= 0) text.substring(0, cut) else text.toString()).trimEnd()
    }

    fun tail(text: String, limit: Int, say: NxSay): String {
        if (text.length <= limit) return text
        val kept = text.takeLast(limit)
        val start = kept.indexOf('\n').let { if (it in 0..200) it + 1 else 0 }
        return say("[earlier output cut, {0} characters]", arrayOf(text.length - kept.length + start)) + "\n" + kept.substring(start)
    }

    fun report(output: String, code: Int, say: NxSay): String {
        val body = tail(output, OUTPUT_LIMIT, say)
        val head = when {
            code == 0 -> ""
            code == 124 -> say("Stopped after {0} minutes", arrayOf(LIMIT_SECONDS / 60))
            else -> say("Exit code {0}", arrayOf(code))
        }
        if (code == 0) return body.ifEmpty { say("(no output)", arrayOf<Any?>()) }
        return if (body.isEmpty()) head else "$body\n$head"
    }
}

enum class NxManager(val word: String) {
    Apt("apt"),
    Npm("npm"),
    Pip("pip"),
}

class NxInstallPlan(val manager: NxManager, val packages: List<String>)

object NxInstall {
    const val MAX_PACKAGES = 10
    const val NO_ROOT = "__NXNOROOT__"
    const val MISSING = "__NXMISSING__"

    private val apt = Regex("^[a-z0-9][a-z0-9+.\\-]{0,99}(=[A-Za-z0-9.+:~\\-]{1,60})?$")
    private val npm = Regex("^(@[A-Za-z0-9][A-Za-z0-9._~\\-]*/)?[A-Za-z0-9][A-Za-z0-9._~\\-]*(@[A-Za-z0-9._~+\\-]{1,60})?$")
    private val pip = Regex("^[A-Za-z0-9][A-Za-z0-9._\\-]{0,99}(==[A-Za-z0-9.!+_\\-]{1,40})?$")

    fun allowed(manager: NxManager, name: String): Boolean = name.length <= 214 && when (manager) {
        NxManager.Apt -> apt.matches(name)
        NxManager.Npm -> npm.matches(name)
        NxManager.Pip -> pip.matches(name)
    }

    fun plan(words: List<String>, say: NxSay): NxInstallPlan {
        if (words.isEmpty()) throw NxRunError(say("nx install needs a package name", arrayOf<Any?>()))
        val named = NxManager.values().firstOrNull { it.word == words[0].lowercase() }
        val manager = named ?: NxManager.Apt
        val rest = if (named != null) words.drop(1) else words
        if (rest.isEmpty()) throw NxRunError(say("nx install needs a package name", arrayOf<Any?>()))
        val packages = rest.distinct()
        if (packages.size > MAX_PACKAGES) throw NxRunError(say("At most {0} packages in one call", arrayOf(MAX_PACKAGES)))
        for (name in packages) {
            if (!allowed(manager, name)) throw NxRunError(say("{0} is not an allowed package name for {1}", arrayOf(name, manager.word)))
        }
        return NxInstallPlan(manager, packages)
    }

    fun display(plan: NxInstallPlan, cwd: String): String {
        val names = plan.packages.joinToString(" ")
        return when (plan.manager) {
            NxManager.Apt -> "apt-get update; apt-get install -y $names"
            NxManager.Npm -> "npm install $names  (in $cwd)"
            NxManager.Pip -> "python3 -m pip install $names"
        }
    }

    fun command(plan: NxInstallPlan, cwd: String): String {
        val names = plan.packages.joinToString(" ") { nxQuote(it) }
        return when (plan.manager) {
            NxManager.Apt -> "command -v apt-get >/dev/null 2>&1 || { echo ${MISSING}apt-get; exit 127; }\n" +
                "S=\"\"\n" +
                "if [ \"\$(id -u)\" != 0 ]; then\n" +
                "  if command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then S=\"sudo -n\"; else echo $NO_ROOT; exit 126; fi\n" +
                "fi\n" +
                "\$S env DEBIAN_FRONTEND=noninteractive apt-get update -qq && \$S env DEBIAN_FRONTEND=noninteractive apt-get install -y $names </dev/null"
            NxManager.Npm -> "command -v npm >/dev/null 2>&1 || { echo ${MISSING}npm; exit 127; }\n" +
                "cd ${nxQuote(cwd)} && npm install --no-fund --no-audit $names </dev/null"
            NxManager.Pip -> "command -v python3 >/dev/null 2>&1 && python3 -m pip --version >/dev/null 2>&1 || { echo ${MISSING}pip; exit 127; }\n" +
                "python3 -m pip install --no-input $names </dev/null"
        }
    }

    fun report(plan: NxInstallPlan, output: String, code: Int, say: NxSay): String {
        if (output.contains(NO_ROOT)) return say("Installing system packages needs root or passwordless sudo on this server", arrayOf<Any?>())
        val missing = Regex("$MISSING(\\S+)").find(output)?.groupValues?.get(1)
        if (missing != null) return say("{0} is not installed on this server, nx install {1} is not available here", arrayOf(missing, plan.manager.word))
        val body = NxRun.tail(output, NxRun.OUTPUT_LIMIT, say)
        if (code == 0) {
            val tailLine = body.lines().lastOrNull { it.isNotBlank() }.orEmpty()
            val done = say("Installed {0} with {1}", arrayOf(plan.packages.joinToString(" "), plan.manager.word))
            return if (tailLine.isEmpty()) done else "$done\n$tailLine"
        }
        val hint = if (plan.manager == NxManager.Pip && output.contains("externally-managed-environment")) {
            "\n" + say("This server blocks system-wide pip installs, use a virtual environment (python3 -m venv)", arrayOf<Any?>())
        } else {
            ""
        }
        return say("Install failed (exit code {0})", arrayOf(code)) + "\n" + body + hint
    }
}

class NxShellRm(val recursive: Boolean, val paths: List<String>)

object NxShellRmParse {
    private const val BLOCKED = ";&|<>$`\\*?[]{}()!~#'\"\n\r"
    private val shortFlags = setOf('r', 'R', 'f', 'v', 'd', 'I')
    private val longFlags = setOf("--recursive", "--force", "--verbose", "--dir")

    fun parse(line: String): NxShellRm? {
        val text = line.trim()
        if (!(text == "rm" || text.startsWith("rm ") || text.startsWith("rm\t"))) return null
        if (text.any { it in BLOCKED }) return null
        val tokens = text.split(Regex("\\s+")).drop(1)
        var recursive = false
        var plain = false
        val paths = ArrayList<String>()
        for (token in tokens) {
            when {
                plain -> paths.add(token)
                token == "--" -> plain = true
                token.startsWith("--") -> {
                    if (token !in longFlags) return null
                    if (token == "--recursive") recursive = true
                }
                token.startsWith("-") && token.length > 1 -> {
                    if (token.drop(1).any { it !in shortFlags }) return null
                    if (token.any { it == 'r' || it == 'R' }) recursive = true
                }
                else -> paths.add(token)
            }
        }
        if (paths.isEmpty()) return null
        return NxShellRm(recursive, paths)
    }
}
