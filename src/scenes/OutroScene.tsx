import { AbsoluteFill } from "remotion";
import { COLORS } from "../theme";
import { FONT, useEnter } from "./shared";

export const OutroScene: React.FC = () => {
  const a = useEnter(0);
  const b = useEnter(16);
  const c = useEnter(30);
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", gap: 36, flexDirection: "column" }}>
      <div
        style={{
          fontFamily: FONT,
          fontWeight: 800,
          fontSize: 110,
          color: COLORS.text,
          opacity: a,
          transform: `scale(${0.9 + a * 0.1})`,
          letterSpacing: -3,
        }}
      >
        UntilyFlow
      </div>
      <div
        style={{
          fontFamily: FONT,
          fontWeight: 500,
          fontSize: 40,
          color: COLORS.muted,
          opacity: b,
          transform: `translateY(${(1 - b) * 30}px)`,
        }}
      >
        Kerja lebih tenang, hasil lebih cepat.
      </div>
      <div
        style={{
          fontFamily: FONT,
          fontWeight: 800,
          fontSize: 34,
          color: "#0B1020",
          padding: "22px 60px",
          borderRadius: 999,
          background: `linear-gradient(90deg, ${COLORS.sky}, ${COLORS.violet})`,
          opacity: c,
          transform: `scale(${0.8 + c * 0.2})`,
          boxShadow: "0 20px 60px rgba(56,189,248,0.35)",
        }}
      >
        Coba UntilyFlow sekarang
      </div>
    </AbsoluteFill>
  );
};
