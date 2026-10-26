export default function Page() {
  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 32 }}>
      <div style={{ maxWidth: 560 }}>
        <p style={{ letterSpacing: '0.4em', fontSize: 12, opacity: 0.6 }}>NIHONGO PROMO</p>
        <h1 style={{ fontSize: 44, fontWeight: 500, margin: '12px 0 24px' }}>Learn Japanese. Open a New World.</h1>
        <p style={{ opacity: 0.7, lineHeight: 1.7 }}>
          npm run audio, then npm run studio to preview or npm run render to export dist/japanese-promo.mp4.
        </p>
      </div>
    </main>
  );
}
