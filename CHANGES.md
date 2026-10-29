# CHANGES (belum dikompilasi)

## Perbaikan setelah uji di HP (Mind, AI File, musik)
Diperbaiki:
- Mind menganggap /storage/emulated/0 sebagai proyek bernama "0" (455 file, peringatan "scan failed ... Android/data", stale terus). MindDetect.broad menolak akar penyimpanan, /, /sdcard, SD card, dan tidak naik ke sana saat mencari marker. Tes ditambah.
- Waktu indeks kini waktu mulai pemindaian, jadi file yang berubah saat membangun ikut terdeteksi stale.
- EOFException (koneksi chunked putus): AiCall mencoba ulang 2 kali bila belum ada teks, bila sudah ada teks balasan ditandai terpotong (AiCall.cut) dan alur lama "balasan terpotong, kirim ulang" berjalan. Pembatalan user tidak dihitung sebagai putus.
- cd kembali ke /storage/0: AgentSheet memanggil target.enter(dir) tiap sheet dibuka. Sekarang hanya bila folder file manager berubah (TermAgent.openedDir).
Ditambah:
- Mind di Home (kotak) dan Tools. Pratinjau ringkasan terakhir yang dikirim di layar Mind.
- Tombol lampirkan di AI File (HP dan server): foto, video, zip disalin ke folder kerja lewat Task (agent/AgentAttach.kt) dan namanya ditambahkan ke prompt. Foto TIDAK dikirim sebagai gambar ke model (penyedia di app ini hanya teks).
- Musik di Home (home/HomeMusic.kt): putar otomatis sekali per proses dari UtilyFlowNxr/music (mp3, wav, ogg, m4a, aac, flac, opus), ulang semua, bisa dimatikan (pref home_music). Tidak ada lagu bawaan.
- Changelog 4.1.0.
Menyimpang dari CLAUDE.md atas permintaan user: Home kini punya kotak Mind dan kartu musik.
Belum diverifikasi: semua belum dikompilasi dan belum diuji di perangkat. Perlu dicek: impor rememberLauncherForActivityResult di AgentSheet, NxSwitch internal dipakai HomeMusic, autoplay saat app dibuka (Android bisa menahan audio bila fokus audio dipegang app lain).

