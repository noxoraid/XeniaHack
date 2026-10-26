# Nihongo Promo

Video promo 30 detik (900 frame, 30 FPS, 1920x1080, H.264 + AAC) dengan Remotion di dalam project Next.js.

## Render lokal

Butuh Node 20, ffmpeg, Python 3.11 dengan `pip install piper-tts==1.2.0`, dan font Noto CJK.

```
npm install
npm run audio
npm run render
```

Hasil: `dist/japanese-promo.mp4`. Preview: `npm run studio`.

## Audio

- Voice-over: Piper TTS (suara `en_US-lessac-medium`). Model diunduh dari Hugging Face saat `npm run audio`. Tanpa API key. Naskah di `scripts/voiceover.txt`, timing di `remotion/voice.ts`.
- Musik: ambient pad disintesis lewat ffmpeg di `scripts/audio.sh`. Tanpa lisensi pihak ketiga.
- File audio dibuat saat build dan tidak disimpan di Git.

## GitHub Actions

Workflow `render-video.yml` jalan pada push ke `main` dan manual. Unduh `japanese-promo-mp4` atau `japanese-promo-zip` dari bagian Artifacts di halaman run.

## Ganti nama brand

Ubah `brand` di `remotion/theme.ts`.
