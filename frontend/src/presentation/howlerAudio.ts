import type { Howl, HowlOptions } from 'howler'
import { audioCues, type Cue } from './audioCues'
import type { AudioSink, AudioVoice } from './audioController'

type Sound = Pick<Howl, 'state' | 'play' | 'volume' | 'stop' | 'once' | 'off' | 'unload'>
type Environment = {
  create(options: HowlOptions): Sound
  usingWebAudio(): boolean
  context(): Pick<AudioContext, 'state' | 'resume'> | null
  visible(): boolean
}
type Timers = { set(callback: () => void, delay: number): number; clear(id: number): void }
type Entry = { sound: Sound | null; failed: boolean; primed: boolean; pending: object | null }

/** Howler adapter with no delayed playback queue, plus injectable browser boundaries. */
export function createHowlerAudio(environment: Environment, timers: Timers = { set: (callback, delay) => globalThis.setTimeout(callback, delay), clear: id => globalThis.clearTimeout(id) }) {
  const sounds = new Map<Cue, Entry>()
  const active = new Set<{ entry: Entry; finish(): void; stop(): void }>()
  let gestureSeen = false
  let generation = 0

  function retire(entry: Entry) {
    entry.failed = true
    entry.primed = false
    entry.pending = null
    for (const voice of [...active]) if (voice.entry === entry) voice.finish()
    try { entry.sound?.unload() } catch { /* Audio failure must not affect play. */ }
  }
  function prepare(retry = false) {
    for (const cue of Object.keys(audioCues) as Cue[]) {
      const previous = sounds.get(cue)
      if (previous && (!retry || !previous.failed)) continue
      const entry: Entry = { sound: null, failed: false, primed: false, pending: null }
      sounds.set(cue, entry)
      try {
        entry.sound = environment.create({ src: [`/audio/${audioCues[cue].file}`], preload: true,
          autoplay: false, loop: false, volume: 0, pool: 8,
          onloaderror: () => retire(entry), onunlock: () => { entry.primed = true } })
        if (entry.failed) entry.sound.unload()
      } catch { retire(entry) }
    }
  }
  function launch(entry: Entry, cue: Cue, volume: number, ended: () => void, prime = false): AudioVoice | null {
    const sound = entry.sound
    if (!sound || entry.failed || entry.pending || sound.state() !== 'loaded') return null
    const token = {}
    entry.pending = token
    let id: number
    try { id = sound.play() } catch { retire(entry); return null }
    if (typeof id !== 'number') { retire(entry); return null }
    let done = false, started = false
    let gain = volume
    let timer = 0
    const releasePending = () => { if (entry.pending === token) entry.pending = null }
    const cleanup = () => {
      timers.clear(timer)
      sound.off('play', start, id); sound.off('end', finish, id); sound.off('playerror', fail, id)
      releasePending()
    }
    const finish = () => {
      if (done) return
      done = true; cleanup(); active.delete(record); ended()
    }
    const stop = () => {
      if (done) return
      // Unload a pending start, since stop() alone can itself be queued by Howler.
      if (!started) { retire(entry); return }
      try { sound.volume(0, id); sound.stop(id) } catch { /* Continue cleanup. */ }
      finish()
    }
    const fail = () => retire(entry)
    const start = () => {
      if (done) return
      started = true; releasePending(); timers.clear(timer)
      if (prime) { entry.primed = true; stop(); return }
      // HTML5 play() holds a promise lock. Apply gain after play, avoiding Howler's setter queue.
      try { sound.volume(gain, id) } catch { retire(entry); return }
      timer = timers.set(stop, audioCues[cue].durationMs + 100)
    }
    const record = { entry, finish, stop }
    active.add(record)
    sound.once('play', start, id); sound.once('end', finish, id); sound.once('playerror', fail, id)
    timer = timers.set(stop, 150)
    return { stop, setVolume(value) {
      gain = value
      if (!done && started) try { sound.volume(gain, id) } catch { retire(entry) }
    } }
  }
  const sink: AudioSink = {
    ready(cue) {
      const entry = sounds.get(cue)
      if (!gestureSeen || !environment.visible() || !entry || entry.failed || entry.pending || entry.sound?.state() !== 'loaded') return false
      return environment.usingWebAudio() ? environment.context()?.state === 'running'
        : entry.primed && ![...active].some(voice => voice.entry === entry)
    },
    play(cue, volume, ended) {
      if (!sink.ready(cue)) return null
      return launch(sounds.get(cue)!, cue, volume, ended)
    },
  }
  return { sink, prepare,
    unlock() {
      prepare(true)
      if (!environment.visible()) return
      gestureSeen = true
      if (environment.usingWebAudio()) {
        const context = environment.context()
        if (context && context.state !== 'running' && context.state !== 'closed') {
          const current = generation
          try { void context.resume().catch(() => { if (generation === current && environment.context()?.state !== 'running') gestureSeen = false }) }
          catch { gestureSeen = false }
        }
      } else {
        // HTML5 audio elements must each be primed inside the trusted gesture.
        for (const [cue, entry] of sounds) if (!entry.primed) launch(entry, cue, 0, () => {}, true)
      }
    },
    dispose() {
      generation++; gestureSeen = false
      for (const entry of sounds.values()) retire(entry)
      sounds.clear()
    },
  }
}
