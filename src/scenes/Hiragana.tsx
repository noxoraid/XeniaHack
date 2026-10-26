import {Scene} from '../parts/Scene';
import {Heading} from '../parts/Heading';
import {Crop} from '../parts/Crop';
import {Chip} from '../parts/Chip';
import {timeline} from '../theme';

const lines = [{text: 'Huruf dan'}, {text: 'bunyinya.', accent: true}];

export const Hiragana = () => (
  <Scene duration={timeline.hiragana.duration}>
    <Heading
      index="03"
      label="HIRAGANA"
      lines={lines}
      sub="Tabel lengkap dengan romaji, suara, dan pencarian cepat."
    />
    <Crop
      src="hiragana.jpg"
      box={{x: 0, y: 305, w: 720, h: 700}}
      top={800}
      duration={timeline.hiragana.duration}
      pan={240}
      zoom={[1, 1.05]}
    />
    <Chip label="46 huruf dasar" top={770} side="right" offset={40} delay={44} />
    <Chip label="Ketuk untuk dengar bunyi" top={1560} side="left" offset={40} delay={62} />
  </Scene>
);
