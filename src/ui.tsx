import {CSSProperties, FC, ReactNode} from 'react'
import {Easing, interpolate, random, useCurrentFrame} from 'remotion'
import {BEAT, C} from './constants'
import {mono} from './fonts'

export const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const
export const ease = Easing.bezier(0.22, 1, 0.36, 1)
export const prog = (f: number, a: number, b: number) =>
  interpolate(f, [a, b], [0, 1], {...clamp, easing: ease})
export const beatPulse = (f: number) => 1 - (f % BEAT) / BEAT

export const Rise: FC<{at: number; children: ReactNode; style?: CSSProperties}> = ({at, children, style}) => {
  const f = useCurrentFrame()
  const p = prog(f, at, at + 24)
  return (
    <span style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'bottom', padding: '0 0.03em 0.1em', ...style}}>
      <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 105}%)`, opacity: Math.min(1, p * 4)}}>
        {children}
      </span>
    </span>
  )
}

const ALNUM = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'

export const scramble = (final: string, f: number, start: number, dur: number, seed: string, set = ALNUM) => {
  const settled = Math.floor(interpolate(f, [start, start + dur], [0, final.length], clamp))
  return [...final]
    .map((c, i) => (i < settled ? c : set[Math.floor(random(`${seed}${i}${Math.floor(f / 2)}`) * set.length)]))
    .join('')
}

const Json: FC<{line: string}> = ({line}) => (
  <div style={{whiteSpace: 'pre', color: C.white}}>
    {line.split(/("[^"]*":?)/).map((s, i) =>
      s.startsWith('"') ? (
        <span key={i} style={{color: s.endsWith(':') ? '#9FB4A7' : C.green}}>
          {s}
        </span>
      ) : (
        s
      )
    )}
  </div>
)

export const Terminal: FC<{method: string; path: string; res: string[]; start: number}> = ({method, path, res, start}) => {
  const f = useCurrentFrame()
  const n = Math.floor(interpolate(f, [start, start + 30], [0, path.length], clamp))
  const typing = f < start + 34
  const done = start + 42
  const status = prog(f, done, done + 12)
  return (
    <div
      style={{
        width: 800,
        minHeight: 400,
        borderRadius: 28,
        background: C.ink,
        overflow: 'hidden',
        fontFamily: mono,
        boxShadow: '0 30px 60px rgba(11,15,12,0.2)'
      }}
    >
      <div style={{display: 'flex', alignItems: 'center', gap: 12, padding: '22px 28px', borderBottom: '1px solid #1C2620'}}>
        {[C.green, C.white, C.term].map((c, i) => (
          <span key={i} style={{width: 16, height: 16, borderRadius: '50%', background: c}} />
        ))}
        <span style={{marginLeft: 'auto', color: C.term, fontSize: 22}}>contoh</span>
      </div>
      <div style={{padding: '32px 36px', fontSize: 34, lineHeight: 1.35}}>
        <div style={{color: C.white}}>
          <span style={{color: C.term}}>$ </span>
          <span style={{color: C.green, fontWeight: 700}}>{method}</span> {path.slice(0, n)}
          {typing && (
            <span
              style={{
                display: 'inline-block',
                width: 16,
                height: 32,
                marginLeft: 4,
                verticalAlign: 'bottom',
                background: C.green,
                opacity: Math.floor(f / 15) % 2 ? 0.2 : 1
              }}
            />
          )}
        </div>
        <div style={{display: 'flex', alignItems: 'center', gap: 16, margin: '22px 0', opacity: status, transform: `translateY(${(1 - status) * 12}px)`}}>
          <span style={{background: C.green, color: C.ink, fontWeight: 700, fontSize: 24, padding: '4px 16px', borderRadius: 999}}>200 OK</span>
          <span style={{color: C.term, fontSize: 24}}>contoh respons</span>
        </div>
        {res.map((line, k) => (
          <div key={k} style={{opacity: prog(f, done + 10 + k * 6, done + 20 + k * 6)}}>
            <Json line={line} />
          </div>
        ))}
      </div>
    </div>
  )
}
