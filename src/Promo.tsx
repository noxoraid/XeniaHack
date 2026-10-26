import {AbsoluteFill, Audio, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {Backdrop} from './parts/Backdrop';
import {Hud} from './parts/Hud';
import {Wipe} from './parts/Wipe';
import {Opening} from './scenes/Opening';
import {Fitur} from './scenes/Fitur';
import {Hiragana} from './scenes/Hiragana';
import {Progres} from './scenes/Progres';
import {Koleksi} from './scenes/Koleksi';
import {Penutup} from './scenes/Penutup';
import {canvas, palette, timeline} from './theme';
import {clamp} from './fx';

const last = canvas.frames;

const musicVolume = (frame: number) => interpolate(frame, [0, 40, last - 100, last], [0, 1, 1, 0], clamp);

const Veil = () => {
  const frame = useCurrentFrame();
  const opacity = interpolate(frame, [0, 36, last - 40, last], [1, 0, 0, 1], clamp);
  return <AbsoluteFill style={{backgroundColor: palette.paper, opacity, pointerEvents: 'none'}} />;
};

export const Promo = () => (
  <AbsoluteFill>
    <Backdrop />
    <Audio src={staticFile('musik.mp3')} volume={musicVolume} />
    <Sequence from={timeline.opening.from} durationInFrames={timeline.opening.duration}>
      <Opening />
    </Sequence>
    <Sequence from={timeline.fitur.from} durationInFrames={timeline.fitur.duration}>
      <Fitur />
    </Sequence>
    <Sequence from={timeline.hiragana.from} durationInFrames={timeline.hiragana.duration}>
      <Hiragana />
    </Sequence>
    <Sequence from={timeline.progres.from} durationInFrames={timeline.progres.duration}>
      <Progres />
    </Sequence>
    <Sequence from={timeline.koleksi.from} durationInFrames={timeline.koleksi.duration}>
      <Koleksi />
    </Sequence>
    <Sequence from={timeline.penutup.from} durationInFrames={timeline.penutup.duration}>
      <Penutup />
    </Sequence>
    <Hud />
    <Wipe />
    <Veil />
  </AbsoluteFill>
);
