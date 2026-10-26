import {FC} from 'react'
import {AbsoluteFill, spring, useCurrentFrame} from 'remotion'
import {C, FPS, LEAD, W} from '../constants'
import {FEATURES} from '../data'
import {mono} from '../fonts'
import {Orbit} from '../Orbit'
import {Rise, prog} from '../ui'

const R = 70

export const Outro: FC = () => {
  const f = useCurrentFrame()
  const s = spring({frame: f - LEAD, fps: FPS, config: {damping: 10, mass: 0.8}})
  const tag = prog(f, LEAD + 52, LEAD + 80)
  return (
    <AbsoluteFill>
      <Orbit cx={960} cy={290} f={f - LEAD - 6} radius={165} />
      <div
        style={{
          position: 'absolute',
          left: 960 - R,
          top: 290 - R,
          width: R * 2,
          height: R * 2,
          borderRadius: '50%',
          background: `radial-gradient(circle at 32% 28%, #6FE79B, ${C.green} 55%, ${C.greenDark})`,
          transform: `scale(${s})`
        }}
      />
      <div style={{position: 'absolute', top: 470, width: W, textAlign: 'center', fontSize: 200, fontWeight: 700, letterSpacing: 6, lineHeight: 1.05, color: C.ink}}>
        {[...'NOXORA'].map((c, k) => (
          <Rise key={k} at={LEAD + 14 + k * 3}>
            {c}
          </Rise>
        ))}
      </div>
      <div style={{position: 'absolute', top: 712, width: W, textAlign: 'center', fontSize: 56, fontWeight: 700, color: C.ink, opacity: tag, transform: `translateY(${(1 - tag) * 24}px)`}}>
        REST API untuk <span style={{color: C.greenDark}}>project kamu</span>
      </div>
      <div style={{position: 'absolute', top: 836, width: W, display: 'flex', justifyContent: 'center', gap: 20}}>
        {FEATURES.map((ft, i) => {
          const p = prog(f, LEAD + 70 + i * 6, LEAD + 94 + i * 6)
          return (
            <div
              key={ft.id}
              style={{
                fontFamily: mono,
                fontWeight: 700,
                fontSize: 30,
                padding: '14px 28px',
                borderRadius: 999,
                background: C.white,
                border: `3px solid ${C.line}`,
                color: C.ink,
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                opacity: p,
                transform: `translateY(${(1 - p) * 30}px)`
              }}
            >
              <span style={{width: 12, height: 12, borderRadius: '50%', background: C.green}} />
              {ft.name}
            </div>
          )
        })}
      </div>
    </AbsoluteFill>
  )
}
