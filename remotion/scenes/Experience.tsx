import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { beat, clamp, color, font, smooth } from '../theme';
import { Glow, Shot } from '../ui';

const glyphs = ['日', '本', '語', '学'];
const lines = ['Every lesson', 'brings you', 'closer.'];
const viewport = 760;
const shotHeight = (540 * 1090) / 517;

export const Experience = ({ offset }: { offset: number }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pulse = beat(frame + offset);
  const prog = interpolate(frame, [16, 160], [0, 1], { ...clamp, easing: smooth });
  const pan = interpolate(frame, [14, 168], [0, shotHeight - viewport], { ...clamp, easing: smooth });
  const lesson = Math.max(1, Math.round(prog * 12));
  const win = spring({ frame: frame - 6, fps, durationInFrames: 46, config: { damping: 200 } });
  const chip = spring({ frame: frame - 30, fps, config: { damping: 16, stiffness: 120 } });

  return (
    <AbsoluteFill style={{ background: color.light, overflow: 'hidden' }}>
      <Glow x={1420} y={540} size={1100} alpha={0.26} pulse={pulse} />
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
                  fontFamily: font.sans,
                  fontSize: 96,
                  fontWeight: 800,
                  letterSpacing: '-0.025em',
                  lineHeight: 1.06,
                  color: i === 2 ? color.greenDeep : color.text,
                }}
              >
                {l}
              </div>
            </div>
          );
        })}
        <div style={{ marginTop: 56, width: 560 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              fontFamily: font.sans,
              fontSize: 20,
              fontWeight: 700,
              letterSpacing: '0.2em',
              color: color.gray,
              marginBottom: 16,
            }}
          >
            <span>LESSON {String(lesson).padStart(2, '0')}</span>
            <span>{Math.round(prog * 100)}%</span>
          </div>
          <div style={{ height: 10, borderRadius: 5, background: color.line }}>
            <div style={{ height: 10, borderRadius: 5, background: color.green, width: `${prog * 100}%` }} />
          </div>
        </div>
      </div>
      {glyphs.map((g, i) => {
        const start = 14 + i * 40;
        const last = i === glyphs.length - 1;
        const o = interpolate(frame, [start, start + 10, start + 34, start + 44], [0, 1, 1, last ? 1 : 0], clamp);
        return (
          <div
            key={g}
            style={{
              position: 'absolute',
              right: 90,
              top: 130,
              width: 760,
              height: 820,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontFamily: font.jp,
              fontSize: 720,
              fontWeight: 700,
              color: '#d3ecdf',
              opacity: o,
              filter: `blur(${(1 - o) * 12}px)`,
            }}
          >
            {g}
          </div>
        );
      })}
      <div
        style={{
          position: 'absolute',
          right: 200,
          top: 160,
          width: 540,
          height: viewport,
          borderRadius: 40,
          overflow: 'hidden',
          opacity: win,
          transform: `translateY(${(1 - win) * 120}px)`,
          boxShadow: '0 60px 110px -40px rgba(7,20,13,0.45)',
        }}
      >
        <div style={{ transform: `translateY(${-pan}px)` }}>
          <Shot src="shots/kana.jpg" width={540} radius={0} style={{ boxShadow: 'none', border: 'none' }} />
        </div>
      </div>
      <div
        style={{
          position: 'absolute',
          right: 640,
          top: 210,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '16px 28px',
          borderRadius: 999,
          background: color.ink,
          color: color.white,
          fontFamily: font.sans,
          fontSize: 22,
          fontWeight: 700,
          opacity: chip,
          transform: `scale(${chip})`,
        }}
      >
        <div style={{ width: 12, height: 12, borderRadius: 6, background: color.green }} />
        Hiragana · 46
      </div>
    </AbsoluteFill>
  );
};
