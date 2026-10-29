import React from "react";
import {CX, CY, H, W, ease, hexA, prog, rng} from "./utils";

export type Palette = {a: string; b: string; c: string};
type Pt = [number, number];

const N = 48;
const TILT = -0.14;
const TILT_DEG = (Math.atan(-TILT) * 180) / Math.PI;
const Y0 = -140;
const Y1 = H + 140;

const rj = rng(99);
const nL = Array.from({length: N + 1}, () => rj());
const nR = Array.from({length: N + 1}, () => rj());
const nC = Array.from({length: N + 1}, () => rj() * 2 - 1);

const yAt = (i: number) => Y0 + ((Y1 - Y0) * i) / N;
const xAt = (y: number) => CX + (y - CY) * TILT;

const rb = rng(5);
const branches = Array.from({length: 9}, (_, i) => {
  const y = CY + (rb() * 1.5 - 0.75) * (H / 2);
  const dir = i % 2 ? 1 : -1;
  const ang = (rb() - 0.5) * 1.0 + (dir > 0 ? 0 : Math.PI);
  const len = 160 + rb() * 360;
  const segs = 7;
  let x = xAt(y);
  let yy = y;
  let d = `M${x.toFixed(1)} ${yy.toFixed(1)}`;
  for (let s = 0; s < segs; s++) {
    const a = ang + (rb() - 0.5) * 0.9;
    x += (Math.cos(a) * len) / segs;
    yy += (Math.sin(a) * len) / segs;
    d += `L${x.toFixed(1)} ${yy.toFixed(1)}`;
  }
  return {d, start: 6 + i * 2.5};
});

export const computeRift = (frame: number) => {
  const extent = prog(frame, 6, 34, ease.outCubic);
  const pre = prog(frame, 18, 36, ease.inCubic);
  const tear = prog(frame, 36, 84, ease.outExpo);
  const flat = prog(frame, 40, 84, ease.inOutCubic);
  const w = extent > 0 ? 0.8 + pre * 22 + tear * 1400 : 0;
  const rough = 24 * (1 - flat) * (0.35 + 0.65 * Math.max(pre, tear));
  const L: Pt[] = [];
  const Rr: Pt[] = [];
  const C: Pt[] = [];
  if (extent > 0) {
    for (let i = 0; i <= N; i++) {
      const u = Math.abs(i - N / 2) / (N / 2);
      if (u > extent) continue;
      const un = u / extent;
      const s = Math.pow(Math.cos((un * Math.PI) / 2), 0.75);
      const y = yAt(i);
      const flick = 1 + 0.18 * Math.sin(frame * 0.45 + i * 1.7);
      const cx = xAt(y) + nC[i] * 6 * (1 - flat);
      const hw = w * (s * (1 - flat) + flat);
      L.push([cx - hw - nL[i] * rough * s * flick, y]);
      Rr.push([cx + hw + nR[i] * rough * s * flick, y]);
      C.push([cx, y]);
    }
  }
  return {extent, pre, tear, flat, w, L, R: Rr, C};
};

export type RiftGeo = ReturnType<typeof computeRift>;

const toPath = (p: Pt[]) =>
  p.length ? "M" + p.map((q) => `${q[0].toFixed(1)} ${q[1].toFixed(1)}`).join("L") : "";

