import { AbsoluteFill } from "remotion";
import { COLORS, fontFamily } from "../theme";
import { FONT, Kicker, Phone, useEnter } from "./shared";

type Props = {
  kicker: string;
  title: string;
  body: string;
  chips: string[];
  footageStart: number;
  flip?: boolean;
};

export const FeatureScene: React.FC<Props> = ({ kicker, title, body, chips, footageStart, flip }) => {
  const a = useEnter(4);
  const b = useEnter(12);
  const c = useEnter(22);
  const textBlock = (
    <div style={{ width: 820, display: "flex", flexDirection: "column", gap: 28 }}>
      <div style={{ opacity: a, transform: `translateX(${(1 - a) * -40}px)` }}>
        <Kicker>{kicker}</Kicker>
      </div>
      <div
        style={{
          fontFamily: FONT,
          fontWeight: 800,
          fontSize: 92,
          lineHeight: 1.05,
          letterSpacing: -2,
          color: COLORS.text,
          opacity: b,
          transform: `translateY(${(1 - b) * 40}px)`,
        }}
      >
        {title}
      </div>
      <div
        style={{
          fontFamily: fontFamily,
          fontWeight: 500,
          fontSize: 34,
          lineHeight: 1.5,
          color: COLORS.muted,
          opacity: c,
        }}
      >
        {body}
      </div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 14, opacity: c }}>
        {chips.map((chip) => (
          <span
            key={chip}
            style={{
              fontFamily: FONT,
              fontWeight: 700,
              fontSize: 24,
              color: COLORS.sky,
              padding: "12px 22px",
              borderRadius: 999,
              border: `1px solid ${COLORS.border}`,
              background: COLORS.surface,
            }}
          >
            {chip}
          </span>
        ))}
      </div>
    </div>
  );
  const phone = <Phone startFrom={footageStart} />;
  return (
    <AbsoluteFill
      style={{
        flexDirection: flip ? "row-reverse" : "row",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0 140px",
      }}
    >
      {textBlock}
      {phone}
    </AbsoluteFill>
  );
};
