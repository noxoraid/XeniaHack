import { AbsoluteFill, Audio, interpolate, staticFile } from "remotion";
import { TransitionSeries, linearTiming } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { slide } from "@remotion/transitions/slide";
import { wipe } from "@remotion/transitions/wipe";
import { flip } from "@remotion/transitions/flip";
import { clockWipe } from "@remotion/transitions/clock-wipe";
import { Background } from "./Background";
import { IntroScene } from "./scenes/IntroScene";
import { FeatureScene } from "./scenes/FeatureScene";
import { OutroScene } from "./scenes/OutroScene";

export const FPS = 30;
const TR = 20; // durasi transisi (frame)

// Durasi adegan (sebelum transisi). Total akhir = jumlah - transisi = 885 frame (~29,5 detik, sesuai musik).
const SEQ = { intro: 170, dasbor: 160, file: 160, agen: 160, kerja: 160, outro: 175 };
export const PROMO_FRAMES =
  Object.values(SEQ).reduce((s, v) => s + v, 0) - TR * 5;

export const Promo: React.FC = () => {
  const audioVolume = (f: number) =>
    interpolate(f, [0, 30, PROMO_FRAMES - 60, PROMO_FRAMES], [0, 0.9, 0.9, 0], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    });

  return (
    <AbsoluteFill>
      <Background />
      <Audio src={staticFile("audio/music.mp3")} volume={audioVolume} />
      <TransitionSeries>
        <TransitionSeries.Sequence durationInFrames={SEQ.intro}>
          <IntroScene />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: TR })} />

        <TransitionSeries.Sequence durationInFrames={SEQ.dasbor}>
          <FeatureScene
            kicker="Dasbor"
            title="Pantau semua dari satu layar"
            body="Ringkasan aktivitas, status, dan pintasan ke setiap modul dalam tampilan yang bersih."
            chips={["Dasbor", "Tools", "Pengaturan"]}
            footageStart={0}
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={slide({ direction: "from-right" })} timing={linearTiming({ durationInFrames: TR })} />

        <TransitionSeries.Sequence durationInFrames={SEQ.file}>
          <FeatureScene
            kicker="File & Terminal"
            title="Kelola file dan server"
            body="Jelajahi berkas dan jalankan terminal atau SSH langsung dari ponsel."
            chips={["File", "Terminal", "SSH", "Localhost"]}
            footageStart={160}
            flip
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={wipe({ direction: "from-left" })} timing={linearTiming({ durationInFrames: TR })} />

        <TransitionSeries.Sequence durationInFrames={SEQ.agen}>
          <FeatureScene
            kicker="Agen AI & CodeMind"
            title="Asisten AI di saku Anda"
            body="Minta bantuan menulis, menjelaskan, dan merapikan kode kapan saja."
            chips={["Agen AI", "CodeMind", "Browser"]}
            footageStart={320}
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={flip({ direction: "from-left" })} timing={linearTiming({ durationInFrames: TR })} />

        <TransitionSeries.Sequence durationInFrames={SEQ.kerja}>
          <FeatureScene
            kicker="GitHub · Arus Kas · Tugas"
            title="Semua pekerjaan, satu tempat"
            body="Pantau repositori, catat arus kas, dan selesaikan tugas tanpa berpindah aplikasi."
            chips={["GitHub", "Arus Kas", "Tugas"]}
            footageStart={480}
            flip
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={clockWipe({ width: 1920, height: 1080 })} timing={linearTiming({ durationInFrames: TR })} />

        <TransitionSeries.Sequence durationInFrames={SEQ.outro}>
          <OutroScene />
        </TransitionSeries.Sequence>
      </TransitionSeries>
    </AbsoluteFill>
  );
};
