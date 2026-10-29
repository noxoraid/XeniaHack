import React from "react";
import {AbsoluteFill, Audio, interpolate, staticFile, useCurrentFrame, useVideoConfig} from "remotion";
import {RiftFx, RiftPortal, computeRift} from "./Rift";
import {Stars} from "./Stars";
import {Title} from "./Title";
import type {Props} from "./schema";
import {DURATION, H, W, ease, hexA, prog} from "./utils";

export const CosmicRift: React.FC<Props> = (props) => {
  const frame = useCurrentFrame();
  const {width} = useVideoConfig();
  const scale = width / W;
  const pal = {a: props.colorA, b: props.colorB, c: props.colorC};
  const g = computeRift(frame);

  const burst = frame >= 36 ? frame - 36 : -1;
  const shakeAmp =
    3 * prog(frame, 10, 36) + (burst >= 0 ? 18 * Math.exp(-burst / 10) : 0);
  const dx = shakeAmp * (Math.sin(frame * 2.3) + 0.6 * Math.sin(frame * 5.1 + 1));
  const dy = shakeAmp * (Math.cos(frame * 2.9) + 0.6 * Math.sin(frame * 4.3));
  const zoom =
    1 +
    0.05 * prog(frame, 0, DURATION) +
    (burst >= 0 ? 0.07 * Math.exp(-burst / 9) : 0) +
    0.45 * ease.inCubic(prog(frame, 200, DURATION));
  const roll = burst >= 0 ? 0.8 * Math.exp(-burst / 12) * Math.sin(burst * 0.9) : 0;

  const open = prog(frame, 30, 90);
  const nebOp = 0.25 + 0.75 * open;
  const flash = interpolate(frame, [34, 37, 48], [0, 0.9, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const black = Math.max(1 - prog(frame, 0, 10), ease.inCubic(prog(frame, 208, DURATION - 1)));

  return (
    <AbsoluteFill style={{background: "#02030a", overflow: "hidden"}}>
      {props.withAudio && (
        <Audio
          src={staticFile("cosmic-intro-sound.mp3")}
          volume={(f) =>
            interpolate(f, [0, 3, DURATION - 8, DURATION], [0, 1, 1, 0], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
            }) * props.volume
          }
        />
      )}
      <div
        style={{
          position: "absolute",
          left: 0,
          top: 0,
          width: W,
          height: H,
          transformOrigin: "0 0",
          transform: `scale(${scale})`,
          overflow: "hidden",
        }}
      >
        <div
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            width: W,
            height: H,
            transformOrigin: "50% 50%",
            transform: `translate(${dx}px, ${dy}px) rotate(${roll}deg) scale(${zoom})`,
          }}
        >
          <div
            style={{
              position: "absolute",
              inset: 0,
              background:
                "radial-gradient(ellipse at 50% 50%, #0a0820 0%, #04040f 55%, #02030a 100%)",
            }}
          />
          <div style={{position: "absolute", inset: 0, opacity: nebOp}}>
            <div
              style={{
                position: "absolute",
                left: -300,
                top: -500,
                width: 1700,
                height: 1700,
                background: `radial-gradient(circle, ${hexA(pal.a, 0.35)} 0%, transparent 60%)`,
                transform: `rotate(${frame * 0.12}deg) scale(${1.05 + open * 0.2})`,
                mixBlendMode: "screen",
              }}
            />
            <div
              style={{
                position: "absolute",
                left: 700,
                top: 0,
                width: 1600,
                height: 1600,
                background: `radial-gradient(circle, ${hexA(pal.b, 0.22)} 0%, transparent 60%)`,
                transform: `rotate(${-frame * 0.09}deg) scale(${1.1 + open * 0.15})`,
                mixBlendMode: "screen",
              }}
            />
            <div
              style={{
                position: "absolute",
                left: 300,
                top: 200,
                width: 1400,
                height: 1400,
                background: `radial-gradient(circle, ${hexA(pal.c, 0.2)} 0%, transparent 62%)`,
                transform: `rotate(${frame * 0.07}deg)`,
                mixBlendMode: "screen",
              }}
            />
          </div>
          <RiftPortal frame={frame} g={g} pal={pal} />
          <Stars frame={frame} pal={pal} />
          <RiftFx frame={frame} g={g} pal={pal} />
          <div
            style={{
              position: "absolute",
              inset: 0,
              opacity: flash,
              background:
                "radial-gradient(circle at 50% 50%, #ffffff 0%, rgba(190,205,255,0.6) 40%, transparent 78%)",
            }}
          />
          <Title frame={frame} title={props.title} subtitle={props.subtitle} pal={pal} />
        </div>
        <div
          style={{
            position: "absolute",
            inset: 0,
            background:
              "radial-gradient(ellipse at center, transparent 50%, rgba(0,0,5,0.75) 100%)",
          }}
        />
        <div style={{position: "absolute", inset: 0, background: "#02030a", opacity: black}} />
      </div>
    </AbsoluteFill>
  );
};
