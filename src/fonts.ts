import {loadFont as loadDisplay} from '@remotion/google-fonts/SpaceGrotesk'
import {loadFont as loadMono} from '@remotion/google-fonts/JetBrainsMono'

const options = {weights: ['500', '700'] as ('500' | '700')[], subsets: ['latin' as const]}

export const display = `${loadDisplay('normal', options).fontFamily}, sans-serif`
export const mono = `${loadMono('normal', options).fontFamily}, monospace`
