import { AbsoluteFill, interpolate, interpolateColors, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { clamp, color, font, smooth } from '../theme';

const glyphs = [...'日本語を学ぼう'];

export const Hook = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const shift = interpolate(frame, [98, 140], [0, 1], { ...clamp, easing: smooth });
  const bg = interpolateColors(shift, [0, 1], [color.ink, color.paper]);
  const fg = interpolateColors(shift, [0, 1], [color.paper, color.ink]);
  const zoom = interpolate(frame, [0, 150], [1, 1.08]);
  const sun = spring({ frame: frame - 6, fps, durationInFrames: 80, config: { damping: 200 } });
  const rule = interpolate(frame, [18, 74], [0, 1], { ...clamp, easing: smooth });
  const sub = interpolate(frame, [64, 100], [0, 1], { ...clamp, easing: smooth });

  return (
    <AbsoluteFill style={{ background: bg, overflow: 'hidden' }}>
      <AbsoluteFill style={{ transform: `scale(${zoom})`, alignItems: 'center', justifyContent: 'center' }}>
        <div
          style={{
            position: 'absolute',
            width: 420,
            height: 420,
            borderRadius: '50%',
            background: color.red,
            right: 250,
            top: 120,
            transform: `scale(${sun})`,
          }}
        />
        <div style={{ position: 'absolute', left: 240, top: 0, width: 2, height: `${rule * 100}%`, background: color.red }} />
        <div style={{ display: 'flex', gap: 20, fontFamily: font.jp, fontSize: 150, fontWeight: 500, color: fg }}>
          {glyphs.map((g, i) => {
            const p = spring({ frame: frame - 14 - i * 5, fps, durationInFrames: 40, config: { damping: 200 } });
            return (
              <span
                key={i}
                style={{
                  opacity: p,
                  transform: `translateY(${(1 - p) * 44}px)`,
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
            position: 'absolute',
            top: 640,
            fontFamily: font.sans,
            fontSize: 30,
            letterSpacing: `${interpolate(sub, [0, 1], [0.6, 0.2])}em`,
            opacity: sub,
            color: fg,
          }}
        >
          Your Japanese journey starts here.
        </div>
      </AbsoluteFill>
      <div
        style={{
          position: 'absolute',
          left: 120,
          top: 90,
          fontFamily: font.sans,
          fontSize: 18,
          letterSpacing: '0.45em',
          color: fg,
          opacity: sub * 0.7,
        }}
      >
        NIHONGO
      </div>
      <div
        style={{
          position: 'absolute',
          right: 120,
          bottom: 90,
          fontFamily: font.sans,
          fontSize: 18,
          letterSpacing: '0.3em',
          color: fg,
          opacity: sub * 0.7,
        }}
      >
        01 / 05
      </div>
    </AbsoluteFill>
  );
};
