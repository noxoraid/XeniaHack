# CLAUDE.md

## Konteks

Video promosi NOXORA REST API berbasis Remotion 4. Spesifikasi tetap: 1920x1080, 60fps, 29 detik (1740 frame), landscape. Semua teks berbahasa Indonesia. Tema terang dengan hijau, hitam, putih; kesan teknologi modern, bersih, profesional, bukan mewah.

Fitur yang boleh ditampilkan hanya: Base64, Hash, Random, Placeholder, Tone. Jangan menambah fitur atau klaim lain.

Audio hanya dari `public/music.mp3` milik pengguna. Jangan membuat musik, voice-over, atau suara lain.

## Aturan kode

- Tanpa komentar di kode, termasuk YAML dan skrip.
- Ringkas tapi tetap terbaca, tanpa overengineering dan tanpa dependensi tambahan.
- Tidak ada file acak; `public/.gitkeep` hanya menjaga folder `public` tetap ada.

## Struktur

- `src/constants.ts` — fps, ukuran, durasi, BPM, warna `C`, batas scene `T`
- `src/data.ts` — daftar fitur, path, dan contoh respons terminal
- `src/Main.tsx` — susunan scene, wipe, dan musik
- `src/Wipes.tsx` — transisi hijau/hitam di setiap pergantian scene
- `src/Music.tsx` — musik otomatis dipotong atau diulang crossfade, fade-in/out
- `src/Background.tsx` — grid, progress bar, indikator beat
- `src/Orbit.tsx` — 12 lingkaran yang mengelilingi bola (intro dan outro)
- `src/ui.tsx` — helper animasi (`Rise`, `prog`, `scramble`) dan komponen `Terminal`
- `src/scenes/` — `Intro`, `Hook`, `Product`, `Features` (+ `visuals`), `Outro`
- `scripts/run.mjs` — menjalankan studio/render dan mendeteksi `public/music.mp3`
- `.github/workflows/render.yml` — render, verifikasi, ZIP, artifact

## Timeline

| Scene | Mulai | Durasi |
| --- | --- | --- |
| Intro bola | 0 s | 4 beat (3,33 s) |
| Hook | 3,33 s | 6 beat (5 s) |
| Pengenalan produk | 8,33 s | 5 beat (4,17 s) |
| Fitur (5 x 3 beat) | 12,5 s | 15 beat (12,5 s) |
| Penutup branding | 25 s | sisa hingga 29 s |

Tempo musik 72 BPM, satu beat = 50 frame. Semua batas ada di `T` dan `FEATURE_LEN` pada `src/constants.ts`. `LEAD` adalah jeda frame agar animasi scene mulai setelah wipe terbuka.

## Workflow

Push ke `main` memicu render di GitHub Actions. Langkahnya: install, `npm run render`, verifikasi 1920x1080 / 60fps / 1740 frame dengan ffprobe, ZIP berisi `.mp4`, unggah artifact `noxora-rest-api-promo`.

## Melanjutkan pengembangan

- Ubah durasi: sesuaikan `DURATION_SEC` dan `T`, lalu perbarui angka `nb_frames` di workflow.
- Ubah fitur: edit `src/data.ts` dan `VISUALS` di `src/scenes/visuals.tsx`; jumlah fitur memengaruhi `FEATURE_LEN`, `Main.tsx`, dan `EDGES` di `Wipes.tsx`.
- Ubah tema: ganti nilai di `C`.
- Jalankan `npm run typecheck` sebelum commit.