## Mind: memori proyek untuk semua AI
Ditambah (folder app/src/main/kotlin/com/uplink/app/mind/):
- Logika murni tanpa import Android: MindModel, MindDetect (jalan naik maksimal 8 folder, marker .git, build.gradle, build.gradle.kts, package.json, pyproject.toml, Cargo.toml, go.mod, cadangan folder kerja bila ada file kode), MindLang, MindOutline (deklarasi tingkat atas dan import untuk Kotlin, Java, Python, JS/TS, C/C++), MindIndexFile (indeks TSV dan validasi), MindWalk (pindai dan bangun indeks), MindSummary (ringkasan di bawah batas karakter, skor leksikal), MindMeter (hitung karakter, 20 terakhir per kondisi), MindChecks (cek ringkasan, ukuran, stale, MindPause jeda otomatis).
- Lapisan Android: MindStore (UtilyFlowNxr/mind/{hash}/index.tsv lewat AppFolder), MindFsSource (FsBackend ke MindSource), MindGate (satu fungsi konteks bersama, Task TaskKind.Mind, status untuk UI), MindScreen (layar Mind dan MindControls).
- Tes JVM di app/src/test/kotlin/com/uplink/app/mind/.
Diubah:
- AiClient: AiCall punya tap untuk menghitung karakter tiap permintaan (system + semua pesan, termasuk keluaran alat).
- TermAgent (AI File HP dan SSH, dipakai juga AgentSheet), AiChat, FlowRun: ringkasan masuk ke system prompt lewat MindGate.context; hasil permintaan (gagal, balasan kosong, JSON rusak) dilaporkan ke jeda otomatis. Saat Mind mati prompt identik dengan sebelumnya.
- SettingsScreen: Cfg.mind, Cfg.mindBudget, grup MIND, NxSwitch jadi internal. Shell: Route.Mind dan item drawer. TaskBoard: TaskKind.Mind. UntilyApp: MindGate.init. LangEn dan LangId: kunci baru.
Keputusan sadar (menyimpang dari teks permintaan):
- Folder penyimpanan memakai AppFolder.dir("mind") (UtilyFlowNxr/mind, terlihat di tab Internal), bukan data/data NoxoraStorage, karena NoxoraStorage berada di filesDir yang dilarang.
- Baris nx read hanya dipasang untuk AI yang punya alat (AI File HP dan SSH). AiChat dan Workflow tidak bisa menjalankan nx, jadi diberi baris "minta user menempel file". Baris cat dipakai bila One Tool mati.
- AiChat dan Workflow tidak punya folder kerja: memakai folder terakhir yang dipakai AI file, dan ringkasan hanya dikirim bila ada kecocokan leksikal dengan permintaan.
- Penyimpanan ukuran: dua cincin per surface (20 permintaan Mind hidup, 20 Mind mati), supaya cek 1,5 kali punya pembanding Mind mati yang stabil.
- AI File di terminal dan AgentSheet memakai satu TermAgent, jadi satu surface "phone". Surface: phone, ssh, chat, flow.
- Pemeriksaan stale juga memakai tanda tangan daftar file (path dan ukuran) karena Root dan VPS tidak memberi waktu ubah murah.
- Pemeriksaan segar dilewati 30 detik setelah indeks terbukti segar, supaya tiap permintaan tidak membuat Task dan notifikasi baru.
- VPS dan Root memakai batas lebih kecil (1500 file, baca 64 KB per file).
- Pindai dan ekstraksi di Kotlin, bukan C++ (penyimpangan dari aturan C++ di CLAUDE.md), dibatasi 6000 file lokal.
- Pesan diagnostik dari logika murni (alasan jeda, hasil cek) tetap berbahasa Inggris; label dan judul lewat tr.
Belum diverifikasi: seluruhnya belum dikompilasi dan belum dijalankan (tidak ada kotlinc, Gradle, Android SDK). Tes JVM belum dijalankan. Belum dicoba di HP: Task Mind, notifikasi, tampilan layar, pindai SFTP, pemakaian karakter nyata. Regex deklarasi hanya diperiksa dengan sampel di Python. Kemungkinan perlu perbaikan saat build: smart cast pada assertNotNull di tes, dan impor di MindScreen.kt.
Catatan pengukuran: karakter per permintaan dengan Mind hidup bisa LEBIH BESAR karena ringkasan (sampai batas karakter) ikut di setiap putaran. Penghematan hanya muncul bila AI memakai lebih sedikit putaran dan keluaran alat. Hitung juga jumlah putaran per tugas saat menguji.

## Font dari Google Fonts lewat URL
Dihapus: app/src/main/res/font (semua file .ttf bawaan, sekitar 2 MB).
Ditambah: ui/GoogleFonts.kt (GoogleFontStore: unduh TTF dari raw.githubusercontent.com/google/fonts ke filesDir/fonts, sekali saja, dengan timeout 15 dan 30 detik, file gagal tidak disimpan).
Diubah: ui/Fonts.kt (FontChoice mulai dengan font sistem, lalu berganti ke font Google setelah unduhan selesai di thread latar; FontPrefs.load memicu unduhan).
Belum diverifikasi: nama file di URL, terutama variable font (InstrumentSans, BricolageGrotesque, WorkSans, Outfit, JetBrainsMono, GeistMono) dan GeistMono. Kalau URL 404, font itu tetap memakai font sistem dan tidak membuat app crash. Terminal (TerminalView) memakai typeface baru setelah view dibuat ulang, jadi perlu buka ulang layar terminal setelah unduhan selesai.
Catatan: perlu internet saat pertama kali membuka app agar font Google terpasang.

## Perbaikan build dan nama aplikasi
Diperbaiki: TaskKind berakhir dengan titik koma setelah Index dan Mcp dihapus; referensi McpServer di KeepAliveService dan CmStopped di ErrorLog dihapus.
Diubah: label aplikasi jadi Uplink (AndroidManifest), artifact CI jadi uplink-apk dan uplink-mapping (build.yml). applicationId, namespace, dan keystore tetap.
Package diganti jadi com.uplink.app (namespace, applicationId, folder kotlin dan test, deklarasi package, import, dan nama fungsi JNI di cpp). Android menganggap ini aplikasi berbeda: install lama harus di-uninstall, dan data serta izin di perangkat tidak terbawa. Folder data di penyimpanan (UntilyFlowNxr) dan nama proyek Gradle tidak diubah.
Belum diverifikasi: compile ulang di GitHub Actions.

