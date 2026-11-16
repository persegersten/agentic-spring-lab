import type { RoundEvent } from '../types/game'

import { audioCues, type Cue } from './audioCues'
export type { Cue } from './audioCues'

export type AudioSettings = { muted: boolean; volume: number }
export type AudioVoice = { stop(): void; setVolume(volume: number): void }
export interface AudioSink {
  ready(cue: Cue): boolean
  play(cue: Cue, volume: number, ended: () => void): AudioVoice | null
}
export function cueForEvent(event: RoundEvent): Cue | null {
  switch (event.type) {
    case 'MOVE': case 'CONVEYOR_MOVE': return 'move'
    case 'TURN': return event.actionType === 'LASER' ? null : 'turn'
    case 'ROTATOR_TURN': return 'turn'
    case 'RAM': case 'CONVEYOR_RAM': return event.actionType === 'LASER' ? null : 'impact'
    case 'MOVE_BLOCKED': case 'PUSH_BLOCKED': return event.actionType === 'LASER' ? null : 'blocked'
    case 'WEAPON_FIRED': return 'laser'
    case 'WEAPON_HIT': return 'hit'
    case 'CRASH': case 'CONVEYOR_CRASH': return 'crash'
    case 'VEHICLE_RESPAWNED': return 'respawn'
    case 'SHIELD_ACTIVATED': return 'shield'
    case 'SCORE_CHANGED': return event.scoreReason === 'CHECKPOINT' ? 'capture' : null
    default: return null
  }
}

/** Bounded, testable audio policy; cosmetic suppression never changes gameplay. */
export function createAudioController(sink: AudioSink, now: () => number = () => performance.now()) {
  let settings: AudioSettings = { muted: false, volume: .5 }
  const seen = new Set<string>()
  const motors = new Set<string>()
  const cooldowns = new Map<Cue, number>()
  const voices: { id: string; priority: number; motor: boolean; gain: number; handle: AudioVoice | null }[] = []
  const remember = (set: Set<string>, id: string) => {
    if (set.has(id)) return false
    set.add(id)
    if (set.size > 256) set.delete(set.values().next().value!)
    return true
  }
  const stop = (prefix?: string) => {
    for (const voice of [...voices]) if (!prefix || voice.id.startsWith(prefix)) {
      const index = voices.indexOf(voice)
      if (index >= 0) voices.splice(index, 1)
      voice.handle?.stop()
    }
  }
  const play = (cue: Cue, id: string, local = true, motorKey?: string) => {
    if (!remember(seen, id)) return
    if (motorKey && !remember(motors, motorKey)) return
    if (settings.muted || settings.volume === 0 || !sink.ready(cue)) return
    const motor = cue === 'move' || cue === 'turn'
    const impact = cue === 'impact' || cue === 'blocked' || cue === 'hit'
    const time = now()
    if (!local && (motor || impact) && time - (cooldowns.get(cue) ?? -Infinity) < 80) return
    const priority = motor ? (local ? 2 : 1) : cue === 'tick' ? 1
      : ['victory', 'complete', 'capture', 'crash', 'submit'].includes(cue) ? (local ? 6 : 5) : (local ? 4 : 3)
    const candidates = motor && voices.filter(voice => voice.motor).length >= 2 ? voices.filter(voice => voice.motor)
      : voices.length >= 8 ? voices : []
    if (candidates.length) {
      const lowest = candidates.reduce((a, b) => a.priority <= b.priority ? a : b)
      if (lowest.priority >= priority) return
      // Remove before stopping: a synchronous end/error callback cannot splice the wrong voice.
      voices.splice(voices.indexOf(lowest), 1)
      lowest.handle?.stop()
    }
    const voice = { id, priority, motor, gain: audioCues[cue].gain * (local ? 1 : .55), handle: null as AudioVoice | null }
    let ended = false
    const finish = () => { ended = true; const index = voices.indexOf(voice); if (index >= 0) voices.splice(index, 1) }
    try { voice.handle = sink.play(cue, settings.volume * voice.gain, finish) } catch { return }
    if (!voice.handle) return
    if (ended) { voice.handle.stop(); return }
    voices.push(voice)
    if (!local && (motor || impact)) cooldowns.set(cue, time)
  }
  return { play, stop,
    configure(next: AudioSettings) {
      settings = { muted: next.muted, volume: Number.isFinite(next.volume) ? Math.max(0, Math.min(1, next.volume)) : 0 }
      if (settings.muted || settings.volume === 0) stop()
      else for (const voice of voices) voice.handle?.setVolume(settings.volume * voice.gain)
    },
    event(event: RoundEvent, id: string, playerId: string) {
      const cue = cueForEvent(event)
      if (!cue) return
      const motorKey = cue === 'move' || cue === 'turn'
        ? `${id.slice(0, id.lastIndexOf(':'))}:${event.vehicleId}:${cue}` : undefined
      play(cue, id, event.playerId === playerId || event.sourcePlayerId === playerId, motorKey)
    },
  }
}
