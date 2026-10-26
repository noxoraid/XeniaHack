import {spring, useCurrentFrame, useVideoConfig} from 'remotion';

export const useSpring = (delay = 0, duration = 40) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  return spring({
    frame: Math.max(0, frame - delay),
    fps,
    durationInFrames: duration,
    config: {damping: 200},
  });
};

export const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
