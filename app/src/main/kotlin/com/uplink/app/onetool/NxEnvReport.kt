package com.uplink.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File

class PhoneProbe(val tools: Map<String, String>, val shell: List<String>)

object NxEnvReport {
    private val phoneCache = NxEnvCache<PhoneProbe>()
    private val vpsCache = NxEnvCache<VpsProbe?>()

    fun online(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        false
    }

    fun vpsState(ctx: Context): VpsState {
        val live = Hub.vps
        if (live != null && live.alive) return VpsState.Connected
        return if (loadProfiles(ctx).isNotEmpty()) VpsState.Disconnected else VpsState.NotConfigured
    }

    fun facts(ctx: Context): EnvFacts = EnvFacts(vpsState(ctx), online(ctx))

    private fun onPath(name: String): Boolean {
        val dirs = (System.getenv("PATH").orEmpty().split(':') + listOf("/system/bin", "/system/xbin", "/vendor/bin")).filter { it.isNotEmpty() }
        return dirs.any { File(it, name).let { f -> f.isFile && f.canExecute() } }
    }

    private fun phoneProbe(): PhoneProbe {
        val tools = LinkedHashMap<String, String>()
        tools["node"] = if (onPath("node")) "yes" else "no"
        tools["python"] = if (onPath("python3") || onPath("python")) "yes" else "no"
        tools["git"] = if (onPath("git")) "yes" else "no"
        tools["zip"] = if (onPath("zip")) "yes" else "built-in"
        tools["unzip"] = if (onPath("unzip")) "yes" else "built-in"
        val shell = (ConsoleSession.reads + ConsoleSession.writes + setOf("echo", "date", "help", "clear")).sorted()
        return PhoneProbe(tools, shell)
    }

    private fun vpsProbe(vps: Vps): VpsProbe? = try {
        VpsProbe.parse(vps.exec(VpsProbe.SCRIPT))
    } catch (_: Exception) {
        null
    }

    fun vpsTools(ctx: Context): Map<String, Boolean>? {
        val facts = facts(ctx)
        val live = Hub.vps?.takeIf { facts.vps == VpsState.Connected } ?: return null
        val sig = NxPick.signature(facts, live.profile.name + "@" + live.profile.host)
        return vpsCache.get(sig) { vpsProbe(live) }?.tools
    }

    fun text(ctx: Context, target: AgentTarget): String {
        val facts = facts(ctx)
        val live = Hub.vps?.takeIf { facts.vps == VpsState.Connected }
        val id = live?.let { it.profile.name + "@" + it.profile.host } ?: ""
        val sig = NxPick.signature(facts, id)
        val phone = phoneCache.get("phone") { phoneProbe() }
        val remote = if (live != null) vpsCache.get(sig) { vpsProbe(live) } else null
        val out = ArrayList<String>()
        out.add(tr("Current target: {0}", target.place))
        out.add(tr("Phone nx commands: {0}", NxCatalog.all.filter { it.ready && it.need != NxNeed.Vps }.joinToString(" ") { it.name }))
        out.add(tr("Phone shell commands: {0}", phone.shell.joinToString(" ")))
        out.add(tr("Phone tools: {0}", phone.tools.entries.joinToString(", ") { it.key + "=" + tr(it.value) }))
        when (facts.vps) {
            VpsState.NotConfigured -> out.add(tr("VPS: not configured. Do not use VPS commands."))
            VpsState.Disconnected -> out.add(tr("VPS: configured but not connected. Do not use VPS commands, use the phone equivalents or tell the user."))
            VpsState.Connected -> {
                out.add(tr("VPS: connected to {0}", id))
                out.add(tr("VPS only nx commands: {0}", NxCatalog.all.filter { it.ready && it.need == NxNeed.Vps }.joinToString(" ") { it.name }))
                if (remote != null) {
                    out.add(tr("VPS system: {0}, shell: {1}", remote.os.ifEmpty { "?" }, remote.shell.ifEmpty { "?" }))
                    out.add(tr("VPS tools: {0}", remote.tools.entries.joinToString(", ") { it.key + "=" + tr(if (it.value) "yes" else "no") }))
                } else {
                    out.add(tr("VPS tools: could not be checked"))
                }
            }
        }
        out.add(if (facts.internet) tr("Internet: available") else tr("Internet: not available"))
        out.add(if (WebHost.opened) tr("Browser: opened") else tr("Browser: not opened yet, nx web, nx text and nx js need it opened once"))
        return out.joinToString("\n")
    }
}
