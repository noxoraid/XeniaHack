import { loadFont } from "@remotion/google-fonts/PlusJakartaSans";

export const { fontFamily } = loadFont("normal", {
  weights: ["500", "700", "800"],
  subsets: ["latin"],
});

export const COLORS = {
  bg: "#070B1A",
  surface: "rgba(255,255,255,0.06)",
  border: "rgba(255,255,255,0.12)",
  sky: "#38BDF8",
  violet: "#A78BFA",
  text: "#F8FAFC",
  muted: "#94A3B8",
};
