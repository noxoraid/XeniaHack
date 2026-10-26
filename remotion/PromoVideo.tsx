import { AbsoluteFill, Audio, Easing, Sequence, interpolate, staticFile, useCurrentFrame } from 'remotion';
import { clamp, color, frames } from './theme';
import { Hook } from './scenes/Hook';
import { Learn } from './scenes/Learn';
import { Explore } from './scenes/Explore';
import { Experience } from './scenes/Experience';
import { Finale } from './scenes/Finale';

const scenes = [
  { from: 0, length: 135, Scene: Hook },
  { from: 135, length: 180, Scene: Learn },
  { from: 315, length: 210, Scene: Explore },
  { from: 525, length: 180, Scene: Experience },
  { from: 705, length: 165, Scene: Finale },
];

const Sweep = () => {
  const frame = useCurrentFrame();
  const move = (delay: number) =>
    interpolate(frame, [delay, 28], [-100, 100], { ...clamp, easing: Easing.inOut(Easing.cubic) });
  return (
    <AbsoluteFill>
      <AbsoluteFill style={{ background: color.green, transform: `translateX(${move(0)}%)` }} />
      <AbsoluteFill style={{ background: color.ink, transform: `translateX(${move(5)}%)` }} />
    </AbsoluteFill>
  );
};

const level = (f: number) => 0.85 * interpolate(f, [0, 24, frames - 60, frames - 1], [0, 1, 1, 0], clamp);

export const PromoVideo = () => (
  <AbsoluteFill style={{ background: color.ink }}>
    {scenes.map(({ from, length, Scene }) => (
      <Sequence key={from} from={from} durationInFrames={length}>
        <Scene offset={from} />
      </Sequence>
    ))}
    {scenes.slice(1).map(({ from }) => (
      <Sequence key={`sweep-${from}`} from={from - 14} durationInFrames={28}>
        <Sweep />
      </Sequence>
    ))}
    <Audio src={staticFile('audio/music.mp3')} volume={level} />
  </AbsoluteFill>
);
