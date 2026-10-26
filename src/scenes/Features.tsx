import {FC} from 'react'
import {AbsoluteFill, Sequence, useCurrentFrame} from 'remotion'
import {C, FEATURE_LEN, LEAD, W} from '../constants'
import {FEATURES} from '../data'
import {mono} from '../fonts'
import {Rise, Terminal, prog} from '../ui'
import {VISUALS} from './visuals'

const Feature: FC<{i: number}> = ({i}) => {
  const f = useCurrentFrame()
  const ft = FEATURES[i]
  const flip = i % 2 === 1
  const Vis = VISUALS[ft.id]
  const slide = (1 - prog(f, LEAD, LEAD + 26)) * (flip ? -240 : 240)
  const size = Math.min(200, 740 / (ft.name.length * 0.6))
  const vis = prog(f, LEAD + 40, LEAD + 62)

  return (
    <AbsoluteFill
      style={{flexDirection: flip ? 'row-reverse' : 'row', alignItems: 'center', justifyContent: 'center', gap: 80, padding: '0 100px 60px'}}
    >
      <div style={{width: 760}}>
        <span
          style={{
            display: 'inline-block',
            fontFamily: mono,
            fontWeight: 700,
            fontSize: 28,
            padding: '8px 24px',
            borderRadius: 999,
            background: C.greenSoft,
            color: C.greenDark,
            opacity: prog(f, LEAD, LEAD + 14)
          }}
        >
          Fitur {i + 1}
        </span>
        <div style={{fontSize: size, fontWeight: 700, letterSpacing: -3, lineHeight: 1.05, color: C.ink, margin: '14px 0 6px'}}>
          {[...ft.name].map((c, k) => (
            <Rise key={k} at={LEAD + 6 + k * 2}>
              {c}
            </Rise>
          ))}
        </div>
        <div style={{fontSize: 46, color: C.grey, opacity: prog(f, LEAD + 24, LEAD + 44), transform: `translateY(${(1 - prog(f, LEAD + 24, LEAD + 44)) * 20}px)`}}>
          {ft.note}
        </div>
        <div style={{marginTop: 48, opacity: vis, transform: `translateY(${(1 - vis) * 30}px)`}}>
          <Vis f={f} />
        </div>
      </div>
      <div style={{transform: `translateX(${slide}px)`, opacity: prog(f, LEAD, LEAD + 14)}}>
        <Terminal method={ft.method} path={ft.path} res={ft.res} start={LEAD + 10} />
      </div>
    </AbsoluteFill>
  )
}

const Rail: FC<{f: number}> = ({f}) => (
  <div style={{position: 'absolute', left: 0, bottom: 52, width: W, display: 'flex', justifyContent: 'center', gap: 40}}>
    {FEATURES.map((ft, i) => {
      const p = Math.min(1, Math.max(0, (f - i * FEATURE_LEN) / FEATURE_LEN))
      return (
        <div key={ft.id} style={{width: 200, fontFamily: mono, fontWeight: 700, fontSize: 26, color: p > 0 ? C.ink : C.term}}>
          {ft.name}
          <div style={{marginTop: 10, height: 6, borderRadius: 3, background: C.line}}>
            <div style={{height: '100%', width: `${p * 100}%`, borderRadius: 3, background: C.green}} />
          </div>
        </div>
      )
    })}
  </div>
)

export const Features: FC = () => {
  const f = useCurrentFrame()
  return (
    <AbsoluteFill>
      {FEATURES.map((ft, i) => (
        <Sequence key={ft.id} from={i * FEATURE_LEN} durationInFrames={FEATURE_LEN}>
          <Feature i={i} />
        </Sequence>
      ))}
      <Rail f={f} />
    </AbsoluteFill>
  )
}
