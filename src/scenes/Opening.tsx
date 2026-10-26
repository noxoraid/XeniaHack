import {interpolate, useCurrentFrame} from 'remotion';
import {Ball} from '../parts/Ball';
import {Satellites} from '../parts/Satellites';
import {Heading} from '../parts/Heading';
import {Rise} from '../parts/Rise';
import {SplitWord} from '../parts/SplitWord';
import {clamp, inOutCubic, inQuad, outQuart} from '../fx';
import {fonts, palette} from '../theme';

const size = 220;
const startY = 1320;
const apex = 170;
const floorY = 600;
const riseEnd = 150;
const impact = 189;
const slideStart = 270;
const brandStart = 453;

const hookLines = [{text: 'Lupa terus'}, {text: 'kosakata'}, {text: 'Jepang?', accent: true}];

const titleStyle = {
  fontFamily: fonts.display,
  fontWeight: 800,
  fontSize: 180,
  lineHeight: 0.95,
  letterSpacing: '-0.05em',
  color: palette.ink,
  position: 'absolute',
  left: 936,
} as const;

export const Opening = () => {
  const frame = useCurrentFrame();
  const dt = Math.max(0, frame - impact);
  const landed = frame >= impact;
  const lift = landed ? 120 * Math.exp((-4.8 * dt) / 60) * Math.abs(Math.sin((2 * Math.PI * 1.7 * dt) / 60)) : 0;
  const y =
    frame < riseEnd
      ? interpolate(frame, [0, riseEnd], [startY, apex], {...clamp, easing: outQuart})
      : frame < impact
        ? interpolate(frame, [riseEnd, impact], [apex, floorY], {...clamp, easing: inQuad})
        : floorY - lift;
  const stretch = frame < impact ? interpolate(frame, [riseEnd, impact], [0, 0.14], clamp) : 0;
  const squash = landed ? Math.exp(-dt / 5) : 0;
  const scaleY = 1 + stretch - 0.34 * squash;
  const scaleX = 1 - stretch * 0.5 + 0.26 * squash;
  const shake = landed ? Math.exp(-dt / 9) : 0;
  const shakeX = Math.sin(dt * 1.9) * 16 * shake;
  const shakeY = Math.cos(dt * 2.3) * 12 * shake;

  const shadowOpacity = interpolate(y, [floorY, floorY + 320], [1, 0], clamp);
  const shadowScale = interpolate(y, [apex, floorY], [0.5, 1], clamp);

  const waves = [0, 10].map((lag) => {
    const t = interpolate(frame, [impact + lag, impact + lag + 54], [0, 1], {...clamp, easing: outQuart});
    return {rx: 150 + 1000 * t, opacity: frame >= impact + lag ? (1 - t) * 0.8 : 0};
  });

  const slide = interpolate(frame, [slideStart, slideStart + 66], [0, 1], {...clamp, easing: inOutCubic});

  return (
    <>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          transformOrigin: '960px 600px',
          transform: `translate(${-440 * slide + shakeX}px, ${-60 * slide + shakeY}px) scale(${1 - 0.22 * slide})`,
        }}
      >
        <svg width={1920} height={1080} viewBox="0 0 1920 1080" style={{position: 'absolute', left: 0, top: 0}}>
          <ellipse
            cx={960}
            cy={722}
            rx={150 * shadowScale * (1 + 0.2 * squash)}
            ry={24 * shadowScale}
            fill={`rgba(11,14,12,${0.22 * shadowOpacity})`}
            style={{filter: 'blur(9px)'}}
          />
          {waves.map((wave, i) => (
            <ellipse
              key={i}
              cx={960}
              cy={722}
              rx={wave.rx}
              ry={wave.rx * 0.15}
              fill="none"
              stroke={palette.mint}
              strokeWidth={3}
              opacity={wave.opacity}
            />
          ))}
        </svg>
        <Ball
          size={size}
          style={{
            position: 'absolute',
            left: 960 - size / 2,
            top: y - size / 2,
            transformOrigin: '50% 100%',
            transform: `scale(${scaleX}, ${scaleY})`,
          }}
        />
        <Satellites cx={960} cy={600} radius={340} start={impact + 8} />
      </div>
      <Heading
        eyebrow="PERNAH BEGINI?"
        lines={hookLines}
        sub="Saatnya belajar dengan cara yang lebih ringan."
        left={936}
        top={230}
        width={880}
        size={118}
        delay={slideStart + 10}
        exitAt={436}
      />
      <Rise
        delay={brandStart + 4}
        distance={26}
        style={{
          position: 'absolute',
          top: 262,
          left: 936,
          fontFamily: fonts.text,
          fontWeight: 700,
          fontSize: 22,
          letterSpacing: '0.34em',
          color: palette.emerald,
        }}
      >
        KUIS KOSAKATA BAHASA JEPANG
      </Rise>
      <SplitWord text="RENN ×" delay={brandStart + 8} style={{...titleStyle, top: 318}} />
      <SplitWord text="JAPAN" delay={brandStart + 24} style={{...titleStyle, top: 496}} />
      <Rise
        delay={brandStart + 52}
        distance={30}
        style={{
          position: 'absolute',
          top: 716,
          left: 936,
          width: 760,
          fontFamily: fonts.text,
          fontWeight: 500,
          fontSize: 30,
          lineHeight: 1.5,
          color: palette.soft,
        }}
      >
        Kuis kosakata yang mudah dan menyenangkan, dari huruf dasar sampai kanji.
      </Rise>
    </>
  );
};
