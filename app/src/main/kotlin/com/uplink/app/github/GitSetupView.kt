package com.uplink.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SetupView(s: GitSession) {
    val ctx = s.ctx
    var name by remember { mutableStateOf(GitAccount.user) }
    var mail by remember { mutableStateOf(GitAccount.email) }
    var secret by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        NxSection(tr("ACCOUNT"), Modifier.nxEnter(0))
        NxCard(Modifier.fillMaxWidth().nxEnter(0)) {
            NxField(name, { name = it }, tr("GitHub username"), mono = true)
            NxField(mail, { mail = it }, tr("Email"), mono = true)
            NxField(secret, { secret = it }, tr("Personal access token (ghp_...)"), obscure = true, mono = true)
            Text(tr("The token needs repo and workflow access."), color = NxDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        NxButton(tr("Connect"), icon = NxIcon.Git, tone = Tone.Accent, wide = true, modifier = Modifier.nxEnter(1)) {
            val u = name.trim()
            val m = mail.trim()
            val t = secret.ifBlank { GitAccount.token }
            if (u.isEmpty() || m.isEmpty() || t.isBlank()) {
                toast(ctx, tr("Fill in username, email and token"))
            } else {
                s.load(tr("Checking token"), {
                    GitAccount.save(ctx, u, m, t)
                    try {
                        GitHubApi.login()
                    } catch (e: Exception) {
                        GitAccount.signOut(ctx)
                        throw e
                    }
                }) {
                    toast(ctx, tr("Connected"))
                    secret = ""
                    s.view = GitView.Hub
                }
            }
        }
        if (GitAccount.ready) {
            Spacer(Modifier.height(10.dp))
            NxButton(tr("Sign out"), icon = NxIcon.Logout, tone = Tone.Danger, wide = true) {
                GitAccount.signOut(ctx)
                secret = ""
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
