import {Scene} from '../parts/Scene';
import {Heading} from '../parts/Heading';
import {Crop} from '../parts/Crop';
import {Chip} from '../parts/Chip';
import {Rise} from '../parts/Rise';
import {fonts, palette, timeline} from '../theme';

const lines = [{text: 'Pantau progres,'}, {text: 'jaga semangat.', accent: true}];

export const Profil = () => (
  <Scene duration={timeline.profil.duration}>
    <Heading
      index="04"
      label="PROGRES"
      lines={lines}
      sub="Streak harian dan level belajar yang terus bertambah."
    />
    <Crop
      src="profil.jpg"
      box={{x: 0, y: 392, w: 720, h: 353}}
      top={800}
      duration={timeline.profil.duration}
      zoom={[1, 1.08]}
    />
    <Chip label="Level belajar" top={1310} side="left" offset={96} delay={46} />
    <Chip label="Streak harian" top={1310} side="right" offset={96} delay={60} />
    <Rise
      delay={78}
      style={{
        position: 'absolute',
        top: 1470,
        left: 96,
        right: 96,
        fontFamily: fonts.serif,
        fontStyle: 'italic',
        fontWeight: 500,
        fontSize: 82,
        lineHeight: 1.08,
        color: palette.ink,
      }}
    >
      Terus tingkatkan semangat belajarmu.
    </Rise>
  </Scene>
);
