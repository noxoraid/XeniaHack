import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { beat, brand, clamp, color, font, smooth } from '../theme';
import { Glow, Phone, Shot } from '../ui';

const headline = ['Start learning', 'Japanese today.'];

export const Finale = ({ offset }: { offset: number }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pulse = beat(frame + offset);
  const body = interpolate(frame, [100, 118], [1, 0], clamp);
  const mark = interpolate(frame, [120, 150], [0, 1], { ...clamp, easing: smooth });
  const btn = spring({ frame: frame - 40, fps, config: { damping: 14, stiffness: 120 } });
  const phone = spring({ frame: frame - 8, fps, durationInFrames: 46, config: { damping: 200 } });
  const card = spring({ frame: frame - 24, fps, durationInFrames: 46, config: { damping: 200 } });

  return (
    <AbsoluteFill style={{ background: color.ink, overflow: 'hidden' }}>
      <Glow x={1400} y={540} size={1200} alpha={0.34} pulse={pulse} />
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
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              fontFamily: font.sans,
              fontSize: 18,
              fontWeight: 700,
              letterSpacing: '0.32em',
              color: color.gray,
              marginBottom: 30,
            }}
          >
            <div style={{ width: 12, height: 12, borderRadius: 6, background: color.green }} />
            {brand.name}
          </div>
          {headline.map((l, i) => {
            const p = spring({ frame: frame - 6 - i * 10, fps, durationInFrames: 36, config: { damping: 200 } });
            return (
              <div key={l} style={{ overflow: 'hidden', paddingBottom: 8 }}>
                <div
                  style={{
                    transform: `translateY(${(1 - p) * 105}%)`,
                    fontFamily: font.sans,
                    fontSize: 92,
                    fontWeight: 800,
                    letterSpacing: '-0.025em',
                    lineHeight: 1.06,
                    color: i === 1 ? color.green : color.white,
                  }}
                >
                  {l}
                </div>
              </div>
            );
          })}
          <div
            style={{
              marginTop: 58,
              position: 'relative',
              alignSelf: 'flex-start',
              opacity: btn,
              transform: `scale(${btn})`,
            }}
          >
            <div
              style={{
                position: 'absolute',
                inset: 0,
                borderRadius: 999,
                border: `2px solid ${color.green}`,
                transform: `scale(${1 + pulse * 0.16})`,
                opacity: pulse * 0.7,
              }}
            />
            <div
              style={{
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                gap: 22,
                background: color.green,
                color: color.ink,
                borderRadius: 999,
                padding: '26px 30px 26px 56px',
                fontFamily: font.sans,
                fontSize: 24,
                fontWeight: 800,
                letterSpacing: '0.16em',
              }}
            >
              START LEARNING
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 22,
                  background: color.ink,
                  color: color.green,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 24,
                  letterSpacing: 0,
                }}
              >
                →
              </div>
            </div>
          </div>
          <div
            style={{
              marginTop: 30,
              fontFamily: font.sans,
              fontSize: 24,
              fontWeight: 500,
              letterSpacing: '0.06em',
              color: color.gray,
              opacity: btn,
            }}
          >
            {brand.domain}
          </div>
        </div>
        <div
          style={{
            position: 'absolute',
            right: 150,
            top: 228,
            opacity: phone,
            transform: `translateY(${(1 - phone) * 200 + Math.sin(frame / 30) * 5}px)`,
          }}
        >
          <Phone src="shots/home.jpg" width={330} />
        </div>
        <div
          style={{
            position: 'absolute',
            right: 520,
            top: 410,
            opacity: card,
            transform: `translateX(${(1 - card) * -140}px) translateY(${Math.sin(frame / 34 + 1) * 6}px)`,
          }}
        >
          <Shot src="shots/features.jpg" width={440} />
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
        <div
          style={{
            fontFamily: font.jp,
            fontSize: 34,
            fontWeight: 500,
            letterSpacing: '0.6em',
            paddingLeft: '0.6em',
            color: color.gray,
          }}
        >
          日本語
        </div>
        <div
          style={{
            marginTop: 8,
            fontFamily: font.sans,
            fontSize: 176,
            fontWeight: 800,
            letterSpacing: '-0.02em',
            color: color.white,
            lineHeight: 1.1,
          }}
        >
          RENN X <span style={{ color: color.green }}>JAPAN</span>
        </div>
        <div style={{ width: 160 * mark, height: 6, borderRadius: 3, background: color.green, marginTop: 14 }} />
        <div
          style={{
            marginTop: 36,
            fontFamily: font.sans,
            fontSize: 22,
            fontWeight: 500,
            letterSpacing: '0.3em',
            color: color.gray,
          }}
        >
          {brand.domain.toUpperCase()}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
