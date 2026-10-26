import {FC} from 'react'
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion'
import {C, FEATURE_LEN, H, T, W} from './constants'
import {clamp} from './ui'

const EDGES = [T.hook, T.product, T.features, ...[1, 2, 3, 4].map((i) => T.features + i * FEATURE_LEN), T.outro]
const LAYERS = [
  {color: C.ink, lag: 5},
  {color: C.green, lag: 0}
]

export const Wipes: FC = () => {
  const f = useCurrentFrame()
  return (
    <AbsoluteFill style={{pointerEvents: 'none'}}>
      {EDGES.flatMap((b, k) =>
        LAYERS.map(({color, lag}) => {
          const horizontal = k % 2 === 0
          const size = horizontal ? W : H
          const v = interpolate(f - lag, [b - 12, b, b + 12], [-size, 0, size], {
            ...clamp,
            easing: Easing.inOut(Easing.cubic)
          })
          if (Math.abs(v) >= size) return null
          return (
            <div
              key={`${b}-${lag}`}
              style={{
                position: 'absolute',
                inset: 0,
                background: color,
                transform: horizontal ? `translateX(${v}px)` : `translateY(${v}px)`
              }}
            />
          )
        })
      )}
    </AbsoluteFill>
  )
}
