import {CSSProperties, ReactNode} from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {clamp, inQuad, useSpring} from '../fx';

type Props = {
  delay?: number;
  distance?: number;
  blur?: number;
  exitAt?: number;
  style?: CSSProperties;
  children: ReactNode;
};

export const Rise = ({delay = 0, distance = 70, blur = 14, exitAt, style, children}: Props) => {
  const frame = useCurrentFrame();
  const progress = useSpring(delay, 54);
  const leave =
    exitAt === undefined ? 0 : interpolate(frame, [exitAt, exitAt + 24], [0, 1], {...clamp, easing: inQuad});

  return (
    <span
      style={{
        display: 'block',
        opacity: progress * (1 - leave),
        transform: `translateY(${(1 - progress) * distance - leave * 60}px)`,
        filter: `blur(${(1 - progress) * blur + leave * 14}px)`,
        ...style,
      }}
    >
      {children}
    </span>
  );
};
