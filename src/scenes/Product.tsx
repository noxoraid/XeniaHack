import {FC, ReactNode} from 'react'
import {AbsoluteFill, useCurrentFrame} from 'remotion'
import {C, LEAD, W} from '../constants'
import {FEATURES} from '../data'
import {mono} from '../fonts'
import {Rise, prog} from '../ui'

const A = {x: 300, y: 720}
const HUB = {x: 960, y: 720}
const FX = 1580
const FY = [480, 600, 720, 840, 960]

const cubic = (a: number, b: number, c: number, d: number, t: number) => {
  const u = 1 - t
  return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
}

const point = (x0: number, y0: number, x1: number, y1: number, t: number) => {
  const m = (x0 + x1) / 2
  return {x: cubic(x0, m, m, x1, t), y: cubic(y0, y0, y1, y1, t)}
}

const path = (x0: number, y0: number, x1: number, y1: number) => {
  const m = (x0 + x1) / 2
  return `M ${x0} ${y0} C ${m} ${y0}, ${m} ${y1}, ${x1} ${y1}`
}

const Node: FC<{x: number; y: number; w: number; h: number; p: number; green?: boolean; children: ReactNode}> = ({
  x,
  y,
  w,
  h,
  p,
  green,
  children
}) => (
  <div
    style={{
      position: 'absolute',
      left: x - w / 2,
      top: y - h / 2,
      width: w,
      height: h,
      borderRadius: 24,
      background: green ? C.green : C.white,
      border: `3px solid ${green ? C.ink : C.line}`,
      color: C.ink,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      opacity: p,
      transform: `scale(${0.8 + 0.2 * p})`,
      boxShadow: '0 16px 36px rgba(11,15,12,0.08)'
    }}
  >
    {children}
  </div>
)

export const Product: FC = () => {
  const f = useCurrentFrame()
  const title = [...'NOXORA REST API']
  const pop = (at: number) => prog(f, at, at + 22)
  const lineA = prog(f, LEAD + 30, LEAD + 56)
  const pulse = 1 + 0.03 * Math.sin(f / 8)
  const run = Math.max(0, f - 100)
  const stroke = {fill: 'none', stroke: C.ink, strokeWidth: 4, strokeLinecap: 'round' as const, strokeDasharray: '1 1'}

  return (
    <AbsoluteFill>
      <div style={{position: 'absolute', top: 64, width: W, display: 'flex', justifyContent: 'center'}}>
        <span
          style={{
            fontFamily: mono,
            fontWeight: 700,
            fontSize: 30,
            padding: '10px 30px',
            borderRadius: 999,
            background: C.greenSoft,
            color: C.greenDark,
            opacity: pop(LEAD),
            transform: `translateY(${(1 - pop(LEAD)) * 20}px)`
          }}
        >
          Kenalkan
        </span>
      </div>
      <div
        style={{
          position: 'absolute',
          top: 140,
          width: W,
          textAlign: 'center',
          fontSize: 150,
          fontWeight: 700,
          letterSpacing: -3,
          lineHeight: 1.05,
          color: C.ink
        }}
      >
        {title.map((c, k) => (
          <Rise key={k} at={LEAD + 4 + k * 2} style={{color: k >= 7 ? C.green : C.ink}}>
            {c === ' ' ? '\u00A0' : c}
          </Rise>
        ))}
      </div>
      <div style={{position: 'absolute', top: 332, width: W, textAlign: 'center', fontSize: 44, color: C.grey, opacity: pop(LEAD + 40)}}>
        Panggil lewat HTTP, langsung dapat respons.
      </div>

      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        <path d={path(A.x + 120, A.y, HUB.x - 150, HUB.y)} pathLength={1} strokeDashoffset={1 - lineA} {...stroke} />
        {FEATURES.map((_, i) => (
          <path
            key={i}
            d={path(HUB.x + 150, HUB.y, FX - 130, FY[i])}
            pathLength={1}
            strokeDashoffset={1 - prog(f, LEAD + 44 + i * 6, LEAD + 72 + i * 6)}
            {...stroke}
          />
        ))}
        {f > 100 && (
          <>
            {[0, 1].map((k) => {
              const t = (run * 0.011 + k * 0.5) % 1
              const p = point(A.x + 120, A.y, HUB.x - 150, HUB.y, k ? 1 - t : t)
              return <circle key={k} cx={p.x} cy={p.y} r={10} fill={k ? C.ink : C.green} />
            })}
            {FEATURES.map((_, i) => {
              const t = (run * 0.011 + i * 0.19) % 1
              const out = point(HUB.x + 150, HUB.y, FX - 130, FY[i], t)
              const back = point(HUB.x + 150, HUB.y, FX - 130, FY[i], 1 - ((t + 0.5) % 1))
              return (
                <g key={i}>
                  <circle cx={out.x} cy={out.y} r={9} fill={C.green} />
                  <circle cx={back.x} cy={back.y} r={9} fill={C.ink} />
                </g>
              )
            })}
          </>
        )}
      </svg>

      <Node x={A.x} y={A.y} w={240} h={120} p={pop(LEAD + 12)}>
        <span style={{fontFamily: mono, fontWeight: 700, fontSize: 30, color: C.green}}>{'</>'}</span>
        <span style={{fontWeight: 700, fontSize: 34}}>Project kamu</span>
      </Node>
      <div style={{position: 'absolute', left: HUB.x - 150, top: HUB.y - 80, width: 300, height: 160, transform: `scale(${pulse})`}}>
        <Node x={150} y={80} w={300} h={160} p={pop(LEAD + 20)} green>
          <span style={{fontWeight: 700, fontSize: 48}}>NOXORA</span>
          <span style={{fontFamily: mono, fontWeight: 700, fontSize: 26}}>REST API</span>
        </Node>
      </div>
      {FEATURES.map((ft, i) => (
        <Node key={ft.id} x={FX} y={FY[i]} w={260} h={84} p={pop(LEAD + 40 + i * 6)}>
          <span style={{fontFamily: mono, fontWeight: 700, fontSize: 34}}>{ft.name}</span>
        </Node>
      ))}
    </AbsoluteFill>
  )
}
