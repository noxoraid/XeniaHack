# Skrip uji manual Mind (Indonesia)

Catatan: semua angka adalah KARAKTER, bukan token. Baca dari layar Mind > KARAKTER TERKIRIM.

## A. Bandingkan Mind mati dan hidup
1. Siapkan folder kosong di Internal, mis. /sdcard/uji-toko. Buat satu file kode kecil di dalamnya (mis. app.js) agar proyek terdeteksi.
2. Settings > MIND: matikan sakelar. Buka AI File di folder itu.
3. Kirim tugas yang sama persis: "buat web toko online sederhana". Tunggu selesai. Catat: jumlah putaran (baris perintah/berkas di log), lalu di layar Mind catat "Mind mati" (rata-rata dan jumlah), "Permintaan terakhir".
4. Hapus hasil (atau pakai folder kedua yang identik). Ulangi sampai ada minimal 3 permintaan Mind mati.
5. Nyalakan Mind. Buka AI File di folder yang sama. Pastikan layar Mind menampilkan proyek, status "Terbaru", jumlah file, waktu indeks. Buka Files > UtilyFlowNxr/mind dan pastikan index.tsv ada dan bisa dibaca.
6. Kirim tugas yang sama. Catat jumlah putaran dan "Mind hidup" (rata-rata, jumlah), "Permintaan terakhir", dan persentase selisih.
7. Bandingkan: rata-rata per permintaan, DAN total karakter = rata-rata x jumlah permintaan per tugas. Harapan jujur: per permintaan Mind hidup bisa lebih besar; hemat hanya jika putaran lebih sedikit.
8. Bila Mind hidup > 1,5 kali Mind mati, harus muncul peringatan "Pemeriksaan ukuran" yang menyebut surface.
9. Matikan Mind lagi, kirim satu permintaan: pastikan tidak ada Task "Mind index" baru dan tidak ada ringkasan (prompt sama seperti dulu).

## B. Jeda otomatis (3 gagal beruntun)
1. Mind hidup. Di Providers ubah API key jadi salah (atau matikan internet).
2. Kirim 3 permintaan AI File. Setelah yang ketiga: toast "Mind dijeda", kartu di layar Mind dengan alasan dan tombol "Nyalakan Mind lagi", badge ! di drawer.
3. Pastikan sakelar di Settings tetap hidup. Kirim permintaan lagi: tidak ada ringkasan dan hitungan masuk "Mind mati".
4. Tekan "Nyalakan Mind lagi", pulihkan API key, kirim permintaan: ringkasan kembali.
5. Tutup paksa app dan buka: Mind ikut pengaturan tersimpan (hidup).
6. Untuk uji 2 gagal bangun indeks: buat folder proyek lalu cabut izin penyimpanan atau ganti nama folder saat Task jalan; ulangi dua kali di folder yang sama. Harus muncul jeda dengan alasan membangun indeks.

## C. Cek lain
- Stale: ubah satu file di proyek, buka layar Mind: status "Kedaluwarsa". Kirim permintaan: indeks dibangun ulang.
- Validasi: edit index.tsv, ubah angka "files" lalu kirim permintaan setelah mengubah file proyek. Harus muncul "Tidak valid" dengan cek count dan path indeks, dan indeks tidak dikirim.
- Batas: ubah budget ke 500, kirim permintaan: ringkasan tidak melebihi 500 karakter.
- Rebuild dan Delete index di Settings > MIND dan layar Mind.

## D. Asumsi eksternal
- Tidak ada URL eksternal baru yang dipanggil.
- Format indeks: TSV buatan sendiri (#mind-index versi 1), bukan format penyedia mana pun.
- Penyedia AI: diasumsikan menerima system prompt sampai batas karakter yang dipilih; batas konteks penyedia tidak diperiksa.
- NoxoraStorage: permintaan menyebut penyimpanan di NoxoraStorage, tetapi juga melarang filesDir; dipilih UtilyFlowNxr/mind (butuh akses semua file, bila tidak ada jatuh ke folder app eksternal).
- Waktu ubah file hanya tersedia untuk penyimpanan lokal; Root dan VPS memakai tanda tangan path dan ukuran.
- Regex deklarasi: heuristik, tidak mengurai bahasa sungguhan. Kurung kurawal di dalam regex literal JS atau template string multi-baris bisa mengacaukan kedalaman.
- Java: anggota kelas (kedalaman 1) ikut dicatat; Kotlin, Python, JS/TS, C/C++ hanya tingkat atas.
- Semua kode belum dikompilasi. Tes JVM belum dijalankan.