export const RiftPortal: React.FC<{frame: number; g: RiftGeo; pal: Palette}> = ({
  frame,
  g,
  pal,
}) => {
  if (g.L.length < 2) return null;
  const pts = [...g.L, ...[...g.R].reverse()];
  const clip = `polygon(${pts
    .map((p) => `${p[0].toFixed(1)}px ${p[1].toFixed(1)}px`)
    .join(",")})`;
  const pulse = 0.92 + 0.08 * Math.sin(frame * 0.35);
  const haloOp = g.extent * (0.25 + 0.75 * g.pre) * (1 - 0.7 * prog(frame, 64, 100));
  const haloX = 0.2 + g.pre * 0.4 + g.tear * 1.4;

  return (
    <>
      <div
        style={{
          position: "absolute",
          left: CX - 600,
          top: CY - 900,
          width: 1200,
          height: 1800,
          transform: `rotate(${TILT_DEG}deg) scaleX(${haloX})`,
          background: `radial-gradient(ellipse at center, ${hexA(pal.b, 0.5)} 0%, ${hexA(
            pal.a,
            0.28
          )} 25%, transparent 62%)`,
          opacity: haloOp,
        }}
      />
      <div
        style={{
          position: "absolute",
          left: 0,
          top: 0,
          width: W,
          height: H,
          clipPath: clip,
          WebkitClipPath: clip,
          overflow: "hidden",
        }}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            opacity: pulse,
            background: `radial-gradient(ellipse 75% 85% at 50% 50%, #ffffff 0%, ${pal.b} 9%, ${pal.a} 30%, #2a0d5c 58%, #07030f 100%)`,
          }}
        />
        <div
          style={{
            position: "absolute",
            left: CX - 1600,
            top: CY - 1600,
            width: 3200,
            height: 3200,
            background: `conic-gradient(from 0deg, transparent 0deg, ${hexA(
              pal.b,
              0.55
            )} 40deg, transparent 90deg, ${hexA(pal.c, 0.5)} 160deg, transparent 220deg, ${hexA(
              pal.a,
              0.7
            )} 290deg, transparent 360deg)`,
            WebkitMaskImage:
              "radial-gradient(circle, #000 0%, rgba(0,0,0,0.6) 35%, transparent 70%)",
            maskImage:
              "radial-gradient(circle, #000 0%, rgba(0,0,0,0.6) 35%, transparent 70%)",
            transform: `rotate(${frame * 1.3}deg)`,
            mixBlendMode: "screen",
          }}
        />
        <div
          style={{
            position: "absolute",
            left: CX - 1200,
            top: CY - 1200,
            width: 2400,
            height: 2400,
            background: `conic-gradient(from 90deg, transparent 0deg, ${hexA(
              pal.c,
              0.45
            )} 60deg, transparent 130deg, ${hexA(pal.b, 0.45)} 210deg, transparent 280deg, ${hexA(
              pal.a,
              0.5
            )} 330deg, transparent 360deg)`,
            WebkitMaskImage:
              "radial-gradient(circle, #000 0%, rgba(0,0,0,0.5) 30%, transparent 68%)",
            maskImage:
              "radial-gradient(circle, #000 0%, rgba(0,0,0,0.5) 30%, transparent 68%)",
            transform: `rotate(${-frame * 2.1}deg)`,
            mixBlendMode: "screen",
            opacity: 0.8,
          }}
        />
        <div
          style={{
            position: "absolute",
            left: CX - 350,
            top: CY - 700,
            width: 700,
            height: 1400,
            background:
              "radial-gradient(ellipse at center, rgba(255,255,255,0.85) 0%, rgba(255,255,255,0.25) 30%, transparent 65%)",
            opacity: (0.55 + 0.35 * Math.sin(frame * 0.2)) * (1 - 0.5 * prog(frame, 90, 150)),
          }}
        />
      </div>
    </>
  );
};

export const RiftFx: React.FC<{frame: number; g: RiftGeo; pal: Palette}> = ({
  frame,
  g,
  pal,
}) => {
  const coreOp = 1 - prog(frame, 52, 84);
  const edgeOp = (1 - prog(frame, 70, 96)) * g.extent;
  const glowW = 10 + g.pre * 40 + g.tear * 60;
  const pc = toPath(g.C);
  const pl = toPath(g.L);
  const pr = toPath(g.R);
  const rings = [0, 1].map((k) => {
    const start = 36 + k * 5;
    if (frame < start) return null;
    const t = prog(frame, start, start + 46, ease.outExpo);
    return (
      <circle
        key={k}
        cx={CX}
        cy={CY}
        r={t * 1500}
        fill="none"
        stroke={k ? pal.b : "#ffffff"}
        strokeWidth={10 * (1 - t) + 2}
        opacity={(1 - t) * (k ? 0.5 : 0.9)}
      />
    );
  });

  return (
    <svg
      width={W}
      height={H}
      viewBox={`0 0 ${W} ${H}`}
      style={{position: "absolute", left: 0, top: 0}}
    >
      {coreOp > 0.01 && pc && (
        <g fill="none" strokeLinejoin="round" strokeLinecap="round" opacity={coreOp}>
          <path d={pc} stroke={hexA(pal.a, 0.16)} strokeWidth={glowW} />
          <path d={pc} stroke={hexA(pal.b, 0.3)} strokeWidth={glowW * 0.5} />
          <path d={pc} stroke={pal.b} strokeWidth={3 + g.pre * 6} opacity={0.9} />
          <path d={pc} stroke="#ffffff" strokeWidth={1.5 + g.pre * 2} />
        </g>
      )}
      {edgeOp > 0.01 && (
        <g fill="none" strokeLinejoin="round" strokeLinecap="round" opacity={edgeOp}>
          {[pl, pr].map((d, i) => (
            <g key={i}>
              <path d={d} stroke={hexA(pal.a, 0.2)} strokeWidth={16} />
              <path d={d} stroke={hexA(pal.b, 0.5)} strokeWidth={7} />
              <path d={d} stroke="#ffffff" strokeWidth={2.2} />
            </g>
          ))}
        </g>
      )}
      <g fill="none" strokeLinejoin="round" strokeLinecap="round">
        {branches.map((b, i) => {
          const p =
            prog(frame, b.start, b.start + 12, ease.outCubic) *
            (1 - prog(frame, 44, 74));
          if (p <= 0.01) return null;
          const fl = 0.7 + 0.3 * Math.sin(frame * 1.3 + i * 2);
          return (
            <g key={i} opacity={fl}>
              <path
                d={b.d}
                pathLength={1}
                strokeDasharray={1}
                strokeDashoffset={1 - p}
                stroke={hexA(pal.b, 0.35)}
                strokeWidth={7}
              />
              <path
                d={b.d}
                pathLength={1}
                strokeDasharray={1}
                strokeDashoffset={1 - p}
                stroke="#ffffff"
                strokeWidth={2.2}
              />
            </g>
          );
        })}
      </g>
      {rings}
    </svg>
  );
};
