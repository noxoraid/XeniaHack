import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {clamp} from '../fx';
import {fonts, palette} from '../theme';

const label = {
  position: 'absolute',
  top: 64,
  opacity: 1,
  fontFamily: fonts.text,
  fontWeight: 700,
  fontSize: 20,
  letterSpacing: '0.34em',
  color: palette.ink,
} as const;

export const Hud = () => {
  const frame = useCurrentFrame();
  const {durationInFrames, fps} = useVideoConfig();
  const opacity = interpolate(frame, [340, 400], [0, 1], clamp);
  const progress = frame / durationInFrames;
  const seconds = String(Math.min(29, Math.floor(frame / fps))).padStart(2, '0');

  return (
    <>
      <span style={{...label, left: 84, opacity}}>
        RENN <span style={{color: palette.emerald}}>×</span> JAPAN
      </span>
      <span style={{...label, right: 84, opacity, fontVariantNumeric: 'tabular-nums', color: palette.soft}}>
        00:{seconds} / 00:29
      </span>
      <div
        style={{
          position: 'absolute',
          left: 84,
          right: 84,
          bottom: 60,
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
