import { interpolate, interpolateColors } from 'remotion';
import { brand, clamp, color, font, smooth } from '../theme';

const kana = ['あ', 'い', 'う', 'え', 'お', 'か', 'き', 'く', 'け', 'こ'];
const nav = ['Lessons', 'Vocabulary', 'Grammar', 'Kana', 'Review'];
const stats = [
  { label: 'Words', to: 248, suffix: '' },
  { label: 'Day streak', to: 14, suffix: '' },
  { label: 'Kana', to: 46, suffix: ' / 46' },
];

export const Browser = ({ t }: { t: number }) => {
  const fill = interpolate(t, [24, 90], [0, 0.68], { ...clamp, easing: smooth });

  return (
    <div
      style={{
        width: 1040,
        height: 680,
        borderRadius: 14,
        background: color.surface,
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 60px 120px -30px rgba(0,0,0,0.55)',
        fontFamily: font.sans,
        color: color.ink,
      }}
    >
      <div
        style={{
          height: 56,
          borderBottom: `1px solid ${color.line}`,
          display: 'flex',
          alignItems: 'center',
          padding: '0 22px',
          gap: 8,
        }}
      >
        {[0, 1, 2].map((d) => (
          <div key={d} style={{ width: 12, height: 12, borderRadius: 6, background: color.line }} />
        ))}
        <div style={{ flex: 1, display: 'flex', justifyContent: 'center' }}>
          <div
            style={{
              background: '#efebe2',
              borderRadius: 20,
              padding: '8px 28px',
              fontSize: 15,
              color: color.mist,
            }}
          >
            {brand.domain}/learn
          </div>
        </div>
        <div style={{ width: 52 }} />
      </div>
      <div style={{ flex: 1, display: 'flex' }}>
        <div
          style={{
            width: 220,
            borderRight: `1px solid ${color.line}`,
            padding: 28,
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
          }}
        >
          <div style={{ fontFamily: font.serif, fontSize: 30, fontWeight: 600, marginBottom: 28 }}>
            {brand.name}
            <span style={{ color: color.red }}>.</span>
          </div>
          {nav.map((n, i) => (
            <div
              key={n}
              style={{
                padding: '12px 14px',
                borderRadius: 8,
                fontSize: 17,
                background: i === 0 ? color.ink : 'transparent',
                color: i === 0 ? color.paper : color.mist,
              }}
            >
              {n}
            </div>
          ))}
        </div>
        <div style={{ flex: 1, padding: '32px 36px', display: 'flex', flexDirection: 'column', gap: 26 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
            <div>
              <div style={{ fontSize: 14, letterSpacing: '0.24em', color: color.mist }}>LESSON 12 OF 40</div>
              <div style={{ fontFamily: font.serif, fontSize: 42, fontWeight: 500 }}>Greetings and Gratitude</div>
            </div>
            <div style={{ fontFamily: font.serif, fontSize: 42, color: color.red }}>{Math.round(fill * 100)}%</div>
          </div>
          <div style={{ height: 8, borderRadius: 4, background: color.line }}>
            <div style={{ height: 8, borderRadius: 4, background: color.red, width: `${fill * 100}%` }} />
          </div>
          <div style={{ flex: 1, display: 'flex', gap: 24 }}>
            <div style={{ flex: 1, display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 10, alignContent: 'start' }}>
              {kana.map((k, i) => {
                const learned = i < 7 ? interpolate(t, [36 + i * 5, 46 + i * 5], [0, 1], clamp) : 0;
                return (
                  <div
                    key={k}
                    style={{
                      height: 76,
                      borderRadius: 10,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontFamily: font.jp,
                      fontSize: 34,
                      border: `1px solid ${i === 7 ? color.red : color.line}`,
                      background: interpolateColors(learned, [0, 1], [color.surface, color.ink]),
                      color: interpolateColors(learned, [0, 1], [color.ink, color.paper]),
                    }}
                  >
                    {k}
                  </div>
                );
              })}
            </div>
            <div
              style={{
                width: 300,
                borderRadius: 12,
                background: color.ink,
                color: color.paper,
                padding: 28,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ fontSize: 13, letterSpacing: '0.26em', color: color.mist }}>NEXT WORD</div>
              <div>
                <div style={{ fontFamily: font.jp, fontSize: 52 }}>ありがとう</div>
                <div style={{ fontFamily: font.serif, fontSize: 30, marginTop: 6 }}>Thank you</div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {Array.from({ length: 8 }).map((_, i) => (
                  <div
                    key={i}
                    style={{ width: 10, height: 10, borderRadius: 5, background: i < 5 ? color.red : '#3a3a3c' }}
                  />
                ))}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 16 }}>
            {stats.map((s) => (
              <div
                key={s.label}
                style={{ flex: 1, border: `1px solid ${color.line}`, borderRadius: 10, padding: '12px 16px' }}
              >
                <div style={{ fontSize: 12, letterSpacing: '0.22em', color: color.mist }}>{s.label.toUpperCase()}</div>
                <div style={{ fontFamily: font.serif, fontSize: 30, fontWeight: 600 }}>
                  {Math.round(interpolate(t, [30, 100], [0, s.to], { ...clamp, easing: smooth }))}
                  {s.suffix}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
