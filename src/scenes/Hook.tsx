import {FC} from 'react'
import {AbsoluteFill, useCurrentFrame} from 'remotion'
import {C, LEAD, W} from '../constants'
import {mono} from '../fonts'
import {Rise, prog} from '../ui'

const CHIPS = [
  {label: '{ }', x: 140, y: 150},
  {label: 'API', x: 860, y: 120},
  {label: 'GET', x: 1560, y: 170},
  {label: '</>', x: 190, y: 850},
  {label: '200', x: 1590, y: 860}
]

export const Hook: FC = () => {
  const f = useCurrentFrame()
  const mark = prog(f, LEAD + 26, LEAD + 52)
  const pill = prog(f, 130, 160)
  return (
    <AbsoluteFill>
      {[0, 1, 2].map((k) => {
        const p = ((f + k * 40) % 120) / 120
        const s = 300 + 1500 * p
        return (
          <div
            key={k}
            style={{
              position: 'absolute',
              left: 960 - s / 2,
              top: 540 - s / 2,
              width: s,
              height: s,
              borderRadius: '50%',
              border: `2px solid ${C.green}`,
              opacity: (1 - p) * 0.45
            }}
          />
        )
      })}
      {CHIPS.map((c, k) => {
        const p = prog(f, LEAD + 30 + k * 8, LEAD + 52 + k * 8)
        return (
          <div
            key={k}
            style={{
              position: 'absolute',
              left: c.x,
              top: c.y + Math.sin((f + k * 20) / 26) * 14,
              opacity: p,
              transform: `scale(${0.6 + 0.4 * p})`,
              fontFamily: mono,
              fontWeight: 700,
              fontSize: 44,
              color: C.ink,
              background: C.white,
              border: `3px solid ${C.line}`,
              borderRadius: 20,
              padding: '14px 28px',
              boxShadow: '0 12px 30px rgba(11,15,12,0.07)'
            }}
          >
            {c.label}
          </div>
        )
      })}
      <div
        style={{
          position: 'absolute',
          top: 300,
          width: W,
          textAlign: 'center',
          fontSize: 156,
          fontWeight: 700,
          letterSpacing: -4,
          lineHeight: 1.06,
          color: C.ink
        }}
      >
        <div>
          <Rise at={LEAD}>Butuh</Rise>{' '}
          <span
            style={{
              backgroundImage: `linear-gradient(${C.green}, ${C.green})`,
              backgroundRepeat: 'no-repeat',
              backgroundPosition: '0 90%',
              backgroundSize: `${mark * 100}% 0.3em`
            }}
          >
            <Rise at={LEAD + 8}>REST API</Rise>
          </span>
        </div>
        <div>
          <Rise at={LEAD + 18}>untuk</Rise> <Rise at={LEAD + 26}>project</Rise> <Rise at={LEAD + 34}>kamu</Rise>
          <Rise at={LEAD + 44} style={{color: C.green}}>
            ?
          </Rise>
        </div>
      </div>
      <div style={{position: 'absolute', top: 740, width: W, display: 'flex', justifyContent: 'center'}}>
        <div
          style={{
            background: C.ink,
            color: C.white,
            fontSize: 44,
            fontWeight: 700,
            padding: '22px 48px',
            borderRadius: 999,
            opacity: pill,
            transform: `translateY(${(1 - pill) * 40}px)`
          }}
        >
          Kenalan dengan <span style={{color: C.green}}>NOXORA REST API</span>
        </div>
      </div>
    </AbsoluteFill>
  )
}
