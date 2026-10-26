import {useCurrentFrame} from 'remotion';
import {Ball} from '../parts/Ball';
import {Heading} from '../parts/Heading';
import {Rise} from '../parts/Rise';
import {Satellites} from '../parts/Satellites';
import {useSpring} from '../fx';
import {fonts, palette} from '../theme';

const lines = [{text: 'Mulai belajar'}, {text: 'hari ini.', accent: true}];

export const Penutup = () => {
  const frame = useCurrentFrame();
  const pop = useSpring(0, 70);
  const breathe = 1 + Math.sin(frame / 22) * 0.02;

  return (
    <>
      <Satellites cx={520} cy={540} radius={300} start={14} disc />
      <Ball
        size={200}
        style={{position: 'absolute', left: 420, top: 440, transform: `scale(${pop * breathe})`}}
      />
      <Heading
        eyebrow="MULAI SEKARANG"
        lines={lines}
        sub="Satu app, semua materi bahasa Jepang."
        left={936}
        top={230}
        width={880}
        size={104}
      />
      <Rise
        delay={60}
        distance={40}
        style={{
          position: 'absolute',
          top: 700,
          left: 936,
          padding: '26px 52px',
          borderRadius: 999,
          fontFamily: fonts.display,
          fontWeight: 700,
          fontSize: 30,
          letterSpacing: '-0.01em',
          color: palette.paper,
          backgroundColor: palette.ink,
          boxShadow: '0 36px 70px -28px rgba(11,14,12,0.6)',
          width: 'fit-content',
        }}
      >
        RENN × JAPAN — Kuis Kosakata
      </Rise>
    </>
  );
};
