import {interpolate, useCurrentFrame} from 'remotion';
import {Easing} from 'remotion';
import {clamp, useSpring} from '../fx';
import {fonts, palette} from '../theme';

type Item = {value: number; label: string};
type Props = {items: Item[]; top: number; delay: number};

const Stat = ({item, delay}: {item: Item; delay: number}) => {
  const frame = useCurrentFrame();
  const count = interpolate(frame, [delay + 8, delay + 70], [0, item.value], {
    ...clamp,
    easing: Easing.out(Easing.cubic),
  });
  return (
    <p
      style={{
        margin: 0,
        paddingLeft: 26,
        borderLeft: `2px solid ${palette.emerald}`,
        fontFamily: fonts.sans,
        fontWeight: 600,
        fontSize: 24,
        letterSpacing: '0.12em',
        textTransform: 'uppercase',
        color: palette.soft,
      }}
    >
      <b
        style={{
          display: 'block',
          fontFamily: fonts.serif,
          fontWeight: 600,
          fontSize: 112,
          lineHeight: 1,
          letterSpacing: '-0.02em',
          textTransform: 'none',
          color: palette.ink,
        }}
      >
        {Math.round(count)}
      </b>
      {item.label}
    </p>
  );
};

export const Stats = ({items, top, delay}: Props) => {
  const enter = useSpring(delay, 40);
  return (
    <div
      style={{
        position: 'absolute',
        top,
        left: 96,
        right: 96,
        display: 'grid',
        gridTemplateColumns: `repeat(${items.length}, 1fr)`,
        gap: 24,
        opacity: enter,
        transform: `translateY(${(1 - enter) * 50}px)`,
      }}
    >
      {items.map((item, i) => (
        <Stat key={item.label} item={item} delay={delay + i * 6} />
      ))}
    </div>
  );
};
