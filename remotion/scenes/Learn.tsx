import { AbsoluteFill, interpolate, interpolateColors, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { beat, clamp, color, font } from '../theme';
import { Glow } from '../ui';

const entries = [
  { jp: 'こんにちは', en: 'Hello', ro: 'konnichiwa' },
  { jp: 'ありがとう', en: 'Thank you', ro: 'arigatou' },
  { jp: 'すごい', en: 'Amazing', ro: 'sugoi' },
];

export const Learn = ({ offset }: { offset: number }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pulse = beat(frame + offset);
  const head = spring({ frame: frame - 4, fps, durationInFrames: 40, config: { damping: 200 } });

  return (
    <AbsoluteFill style={{ background: color.light, overflow: 'hidden' }}>
      <Glow x={1650} y={150} size={900} alpha={0.22} pulse={pulse} />
      <div style={{ position: 'absolute', left: 140, top: 110, overflow: 'hidden', paddingBottom: 14 }}>
        <div
          style={{
            transform: `translateY(${(1 - head) * 110}%)`,
            fontFamily: font.sans,
            fontSize: 92,
            fontWeight: 800,
            letterSpacing: '-0.02em',
            lineHeight: 1.08,
            color: color.text,
          }}
        >
          Learn Japanese, <span style={{ color: color.greenDeep }}>naturally.</span>
        </div>
      </div>
      <div style={{ position: 'absolute', left: 140, right: 140, top: 400, bottom: 110, display: 'flex', gap: 40 }}>
        {entries.map((e, i) => {
          const p = spring({ frame: frame - 24 - i * 30, fps, config: { damping: 18, stiffness: 110 } });
          const start = 44 + i * 36;
          const last = i === entries.length - 1;
          const on = interpolate(frame, [start, start + 10, start + 36, start + 46], [0, 1, 1, last ? 1 : 0], clamp);
          const float = Math.sin((frame + i * 22) / 26) * 6;
          return (
            <div
              key={e.en}
              style={{
                flex: 1,
                background: interpolateColors(on, [0, 1], [color.card, color.green]),
                borderRadius: 40,
                padding: '44px 48px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                opacity: Math.min(1, p * 1.4),
                transform: `translateY(${(1 - p) * 140 + float}px) scale(${1 + on * 0.025})`,
                boxShadow: '0 40px 80px -40px rgba(7,20,13,0.28)',
                color: color.text,
              }}
            >
              <div
                style={{
                  alignSelf: 'flex-start',
                  fontFamily: font.sans,
                  fontSize: 20,
                  fontWeight: 700,
                  letterSpacing: '0.2em',
                  padding: '8px 18px',
                  borderRadius: 999,
                  background: interpolateColors(on, [0, 1], ['#e3f6ec', 'rgba(7,20,13,0.12)']),
                  color: interpolateColors(on, [0, 1], [color.greenDeep, color.text]),
                }}
              >
                {`0${i + 1}`}
              </div>
              <div style={{ fontFamily: font.jp, fontSize: 76, fontWeight: 700 }}>{e.jp}</div>
              <div>
                <div style={{ fontFamily: font.sans, fontSize: 56, fontWeight: 800, letterSpacing: '-0.02em' }}>{e.en}</div>
                <div
                  style={{
                    fontFamily: font.sans,
                    fontSize: 24,
                    fontWeight: 500,
                    letterSpacing: '0.06em',
                    marginTop: 6,
                    color: interpolateColors(on, [0, 1], [color.gray, 'rgba(7,20,13,0.7)']),
                  }}
                >
                  {e.ro}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};
