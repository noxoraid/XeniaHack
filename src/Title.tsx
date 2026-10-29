import React from "react";
import {loadFont} from "@remotion/google-fonts/Orbitron";
import {H, W, ease, hexA, prog, clamp01} from "./utils";
import type {Palette} from "./Rift";

const {fontFamily} = loadFont("normal", {weights: ["500", "800"], subsets: ["latin"]});

type Mode = "glow" | "fill" | "red" | "cyan";

const Word: React.FC<{
  text: string;
  frame: number;
  mode: Mode;
  size: number;
  pal: Palette;
  g: number;
}> = ({text, frame, mode, size, pal, g}) => {
  const chars = Array.from(text);
  const gp = 0.85 + 0.15 * Math.sin(frame * 0.12);
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        top: 0,
        width: W,
        height: H,
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        transform: "translateY(-40px)",
        fontFamily,
        fontWeight: 800,
        fontSize: size,
        letterSpacing: "0.08em",
        whiteSpace: "pre",
        lineHeight: 1,
        pointerEvents: "none",
      }}
    >
      {chars.map((ch, i) => {
        const start = 70 + i * 3.2;
        const p = ease.outExpo(prog(frame, start, start + 26));
        const t = clamp01((frame - 118 - i * 3) / 16);
        const base: React.CSSProperties = {
          display: "inline-block",
          opacity: p,
          transform: `translate(${mode === "red" ? -g : mode === "cyan" ? g : 0}px, ${
            (1 - p) * 46
          }px) scale(${1 + (1 - p) * 0.9})`,
          filter: p < 1 ? `blur(${(1 - p) * 16}px)` : undefined,
        };
        let style: React.CSSProperties = base;
        if (mode === "glow") {
          style = {
            ...base,
            opacity: p * gp,
            color: hexA(pal.b, 0.9),
            textShadow: `0 0 18px ${hexA(pal.b, 0.9)}, 0 0 60px ${hexA(pal.a, 0.8)}, 0 0 140px ${hexA(
              pal.c,
              0.55
            )}`,
          };
        } else if (mode === "fill") {
          style = {
            ...base,
            backgroundImage: `linear-gradient(105deg, transparent 0%, transparent 42%, rgba(255,255,255,0.95) 50%, transparent 58%, transparent 100%), linear-gradient(180deg, #ffffff 15%, #c4ecff 55%, ${pal.a} 130%)`,
            backgroundSize: "300% 100%, 100% 100%",
            backgroundPosition: `${(1 - t) * 100}% 0, 0 0`,
            backgroundRepeat: "no-repeat",
            WebkitBackgroundClip: "text",
            backgroundClip: "text",
            color: "transparent",
            WebkitTextFillColor: "transparent",
          };
        } else {
          style = {
            ...base,
            color: mode === "red" ? "rgba(255,40,90,0.85)" : "rgba(0,235,255,0.85)",
            mixBlendMode: "screen",
          };
        }
        return (
          <span key={i} style={style}>
            {ch === " " ? "\u00A0" : ch}
          </span>
        );
      })}
    </div>
  );
};

export const Title: React.FC<{
  frame: number;
  title: string;
  subtitle: string;
  pal: Palette;
}> = ({frame, title, subtitle, pal}) => {
  const len = Math.max(1, Array.from(title).length);
  const size = Math.min(200, 1500 / (len * 0.95));
  let g = 0;
  if (frame >= 88 && frame < 98) g = 16 * (1 - (frame - 88) / 10) * Math.sin(frame * 7);
  else if (frame >= 152 && frame < 158) g = 10 * (1 - (frame - 152) / 6) * Math.sin(frame * 9);

  const subP = ease.outCubic(prog(frame, 98, 124));
  const lineP = ease.outExpo(prog(frame, 100, 130));
  const ringP = ease.outExpo(prog(frame, 80, 120));
  const orbit = frame * 0.06;
  const rot = (-10 * Math.PI) / 180;
  const ox = 760 * Math.cos(orbit);
  const oy = 760 * 0.22 * Math.sin(orbit);
  const dotX = W / 2 + ox * Math.cos(rot) - oy * Math.sin(rot);
  const dotY = H / 2 - 40 + ox * Math.sin(rot) + oy * Math.cos(rot);
  const subTop = H / 2 - 40 + size * 0.62 + 26;

  return (
    <>
      <svg
        width={W}
        height={H}
        viewBox={`0 0 ${W} ${H}`}
        style={{position: "absolute", left: 0, top: 0, opacity: ringP}}
      >
        <g transform={`translate(${W / 2} ${H / 2 - 40}) rotate(-10) scale(1 0.22)`}>
          <circle r={760} fill="none" stroke={hexA(pal.b, 0.45)} strokeWidth={3} />
          <circle
            r={700}
            fill="none"
            stroke={hexA(pal.c, 0.55)}
            strokeWidth={3}
            strokeDasharray="2 14"
            transform={`rotate(${frame * 0.5})`}
          />
          <circle r={820} fill="none" stroke={hexA(pal.a, 0.35)} strokeWidth={2} />
        </g>
        <circle cx={dotX} cy={dotY} r={26} fill={hexA(pal.b, 0.25)} />
        <circle cx={dotX} cy={dotY} r={8} fill="#ffffff" />
      </svg>
      <Word text={title} frame={frame} mode="glow" size={size} pal={pal} g={0} />
      {Math.abs(g) > 0.5 && (
        <>
          <Word text={title} frame={frame} mode="red" size={size} pal={pal} g={g} />
          <Word text={title} frame={frame} mode="cyan" size={size} pal={pal} g={g} />
        </>
      )}
      <Word text={title} frame={frame} mode="fill" size={size} pal={pal} g={0} />
      <div
        style={{
          position: "absolute",
          left: 0,
          top: subTop,
          width: W,
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          gap: 28,
          fontFamily,
          fontWeight: 500,
          fontSize: 34,
          color: "#dfeaff",
          opacity: subP,
          transform: `translateY(-40px)`,
          textShadow: `0 0 20px ${hexA(pal.b, 0.8)}`,
        }}
      >
        <div
          style={{
            width: 220 * lineP,
            height: 2,
            background: `linear-gradient(90deg, transparent, ${pal.b})`,
          }}
        />
        <div style={{letterSpacing: `${0.9 - 0.4 * subP}em`, whiteSpace: "pre"}}>{subtitle}</div>
        <div
          style={{
            width: 220 * lineP,
            height: 2,
            background: `linear-gradient(270deg, transparent, ${pal.b})`,
          }}
        />
      </div>
    </>
  );
};
