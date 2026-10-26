import {CSSProperties} from 'react';
import {useSpring} from '../fx';
import {palette} from '../theme';

type Props = {text: string; delay: number; style?: CSSProperties};

const Letter = ({char, delay}: {char: string; delay: number}) => {
  const progress = useSpring(delay, 50);
  return (
    <span
      style={{
        display: 'inline-block',
        whiteSpace: 'pre',
        color: char === '×' ? palette.emerald : undefined,
        opacity: progress,
        transform: `translateY(${(1 - progress) * 150}px)`,
        filter: `blur(${(1 - progress) * 16}px)`,
      }}
    >
      {char}
    </span>
  );
};

export const SplitWord = ({text, delay, style}: Props) => (
  <p style={{margin: 0, display: 'flex', ...style}}>
    {[...text].map((char, i) => (
      <Letter key={i} char={char} delay={delay + i * 4} />
    ))}
  </p>
);
