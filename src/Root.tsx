import { Composition } from "remotion";
import { Promo, PROMO_FRAMES, FPS } from "./Promo";

export const RemotionRoot: React.FC = () => (
  <Composition
    id="UntilyFlowPromo"
    component={Promo}
    durationInFrames={PROMO_FRAMES}
    fps={FPS}
    width={1920}
    height={1080}
  />
);
