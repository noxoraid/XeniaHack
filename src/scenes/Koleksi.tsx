import {Heading} from '../parts/Heading';
import {Phone} from '../parts/Phone';
import {Satellites} from '../parts/Satellites';
import {FeatureList} from '../parts/FeatureList';
import {timeline} from '../theme';

const lines = [{text: 'Satu app.'}, {text: 'Semua materi.', accent: true}];
const features = [
  'Latihan dari buku Minna No Nihongo',
  'Dengarkan bunyi setiap huruf',
  'Pantau progres dan streak harian',
];

export const Koleksi = () => (
  <>
    <Satellites cx={1325} cy={540} radius={500} start={6} disc />
    <Phone
      src="beranda.jpg"
      left={830}
      top={330}
      width={330}
      duration={timeline.koleksi.duration}
      delay={14}
      zoom={[1, 1.06]}
      focusY={[850, 850]}
    />
    <Phone
      src="hiragana.jpg"
      left={1160}
      top={210}
      width={330}
      duration={timeline.koleksi.duration}
      delay={26}
      zoom={[1, 1.06]}
      focusY={[850, 850]}
    />
    <Phone
      src="profil.jpg"
      left={1490}
      top={330}
      width={330}
      duration={timeline.koleksi.duration}
      delay={38}
      zoom={[1, 1.06]}
      focusY={[850, 850]}
    />
    <Heading
      eyebrow="04 — LENGKAP"
      lines={lines}
      sub="Dari Hiragana sampai Kanji, semuanya ada di satu tempat."
      left={120}
      top={190}
      width={680}
      size={88}
    />
    <FeatureList items={features} left={120} top={640} width={640} columns={1} delay={44} />
  </>
);
