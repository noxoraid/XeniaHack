import {Easing, interpolate, useCurrentFrame} from 'remotion';
import {Scene} from '../parts/Scene';
import {Rise} from '../parts/Rise';
import {clamp, useSpring} from '../fx';
import {fonts, palette, timeline} from '../theme';

export const Outro = () => {
  const frame = useCurrentFrame();
  const sun = useSpring(0, 50);
  const rule = interpolate(frame, [40, 85], [0, 420], {...clamp, easing: Easing.out(Easing.cubic)});

  return (
    <Scene duration={timeline.outro.duration}>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
          textAlign: 'center',
        }}
      >
        <div
          style={{
            width: 190,
            height: 190,
            borderRadius: '50%',
            marginBottom: 56,
            backgroundColor: palette.emerald,
            backgroundImage: `radial-gradient(circle at 34% 30%, ${palette.mint}, transparent 62%)`,
            boxShadow: '0 40px 90px -30px rgba(15,94,67,0.55)',
            transform: `scale(${sun + Math.sin(frame / 14) * 0.015})`,
            opacity: sun,
          }}
        />
        <Rise
          delay={14}
          style={{fontFamily: fonts.serif, fontWeight: 600, fontSize: 150, lineHeight: 1, letterSpacing: '-0.02em', color: palette.ink}}
        >
          Mulai belajar
        </Rise>
        <Rise
          delay={24}
          style={{
            fontFamily: fonts.serif,
            fontStyle: 'italic',
            fontWeight: 500,
            fontSize: 150,
            lineHeight: 1,
            color: palette.emerald,
          }}
        >
          hari ini.
        </Rise>
        <div style={{width: rule, height: 2, margin: '48px 0 40px', backgroundColor: palette.emerald}} />
        <Rise
          delay={56}
          distance={26}
          style={{fontFamily: fonts.sans, fontWeight: 700, fontSize: 34, letterSpacing: '0.5em', color: palette.ink}}
        >
          RENN × JAPAN
        </Rise>
        <Rise
          delay={66}
          distance={26}
          style={{fontFamily: fonts.sans, fontWeight: 500, fontSize: 28, letterSpacing: '0.1em', color: palette.soft}}
        >
          Kuis Kosakata Bahasa Jepang
        </Rise>
      </div>
    </Scene>
  );
};
