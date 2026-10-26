import {FC} from 'react'
import {AbsoluteFill, Sequence} from 'remotion'
import {Background} from './Background'
import {C, FEATURE_LEN, T, TOTAL} from './constants'
import {display} from './fonts'
import {Music} from './Music'
import {Wipes} from './Wipes'
import {Features} from './scenes/Features'
import {Hook} from './scenes/Hook'
import {Intro} from './scenes/Intro'
import {Outro} from './scenes/Outro'
import {Product} from './scenes/Product'

const hasMusic = process.env.REMOTION_HAS_MUSIC === 'true'

export const Main: FC = () => (
  <AbsoluteFill style={{background: C.bg, fontFamily: display}}>
    <Background />
    <Sequence durationInFrames={T.hook}>
      <Intro />
    </Sequence>
    <Sequence from={T.hook} durationInFrames={T.product - T.hook}>
      <Hook />
    </Sequence>
    <Sequence from={T.product} durationInFrames={T.features - T.product}>
      <Product />
    </Sequence>
    <Sequence from={T.features} durationInFrames={FEATURE_LEN * 5}>
      <Features />
    </Sequence>
    <Sequence from={T.outro} durationInFrames={TOTAL - T.outro}>
      <Outro />
    </Sequence>
    <Wipes />
    {hasMusic && <Music />}
  </AbsoluteFill>
)
