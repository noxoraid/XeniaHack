package com.uplink.app

enum class VpsState { NotConfigured, Disconnected, Connected }

class VpsProbe(val os: String, val shell: String, val tools: Map<String, Boolean>) {
    companion object {
        const val SCRIPT = "echo \"os=\$(. /etc/os-release 2>/dev/null; echo \$PRETTY_NAME)\"; " +
            "echo \"kernel=\$(uname -sr 2>/dev/null)\"; " +
            "echo \"sh=\$(readlink -f /bin/sh 2>/dev/null)\"; " +
            "echo \"bash=\$(bash --version 2>/dev/null | head -n 1)\"; " +
            "for c in du tar zip unzip node python3 git; do if command -v \$c >/dev/null 2>&1; then echo \"has.\$c=1\"; else echo \"has.\$c=0\"; fi; done"

        val TOOLS = listOf("du", "tar", "zip", "unzip", "node", "python3", "git")

        fun parse(text: String): VpsProbe {
            val map = HashMap<String, String>()
            for (line in text.lines()) {
                val at = line.indexOf('=')
                if (at > 0) map[line.substring(0, at).trim()] = line.substring(at + 1).trim()
            }
            val os = map["os"].orEmpty().ifEmpty { map["kernel"].orEmpty() }
            val bash = map["bash"].orEmpty()
            val shell = if (bash.isNotEmpty()) bash else map["sh"].orEmpty().substringAfterLast('/')
            return VpsProbe(os, shell, TOOLS.associateWith { map["has.$it"] == "1" })
        }
    }
}

class EnvFacts(
    val vps: VpsState,
    val internet: Boolean,
)

sealed class NxChoice {
    class Use(val command: String) : NxChoice()
    class Unavailable(val reason: String) : NxChoice()
}

object NxPick {
    fun forCommand(name: String, facts: EnvFacts): NxChoice {
        val entry = NxCatalog.all.firstOrNull { it.name == name } ?: return NxChoice.Unavailable("unknown action")
        if (!entry.ready) return NxChoice.Unavailable("not available yet")
        if (entry.need == NxNeed.Vps && facts.vps != VpsState.Connected) return NxChoice.Unavailable("needs a connected VPS")
        if (entry.need == NxNeed.Internet && !facts.internet) return NxChoice.Unavailable("needs internet")
        return NxChoice.Use("nx $name")
    }

    fun signature(facts: EnvFacts, vpsId: String): String = "${facts.vps}|$vpsId|${facts.internet}"
}

class NxEnvCache<T> {
    private var signature: String? = null
    private var value: T? = null
    var builds = 0
        private set

    @Synchronized
    fun get(sig: String, build: () -> T): T {
        val have = value
        if (have != null && signature == sig) return have
        val fresh = build()
        builds++
        signature = sig
        value = fresh
        return fresh
    }
}
