import {interpolate, staticFile, useCurrentFrame} from 'remotion';
import {clamp, useSpring} from '../fx';
import {palette} from '../theme';

type Box = {x: number; y: number; w: number; h: number};

type Props = {
  src: string;
  box: Box;
  top: number;
  duration: number;
  width?: number;
  left?: number;
  pan?: number;
  zoom?: [number, number];
  delay?: number;
};

const source = {w: 720, h: 1612};

export const Crop = ({src, box, top, duration, width = 888, left = 96, pan = 0, zoom = [1, 1.07], delay = 14}: Props) => {
  const frame = useCurrentFrame();
  const enter = useSpring(delay, 50);
  const t = interpolate(frame, [0, duration], [0, 1], clamp);
  const height = (width * box.h) / box.w;
  const scale = (width / box.w) * (zoom[0] + (zoom[1] - zoom[0]) * t);
  const centerX = box.x + box.w / 2;
  const centerY = box.y + box.h / 2 + pan * t;

  return (
    <div
      style={{
        position: 'absolute',
        top,
        left,
        width,
        height,
        borderRadius: 44,
        backgroundImage: `url(${staticFile(src)})`,
        backgroundRepeat: 'no-repeat',
        backgroundSize: `${source.w * scale}px ${source.h * scale}px`,
        backgroundPosition: `${width / 2 - centerX * scale}px ${height / 2 - centerY * scale}px`,
        boxShadow: `0 50px 100px -36px rgba(15,94,67,0.4), 0 0 0 1px ${palette.line}`,
        opacity: enter,
        transform: `translateY(${(1 - enter) * 110}px) scale(${0.95 + 0.05 * enter})`,
      }}
    />
  );
};
