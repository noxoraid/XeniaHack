import {interpolate, useCurrentFrame} from 'remotion';
import {clamp, outQuart, useSpring} from '../fx';
import {fonts, palette} from '../theme';

type Item = {value: number; label: string};

const Card = ({item, delay}: {item: Item; delay: number}) => {
  const frame = useCurrentFrame();
  const enter = useSpring(delay, 50);
  const count = interpolate(frame, [delay + 8, delay + 90], [0, item.value], {...clamp, easing: outQuart});

  return (
    <p
      style={{
        margin: 0,
        padding: '30px 30px 28px',
        borderRadius: 30,
        fontFamily: fonts.text,
        fontWeight: 700,
        fontSize: 20,
        letterSpacing: '0.12em',
        textTransform: 'uppercase',
        color: palette.soft,
        backgroundColor: 'rgba(255,255,255,0.9)',
        border: `1px solid ${palette.line}`,
        boxShadow: '0 34px 60px -34px rgba(15,94,67,0.35)',
        opacity: enter,
        transform: `translateY(${(1 - enter) * 60}px)`,
      }}
    >
      <b
        style={{
          display: 'block',
          marginBottom: 6,
          fontFamily: fonts.display,
          fontWeight: 700,
          fontSize: 92,
          lineHeight: 1,
          letterSpacing: '-0.04em',
          textTransform: 'none',
          color: palette.emerald,
        }}
      >
        {Math.round(count)}
      </b>
      {item.label}
    </p>
  );
};

type Props = {items: Item[]; left: number; top: number; width: number; delay: number};

export const StatRow = ({items, left, top, width, delay}: Props) => (
  <div
    style={{
      position: 'absolute',
      left,
      top,
      width,
      display: 'grid',
      gridTemplateColumns: `repeat(${items.length}, 1fr)`,
      gap: 20,
    }}
  >
    {items.map((item, i) => (
      <Card key={item.label} item={item} delay={delay + i * 8} />
    ))}
  </div>
);
