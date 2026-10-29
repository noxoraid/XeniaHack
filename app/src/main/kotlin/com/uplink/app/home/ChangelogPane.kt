package com.uplink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class Release(val version: String, val notes: List<String>)

private fun pick(id: String, en: String): String = if (Lang.current.code == "id") id else en

private fun releases(): List<Release> = listOf(
    Release(
        "4.1.0",
        listOf(
            pick("Mind: memori proyek untuk AI File, AI server, Obrolan AI, dan Workflow, dengan indeks yang bisa dibuka di Uplink/mind", "Mind: project memory for AI File, server AI, AI Chat and workflows, with an index you can open in Uplink/mind"),
            pick("Mind muncul di Home dan Tools; root penyimpanan tidak lagi dianggap proyek", "Mind appears in Home and Tools; the storage root is no longer treated as a project"),
            pick("Pratinjau ringkasan terakhir yang dikirim ke AI di layar Mind", "Preview of the last summary sent to the AI in the Mind screen"),
            pick("Perbaikan EOFException: koneksi putus saat balasan AI dicoba ulang, atau balasan sebagian ditandai terpotong", "Fixed EOFException: a dropped AI connection is retried, or a partial reply is marked as cut off"),
            pick("Perbaikan cd di AI File: folder kerja tidak lagi kembali ke /storage/0 saat AI dibuka ulang", "Fixed cd in AI File: the working folder no longer jumps back to /storage/0 when AI is reopened"),
            pick("Tombol lampirkan di AI File: foto, video, dan zip disalin ke folder kerja", "Attach button in AI File: photos, videos and zips are copied into the working folder"),
            pick("Musik di Home: putar otomatis dari Uplink/music (mp3, wav, dan lainnya), bisa dimatikan", "Music on Home: autoplay from Uplink/music (mp3, wav and more), can be turned off"),
        ),
    ),
    Release(
        "4.0.0",
        listOf(
            pick("Tampilan baru terang berwarna biru dengan bar tab bawah", "New light blue interface with a bottom tab bar"),
            pick("Perbaikan crash saat berpindah tab", "Fixed crash when switching tabs"),
            pick("Perbaikan crash saat membuka laporan error", "Fixed crash when opening the error report"),
            pick("Tab Changelog di Pengaturan", "Changelog tab in Settings"),
            pick("Tanda tangan APK V1, V2 dan V3", "APK signing with V1, V2 and V3"),
        ),
    ),
    Release(
        "3.0.0",
        listOf(
            pick("Agen AI untuk file dan terminal", "AI agent for files and terminal"),
            pick("Pemutar audio dan video bawaan", "Built-in audio and video player"),
            pick("Tile cepat di panel notifikasi", "Quick tile in the notification panel"),
        ),
    ),
    Release(
        "2.0.0",
        listOf(
            pick("Arus Kas dengan grafik donat dan batang", "Cash flow with donut and bar charts"),
            pick("Peramban dengan pemblokir iklan", "Browser with ad blocker"),
            pick("Server Localhost dari folder HP", "Localhost server from phone folders"),
            pick("Jelajah isi ZIP tanpa ekstrak", "Browse ZIP contents without extracting"),
        ),
    ),
    Release(
        "1.0.0",
        listOf(pick("Rilis pertama", "First Release")),
    ),
)

@Composable
fun ChangelogPane() {
    val list = releases()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        for ((i, release) in list.withIndex()) {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp, start = 2.dp).nxEnter(i), verticalAlignment = Alignment.CenterVertically) {
                Text(release.version, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = NxText)
                if (i == 0) {
                    Text(
                        tr("New"),
                        Modifier.padding(start = 10.dp).clip(CircleShape).background(NxAccent).padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = NxOnAccent,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().nxEnter(i).nxGlass(20.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (note in release.notes) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(NxAccent))
                        Text(note, Modifier.padding(start = 12.dp), fontSize = 14.sp)
                    }
                }
            }
            Box(Modifier.height(8.dp))
        }
        Box(Modifier.height(32.dp))
    }
}
