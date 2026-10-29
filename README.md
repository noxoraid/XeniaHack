# Cosmic Rift Transition

Intro Remotion 8 detik (1920x1080, 30fps) + audio.

## Lokal

```
npm install
npm run studio
npm run render
npm run pack
```

## GitHub Action

Push ke `main` atau jalankan manual (Actions > Render Cosmic Rift Transition). Hasil di Artifacts: `cosmic-rift-transition.zip` berisi mp4 1080p, mp4 720p, poster, dan `web/cosmic-intro.js`. Tag `v*` otomatis bikin Release.

## Pasang di web

```html
<script src="/cosmic-intro.js" data-auto
  data-src="/cosmic-rift-1080p.mp4"
  data-src-mobile="/cosmic-rift-720p.mp4"
  data-poster="/cosmic-rift-poster.jpg"></script>
```

Atau manual: `CosmicIntro.play({src: "...", once: true}).then(() => {})`.

Ganti teks: edit `src/schema.ts` atau props di Studio.
