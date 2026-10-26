import { Img, staticFile } from 'remotion';
import type { CSSProperties } from 'react';

export const Glow = ({ x, y, size, alpha, pulse = 0 }: { x: number; y: number; size: number; alpha: number; pulse?: number }) => (
  <div
    style={{
      position: 'absolute',
      left: x - size / 2,
      top: y - size / 2,
      width: size,
      height: size,
      borderRadius: '50%',
      background: `radial-gradient(circle, rgba(34,217,138,${alpha}) 0%, rgba(34,217,138,0) 68%)`,
      transform: `scale(${1 + pulse * 0.05})`,
    }}
  />
);

export const Phone = ({ src, width, style }: { src: string; width: number; style?: CSSProperties }) => (
  <div
    style={{
      padding: 12,
      borderRadius: 56,
      background: '#0b1511',
      border: '1px solid rgba(255,255,255,0.14)',
      boxShadow: '0 60px 120px -30px rgba(0,0,0,0.75)',
      ...style,
    }}
  >
    <div style={{ width, borderRadius: 44, overflow: 'hidden', lineHeight: 0 }}>
      <Img src={staticFile(src)} style={{ width: '100%', display: 'block' }} />
    </div>
  </div>
);

export const Shot = ({ src, width, radius = 28, style }: { src: string; width: number; radius?: number; style?: CSSProperties }) => (
  <div
    style={{
      width,
      borderRadius: radius,
      overflow: 'hidden',
      lineHeight: 0,
      background: '#fff',
      boxShadow: '0 40px 90px -30px rgba(0,0,0,0.6)',
      border: '1px solid rgba(255,255,255,0.1)',
      ...style,
    }}
  >
    <Img src={staticFile(src)} style={{ width: '100%', display: 'block' }} />
  </div>
);
