import {useSpring} from '../fx';
import {fonts, palette} from '../theme';

type ItemProps = {label: string; delay: number};

const Item = ({label, delay}: ItemProps) => {
  const enter = useSpring(delay, 50);
  return (
    <p
      style={{
        margin: 0,
        padding: '22px 28px 22px 72px',
        borderRadius: 22,
        fontFamily: fonts.text,
        fontWeight: 600,
        fontSize: 26,
        color: palette.ink,
        backgroundColor: 'rgba(255,255,255,0.82)',
        backgroundImage: `radial-gradient(circle at 36px 50%, ${palette.mint} 8px, transparent 9px)`,
        border: `1px solid ${palette.line}`,
        opacity: enter,
        transform: `translateX(${(1 - enter) * -60}px)`,
      }}
    >
      {label}
    </p>
  );
};

type Props = {items: string[]; left: number; top: number; width: number; columns: number; delay: number};

export const FeatureList = ({items, left, top, width, columns, delay}: Props) => (
  <div
    style={{
      position: 'absolute',
      left,
      top,
      width,
      display: 'grid',
      gridTemplateColumns: `repeat(${columns}, 1fr)`,
      gap: 14,
    }}
  >
    {items.map((label, i) => (
      <Item key={label} label={label} delay={delay + i * 7} />
    ))}
  </div>
);
