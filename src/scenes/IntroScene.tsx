import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { COLORS } from "../theme";
import { FONT, useEnter } from "./shared";

export const IntroScene: React.FC = () => {
  const frame = useCurrentFrame();
  const logo = useEnter(0);
  const title = useEnter(18);
  const tag = useEnter(34);
  const line = interpolate(frame, [30, 90], [0, 100], { extrapolateRight: "clamp" });
  const exit = interpolate(frame, [140, 170], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: exit }}>
      <div
        style={{
          width: 140,
          height: 140,
          borderRadius: 36,
          background: `linear-gradient(135deg, ${COLORS.sky}, ${COLORS.violet})`,
          transform: `scale(${logo}) rotate(${(1 - logo) * -30}deg)`,
          boxShadow: "0 30px 80px rgba(167,139,250,0.45)",
        }}
      />
      <div
        style={{
          fontFamily: FONT,
          fontWeight: 800,
          fontSize: 150,
          letterSpacing: -4,
          color: COLORS.text,
          marginTop: 40,
          opacity: title,
          transform: `translateY(${(1 - title) * 50}px)`,
        }}
      >
        Untily
        <span
          style={{
            background: `linear-gradient(90deg, ${COLORS.sky}, ${COLORS.violet})`,
            WebkitBackgroundClip: "text",
            color: "transparent",
          }}
        >
          Flow
        </span>
      </div>
      <div style={{ width: `${line * 6}px`, height: 4, background: COLORS.sky, borderRadius: 2, marginTop: 10 }} />
      <div
        style={{
          fontFamily: FONT,
          fontWeight: 500,
          fontSize: 38,
          color: COLORS.muted,
          marginTop: 36,
          opacity: tag,
          transform: `translateY(${(1 - tag) * 30}px)`,
        }}
      >
        Satu aplikasi. Semua alat kerja Anda.
      </div>
    </AbsoluteFill>
  );
};
