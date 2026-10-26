import {existsSync} from 'node:fs'
import {spawnSync} from 'node:child_process'

const mode = process.argv[2]
const hasMusic = existsSync('public/music.mp3')

if (!hasMusic) console.warn('public/music.mp3 tidak ditemukan, video dirender tanpa audio.')

const args =
  mode === 'render'
    ? ['remotion', 'render', 'Promo', 'out/noxora-rest-api.mp4']
    : ['remotion', 'studio']

const result = spawnSync('npx', args, {
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: {...process.env, REMOTION_HAS_MUSIC: String(hasMusic)}
})

process.exit(result.status ?? 1)
