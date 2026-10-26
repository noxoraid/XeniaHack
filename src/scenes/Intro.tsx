import {FC} from 'react'
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion'
import {C, FPS, sec} from '../constants'
import {mono} from '../fonts'
import {Orbit} from '../Orbit'
import {clamp, prog} from '../ui'

const GROUND = 820
const R = 70
const RISE = 520
const IMPACTS: [number, number][] = [
  [1.7, 1],
  [2.2, 0.5],
  [2.55, 0.25]
]

const height = (t: number) => {
  if (t < 1) return RISE * Easing.out(Easing.cubic)(t)
  if (t < 1.7) return RISE * (1 - Easing.in(Easing.quad)((t - 1) / 0.7))
  if (t < 2.2) {
    const u = (t - 1.7) / 0.5
    return 150 * 4 * u * (1 - u)
  }
  if (t < 2.55) {
    const u = (t - 2.2) / 0.35
    return 45 * 4 * u * (1 - u)
  }
  return 0
}

export const Intro: FC = () => {
  const f = useCurrentFrame()
  const t = f / FPS
  const h = height(t)
  const squash = Math.max(...IMPACTS.map(([a, m]) => interpolate(t, [a - 0.04, a + 0.02, a + 0.2], [0, m, 0], clamp)))
  const cy = GROUND - R - h
  const born = f - sec(1.7)
  const ripple = prog(born, 0, 44)
  const shadow = 1 - (0.55 * h) / RISE
  const enter = prog(f, 0, 8)

  return (
    <AbsoluteFill>
      <div
        style={{
          position: 'absolute',
          left: 960 - 80 * shadow,
          top: GROUND + 2,
          width: 160 * shadow,
          height: 22 * shadow,
          borderRadius: '50%',
          background: C.ink,
          opacity: 0.16 * enter
        }}
      />
      {born >= 0 && (
        <div
          style={{
            position: 'absolute',
            left: 960 - (70 + 520 * ripple),
            top: GROUND + 12 - (14 + 130 * ripple),
            width: 140 + 1040 * ripple,
            height: 28 + 260 * ripple,
            borderRadius: '50%',
            border: `5px solid ${C.green}`,
            opacity: 1 - ripple
          }}
        />
      )}
      <Orbit cx={960} cy={GROUND - R} f={born} radius={230} />
      <div
        style={{
          position: 'absolute',
          left: 960 - R,
          top: cy - R,
          width: R * 2,
          height: R * 2,
          borderRadius: '50%',
          background: `radial-gradient(circle at 32% 28%, #6FE79B, ${C.green} 55%, ${C.greenDark})`,
          opacity: enter,
          transformOrigin: '50% 100%',
          transform: `scale(${1 + 0.22 * squash}, ${1 - 0.22 * squash})`
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: 936,
          width: '100%',
          textAlign: 'center',
          fontFamily: mono,
          fontWeight: 700,
          fontSize: 32,
          letterSpacing: 12,
          color: C.ink,
          opacity: prog(f, sec(2.5), sec(2.9))
        }}
      >
        NOXORA
      </div>
    </AbsoluteFill>
  )
}
