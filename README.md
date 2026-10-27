# UntilyFlow Promo (Remotion)

- Komposisi: `UntilyFlowPromo`, 1920x1080 (landscape HD), 30 fps, ~29,5 detik.
- Musik: hanya `public/audio/music.mp3`. Audio dari footage dimatikan.
- Render lokal: `npm install && npm run render` → `out/UntilyFlow-Promo.mp4`.
- GitHub Actions: `.github/workflows/render.yml` merender MP4 lalu membuat zip (artifact `untilyflow-promo`).
- Edit teks adegan di `src/Promo.tsx` dan `src/scenes/`.
