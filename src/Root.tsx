import React from "react";
import {Composition} from "remotion";
import {CosmicRift} from "./CosmicRift";
import {defaults, schema} from "./schema";
import {DURATION, FPS} from "./utils";

export const Root: React.FC = () => {
  return (
    <>
      <Composition
        id="CosmicRift"
        component={CosmicRift}
        durationInFrames={DURATION}
        fps={FPS}
        width={1920}
        height={1080}
        schema={schema}
        defaultProps={defaults}
      />
      <Composition
        id="CosmicRift720"
        component={CosmicRift}
        durationInFrames={DURATION}
        fps={FPS}
        width={1280}
        height={720}
        schema={schema}
        defaultProps={defaults}
      />
    </>
  );
};
