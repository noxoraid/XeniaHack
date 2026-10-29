import React, {useEffect, useRef} from "react";
import {CX, CY, DURATION, H, W, ease, prog, rng} from "./utils";
import type {Palette} from "./Rift";

const R = 1250;
const BURST = 36;

const speedAt = (f: number) => {
  const pre = prog(f, 10, 34, ease.inCubic) * 3;
  const up = prog(f, 30, 44, ease.outCubic);
  const down = 1 - prog(f, 86, 170, ease.inOutCubic);
  return 0.45 + pre + up * down * 46;
};

const travel: number[] = [];
{
  let acc = 0;
  for (let f = 0; f <= DURATION; f++) {
    acc += speedAt(f);
    travel.push(acc);
  }
}

const STAR_COLORS = ["#ffffff", "#bfe3ff", "#d9c8ff", "#9ff3ff", "#ffd3f1"];
const r1 = rng(7);
const stars = Array.from({length: 340}, () => ({
  a: r1() * Math.PI * 2,
  r: r1() * R,
  s: 0.35 + r1() * 1.25,
  z: 0.6 + r1() * 1.8,
  c: Math.floor(r1() * 5),
  p: r1() * 6.28,
}));

const r2 = rng(21);
const sparks = Array.from({length: 150}, () => ({
  a: r2() * Math.PI * 2,
  r: 380 + r2() * 760,
  d: r2() * 14,
  v: 25 + r2() * 70,
  z: 1.5 + r2() * 3.5,
  c: Math.floor(r2() * 3),
  ty: (r2() - 0.5) * 600,
}));

export const Stars: React.FC<{frame: number; pal: Palette}> = ({frame, pal}) => {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const cv = ref.current;
    if (!cv) return;
    const ctx = cv.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, W, H);
    ctx.globalCompositeOperation = "lighter";
    ctx.lineCap = "round";

    const f = Math.min(DURATION, Math.max(0, frame));
    const fade = prog(f, 0, 14);
    const sp = speedAt(f);
    const tr = travel[f];

    for (const st of stars) {
      const raw = st.r + tr * st.s;
      const k = Math.floor(raw / R);
      const r = raw - k * R;
      const rp = Math.max(0, r - sp * st.s * 2.2);
      const ca = Math.cos(st.a);
      const sa = Math.sin(st.a);
      const alpha =
        fade * (0.55 + 0.45 * Math.sin(f * 0.13 + st.p)) * Math.min(1, r / 220);
      if (alpha < 0.02) continue;
      ctx.globalAlpha = alpha;
      ctx.strokeStyle = STAR_COLORS[st.c];
      ctx.lineWidth = st.z * (0.5 + (r / R) * 1.3);
      ctx.beginPath();
      ctx.moveTo(CX + ca * rp, CY + sa * rp);
      ctx.lineTo(CX + ca * r, CY + sa * r);
      ctx.stroke();
    }

    const cols = [pal.a, pal.b, pal.c];
    for (const sk of sparks) {
      const ca = Math.cos(sk.a);
      const sa = Math.sin(sk.a);
      const tx = CX - 0.14 * sk.ty;
      const ty = CY + sk.ty;
      let x: number;
      let y: number;
      let alpha: number;
      if (f < BURST) {
        const t = prog(f, 3 + sk.d * 0.6, BURST, ease.inCubic);
        const sx = CX + ca * sk.r;
        const sy = CY + sa * sk.r;
        x = sx + (tx - sx) * t;
        y = sy + (ty - sy) * t;
        alpha = prog(f, 3, 14) * (0.2 + 0.8 * t);
      } else {
        const t = Math.max(0, f - BURST - sk.d * 0.2);
        const dist = sk.v * 14 * (1 - Math.exp(-t / 14));
        x = tx + ca * dist;
        y = ty + sa * dist;
        alpha = 1 - prog(f, BURST + 20, BURST + 80);
      }
      if (alpha < 0.02) continue;
      ctx.fillStyle = cols[sk.c];
      ctx.globalAlpha = alpha * 0.18;
      ctx.beginPath();
      ctx.arc(x, y, sk.z * 4, 0, 6.2832);
      ctx.fill();
      ctx.globalAlpha = alpha;
      ctx.beginPath();
      ctx.arc(x, y, sk.z, 0, 6.2832);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
  }, [frame, pal.a, pal.b, pal.c]);

  return (
    <canvas
      ref={ref}
      width={W}
      height={H}
      style={{position: "absolute", left: 0, top: 0, width: W, height: H}}
    />
  );
};
