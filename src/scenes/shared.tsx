import { OffthreadVideo, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { COLORS, fontFamily } from "../theme";

export const FONT = fontFamily;

export const useEnter = (delay = 0) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return spring({ frame: frame - delay, fps, config: { damping: 200 } });
};

// Bingkai ponsel untuk footage portrait; audio footage dimatikan.
export const Phone: React.FC<{ startFrom: number }> = ({ startFrom }) => {
  const enter = useEnter(10);
  return (
    <div
      style={{
        width: 350,
        height: 780,
        borderRadius: 48,
        border: `10px solid #111827`,
        boxShadow: `0 40px 120px rgba(56,189,248,0.25)`,
        overflow: "hidden",
        background: "#000",
        transform: `translateY(${(1 - enter) * 120}px) rotate(${(1 - enter) * 4}deg)`,
        opacity: enter,
      }}
    >
      <OffthreadVideo
        src={staticFile("video/app-footage.mp4")}
        muted
        startFrom={startFrom}
        style={{ width: "100%", height: "100%", objectFit: "cover" }}
      />
    </div>
  );
};

export const Kicker: React.FC<{ children: string }> = ({ children }) => (
  <div
    style={{
      fontFamily: FONT,
      fontWeight: 700,
      fontSize: 22,
      letterSpacing: 6,
      textTransform: "uppercase",
      color: COLORS.sky,
    }}
  >
    {children}
  </div>
);
