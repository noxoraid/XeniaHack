import { AbsoluteFill, interpolate, interpolateColors, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { beat, brand, clamp, color, font, smooth } from '../theme';
import { Glow } from '../ui';

const glyphs = [...'日本語を学ぼう'];

export const Hook = ({ offset }: { offset: number }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pulse = beat(frame + offset);
  const shift = interpolate(frame, [86, 124], [0, 1], { ...clamp, easing: smooth });
  const bg = interpolateColors(shift, [0, 1], [color.ink, color.light]);
  const fg = interpolateColors(shift, [0, 1], [color.white, color.text]);
  const zoom = interpolate(frame, [0, 135], [1, 1.07]);
  const bar = interpolate(frame, [30, 74], [0, 1], { ...clamp, easing: smooth });
  const sub = interpolate(frame, [58, 90], [0, 1], { ...clamp, easing: smooth });

  return (
    <AbsoluteFill style={{ background: bg, overflow: 'hidden' }}>
      <Glow x={1300} y={360} size={1100} alpha={0.4} pulse={pulse} />
      <Glow x={420} y={820} size={800} alpha={0.2} pulse={pulse} />
      <AbsoluteFill style={{ transform: `scale(${zoom})`, alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ display: 'flex', gap: 14, fontFamily: font.jp, fontSize: 156, fontWeight: 700, color: fg }}>
          {glyphs.map((g, i) => {
            const p = spring({ frame: frame - 10 - i * 4, fps, durationInFrames: 38, config: { damping: 200 } });
            return (
              <span
                key={i}
                style={{
                  opacity: p,
                  transform: `translateY(${(1 - p) * 50}px)`,
                  filter: `blur(${(1 - p) * 14}px)`,
                }}
              >
                {g}
              </span>
            );
          })}
        </div>
        <div
          style={{
            marginTop: 40,
            width: 140 * bar,
            height: 6,
            borderRadius: 3,
            background: color.green,
          }}
        />
        <div
          style={{
            marginTop: 36,
            fontFamily: font.sans,
            fontSize: 34,
            fontWeight: 500,
            letterSpacing: '0.04em',
            color: fg,
            opacity: sub,
            transform: `translateY(${(1 - sub) * 16}px)`,
          }}
        >
          Your Japanese journey starts here.
        </div>
      </AbsoluteFill>
      <div
        style={{
          position: 'absolute',
          left: 120,
          top: 88,
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          fontFamily: font.sans,
          fontSize: 18,
          fontWeight: 700,
          letterSpacing: '0.32em',
          color: fg,
          opacity: sub,
        }}
      >
        <div style={{ width: 12, height: 12, borderRadius: 6, background: color.green }} />
        {brand.name}
      </div>
    </AbsoluteFill>
  );
};
