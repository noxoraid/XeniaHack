import { Easing } from 'remotion';
import { loadFont as loadSerif } from '@remotion/google-fonts/CormorantGaramond';
import { loadFont as loadSans } from '@remotion/google-fonts/Inter';

const serif = loadSerif('normal', { weights: ['500', '600'], subsets: ['latin'] });
const sans = loadSans('normal', { weights: ['400', '500', '600'], subsets: ['latin'] });

export const fps = 30;
export const frames = 900;

export const brand = { name: 'Nihongo', domain: 'nihongo.example' };

export const color = {
  ink: '#0b0b0c',
  paper: '#f3efe7',
  red: '#d3201f',
  mist: '#8c8a85',
  line: '#d8d3c8',
  surface: '#fbf9f4',
};

export const font = {
  serif: `${serif.fontFamily}, serif`,
  sans: `${sans.fontFamily}, sans-serif`,
  jp: "'Noto Serif CJK JP', 'Noto Serif JP', 'Hiragino Mincho ProN', serif",
};

export const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
export const smooth = Easing.bezier(0.22, 1, 0.36, 1);
