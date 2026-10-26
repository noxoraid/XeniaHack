import { AbsoluteFill, spring, useCurrentFrame, useVideoConfig, interpolate } from 'remotion';
import { color, font } from '../theme';
import { Browser } from './Browser';

const lines = ['Words.', 'Grammar.', 'Real progress.'];

export const Explore = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const win = spring({ frame: frame - 10, fps, durationInFrames: 50, config: { damping: 200 } });
  const drift = interpolate(frame, [0, 210], [0, -26]);

  return (
    <AbsoluteFill style={{ background: color.ink, overflow: 'hidden' }}>
      <div
        style={{
          position: 'absolute',
          left: -40,
          bottom: -260,
          fontFamily: font.jp,
          fontSize: 900,
          color: '#121213',
          lineHeight: 1,
        }}
      >
        学
      </div>
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 0,
          bottom: 0,
          width: 660,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 10,
        }}
      >
        {lines.map((l, i) => {
          const p = spring({ frame: frame - 12 - i * 20, fps, durationInFrames: 36, config: { damping: 200 } });
          return (
            <div key={l} style={{ overflow: 'hidden', paddingBottom: 10 }}>
              <div
                style={{
                  transform: `translateY(${(1 - p) * 105}%)`,
                  fontFamily: font.serif,
                  fontSize: 104,
                  fontWeight: 500,
                  lineHeight: 1.05,
                  color: i === 2 ? color.red : color.paper,
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
          right: 80,
          top: 200,
          opacity: win,
          transformOrigin: 'right center',
          transform: `translateX(${(1 - win) * 160 + drift}px) perspective(2400px) rotateY(${-10 * (1 - win) - 3}deg)`,
        }}
      >
        <Browser t={frame} />
      </div>
    </AbsoluteFill>
  );
};