## Penghapusan CodeMind dan MCP (Localhost dipertahankan)
Dihapus: folder codemind/ (indeks, pencarian, konteks, server MCP), native codescan (cpp dan JNI), tes CodeMindTests dan CodeMindFixtures, tools/codemind-test, tools/codescan-test, CODEMIND.md, seluruh docs/, perintah nx cm, Route.Mind, TaskKind Index dan Mcp, serta kunci lang terkait.
Dipertahankan: Localhost (LocalhostSheet, LocalhostServer, TreePrint beserta native treeprint dan tools/treeprint-test), nx tool, TextGrep, TreeScan, GitBlob, FileSniff, VtScreen, dan semua fitur AI.
Belum diverifikasi: compile (tanpa Android SDK dan kotlinc), dan perilaku penuh di perangkat.

## Batas token keluaran
Anthropic: max_tokens bawaan naik dari 4096 ke 16000, bila server menolak karena max_tokens dicoba ulang dengan 8192 lalu 4096 (ai/AiClient.kt `stream` dan `send`). Bila body tambahan sudah memuat max_tokens, nilai user dipakai apa adanya.
Diubah: ai/AiClient.kt (alasan berhenti dari server dibaca: finish_reason, stop_reason, finishReason; `AiCall.finish` dan `truncated`), terminal/TermAgent.kt dan ai/AiChat.kt (pesan jelas bila model kehabisan token, permintaan kirim ulang dengan file lebih kecil), lang/LangEn.kt, lang/LangId.kt.
Catatan: `max_tokens` untuk jenis OpenAI-compatible tidak dikirim, jadi memakai batas bawaan server. Model penalaran bisa menghabiskan batas itu untuk berpikir lalu balasan kosong. Belum diverifikasi dengan deepseek-v4-flash sungguhan.

## Signing fallback
Diubah: app/build.gradle.kts. Tanpa nilai NOXORA_*, release kini ditandatangani dengan kunci debug (V1, V2, V3 aktif) supaya APK tidak unsigned. Kunci debug berbeda dari kunci Noxora: update di atas install lama bertanda tangan Noxora gagal sampai uninstall, atau isi secrets NOXORA_* agar memakai kunci Noxora.

## nx lengkap, Tahap 7
Ditambah: onetool/NxPolish.kt (inti murni: NxCondense, NxNoPipes, NxRetry, NxGate), test NxStageSevenTests.kt.
Belum diverifikasi: compile, tes JVM (kotlinc tidak ada di lingkungan pembuat), simulasi `nx sh 'toybox du'` di HP nyata, perilaku batas dua percobaan dengan model sungguhan.

## nx lengkap, Tahap 6
Ditambah: onetool/NxRun.kt (inti murni: nx node, nx py, nx install, parser rm polos), onetool/NxRunTools.kt (Task, persetujuan, keluaran), test NxStageSixTests.kt.
Belum diverifikasi: compile, tes JVM, nx node dan nx py di VPS nyata, apt-get dengan dan tanpa root, npm dan pip install, kartu persetujuan, konfirmasi rm lewat SSH.
Pertanyaan terbuka untuk pemilik ada di CLAUDE.md bagian Tahap 6.

## nx lengkap, Tahap 5
Ditambah: onetool/NxFetch.kt (inti murni: penjaga alamat, rentang IP, pengalihan, domain, HTML ke teks, paging), onetool/NxFetchTools.kt (Task, cek DNS, persetujuan domain, unduhan berbatas), test NxStageFiveTests.kt.
Belum diverifikasi: compile, tes JVM, unduhan URL publik di HP, kartu persetujuan domain di UI, pengalihan nyata, perilaku DNS rebinding.

## nx lengkap, Tahap 4
Ditambah: onetool/NxChange.kt, onetool/NxZip.kt, onetool/NxChangeTools.kt, onetool/NxArchiveTools.kt, onetool/NxTask.kt, agent/AgentConfirm.kt, test NxStageFourTests.kt.
Belum diverifikasi: compile, tes JVM, konfirmasi rm/cp/mv di HP, zip dan unzip di HP, zip dan unzip di VPS nyata.

## nx lengkap, Tahap 3
Ditambah: onetool/NxRead.kt, onetool/NxReadTools.kt, test NxStageThreeTests.kt.
Belum diverifikasi: compile, tes JVM, du di VPS nyata (bandingkan dengan du -sb), du di HP dengan 3 file, stat di Root.

