import {useCurrentFrame} from 'remotion';
import {useSpring} from '../fx';
import {fonts, palette} from '../theme';

type Props = {
  label: string;
  top: number;
  side: 'left' | 'right';
  offset: number;
  delay: number;
};

export const Chip = ({label, top, side, offset, delay}: Props) => {
  const frame = useCurrentFrame();
  const enter = useSpring(delay, 36);
  const direction = side === 'left' ? -1 : 1;
  const float = Math.sin((frame + delay * 3) / 18) * 7;

  return (
    <span
      style={{
        position: 'absolute',
        top,
        [side]: offset,
        padding: '22px 42px 22px 78px',
        borderRadius: 999,
        whiteSpace: 'nowrap',
        fontFamily: fonts.sans,
        fontWeight: 700,
        fontSize: 30,
        color: palette.ink,
        backgroundColor: 'rgba(255,255,255,0.94)',
        backgroundImage: `radial-gradient(circle at 42px 50%, ${palette.mint} 9px, transparent 10px)`,
        border: `1px solid ${palette.line}`,
        boxShadow: '0 24px 50px -22px rgba(11,14,12,0.35)',
        opacity: enter,
        transform: `translate(${direction * 90 * (1 - enter)}px, ${float}px)`,
      }}
    >
      {label}
    </span>
  );
};
