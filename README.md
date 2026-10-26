# RENN X JAPAN Promo

Video promo 29 detik (870 frame, 30 FPS, 1920x1080, H.264 + AAC) dengan Remotion di dalam project Next.js. Tanpa voice-over, hanya musik.

## Render lokal

Butuh Node 20, ffmpeg (untuk verifikasi), dan font Noto CJK.

```
npm install
npm run render
```

Hasil: `dist/japanese-promo.mp4`. Preview: `npm run studio`.

## Aset

- Musik: `public/audio/music.mp3`
- Screenshot yang sudah dipotong: `public/shots/`

## GitHub Actions

Workflow `render-video.yml` jalan pada push ke `main` dan manual. Unduh `japanese-promo-mp4` atau `japanese-promo-zip` dari bagian Artifacts di halaman run.

## Ganti brand dan domain

Ubah `brand` di `remotion/theme.ts`.