## nx lengkap, Tahap 2
Ditambah: onetool/NxEnv.kt, onetool/NxEnvReport.kt. Tes ditambah di NxStageOneTests.kt (parser probe VPS, padanan, cache).
Belum diverifikasi: compile, tes JVM, probe di VPS nyata, tes dengan internet dimatikan di HP.

## nx lengkap, Tahap 1
Ditambah: secret/SecretBox.kt, secret/SecretVault.kt, agent/DeletePolicy.kt, onetool/NxCatalog.kt, app/proguard-rules.pro, test NxStageOneTests.kt.
Belum diverifikasi: compile, tes JVM, assembleRelease/R8, upgrade dengan key lama di HP.

## 4.0.0
Diperbaiki: crash IllegalArgumentException alpha negatif di NxBottomBar (nilai spring "barSel" undershoot, kini di-coerce 0..1); coerce alpha di AiScreen, Splash, Motion.
Ditambah: tab Changelog di Pengaturan (home/ChangelogPane.kt).
Versi: versionName 4.0.0, versionCode 4. Signing V1, V2, V3 aktif.

## Fase 5
Bahasa: lang/LangEn.kt dan lang/LangId.kt (+ kunci "Active").

Catatan:
- terminal/TermParts.kt tidak ada di daftar, tetapi input bar agen (TermPrompt) ada di sana dan dipakai bersama terminal HP.
- Logika, data, handler, dan navigasi tidak diubah. Tombol Back di semua layar tetap.
- Warna: tidak ada Color(0x...) tersisa di file fase 5 selain ssh/TerminalView.kt (sengaja tetap). Warna irisan donat di CashCharts kini token (Accent, Live, Danger, varian alpha, Dim), jadi urutan warnanya berbeda dari sebelumnya.

Sengaja dihilangkan atau diganti:
- Grid 2 kolom kartu alat di hub GitHub diganti daftar NxListRow (data sama).
- Ikon bernapas (nxBreathe) di header Panel SystemPane diganti NxIconBox statis.
- Garis pemisah tipis antar baris di daftar repo, Browse, Actions, riwayat browser diganti jarak antar baris/kartu.

Risiko compile (belum diverifikasi):
- ai/AiScreen.kt, cash/CashflowScreen.kt: NxSegment dipanggil dengan List<Pair<String, ImageVector>> dan List<Pair<String, Nothing?>> untuk parameter List<Pair<String, ImageVector?>>; seharusnya lolos lewat kovarians.
- ssh/Screens.kt: `Hub.vps?.profile?.let` (safe call pada tipe non-null, hanya peringatan).
- web/WebScreen.kt: `decorationBox` pada BasicTextField bersama modifier padding dan onFocusChanged.
- ssh/VpsScreen.kt: `NxBarItem(...) { hideKeyboard(view); tab = 0 }` (lambda berakhir assignment).
- Impor tak terpakai tersisa di beberapa file (hanya peringatan).

## Fase 1–4
Ditambah: ui/NxCloud.kt, home/ToolsScreen.kt
Diubah: ui/Theme.kt, ui/NxV3.kt, ui/NxTile.kt, core/Motion.kt, core/Shell.kt, home/Home.kt, home/SettingsScreen.kt, files/LocalFiles.kt, files/FilesPane.kt, lang/LangEn.kt, lang/LangId.kt
Sengaja dihilangkan (tidak ada data/fungsi nyata): bell notifikasi, profil Noxora, Akun/Notifikasi/Bantuan, Proyek Terbaru, Bagikan, layar detail folder, waktu ubah file, tombol filter di search bar.

## iBank Style
- Theme.kt: single iBank palette (light/dark), semantic colors, purple NxBar header with sheet corners, edge-to-edge system bars, shadow cards, pill buttons, 16dp fields.
- Poppins added (res/font), default UI font. SemiBold maps to the Bold file until a real Poppins-SemiBold.ttf is added.
- New: NxGridTile, NxFab, NxComposer, NxHeadAction, NxPress; NxSegment is a sliding pill; NxChip is pill; NxBottomBar is a 5-tab pill nav.
- Home: hero "My device" card + 3-column tile grid. Tools: semantic tints. AI: primary/tint bubbles that wrap, floating composer.
- Files: header with search/overflow, segmented tabs, chip row, breadcrumb, 64dp rows, FAB speed-dial, contextual selection bar.
- Launcher icon replaced (adaptive + legacy), Splash uses the new logo.
