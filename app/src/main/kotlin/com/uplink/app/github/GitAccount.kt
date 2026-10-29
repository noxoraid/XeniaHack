package com.uplink.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

enum class GitMode(val key: String) {
    Token("token"),
    Engine("engine");

    companion object {
        fun of(key: String): GitMode = entries.firstOrNull { it.key == key } ?: Token
    }
}

class RepoLink(val repo: String, val branch: String, val subdir: String)

object GitAccount {
    var user by mutableStateOf("")
    var email by mutableStateOf("")
    var token by mutableStateOf("")
    var mode by mutableStateOf(GitMode.Token)
        private set
    private var links = JSONObject()

    val ready: Boolean get() = token.isNotBlank() && user.isNotBlank() && email.isNotBlank()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nx_git", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        user = p.getString("user", "") ?: ""
        email = p.getString("email", "") ?: ""
        val rawToken = p.getString("token", "") ?: ""
        token = SecretVault.value("git:token", rawToken)
        if (SecretVault.needsMigration(rawToken) && SecretVault.available) {
            p.edit().putString("token", SecretVault.seal("git:token", token)).apply()
        }
        mode = GitMode.of(p.getString("mode", "token") ?: "token")
        links = try {
            JSONObject(p.getString("links", "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun save(ctx: Context, name: String, mail: String, secret: String) {
        user = name.trim()
        email = mail.trim()
        token = AiWire.cleanKey(secret)
        prefs(ctx).edit().putString("user", user).putString("email", email).putString("token", SecretVault.seal("git:token", token)).apply()
    }

    fun pickMode(ctx: Context, next: GitMode) {
        mode = next
        prefs(ctx).edit().putString("mode", next.key).apply()
    }

    fun signOut(ctx: Context) {
        token = ""
        prefs(ctx).edit().remove("token").apply()
    }

    fun linkOf(dir: String): RepoLink? {
        val o = links.optJSONObject(dir) ?: return null
        val repo = o.optString("repo")
        if (repo.isEmpty()) return null
        return RepoLink(repo, o.optString("branch", "main"), o.optString("subdir"))
    }

    fun link(ctx: Context, dir: String, value: RepoLink) {
        links.put(dir, JSONObject().put("repo", value.repo).put("branch", value.branch).put("subdir", value.subdir))
        prefs(ctx).edit().putString("links", links.toString()).apply()
    }

    fun unlink(ctx: Context, dir: String) {
        links.remove(dir)
        prefs(ctx).edit().putString("links", links.toString()).apply()
    }
}
