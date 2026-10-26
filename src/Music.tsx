import {FC, useEffect, useState} from 'react'
import {Audio, Sequence, cancelRender, continueRender, delayRender, interpolate, staticFile} from 'remotion'
import {getAudioDurationInSeconds} from '@remotion/media-utils'
import {FPS, TOTAL, sec} from './constants'
import {clamp} from './ui'

const FADE_IN = sec(1)
const FADE_OUT = sec(2)
const CROSS = sec(0.6)
const src = staticFile('music.mp3')

const master = (f: number) => interpolate(f, [0, FADE_IN, TOTAL - FADE_OUT, TOTAL], [0, 1, 1, 0], clamp)

export const Music: FC = () => {
  const [handle] = useState(() => delayRender('music'))
  const [frames, setFrames] = useState(0)

  useEffect(() => {
    getAudioDurationInSeconds(src)
      .then((d) => {
        setFrames(Math.round(d * FPS))
        continueRender(handle)
      })
      .catch((e) => cancelRender(e))
  }, [handle])

  if (!frames) return null

  const step = Math.max(frames - CROSS, 1)
  const count = frames >= TOTAL ? 1 : Math.ceil(TOTAL / step)

  return (
    <>
      {Array.from({length: count}, (_, i) => {
        const from = i * step
        const last = i === count - 1
        return (
          <Sequence key={i} from={from} durationInFrames={frames}>
            <Audio
              src={src}
              volume={(l) => {
                const fadeIn = i === 0 ? 1 : interpolate(l, [0, CROSS], [0, 1], clamp)
                const fadeOut = last ? 1 : interpolate(l, [frames - CROSS, frames], [1, 0], clamp)
                return master(from + l) * fadeIn * fadeOut
              }}
            />
          </Sequence>
        )
      })}
    </>
  )
}
