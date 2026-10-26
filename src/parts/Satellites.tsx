import {interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {clamp, outQuart} from '../fx';
import {palette} from '../theme';

const count = 12;
const tones = [palette.emerald, palette.ink, palette.mint];

type DotProps = {index: number; cx: number; cy: number; radius: number; start: number; spin: number};

const Dot = ({index, cx, cy, radius, start, spin}: DotProps) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const pop = spring({
    frame: Math.max(0, frame - start - index * 3),
    fps,
    config: {damping: 10, mass: 0.7, stiffness: 120},
  });
  const angle = (index / count) * Math.PI * 2 - Math.PI / 2 + spin;
  const wobble = Math.sin((frame + index * 9) / 26) * 6;
  const distance = (radius + wobble) * pop;
  const size = (9 + (index % 3) * 7) * Math.max(pop, 0);

  return (
    <circle
      cx={cx + Math.cos(angle) * distance}
      cy={cy + Math.sin(angle) * distance}
      r={size}
      fill={tones[index % 3]}
    />
  );
};

type Props = {cx: number; cy: number; radius: number; start: number; disc?: boolean};

export const Satellites = ({cx, cy, radius, start, disc = false}: Props) => {
  const frame = useCurrentFrame();
  const draw = interpolate(frame, [start, start + 70], [0, 1], {...clamp, easing: outQuart});
  const spin = Math.max(0, frame - start) * 0.0055;
  const length = 2 * Math.PI * radius;

  return (
    <svg width={1920} height={1080} viewBox="0 0 1920 1080" style={{position: 'absolute', left: 0, top: 0}}>
      {disc ? <circle cx={cx} cy={cy} r={radius * 0.74} fill={palette.mist} opacity={draw} /> : null}
      <circle
        cx={cx}
        cy={cy}
        r={radius}
        fill="none"
        stroke={palette.mint}
        strokeWidth={2}
        strokeDasharray={length}
        strokeDashoffset={length * (1 - draw)}
        transform={`rotate(-90 ${cx} ${cy})`}
      />
      <circle
        cx={cx}
        cy={cy}
        r={radius * 1.16}
        fill="none"
        stroke={palette.line}
        strokeWidth={3}
        strokeLinecap="round"
        strokeDasharray="1 18"
        opacity={draw}
        transform={`rotate(${-spin * 28} ${cx} ${cy})`}
      />
      {Array.from({length: count}, (_, i) => (
        <Dot key={i} index={i} cx={cx} cy={cy} radius={radius} start={start} spin={spin} />
      ))}
    </svg>
  );
};
