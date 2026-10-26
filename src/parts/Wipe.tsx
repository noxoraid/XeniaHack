import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion';
import {cuts, palette} from '../theme';

const reach = 22;

type PanelProps = {lag: number; color: string; edge: string};

const Panel = ({lag, color, edge}: PanelProps) => {
  const frame = useCurrentFrame();
  const cut = cuts.find((c) => Math.abs(frame - lag - c) <= reach);
  if (cut === undefined) {
    return null;
  }
  const p = (frame - lag - cut + reach) / (reach * 2);
  const x =
    p < 0.5
      ? interpolate(p, [0, 0.5], [-100, 0], {easing: Easing.out(Easing.cubic)})
      : interpolate(p, [0.5, 1], [0, 100], {easing: Easing.in(Easing.cubic)});
  return (
    <AbsoluteFill
      style={{
        transform: `translateX(${x}%)`,
        backgroundColor: color,
        backgroundImage: `linear-gradient(90deg, transparent 99.4%, ${edge} 99.4%)`,
        pointerEvents: 'none',
      }}
    />
  );
};

export const Wipe = () => (
  <>
    <Panel lag={7} color={palette.ink} edge={palette.ink} />
    <Panel lag={0} color={palette.emerald} edge={palette.mint} />
  </>
);
