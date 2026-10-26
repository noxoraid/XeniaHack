import { AbsoluteFill, Audio, Easing, Sequence, interpolate, staticFile, useCurrentFrame } from 'remotion';
import { clamp, color, fps } from './theme';
import { voice } from './voice';
import { Hook } from './scenes/Hook';
import { Learn } from './scenes/Learn';
import { Explore } from './scenes/Explore';
import { Experience } from './scenes/Experience';
import { Finale } from './scenes/Finale';

const scenes = [
  { from: 0, length: 150, Scene: Hook },
  { from: 150, length: 180, Scene: Learn },
  { from: 330, length: 210, Scene: Explore },
  { from: 540, length: 180, Scene: Experience },
  { from: 720, length: 180, Scene: Finale },
];

const Sweep = () => {
  const frame = useCurrentFrame();
  const x = interpolate(frame, [0, 28], [-100, 100], { easing: Easing.inOut(Easing.cubic) });
  return <AbsoluteFill style={{ background: color.red, transform: `translateX(${x}%)` }} />;
};

const musicLevel = (f: number) => {
  const tail = interpolate(f, [850, 899], [1, 0], clamp);
  const duck = voice.reduce((v, l) => {
    const s = l.start * fps;
    const e = s + l.span * fps;
    return Math.min(v, interpolate(f, [s - 10, s + 6, e, e + 18], [1, 0.4, 0.4, 1], clamp));
  }, 1);
  return 0.6 * duck * tail;
};

export const PromoVideo = () => (
  <AbsoluteFill style={{ background: color.ink }}>
    {scenes.map(({ from, length, Scene }) => (
      <Sequence key={from} from={from} durationInFrames={length}>
        <Scene />
      </Sequence>
    ))}
    {scenes.slice(1).map(({ from }) => (
      <Sequence key={`sweep-${from}`} from={from - 14} durationInFrames={28}>
        <Sweep />
      </Sequence>
    ))}
    <Audio src={staticFile('audio/music.wav')} volume={musicLevel} />
    {voice.map((l) => (
      <Sequence key={l.file} from={Math.round(l.start * fps)} layout="none">
        <Audio src={staticFile(l.file)} volume={1} />
      </Sequence>
    ))}
  </AbsoluteFill>
);
