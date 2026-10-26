import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {clamp} from '../fx';
import {fonts, palette} from '../theme';

export const Hud = () => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const opacity = interpolate(frame, [120, 150, 735, 765], [0, 1, 1, 0], clamp);
  const progress = frame / durationInFrames;

  return (
    <>
      <span
        style={{
          position: 'absolute',
          top: 96,
          left: 96,
          opacity,
          fontFamily: fonts.sans,
          fontWeight: 700,
          fontSize: 24,
          letterSpacing: '0.42em',
          color: palette.ink,
        }}
      >
        RENN <span style={{color: palette.emerald}}>×</span> JAPAN
      </span>
      <div
        style={{
          position: 'absolute',
          left: 96,
          right: 96,
          bottom: 92,
          height: 2,
          opacity,
          backgroundColor: palette.line,
          backgroundImage: `linear-gradient(${palette.emerald}, ${palette.emerald})`,
          backgroundRepeat: 'no-repeat',
          backgroundSize: `${progress * 100}% 100%`,
        }}
      />
    </>
  );
};
