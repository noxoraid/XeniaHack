import { Easing } from 'remotion';
import { loadFont } from '@remotion/google-fonts/Manrope';

const sans = loadFont('normal', { weights: ['500', '700', '800'], subsets: ['latin'] });

export const fps = 30;
export const frames = 870;

export const brand = { name: 'RENN X JAPAN', domain: 'nihongo.example' };

export const color = {
  ink: '#050d09',
  light: '#eef6f1',
  text: '#07140d',
  white: '#f4faf6',
  green: '#22d98a',
  greenDeep: '#12a867',
  gray: '#6f857a',
  line: '#d4e6db',
  card: '#ffffff',
};

export const font = {
  sans: `${sans.fontFamily}, sans-serif`,
  jp: "'Noto Sans CJK JP', 'Noto Sans JP', 'Hiragino Sans', sans-serif",
};

export const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
export const smooth = Easing.bezier(0.22, 1, 0.36, 1);

const bpm = 107.66;
const firstBeat = 0.14;

export const beat = (frame: number) => {
  const t = frame / fps - firstBeat;
  if (t < 0) return 0;
  const phase = ((t * bpm) / 60) % 1;
  return Math.exp(-phase * 5);
};
