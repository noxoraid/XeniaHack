import {loadFont as loadSerif} from '@remotion/google-fonts/CormorantGaramond';
import {loadFont as loadSans} from '@remotion/google-fonts/Manrope';

const serif = loadSerif('normal', {weights: ['500', '600', '700'], subsets: ['latin']});
loadSerif('italic', {weights: ['500', '600'], subsets: ['latin']});
const sans = loadSans('normal', {weights: ['500', '600', '700'], subsets: ['latin']});

export const palette = {
  paper: '#FAFAF7',
  ink: '#0B0E0C',
  emerald: '#0F5E43',
  mint: '#34C38A',
  mist: '#E8F2EC',
  line: '#D8E1DB',
  soft: '#5E6B64',
};

export const fonts = {
  serif: serif.fontFamily,
  sans: sans.fontFamily,
};

export const canvas = {width: 1080, height: 1920, fps: 30, frames: 870};

export const timeline = {
  intro: {from: 0, duration: 135},
  beranda: {from: 135, duration: 150},
  fitur: {from: 285, duration: 150},
  hiragana: {from: 435, duration: 150},
  profil: {from: 585, duration: 150},
  outro: {from: 735, duration: 135},
};
