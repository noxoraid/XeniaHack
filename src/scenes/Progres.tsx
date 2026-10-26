import {Heading} from '../parts/Heading';
import {Phone} from '../parts/Phone';
import {Satellites} from '../parts/Satellites';
import {StatRow} from '../parts/StatRow';
import {Chip} from '../parts/Chip';
import {timeline} from '../theme';

const lines = [{text: 'Pantau'}, {text: 'progresmu.', accent: true}];
const stats = [
  {value: 361, label: 'Kosakata'},
  {value: 46, label: 'Huruf dasar'},
  {value: 6, label: 'Fitur utama'},
];

export const Progres = () => (
  <>
    <Satellites cx={1464} cy={540} radius={470} start={10} disc />
    <Phone
      src="profil.jpg"
      left={1230}
      top={99}
      width={468}
      duration={timeline.progres.duration}
      zoom={[1.25, 1.3]}
      focusY={[716, 760]}
    />
    <Heading
      eyebrow="03 — PROGRES"
      lines={lines}
      sub="Streak harian dan level belajar yang terus bertambah."
      left={120}
      top={170}
      width={860}
      size={120}
    />
    <StatRow items={stats} left={120} top={640} width={820} delay={44} />
    <Chip label="Streak harian" top={200} left={1010} delay={70} from="left" />
    <Chip label="Level belajar" top={850} left={1060} delay={88} from="left" />
  </>
);
