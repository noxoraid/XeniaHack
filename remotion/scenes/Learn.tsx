import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { clamp, color, font, smooth } from '../theme';

const entries = [
  { jp: 'こんにちは', en: 'Hello', ro: 'konnichiwa' },
  { jp: 'ありがとう', en: 'Thank you', ro: 'arigatou' },
  { jp: 'すごい', en: 'Amazing', ro: 'sugoi' },
];

export const Learn = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const head = spring({ frame: frame - 4, fps, durationInFrames: 40, config: { damping: 200 } });

  return (
    <AbsoluteFill style={{ background: color.paper }}>
      <div style={{ position: 'absolute', left: 140, top: 110, overflow: 'hidden', paddingBottom: 12 }}>
        <div
          style={{
            transform: `translateY(${(1 - head) * 110}%)`,
            fontFamily: font.serif,
            fontSize: 96,
            fontWeight: 500,
            lineHeight: 1.05,
            color: color.ink,
          }}
        >
          Learn Japanese, <span style={{ color: color.red }}>naturally.</span>
        </div>
      </div>
      <div style={{ position: 'absolute', left: 140, right: 140, top: 400, bottom: 110, display: 'flex', gap: 48 }}>
        {entries.map((e, i) => {
          const p = spring({ frame: frame - 26 - i * 28, fps, config: { damping: 18, stiffness: 110 } });
          const float = Math.sin((frame + i * 22) / 26) * 6;
          const mark = interpolate(frame, [44 + i * 28, 74 + i * 28], [0, 1], { ...clamp, easing: smooth });
          return (
            <div
              key={e.en}
              style={{
                flex: 1,
                background: color.surface,
                border: `1px solid ${color.line}`,
                borderRadius: 6,
                padding: '44px 48px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                opacity: Math.min(1, p * 1.4),
                transform: `translateY(${(1 - p) * 140 + float}px) rotate(${(1 - p) * 4}deg)`,
                boxShadow: '0 40px 80px -40px rgba(11,11,12,0.25)',
              }}
            >
              <div style={{ fontFamily: font.sans, fontSize: 20, letterSpacing: '0.3em', color: color.mist }}>
                {`0${i + 1}`}
              </div>
              <div style={{ fontFamily: font.jp, fontSize: 76, fontWeight: 500, color: color.ink }}>{e.jp}</div>
              <div>
                <div style={{ height: 2, background: color.line, marginBottom: 28, position: 'relative' }}>
                  <div
                    style={{
                      position: 'absolute',
                      inset: 0,
                      background: color.red,
                      transformOrigin: 'left',
                      transform: `scaleX(${mark})`,
                    }}
                  />
                </div>
                <div style={{ fontFamily: font.serif, fontSize: 60, fontWeight: 500, color: color.ink }}>{e.en}</div>
                <div
                  style={{
                    fontFamily: font.sans,
                    fontSize: 22,
                    letterSpacing: '0.12em',
                    color: color.mist,
                    marginTop: 6,
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
