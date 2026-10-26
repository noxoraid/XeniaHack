import {Composition} from 'remotion'
import {FPS, H, TOTAL, W} from './constants'
import {Main} from './Main'

export const Root = () => (
  <Composition id="Promo" component={Main} durationInFrames={TOTAL} fps={FPS} width={W} height={H} />
)
