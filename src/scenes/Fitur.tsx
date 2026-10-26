import {Heading} from '../parts/Heading';
import {Phone} from '../parts/Phone';
import {Satellites} from '../parts/Satellites';
import {FeatureList} from '../parts/FeatureList';
import {Chip} from '../parts/Chip';
import {timeline} from '../theme';

const lines = [{text: 'Enam jalur'}, {text: 'satu tujuan.', accent: true}];
const features = [
  'Hiragana & Katakana',
  'Kosakata Bab 1 & 2',
  'Minna No Nihongo 1',
  'Minna No Nihongo 2',
  'Kanji dasar',
  'Bunpou',
];

export const Fitur = () => (
  <>
    <Satellites cx={1464} cy={540} radius={470} start={10} disc />
    <Phone
      src="beranda.jpg"
      left={1230}
      top={99}
      width={468}
      duration={timeline.fitur.duration}
      zoom={[1.3, 1.34]}
      focusY={[700, 1000]}
    />
    <Heading
      eyebrow="01 — FITUR UTAMA"
      lines={lines}
      sub="Semua materi belajar bahasa Jepang, rapi dalam satu beranda."
      left={120}
      top={170}
      width={860}
      size={104}
    />
    <FeatureList items={features} left={120} top={630} width={800} columns={2} delay={56} />
    <Chip label="6 fitur utama" top={190} left={1020} delay={70} from="left" />
  </>
);
