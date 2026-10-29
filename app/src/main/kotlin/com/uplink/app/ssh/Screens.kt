package com.uplink.app

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerList() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles = remember { mutableStateListOf<Profile>().also { it.addAll(loadProfiles(ctx)) } }
    var connecting by remember { mutableStateOf<String?>(null) }
    val seen = rememberNxSeen()
    var showForm by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var delTarget by remember { mutableStateOf<Profile?>(null) }

    fun connect(p: Profile) {
        if (connecting != null) return
        connecting = p.name
        scope.launch {
            val err = openVps(p)
            connecting = null
            if (err != null) toast(ctx, tr("Cannot connect: {0}", err))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (profiles.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    NxIconBox(NxIcon.Server, Modifier.nxFloat(6.dp, 2400), box = 64.dp)
                    Text(tr("No servers yet"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
                    Text(tr("Add your first VPS below."), color = NxDim, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(profiles.toList()) { index, p ->
                        val busy = connecting == p.name
                        val live = Hub.vps?.profile?.let { it.host == p.host && it.port == p.port && it.user == p.user } == true
                        NxListRow(
                            p.name, NxIcon.Server,
                            Modifier.nxRow(seen, p.name, index),
                            meta = if (busy) tr("Connecting...") else "${p.user}@${p.host}:${p.port}",
                            trailing = {
                                if (live) Box(Modifier.padding(end = 4.dp).size(8.dp).clip(CircleShape).background(NxLive))
                                NxIconButton(NxIcon.Edit, tr("Edit"), NxDim) { editTarget = p; showForm = true }
                                NxIconButton(NxIcon.Trash, tr("Delete"), NxDim) { delTarget = p }
                            },
                        ) { connect(p) }
                    }
                }
            }
        }
        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 6.dp)) {
            NxButton(tr("Add VPS"), icon = NxIcon.Plus, tone = Tone.Accent, wide = true) { editTarget = null; showForm = true }
        }
    }

    if (showForm) {
        ProfileForm(
            initial = editTarget,
            onDismiss = { showForm = false },
            onSave = { result ->
                showForm = false
                val old = editTarget
                if (old == null) profiles.add(result) else profiles[profiles.indexOf(old)] = result
                saveProfiles(ctx, profiles)
            },
        )
    }

    delTarget?.let { p ->
        NxConfirm(tr("Delete {0}?", p.name), tr("Delete"), onDismiss = { delTarget = null }) {
            delTarget = null
            profiles.remove(p)
            saveProfiles(ctx, profiles)
        }
    }
}

private fun parseLogin(login: String, name: String, pass: String, key: String): Profile? {
    val parts = login.trim().split(Regex("\\s+"))
    var target = ""
    var port = 22
    var i = 0
    while (i < parts.size) {
        val part = parts[i]
        if (part == "-p" && i + 1 < parts.size) {
            i++
            port = parts[i].toIntOrNull() ?: 0
        } else if (part.startsWith("-p") && part.length > 2) {
            port = part.substring(2).toIntOrNull() ?: 0
        } else if (!part.startsWith("-") && part != "ssh") {
            target = part
        }
        i++
    }
    val at = target.lastIndexOf('@')
    val user = if (at > 0) target.substring(0, at) else "root"
    val host = if (at >= 0) target.substring(at + 1) else target
    if (host.isEmpty() || port <= 0) return null
    return Profile(if (name.isBlank()) host else name.trim(), host.trim(), port, user.trim(), pass, key.trim())
}

@Composable
fun ProfileForm(initial: Profile?, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var login by remember {
        mutableStateOf(if (initial == null) "ssh root@" else "ssh ${initial.user}@${initial.host} -p ${initial.port}")
    }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var key by remember { mutableStateOf(initial?.key ?: "") }
    var problem by remember { mutableStateOf<String?>(null) }

    NxDialog(
        title = if (initial == null) tr("Add VPS") else tr("Edit VPS"),
        onDismiss = onDismiss,
        confirm = tr("Save"),
        onConfirm = {
            val r = parseLogin(login, name, pass, key)
            if (r == null) problem = tr("Use the format: ssh root@host -p 22") else onSave(r)
        },
    ) {
        NxField(name, { name = it }, tr("Name (optional)"))
        NxField(login, { login = it }, tr("SSH command"), mono = true)
        NxField(pass, { pass = it }, tr("Password"), obscure = true)
        NxField(key, { key = it }, tr("Private key (optional, PEM)"), mono = true, lines = 4)
        problem?.let { Text(it, color = NxDanger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)) }
    }
}

@Composable
fun FontRow(choice: FontChoice, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().nxTap(14.dp, onPick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(choice.label, Modifier.weight(1f), fontFamily = choice.family, fontSize = 16.sp)
        if (selected) Icon(NxIcon.Check, tr("Selected"), Modifier.size(20.dp), tint = NxAccent)
    }
}

suspend fun openVps(p: Profile): String? {
    val cur = Hub.vps
    if (cur != null) {
        val c = cur.profile
        if (c.host == p.host && c.port == p.port && c.user == p.user) {
            Nav.route = Route.Ssh
            return null
        }
        withContext(Dispatchers.IO) { Hub.closeAll() }
    }
    val vps = Vps(p)
    val err = withContext(Dispatchers.IO) {
        try { vps.connect(); null } catch (e: Throwable) { e.message ?: e.toString() }
    }
    if (err == null) {
        Hub.vps = vps
        Nav.route = Route.Ssh
    }
    return err
}
