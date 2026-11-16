import { Howl, Howler } from 'howler'
import { createAudioController, type Cue } from './audioController'

const cues: Cue[] = ['submit', 'tick', 'move', 'turn', 'impact', 'blocked', 'laser', 'hit', 'crash', 'respawn', 'capture', 'victory', 'complete', 'shield']
const sounds = new Map<Cue, Howl>()
let unlocked = false
Howler.autoUnlock = false
const controller = createAudioController({
  ready: () => unlocked && !document.hidden && (!Howler.usingWebAudio || Howler.ctx?.state === 'running'),
  play(cue, volume, ended) {
    const sound = sounds.get(cue)
    if (!sound || sound.state() !== 'loaded') return null
    const id = sound.play()
    sound.volume(volume, id)
    const cleanup = () => { sound.off('end', finish, id); sound.off('playerror', fail, id) }
    const finish = () => { cleanup(); ended() }
    const fail = () => { sound.stop(id); finish() }
    sound.once('end', finish, id)
    sound.once('playerror', fail, id)
    return () => { sound.stop(id); cleanup() }
  },
})
export const gameAudio = { ...controller,
  unlock() {
    if (!sounds.size) for (const cue of cues) sounds.set(cue, new Howl({ src: [`/audio/${cue}.wav`], preload: true, pool: 8 }))
    unlocked = true
    if (Howler.ctx?.state === 'suspended') void Howler.ctx.resume().catch(() => { unlocked = false })
  },
}
