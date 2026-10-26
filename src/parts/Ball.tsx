import {CSSProperties} from 'react';

type Props = {size: number; style?: CSSProperties};

export const Ball = ({size, style}: Props) => (
  <div
    style={{
      width: size,
      height: size,
      borderRadius: '50%',
      backgroundImage: 'radial-gradient(circle at 34% 26%, #9CF3CC 0%, #2BB47F 30%, #0F5E43 68%, #06271B 100%)',
      boxShadow:
        'inset -22px -30px 56px rgba(0,0,0,0.3), inset 18px 18px 44px rgba(255,255,255,0.28), 0 50px 90px -30px rgba(15,94,67,0.55)',
      ...style,
    }}
  />
);
