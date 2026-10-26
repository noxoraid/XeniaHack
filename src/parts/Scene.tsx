import {ReactNode} from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {clamp} from '../fx';

type Props = {duration: number; children: ReactNode};

export const Scene = ({duration, children}: Props) => {
  const frame = useCurrentFrame();
  const opacity = interpolate(frame, [0, 14, duration - 14, duration], [0, 1, 1, 0], clamp);
  return <AbsoluteFill style={{opacity}}>{children}</AbsoluteFill>;
};
