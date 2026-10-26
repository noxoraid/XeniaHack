import {CSSProperties, ReactNode} from 'react';
import {useSpring} from '../fx';

type Props = {
  delay?: number;
  distance?: number;
  blur?: number;
  style?: CSSProperties;
  children: ReactNode;
};

export const Rise = ({delay = 0, distance = 70, blur = 12, style, children}: Props) => {
  const progress = useSpring(delay, 36);
  return (
    <span
      style={{
        display: 'block',
        opacity: progress,
        transform: `translateY(${(1 - progress) * distance}px)`,
        filter: `blur(${(1 - progress) * blur}px)`,
        ...style,
      }}
    >
      {children}
    </span>
  );
};
