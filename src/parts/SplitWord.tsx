import {CSSProperties} from 'react';
import {useSpring} from '../fx';

type Props = {text: string; delay: number; style?: CSSProperties};

const Letter = ({char, delay}: {char: string; delay: number}) => {
  const progress = useSpring(delay, 34);
  return (
    <span
      style={{
        display: 'inline-block',
        opacity: progress,
        transform: `translateY(${(1 - progress) * 130}px)`,
        filter: `blur(${(1 - progress) * 14}px)`,
      }}
    >
      {char}
    </span>
  );
};

export const SplitWord = ({text, delay, style}: Props) => (
  <p style={{margin: 0, display: 'flex', ...style}}>
    {[...text].map((char, i) => (
      <Letter key={i} char={char} delay={delay + i * 5} />
    ))}
  </p>
);
