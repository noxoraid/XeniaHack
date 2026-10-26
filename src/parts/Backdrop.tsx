import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {canvas, palette} from '../theme';

export const Backdrop = () => {
  const frame = useCurrentFrame();
  const drift = frame / canvas.frames;
  const glowTop = `radial-gradient(circle at ${20 + 30 * drift}% ${15 + 10 * Math.sin(frame / 90)}%, rgba(52,195,138,0.22), transparent 42%)`;
  const glowBottom = `radial-gradient(circle at ${85 - 25 * drift}% ${88 - 10 * Math.cos(frame / 110)}%, rgba(15,94,67,0.14), transparent 46%)`;

  return (
    <AbsoluteFill style={{backgroundColor: palette.paper, backgroundImage: `${glowTop}, ${glowBottom}`}}>
      <div
        style={{
          position: 'absolute',
          inset: 36,
          borderRadius: 44,
          border: `1px solid ${palette.line}`,
        }}
      />
    </AbsoluteFill>
  );
};
