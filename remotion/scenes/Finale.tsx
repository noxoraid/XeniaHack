import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { brand, clamp, color, font, smooth } from '../theme';
import { Browser } from './Browser';

const headline = ['Start learning', 'Japanese today.'];

export const Finale = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const body = interpolate(frame, [112, 130], [1, 0], clamp);
  const mark = interpolate(frame, [132, 164], [0, 1], { ...clamp, easing: smooth });
  const btn = spring({ frame: frame - 46, fps, config: { damping: 14, stiffness: 120 } });
  const win = spring({ frame: frame - 12, fps, durationInFrames: 50, config: { damping: 200 } });
  const cycle = frame >= 70 && frame < 120 ? ((frame - 70) % 54) / 54 : 1;

  return (
    <AbsoluteFill style={{ background: color.ink, overflow: 'hidden' }}>
      <AbsoluteFill style={{ opacity: body }}>
        <div
          style={{
            position: 'absolute',
            left: 140,
            top: 0,
            bottom: 0,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
          }}
        >
          <div
            style={{
              fontFamily: font.sans,
              fontSize: 18,
              letterSpacing: '0.45em',
              color: color.mist,
              marginBottom: 28,
            }}
          >
            {brand.name.toUpperCase()}
          </div>
          {headline.map((l, i) => {
            const p = spring({ frame: frame - 6 - i * 10, fps, durationInFrames: 36, config: { damping: 200 } });
            return (
              <div key={l} style={{ overflow: 'hidden', paddingBottom: 8 }}>
                <div
                  style={{
                    transform: `translateY(${(1 - p) * 105}%)`,
                    fontFamily: font.serif,
                    fontSize: 104,
                    fontWeight: 500,
                    lineHeight: 1.04,
                    color: i === 1 ? color.red : color.paper,
                  }}
                >
                  {l}
                </div>
              </div>
            );
          })}
          <div style={{ marginTop: 56, position: 'relative', alignSelf: 'flex-start', transform: `scale(${btn})`, opacity: btn }}>
            <div
              style={{
                position: 'absolute',
                inset: 0,
                border: `2px solid ${color.red}`,
                borderRadius: 4,
                transform: `scale(${1 + cycle * 0.35})`,
                opacity: (1 - cycle) * 0.6,
              }}
            />
            <div
              style={{
                position: 'relative',
                background: color.red,
                color: '#fff',
                borderRadius: 4,
                padding: '30px 58px',
                fontFamily: font.sans,
                fontSize: 24,
                fontWeight: 600,
                letterSpacing: '0.22em',
              }}
            >
              START LEARNING →
            </div>
          </div>
          <div
            style={{
              marginTop: 28,
              fontFamily: font.sans,
              fontSize: 24,
              letterSpacing: '0.08em',
              color: color.mist,
              opacity: btn,
            }}
          >
            {brand.domain}
          </div>
        </div>
        <div
          style={{
            position: 'absolute',
            right: 100,
            top: 295,
            width: 1040,
            height: 680,
            opacity: win,
            transformOrigin: '100% 0',
            transform: `translateY(${(1 - win) * 80}px) scale(0.72)`,
          }}
        >
          <Browser t={frame + 60} />
        </div>
      </AbsoluteFill>
      <AbsoluteFill
        style={{
          alignItems: 'center',
          justifyContent: 'center',
          opacity: mark,
          transform: `translateY(${(1 - mark) * 30}px)`,
          filter: `blur(${(1 - mark) * 10}px)`,
        }}
      >
        <div style={{ fontFamily: font.jp, fontSize: 34, letterSpacing: '0.6em', color: color.mist, paddingLeft: '0.6em' }}>
          日本語
        </div>
        <div style={{ fontFamily: font.serif, fontSize: 200, fontWeight: 500, color: color.paper, lineHeight: 1.1 }}>
          {brand.name}
        </div>
        <div style={{ width: 160 * mark, height: 3, background: color.red, marginTop: 10 }} />
        <div
          style={{
            marginTop: 36,
            fontFamily: font.sans,
            fontSize: 22,
            letterSpacing: '0.3em',
            color: color.mist,
          }}
        >
          {brand.domain.toUpperCase()}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
