import {CSSProperties, FC, ReactNode} from 'react'
import {C} from '../constants'
import {mono} from '../fonts'
import {scramble} from '../ui'

type V = FC<{f: number}>

const Row: FC<{children: ReactNode}> = ({children}) => (
  <div style={{display: 'flex', alignItems: 'center', gap: 28}}>{children}</div>
)

const box = (dark: boolean, size = 56): CSSProperties => ({
  fontFamily: mono,
  fontWeight: 700,
  fontSize: size,
  padding: '18px 30px',
  borderRadius: 20,
  background: dark ? C.ink : C.green,
  color: dark ? C.white : C.ink
})

const Arrow: V = ({f}) => (
  <div style={{fontFamily: mono, fontSize: 56, fontWeight: 700, color: C.green, transform: `translateX(${Math.sin(f / 8) * 8}px)`}}>→</div>
)

const Base64: V = ({f}) => (
  <Row>
    <div style={box(true)}>Halo</div>
    <Arrow f={f} />
    <div style={box(false)}>{scramble('SGFsbw==', f, 54, 40, 'b')}</div>
  </Row>
)

const Hash: V = ({f}) => {
  const h = scramble('a3f1c9e27b04d8e6153fa9c2e07b4d81', f, 54, 60, 'h', '0123456789abcdef')
  return (
    <Row>
      <div style={box(true)}>Halo</div>
      <Arrow f={f} />
      <div style={{...box(false, 34), lineHeight: 1.3, padding: '16px 24px'}}>
        {h.slice(0, 16)}
        <br />
        {h.slice(16)}
      </div>
    </Row>
  )
}

const Random: V = ({f}) => {
  const digits = scramble('482913', f, 44, 60, 'r', '0123456789')
  return (
    <div style={{display: 'flex', gap: 14}}>
      {[...digits].map((d, i) => (
        <div key={i} style={{...box(true, 80), minWidth: 52, textAlign: 'center', padding: '18px 16px', color: f > 108 ? C.green : C.white}}>
          {d}
        </div>
      ))}
    </div>
  )
}

const Placeholder: V = ({f}) => (
  <div
    style={{
      position: 'relative',
      overflow: 'hidden',
      width: 640,
      padding: 28,
      display: 'flex',
      gap: 28,
      alignItems: 'center',
      background: C.white,
      border: `3px solid ${C.line}`,
      borderRadius: 24
    }}
  >
    <div style={{width: 110, height: 110, flex: 'none', borderRadius: '50%', background: C.greenSoft}} />
    <div style={{flex: 1, display: 'flex', flexDirection: 'column', gap: 16}}>
      {['100%', '82%', '56%'].map((w) => (
        <div key={w} style={{width: w, height: 22, borderRadius: 11, background: C.greenSoft}} />
      ))}
    </div>
    <div
      style={{
        position: 'absolute',
        top: 0,
        bottom: 0,
        width: 220,
        left: ((f * 14) % 1000) - 260,
        background: 'linear-gradient(100deg, transparent, rgba(255,255,255,0.95), transparent)'
      }}
    />
  </div>
)

const Tone: V = ({f}) => (
  <div style={{display: 'flex', alignItems: 'center', gap: 8, height: 150}}>
    {Array.from({length: 34}, (_, i) => (
      <div
        key={i}
        style={{
          width: 12,
          height: 24 + 110 * Math.abs(Math.sin(f * 0.11 + i * 0.42)),
          borderRadius: 6,
          background: i % 2 ? C.ink : C.green
        }}
      />
    ))}
  </div>
)

export const VISUALS: Record<string, V> = {base64: Base64, hash: Hash, random: Random, placeholder: Placeholder, tone: Tone}
