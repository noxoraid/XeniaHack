import {Easing, interpolate, useCurrentFrame} from 'remotion';
import {Scene} from '../parts/Scene';
import {Rise} from '../parts/Rise';
import {SplitWord} from '../parts/SplitWord';
import {clamp, useSpring} from '../fx';
import {fonts, palette, timeline} from '../theme';

const radius = 380;
const circumference = 2 * Math.PI * radius;

const wordStyle = {
  fontFamily: fonts.serif,
  fontWeight: 700,
  fontSize: 200,
  lineHeight: 0.95,
  letterSpacing: '0.04em',
  color: palette.ink,
};

export const Intro = () => {
  const frame = useCurrentFrame();
  const ring = interpolate(frame, [0, 75], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
  const rule = interpolate(frame, [38, 84], [0, 250], {...clamp, easing: Easing.out(Easing.cubic)});
  const sun = useSpring(8, 40);
  const cross = useSpring(46, 30);

  return (
    <Scene duration={timeline.intro.duration}>
      <svg width={1080} height={1920} viewBox="0 0 1080 1920" style={{position: 'absolute', inset: 0}}>
        <circle
          cx={540}
          cy={960}
          r={radius}
          fill="none"
          stroke={palette.mint}
          strokeWidth={2}
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - ring)}
          transform="rotate(-90 540 960)"
        />
        <circle cx={540} cy={960 - radius} r={16 * sun} fill={palette.emerald} />
      </svg>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 18,
        }}
      >
        <Rise
          delay={24}
          distance={24}
          style={{
            fontFamily: fonts.sans,
            fontWeight: 700,
            fontSize: 22,
            letterSpacing: '0.48em',
            color: palette.emerald,
          }}
        >
          KUIS KOSAKATA JEPANG
        </Rise>
        <SplitWord text="RENN" delay={14} style={wordStyle} />
        <div style={{display: 'flex', alignItems: 'center', gap: 28}}>
          <div style={{width: rule, height: 2, backgroundColor: palette.emerald}} />
          <span
            style={{
              fontFamily: fonts.serif,
              fontStyle: 'italic',
              fontWeight: 500,
              fontSize: 96,
              lineHeight: 1,
              color: palette.emerald,
              opacity: cross,
              transform: `scale(${0.4 + 0.6 * cross}) rotate(${(1 - cross) * -90}deg)`,
            }}
          >
            ×
          </span>
          <div style={{width: rule, height: 2, backgroundColor: palette.emerald}} />
        </div>
        <SplitWord text="JAPAN" delay={34} style={wordStyle} />
        <Rise
          delay={88}
          distance={24}
          style={{
            fontFamily: fonts.sans,
            fontWeight: 500,
            fontSize: 30,
            letterSpacing: '0.12em',
            color: palette.soft,
          }}
        >
          Belajar Jepang, lebih elegan.
        </Rise>
      </div>
    </Scene>
  );
};
