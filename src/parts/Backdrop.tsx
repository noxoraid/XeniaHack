import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {canvas, palette} from '../theme';

export const Backdrop = () => {
  const frame = useCurrentFrame();
  const drift = frame / canvas.frames;
  const glowTop = `radial-gradient(circle at ${15 + 30 * drift}% ${10 + 12 * Math.sin(frame / 160)}%, rgba(52,195,138,0.2), transparent 40%)`;
  const glowBottom = `radial-gradient(circle at ${90 - 35 * drift}% ${95 - 12 * Math.cos(frame / 190)}%, rgba(15,94,67,0.14), transparent 44%)`;
  const columns = 'repeating-linear-gradient(90deg, rgba(216,225,219,0.5) 0px, rgba(216,225,219,0.5) 1px, transparent 1px, transparent 160px)';

  return (
    <AbsoluteFill style={{backgroundColor: palette.paper, backgroundImage: `${glowTop}, ${glowBottom}, ${columns}`}}>
      <div
        style={{
          position: 'absolute',
          inset: 32,
          borderRadius: 40,
          border: `1px solid ${palette.line}`,
        }}
      />
    </AbsoluteFill>
  );
};
