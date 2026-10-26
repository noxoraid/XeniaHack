export const FPS = 60
export const W = 1920
export const H = 1080
export const DURATION_SEC = 29
export const TOTAL = FPS * DURATION_SEC
export const BPM = 72
export const BEAT = (FPS * 60) / BPM
export const sec = (s: number) => Math.round(s * FPS)
export const LEAD = 14

export const C = {
  bg: '#F5F9F6',
  ink: '#0B0F0C',
  green: '#14C45A',
  greenDark: '#0A8A3C',
  greenSoft: '#DDF6E6',
  white: '#FFFFFF',
  line: '#E1EAE4',
  grey: '#58685D',
  term: '#6F8277'
}

export const T = {
  hook: 4 * BEAT,
  product: 10 * BEAT,
  features: 15 * BEAT,
  outro: 30 * BEAT
}

export const FEATURE_LEN = sec(2.5)
