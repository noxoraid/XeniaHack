import {AbsoluteFill, Audio, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {Backdrop} from './parts/Backdrop';
import {Hud} from './parts/Hud';
import {Intro} from './scenes/Intro';
import {Beranda} from './scenes/Beranda';
import {Fitur} from './scenes/Fitur';
import {Hiragana} from './scenes/Hiragana';
import {Profil} from './scenes/Profil';
import {Outro} from './scenes/Outro';
import {canvas, palette, timeline} from './theme';
import {clamp} from './fx';

const last = canvas.frames;

const musicVolume = (frame: number) =>
  interpolate(frame, [0, 45, last - 50, last], [0, 1, 1, 0], clamp);

const Veil = () => {
  const frame = useCurrentFrame();
  const opacity = interpolate(frame, [0, 24, last - 24, last], [1, 0, 0, 1], clamp);
  return <AbsoluteFill style={{backgroundColor: palette.paper, opacity, pointerEvents: 'none'}} />;
};

export const Promo = () => (
  <AbsoluteFill>
    <Backdrop />
    <Audio src={staticFile('musik.mp3')} volume={musicVolume} />
    <Sequence from={timeline.intro.from} durationInFrames={timeline.intro.duration}>
      <Intro />
    </Sequence>
    <Sequence from={timeline.beranda.from} durationInFrames={timeline.beranda.duration}>
      <Beranda />
    </Sequence>
    <Sequence from={timeline.fitur.from} durationInFrames={timeline.fitur.duration}>
      <Fitur />
    </Sequence>
    <Sequence from={timeline.hiragana.from} durationInFrames={timeline.hiragana.duration}>
      <Hiragana />
    </Sequence>
    <Sequence from={timeline.profil.from} durationInFrames={timeline.profil.duration}>
      <Profil />
    </Sequence>
    <Sequence from={timeline.outro.from} durationInFrames={timeline.outro.duration}>
      <Outro />
    </Sequence>
    <Hud />
    <Veil />
  </AbsoluteFill>
);
