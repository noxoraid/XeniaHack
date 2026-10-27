import { AbsoluteFill, useCurrentFrame } from "remotion";
import { COLORS } from "./theme";

// Gradasi awan yang bergerak pelan di belakang semua adegan.
export const Background: React.FC = () => {
  const f = useCurrentFrame();
  const blob = (x: number, y: number, size: number, color: string, phase: number) => ({
    position: "absolute" as const,
    left: x + Math.sin((f + phase) / 90) * 80,
    top: y + Math.cos((f + phase) / 110) * 60,
    width: size,
    height: size,
    borderRadius: "50%",
    background: color,
    filter: "blur(120px)",
    opacity: 0.55,
  });
  return (
    <AbsoluteFill style={{ backgroundColor: COLORS.bg, overflow: "hidden" }}>
      <div style={blob(-200, -200, 900, COLORS.sky, 0)} />
      <div style={blob(1200, 300, 800, COLORS.violet, 120)} />
      <div style={blob(500, 700, 700, "#1E3A8A", 240)} />
      <AbsoluteFill
        style={{
          backgroundImage:
            "linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px)",
          backgroundSize: "80px 80px",
        }}
      />
    </AbsoluteFill>
  );
};
