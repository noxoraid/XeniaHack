import {Rise} from './Rise';
import {fonts, palette} from '../theme';

type Line = {text: string; accent?: boolean};
type Props = {index: string; label: string; lines: Line[]; sub: string};

export const Heading = ({index, label, lines, sub}: Props) => (
  <>
    <Rise
      delay={4}
      distance={30}
      style={{
        position: 'absolute',
        top: 214,
        left: 96,
        fontFamily: fonts.sans,
        fontWeight: 700,
        fontSize: 24,
        letterSpacing: '0.38em',
        color: palette.emerald,
      }}
    >
      {index} — {label}
    </Rise>
    <h2
      style={{
        position: 'absolute',
        top: 276,
        left: 96,
        right: 96,
        margin: 0,
        fontFamily: fonts.serif,
        fontWeight: 600,
        fontSize: 124,
        lineHeight: 1,
        letterSpacing: '-0.02em',
        color: palette.ink,
      }}
    >
      {lines.map((line, i) => (
        <Rise
          key={line.text}
          delay={10 + i * 9}
          style={line.accent ? {color: palette.emerald, fontStyle: 'italic', fontWeight: 500} : undefined}
        >
          {line.text}
        </Rise>
      ))}
    </h2>
    <Rise
      delay={34}
      distance={30}
      style={{
        position: 'absolute',
        top: 612,
        left: 96,
        width: 760,
        fontFamily: fonts.sans,
        fontWeight: 500,
        fontSize: 34,
        lineHeight: 1.45,
        color: palette.soft,
      }}
    >
      {sub}
    </Rise>
  </>
);
