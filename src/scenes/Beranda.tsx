import {Scene} from '../parts/Scene';
import {Heading} from '../parts/Heading';
import {Crop} from '../parts/Crop';
import {Stats} from '../parts/Stats';
import {timeline} from '../theme';

const lines = [{text: 'Belajar Jepang,'}, {text: 'tanpa ribet.', accent: true}];
const stats = [
  {value: 6, label: 'Fitur utama'},
  {value: 361, label: 'Kosakata'},
  {value: 46, label: 'Huruf dasar'},
];

export const Beranda = () => (
  <Scene duration={timeline.beranda.duration}>
    <Heading
      index="01"
      label="BERANDA"
      lines={lines}
      sub="Beranda ringkas dengan semua fitur belajar dalam satu layar."
    />
    <Crop
      src="beranda.jpg"
      box={{x: 0, y: 245, w: 704, h: 405}}
      top={800}
      duration={timeline.beranda.duration}
      zoom={[1, 1.1]}
    />
    <Stats items={stats} top={1400} delay={46} />
  </Scene>
);
