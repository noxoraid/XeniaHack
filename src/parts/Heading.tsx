import {Rise} from './Rise';
import {fonts, palette} from '../theme';

type Line = {text: string; accent?: boolean};

type Props = {
  eyebrow: string;
  lines: Line[];
  sub: string;
  left: number;
  top: number;
  width: number;
  size: number;
  delay?: number;
  exitAt?: number;
};

export const Heading = ({eyebrow, lines, sub, left, top, width, size, delay = 0, exitAt}: Props) => (
  <>
    <Rise
      delay={delay + 4}
      distance={26}
      exitAt={exitAt}
      style={{
        position: 'absolute',
        top,
        left,
        paddingLeft: 36,
        fontFamily: fonts.text,
        fontWeight: 700,
        fontSize: 22,
        letterSpacing: '0.32em',
        color: palette.emerald,
        backgroundImage: `radial-gradient(circle at 9px 50%, ${palette.mint} 8px, transparent 9px)`,
        backgroundRepeat: 'no-repeat',
      }}
    >
      {eyebrow}
    </Rise>
    <h2
      style={{
        position: 'absolute',
        top: top + 60,
        left,
        width,
        margin: 0,
        fontFamily: fonts.display,
        fontWeight: 700,
        fontSize: size,
        lineHeight: 0.98,
        letterSpacing: '-0.04em',
        color: palette.ink,
      }}
    >
      {lines.map((line, i) => (
        <Rise
          key={line.text}
          delay={delay + 10 + i * 8}
          exitAt={exitAt === undefined ? undefined : exitAt + i * 4}
          style={line.accent ? {color: palette.emerald} : undefined}
        >
          {line.text}
        </Rise>
      ))}
    </h2>
    <Rise
      delay={delay + 12 + lines.length * 8}
      distance={30}
      exitAt={exitAt}
      style={{
        position: 'absolute',
        top: top + 60 + lines.length * size * 0.98 + 36,
        left,
        width: Math.min(width, 760),
        fontFamily: fonts.text,
        fontWeight: 500,
        fontSize: 30,
        lineHeight: 1.5,
        color: palette.soft,
      }}
    >
      {sub}
    </Rise>
  </>
);
