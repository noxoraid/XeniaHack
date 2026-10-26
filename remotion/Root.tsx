import { Composition } from 'remotion';
import { PromoVideo } from './PromoVideo';
import { fps, frames } from './theme';

export const Root = () => (
  <Composition
    id="PromoVideo"
    component={PromoVideo}
    durationInFrames={frames}
    fps={fps}
    width={1920}
    height={1080}
  />
);
