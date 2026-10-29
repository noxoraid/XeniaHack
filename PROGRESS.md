# PROGRESS

Kode BELUM dikompilasi dan BELUM diuji di perangkat.

- [x] 1. Fondasi: palet Cloud (terang+gelap, default), NxCard/nxGlass, NxSection, NxBar, NxButton, NxField, NxChip, NxTile, NxSegment, NxAmbient
- [x] 1. Komponen ui/NxCloud.kt: NxIconBox, NxSearch, NxListRow, NxBottomBar
- [x] 1. Shell: Route.Tools, bottom bar pil 5 item, drawer tetap lengkap
- [x] 2. Dasbor, layar Tools
- [x] 3. File manager (Internal/Root/NoxoraStorage + VPS)
- [x] 4. Pengaturan
- [x] 5.1 Agen AI: AiScreen (segment Chat/Workflows, penyedia sebagai NxListRow, bubble kartu, input pil), ModelPicker (NxListRow), FlowScreen (daftar workflow NxListRow, tombol tambah langkah NxChip), AgentSheet (kartu putih untuk balasan, chip saran, ikon NxIconBox), TermPrompt (input pil)
- [x] 5.3 Terminal/SSH: daftar server NxListRow + titik hijau saat tersambung, bar sesi pil, laci sesi NxListRow, tab bawah NxBottomBar, Panel SystemPane NxIconBox, TransferPane NxIconBox. Area terminal tetap gelap
- [x] 5.4 Browser: bar alamat pil dengan ikon cari, tombol toggle jadi NxChip, riwayat NxSearch + NxListRow
- [x] 5.5 GitHub: hub jadi daftar NxListRow ber-ikon, daftar repo dan cabang NxListRow + NxSearch, chip repo/branch, baris Browse dan Actions bergaya kartu
- [x] 5.6 Arus Kas: NxSegment rentang, kartu pemasukan/pengeluaran terpisah, Color(0x...) di CashCharts diganti token
- [x] 5.7 Tugas dan Task Error: kartu task dengan NxIconBox + chip status, chip filter level lewat onTap
- [x] 5.8 Localhost (kartu server berjalan, chip auto reload) dan EditorScreen (radius dan jarak selaras)
- [x] Review mandiri putaran 1: impor, kurung, signature Nx*, kunci tr()
- [ ] Belum disentuh di 5.8: AudioPlayer, ImageViewer, VideoPlayer, Viewer, TextPager (sudah memakai NxBar + Back, tidak diubah)
- [ ] Belum disentuh di 5.5: GitSetupView, GitUploadView, GitReleaseView, GitPullView, GitEngineView (ikut komponen Nx* bersama)
- [ ] Build dan uji di perangkat

## Langkah berikutnya
1. Build lewat GitHub Actions, perbaiki error compile bila ada (lihat risiko di CHANGES.md).
2. Rapikan viewer media dan view GitHub yang belum disentuh.
3. Periksa tampilan mode gelap Cloud di layar fase 5.
