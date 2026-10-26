import {loadFont as loadDisplay} from '@remotion/google-fonts/Sora';
import {loadFont as loadText} from '@remotion/google-fonts/Manrope';

const display = loadDisplay('normal', {weights: ['600', '700', '800'], subsets: ['latin']});
const text = loadText('normal', {weights: ['500', '600', '700'], subsets: ['latin']});

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
  display: display.fontFamily,
  text: text.fontFamily,
};

export const canvas = {width: 1920, height: 1080, fps: 60, frames: 1740};

export const timeline = {
  opening: {from: 0, duration: 552},
  fitur: {from: 552, duration: 267},
  hiragana: {from: 819, duration: 264},
  progres: {from: 1083, duration: 249},
  koleksi: {from: 1332, duration: 196},
  penutup: {from: 1528, duration: 212},
};

export const cuts = [552, 819, 1083, 1332, 1528];
