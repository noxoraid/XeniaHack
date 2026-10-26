# NOXORA REST API — Video Promo

Video promosi 29 detik (1920x1080, 60fps, landscape 16:9) yang seluruhnya dibuat dengan Remotion. Tidak ada editing manual, voice-over, atau suara buatan. Audio hanya berasal dari file musik yang kamu unggah sendiri.

Alur video: intro bola memantul dengan 12 lingkaran, hook "Butuh REST API untuk project kamu?", pengenalan NOXORA REST API, lima fitur (Base64, Hash, Random, Placeholder, Tone), lalu penutup branding NOXORA.

## Menjalankan project

Butuh Node.js 20 atau lebih baru.

```bash
npm install
npm run dev
```

`npm run dev` membuka Remotion Studio untuk pratinjau.

## Menambahkan musik

Letakkan file musik di `public/music.mp3`, lalu commit.

- Musik lebih panjang dari 29 detik: dipotong tepat di 29 detik dengan fade-out 2 detik.
- Musik lebih pendek dari 29 detik: diulang dengan crossfade 0,6 detik di sambungan, tanpa mengubah durasi video.
- Fade-in 1 detik di awal selalu diterapkan.
- Tanpa `public/music.mp3`, video tetap dirender tanpa audio dan workflow memberi peringatan.

Musik bawaan project ini berdurasi 29,49 detik dengan tempo sekitar 72 BPM. Batas scene jatuh di kelipatan beat (50 frame per beat), dan setiap fitur berdurasi 3 beat. Jika kamu mengganti musik dengan tempo lain, ubah `BPM` di `src/constants.ts` dan batas scene `T` ikut menyesuaikan.

## Render

Lokal:

```bash
npm run render
```

Hasil: `out/noxora-rest-api.mp4` (1920x1080, 60fps, 1740 frame, tepat 29 detik).

GitHub Actions: setiap push ke branch `main` menjalankan `.github/workflows/render.yml`, yang merender video, memverifikasi resolusi, fps, dan jumlah frame, lalu mengemasnya menjadi ZIP. Workflow juga bisa dijalankan manual lewat tab Actions.

## Mengambil hasil

1. Buka tab **Actions** di repo, pilih run terbaru.
2. Unduh artifact **noxora-rest-api-promo** di bagian Artifacts.
3. Ekstrak ZIP untuk mendapatkan `noxora-rest-api.mp4`.

## Menyesuaikan konten

Path endpoint dan contoh respons di terminal ada di `src/data.ts`. Nilainya hanya ilustrasi, jadi sesuaikan dengan endpoint asli NOXORA REST API sebelum dipublikasikan.
