import {useCurrentFrame} from 'remotion';
import {useSpring} from '../fx';
import {fonts, palette} from '../theme';

type Props = {
  label: string;
  top: number;
  left: number;
  delay: number;
  from?: 'left' | 'right';
};

export const Chip = ({label, top, left, delay, from = 'right'}: Props) => {
  const frame = useCurrentFrame();
  const enter = useSpring(delay, 50);
  const direction = from === 'left' ? -1 : 1;
  const float = Math.sin((frame + delay * 3) / 34) * 8;

  return (
    <span
      style={{
        position: 'absolute',
        top,
        left,
        padding: '20px 40px 20px 72px',
        borderRadius: 999,
        whiteSpace: 'nowrap',
        fontFamily: fonts.display,
        fontWeight: 600,
        fontSize: 26,
        letterSpacing: '-0.01em',
        color: palette.ink,
        backgroundColor: 'rgba(255,255,255,0.95)',
        backgroundImage: `radial-gradient(circle at 38px 50%, ${palette.mint} 9px, transparent 10px)`,
        border: `1px solid ${palette.line}`,
        boxShadow: '0 28px 56px -24px rgba(11,14,12,0.4)',
        opacity: enter,
        transform: `translate(${direction * 100 * (1 - enter)}px, ${float}px)`,
      }}
    >
      {label}
    </span>
  );
};
