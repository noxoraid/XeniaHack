import {Scene} from '../parts/Scene';
import {Heading} from '../parts/Heading';
import {Crop} from '../parts/Crop';
import {Chip} from '../parts/Chip';
import {timeline} from '../theme';

const lines = [{text: 'Enam jalur,'}, {text: 'satu tujuan.', accent: true}];

export const Fitur = () => (
  <Scene duration={timeline.fitur.duration}>
    <Heading
      index="02"
      label="FITUR UTAMA"
      lines={lines}
      sub="Hiragana, Katakana, Minna No Nihongo, Kanji, hingga Bunpou."
    />
    <Crop
      src="beranda.jpg"
      box={{x: 0, y: 660, w: 704, h: 685}}
      top={790}
      duration={timeline.fitur.duration}
      zoom={[1, 1.08]}
    />
    <Chip label="Hiragana & Katakana" top={830} side="right" offset={40} delay={40} />
    <Chip label="Minna No Nihongo 1 & 2" top={1190} side="left" offset={40} delay={56} />
    <Chip label="Kanji & Bunpou" top={1540} side="right" offset={60} delay={72} />
  </Scene>
);
