import {Composition} from 'remotion';
import {Promo} from './Promo';
import {canvas} from './theme';

export const Root = () => (
  <Composition
    id="RennXJapanLandscape"
    component={Promo}
    width={canvas.width}
    height={canvas.height}
    fps={canvas.fps}
    durationInFrames={canvas.frames}
  />
);
