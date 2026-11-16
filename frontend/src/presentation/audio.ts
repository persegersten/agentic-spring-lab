import { Howl, Howler } from 'howler'
import { createAudioController } from './audioController'
import { createHowlerAudio } from './howlerAudio'

// Keep Howler's standard gesture-unlock support (including touch and HTML5 pools).
const browserAudio = createHowlerAudio({
  create: options => new Howl(options),
  usingWebAudio: () => Howler.usingWebAudio,
  context: () => Howler.ctx ?? null,
  visible: () => !document.hidden,
})
const controller = createAudioController(browserAudio.sink)
export const gameAudio = { ...controller,
  prepare: browserAudio.prepare,
  unlock: browserAudio.unlock,
  dispose() { controller.stop(); browserAudio.dispose() },
}
