export const FEATURES = [
  {
    id: 'base64',
    name: 'Base64',
    note: 'Encode dan decode teks',
    method: 'GET',
    path: '/base64?text=Halo',
    res: ['{', '  "result": "SGFsbw=="', '}']
  },
  {
    id: 'hash',
    name: 'Hash',
    note: 'Hash dari data kamu',
    method: 'GET',
    path: '/hash?text=Halo',
    res: ['{', '  "hash": "a3f1c9e27b04…"', '}']
  },
  {
    id: 'random',
    name: 'Random',
    note: 'Data acak sekali panggil',
    method: 'GET',
    path: '/random',
    res: ['{', '  "value": 482913', '}']
  },
  {
    id: 'placeholder',
    name: 'Placeholder',
    note: 'Data contoh untuk tampilan',
    method: 'GET',
    path: '/placeholder',
    res: ['{', '  "title": "Judul contoh"', '}']
  },
  {
    id: 'tone',
    name: 'Tone',
    note: 'Siap dipanggil lewat HTTP',
    method: 'GET',
    path: '/tone',
    res: ['{', '  "status": "ok"', '}']
  }
]
