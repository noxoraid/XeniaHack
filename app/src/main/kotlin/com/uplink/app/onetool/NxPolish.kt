package com.uplink.app

object NxCondense {
    fun squeeze(text: String): String {
        val rows = ArrayList<String>()
        val counts = ArrayList<Int>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val row = raw.trimEnd()
            if (row.isEmpty()) {
                if (rows.isNotEmpty() && rows.last().isNotEmpty()) {
                    rows.add("")
                    counts.add(1)
                }
                continue
            }
            if (rows.isNotEmpty() && rows.last() == row) counts[counts.size - 1] = counts.last() + 1
            else {
                rows.add(row)
                counts.add(1)
            }
        }
        while (rows.isNotEmpty() && rows.last().isEmpty()) {
            rows.removeAt(rows.size - 1)
            counts.removeAt(counts.size - 1)
        }
        return rows.indices.joinToString("\n") { if (counts[it] > 1) rows[it] + "  (x" + counts[it] + ")" else rows[it] }
    }

    fun forModel(text: String, limit: Int, say: NxSay): String {
        if (text.length <= limit) return text
        val tight = squeeze(text)
        if (tight.length <= limit) return tight
        val headSize = limit * 55 / 100
        val tailSize = limit - headSize
        val headRaw = tight.substring(0, headSize)
        val tailRaw = tight.substring(tight.length - tailSize)
        val head = if (headRaw.contains('\n')) headRaw.substringBeforeLast('\n') else headRaw
        val tail = if (tailRaw.contains('\n')) tailRaw.substringAfter('\n') else tailRaw
        val left = tight.length - head.length - tail.length
        return head + "\n" + say("[{0} characters left out of the middle, narrow the command to see them]", arrayOf(left)) + "\n" + tail
    }
}

object NxNoPipes {
    private val rule = Regex("""^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?$""")

    fun clean(text: String): String {
        if (!text.contains('|')) return text
        val out = ArrayList<String>()
        for (line in text.lines()) {
            val t = line.trim()
            if (t.length > 1 && t.startsWith("|") && t.endsWith("|")) {
                if (rule.matches(t)) continue
                out.add(t.trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  "))
            } else out.add(line)
        }
        return out.joinToString("\n")
    }
}

sealed class NxGate {
    object Pass : NxGate()
    class Refuse(val message: String) : NxGate()
}

class NxRetry(
    private val phone: Boolean,
    private val phoneKnown: Set<String>,
    private val ready: Set<String>,
    private val maxTries: Int = MAX_TRIES,
) {
    companion object {
        const val MAX_TRIES = 2

        private val wrappers = setOf("toybox", "busybox", "env", "command", "sudo", "exec", "nohup", "time")

        val twins: Map<String, String> = mapOf(
            "du" to "du", "tree" to "tree", "stat" to "stat", "head" to "head", "tail" to "tail", "cat" to "read",
            "ls" to "ls", "find" to "find", "grep" to "grep", "sha256sum" to "hash", "shasum" to "hash",
            "md5sum" to "hash", "cksum" to "hash", "diff" to "diff", "cmp" to "diff", "jq" to "json",
            "mkdir" to "mkdir", "cp" to "cp", "mv" to "mv", "rm" to "rm", "zip" to "zip", "unzip" to "unzip",
            "curl" to "fetch", "wget" to "fetch", "node" to "node", "python" to "py", "python3" to "py",
        )

        private val missing = Regex("(?i)command not found|sh: \\d+: \\S+: not found|unknown command|perintah tidak dikenal|not available on the phone|run nx env to see")
    }

    private class NxCall(val name: String, val goal: String, val wrapped: Boolean, val inner: String)

    private val refused = LinkedHashMap<String, String>()
    private val tries = HashMap<String, MutableList<String>>()

    private fun parse(line: String): NxCall? {
        var words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        if (words[0] == "nx") {
            if (words.size < 2) return null
            if (words[1].lowercase() != "sh") {
                val action = words[1].lowercase()
                val arg = words.drop(2).firstOrNull { !it.startsWith("-") }.orEmpty()
                return NxCall("nx $action", "nx $action $arg".trim(), false, action)
            }
            var rest = line.trim().substring(2).trim().substring(2).trim()
            if (rest.length > 1 && (rest[0] == '\'' || rest[0] == '"') && rest.last() == rest[0]) rest = rest.substring(1, rest.length - 1).trim()
            words = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
        }
        var wrapped = false
        while (words.isNotEmpty()) {
            val head = words[0]
            val isWrapper = head.substringAfterLast('/') in wrappers
            val isAssign = !isWrapper && head.contains('=') && !head.startsWith("-") && !head.startsWith("/")
            if (!isWrapper && !isAssign) break
            if (isWrapper) wrapped = true
            words = words.drop(1).let { rest -> if (isWrapper) rest.dropWhile { it.startsWith("-") } else rest }
        }
        if (words.isEmpty()) return null
        if (words[0].startsWith("/")) wrapped = true
        val inner = words[0].substringAfterLast('/')
        val arg = words.drop(1).firstOrNull { !it.startsWith("-") }.orEmpty()
        return NxCall(inner, "$inner $arg".trim(), wrapped, inner)
    }

    private fun suggestion(inner: String): String? = twins[inner]?.takeIf { it in ready }?.let { "nx $it" }

    fun check(line: String, say: NxSay): NxGate {
        val call = parse(line) ?: return NxGate.Pass
        val wrapKey = if (call.wrapped && phone) "wrapped:" + call.inner else null
        val known = refused[wrapKey ?: call.name]
        if (known != null) {
            return NxGate.Refuse(say("Already refused: {0}. Use {1} and do not try other spellings.", arrayOf(wrapKey?.removePrefix("wrapped:") ?: call.name, known)))
        }
        val past = tries[call.goal]
        if (past != null && past.size >= maxTries) {
            return NxGate.Refuse(say("Stopped after {0} attempts at {1}. Tried: {2}. Tell the user what failed and what was tried.", arrayOf(maxTries, call.goal, past.joinToString("; "))))
        }
        if (phone && !call.name.startsWith("nx ")) {
            if (call.wrapped) {
                val use = suggestion(call.inner) ?: "nx help"
                refused[wrapKey!!] = use
                return NxGate.Refuse(say("{0} is not available on the phone. Use {1} instead.", arrayOf(line.trim(), use)))
            }
            if (call.name !in phoneKnown) {
                val use = suggestion(call.name) ?: "nx help"
                refused[call.name] = use
                return NxGate.Refuse(say("{0} is not available on the phone. Use {1} instead.", arrayOf(call.name, use)))
            }
        }
        return NxGate.Pass
    }

    fun record(line: String, ok: Boolean, text: String) {
        val call = parse(line) ?: return
        if (ok) {
            tries.remove(call.goal)
            return
        }
        tries.getOrPut(call.goal) { ArrayList() }.add(line.trim().take(80))
        if (missing.containsMatchIn(text.take(300))) {
            refused[call.name] = if (call.name.startsWith("nx ")) "nx env" else suggestion(call.inner) ?: "nx env"
        }
    }
}
