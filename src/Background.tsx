import {FC} from 'react'
import {AbsoluteFill, useCurrentFrame} from 'remotion'
import {C, TOTAL} from './constants'
import {beatPulse} from './ui'

export const Background: FC = () => {
  const f = useCurrentFrame()
  const beat = beatPulse(f)
  const drift = f * 0.6
  return (
    <AbsoluteFill>
      <AbsoluteFill
        style={{
          backgroundImage: `linear-gradient(${C.line} 1px, transparent 1px), linear-gradient(90deg, ${C.line} 1px, transparent 1px)`,
          backgroundSize: '96px 96px',
          backgroundPosition: `${drift}px ${drift}px`,
          opacity: 0.7
        }}
      />
      <div
        style={{
          position: 'absolute',
          width: 760,
          height: 760,
          left: 1300 + Math.sin(f / 90) * 80,
          top: -280,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${C.greenSoft}, transparent 70%)`
        }}
      />
      <div
        style={{
          position: 'absolute',
          width: 760,
          height: 760,
          left: -260,
          top: 640 + Math.cos(f / 110) * 60,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${C.greenSoft}, transparent 70%)`
        }}
      />
      <div style={{position: 'absolute', left: 0, top: 0, height: 6, width: `${(f / TOTAL) * 100}%`, background: C.green}} />
      <div
        style={{
          position: 'absolute',
          right: 48,
          bottom: 48,
          width: 18,
          height: 18,
          borderRadius: 6,
          background: C.green,
          opacity: 0.55 + beat * 0.45,
          transform: `scale(${1 + beat * 0.6})`
        }}
      />
    </AbsoluteFill>
  )
}
