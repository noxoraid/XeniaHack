import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { beat, color, font } from '../theme';
import { Glow, Phone, Shot } from '../ui';

const lines = ['Words.', 'Grammar.', 'Real progress.'];

export const Explore = ({ offset }: { offset: number }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pulse = beat(frame + offset);
  const enter = (delay: number) => spring({ frame: frame - delay, fps, durationInFrames: 46, config: { damping: 200 } });
  const phone = enter(8);
  const hero = enter(26);
  const stats = enter(44);
  const drift = interpolate(frame, [0, 210], [0, -24]);

  return (
    <AbsoluteFill style={{ background: color.ink, overflow: 'hidden' }}>
      <Glow x={1450} y={540} size={1200} alpha={0.32} pulse={pulse} />
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 0,
          bottom: 0,
          width: 720,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 6,
        }}
      >
        {lines.map((l, i) => {
          const p = spring({ frame: frame - 10 - i * 18, fps, durationInFrames: 36, config: { damping: 200 } });
          return (
            <div key={l} style={{ overflow: 'hidden', paddingBottom: 10 }}>
              <div
                style={{
                  transform: `translateY(${(1 - p) * 105}%)`,
                  fontFamily: font.sans,
                  fontSize: 84,
                  fontWeight: 800,
                  letterSpacing: '-0.02em',
                  lineHeight: 1.08,
                  color: i === 2 ? color.green : color.white,
                }}
              >
                {l}
              </div>
            </div>
          );
        })}
      </div>
      <div
        style={{
          position: 'absolute',
          right: 170,
          top: 146,
          opacity: phone,
          transform: `translateY(${(1 - phone) * 220 + drift + Math.sin(frame / 30) * 5}px) scale(${1 + pulse * 0.01})`,
        }}
      >
        <Phone src="shots/home.jpg" width={420} />
      </div>
      <div
        style={{
          position: 'absolute',
          left: 900,
          top: 96,
          opacity: hero,
          transform: `translateX(${(1 - hero) * -160}px) translateY(${Math.sin(frame / 34) * 6}px)`,
        }}
      >
        <Shot src="shots/hero.jpg" width={520} />
      </div>
      <div
        style={{
          position: 'absolute',
          left: 860,
          top: 756,
          opacity: stats,
          transform: `translateX(${(1 - stats) * -160}px) translateY(${Math.sin(frame / 30 + 2) * 6}px)`,
        }}
      >
        <Shot src="shots/stats.jpg" width={540} />
      </div>
    </AbsoluteFill>
  );
};
