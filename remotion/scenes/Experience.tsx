import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { clamp, color, font, smooth } from '../theme';

const glyphs = ['日', '本', '語', '学'];
const lines = ['Every lesson', 'brings you', 'closer.'];
const radius = 280;
const circumference = 2 * Math.PI * radius;

export const Experience = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const prog = interpolate(frame, [16, 160], [0, 1], { ...clamp, easing: smooth });
  const lesson = Math.max(1, Math.round(prog * 12));

  return (
    <AbsoluteFill style={{ background: color.paper }}>
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
        {lines.map((l, i) => {
          const p = spring({ frame: frame - 6 - i * 14, fps, durationInFrames: 36, config: { damping: 200 } });
          return (
            <div key={l} style={{ overflow: 'hidden', paddingBottom: 8 }}>
              <div
                style={{
                  transform: `translateY(${(1 - p) * 105}%)`,
                  fontFamily: font.serif,
                  fontSize: 108,
                  fontWeight: 500,
                  lineHeight: 1.04,
                  color: i === 2 ? color.red : color.ink,
                }}
              >
                {l}
              </div>
            </div>
          );
        })}
        <div style={{ marginTop: 52, width: 560 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              fontFamily: font.sans,
              fontSize: 18,
              letterSpacing: '0.28em',
              color: color.mist,
              marginBottom: 14,
            }}
          >
            <span>LESSON {String(lesson).padStart(2, '0')}</span>
            <span>12</span>
          </div>
          <div style={{ height: 3, background: color.line }}>
            <div style={{ height: 3, background: color.ink, width: `${prog * 100}%` }} />
          </div>
        </div>
      </div>
      <div style={{ position: 'absolute', right: 180, top: 220, width: 640, height: 640 }}>
        <svg width={640} height={640} viewBox="0 0 640 640" style={{ position: 'absolute', inset: 0 }}>
          <circle cx={320} cy={320} r={radius} fill="none" stroke={color.line} strokeWidth={3} />
          <circle
            cx={320}
            cy={320}
            r={radius}
            fill="none"
            stroke={color.red}
            strokeWidth={6}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={circumference * (1 - prog)}
            transform="rotate(-90 320 320)"
          />
        </svg>
        {glyphs.map((g, i) => {
          const start = 14 + i * 40;
          const last = i === glyphs.length - 1;
          const o = interpolate(
            frame,
            [start, start + 10, start + 34, start + 44],
            [0, 1, 1, last ? 1 : 0],
            clamp,
          );
          return (
            <div
              key={g}
              style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontFamily: font.jp,
                fontSize: 340,
                fontWeight: 500,
                color: color.ink,
                opacity: o,
                transform: `scale(${interpolate(o, [0, 1], [0.94, 1])})`,
                filter: `blur(${(1 - o) * 10}px)`,
              }}
            >
              {g}
            </div>
          );
        })}
        <div
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 96,
            textAlign: 'center',
            fontFamily: font.sans,
            fontSize: 26,
            letterSpacing: '0.3em',
            color: color.mist,
          }}
        >
          {Math.round(prog * 100)}%
        </div>
      </div>
    </AbsoluteFill>
  );
};
