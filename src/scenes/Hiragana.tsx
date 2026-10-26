import {Heading} from '../parts/Heading';
import {Phone} from '../parts/Phone';
import {Satellites} from '../parts/Satellites';
import {FeatureList} from '../parts/FeatureList';
import {Chip} from '../parts/Chip';
import {timeline} from '../theme';

const lines = [{text: 'Hafal huruf'}, {text: 'dengan bunyi.', accent: true}];
const features = ['Romaji di setiap huruf', 'Ketuk untuk mendengar bunyi', 'Cari huruf dengan cepat'];

export const Hiragana = () => (
  <>
    <Satellites cx={454} cy={540} radius={470} start={10} disc />
    <Phone
      src="hiragana.jpg"
      left={220}
      top={99}
      width={468}
      duration={timeline.hiragana.duration}
      zoom={[1.3, 1.34]}
      focusY={[700, 1000]}
      tilt={14}
    />
    <Heading
      eyebrow="02 — HIRAGANA"
      lines={lines}
      sub="Tabel lengkap dengan romaji, suara, dan pencarian cepat."
      left={980}
      top={170}
      width={840}
      size={92}
    />
    <FeatureList items={features} left={980} top={610} width={640} columns={1} delay={56} />
    <Chip label="46 huruf dasar" top={200} left={540} delay={64} from="right" />
    <Chip label="Ketuk, lalu dengarkan" top={860} left={560} delay={84} from="right" />
  </>
);
