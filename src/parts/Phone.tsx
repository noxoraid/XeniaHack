import {interpolate, staticFile, useCurrentFrame} from 'remotion';
import {clamp, useSpring} from '../fx';
import {palette} from '../theme';

const view = {x: 8, y: 170, w: 704, h: 1360};
const source = {w: 720, h: 1612};
const bezel = 12;

type Props = {
  src: string;
  left: number;
  top: number;
  width: number;
  duration: number;
  delay?: number;
  zoom?: [number, number];
  focusY?: [number, number];
  tilt?: number;
};

export const Phone = ({src, left, top, width, duration, delay = 16, zoom = [1.3, 1.34], focusY = [700, 1000], tilt = -14}: Props) => {
  const frame = useCurrentFrame();
  const enter = useSpring(delay, 80);
  const t = interpolate(frame, [0, duration], [0, 1], clamp);
  const innerWidth = width - bezel * 2;
  const innerHeight = (innerWidth * view.h) / view.w;
  const scale = (innerWidth / view.w) * (zoom[0] + (zoom[1] - zoom[0]) * t);
  const centerY = focusY[0] + (focusY[1] - focusY[0]) * t;
  const float = Math.sin(frame / 48) * 9;

  return (
    <div
      style={{
        position: 'absolute',
        left,
        top,
        width,
        height: innerHeight + bezel * 2,
        boxSizing: 'border-box',
        border: `${bezel}px solid ${palette.ink}`,
        borderRadius: 64,
        backgroundColor: palette.paper,
        backgroundImage: `linear-gradient(115deg, rgba(255,255,255,0.24), transparent 32%), url(${staticFile(src)})`,
        backgroundRepeat: 'no-repeat',
        backgroundSize: `100% 100%, ${source.w * scale}px ${source.h * scale}px`,
        backgroundPosition: `0 0, ${innerWidth / 2 - 360 * scale}px ${innerHeight / 2 - centerY * scale}px`,
        boxShadow: `0 70px 120px -34px rgba(11,14,12,0.45), 0 0 0 2px #1B211E`,
        opacity: enter,
        transform: `perspective(2400px) rotateY(${tilt * (1 - enter)}deg) translateY(${(1 - enter) * 170 + float * enter}px)`,
      }}
    />
  );
};
