import {FC} from 'react'
import {spring} from 'remotion'
import {C, FPS} from './constants'

export const Orbit: FC<{cx: number; cy: number; f: number; radius: number}> = ({cx, cy, f, radius}) => (
  <>
    {Array.from({length: 12}, (_, i) => {
      const s = spring({frame: f - i * 2, fps: FPS, config: {damping: 11, mass: 0.7}})
      const a = ((i * 30 + Math.max(f, 0) * 2.5) * Math.PI) / 180
      const size = (14 + (i % 3) * 7) * Math.min(1, s * 4)
      return (
        <div
          key={i}
          style={{
            position: 'absolute',
            left: cx + Math.cos(a) * radius * s - size / 2,
            top: cy + Math.sin(a) * radius * s - size / 2,
            width: size,
            height: size,
            borderRadius: '50%',
            background: i % 2 ? C.ink : C.green
          }}
        />
      )
    })}
  </>
)
