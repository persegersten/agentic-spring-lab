import type { RoundEvent } from '../types/game'

export type Cue = 'submit' | 'tick' | 'move' | 'turn' | 'impact' | 'blocked' | 'laser' | 'hit' | 'crash' | 'respawn' | 'capture' | 'victory' | 'complete' | 'shield'
export type AudioSettings = { muted: boolean; volume: number }
export interface AudioSink {
  ready(): boolean
  play(cue: Cue, volume: number, ended: () => void): (() => void) | null
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

/** Bounded deduplication and voices, independent of browser audio for deterministic tests. */
export function createAudioController(sink: AudioSink) {
  let settings: AudioSettings = { muted: false, volume: .5 }
  const seen = new Set<string>()
  const voices: { priority: number; stop: () => void }[] = []
  const stop = () => { voices.splice(0).forEach(voice => voice.stop()) }
  const play = (cue: Cue, id: string, local = true) => {
    if (seen.has(id)) return
    seen.add(id)
    if (seen.size > 256) seen.delete(seen.values().next().value!)
    if (settings.muted || settings.volume === 0 || !sink.ready()) return
    const motor = cue === 'move' || cue === 'turn' || cue === 'tick'
    const priority = motor ? (local ? 2 : 1) : ['victory', 'complete', 'capture', 'crash', 'submit'].includes(cue) ? 4 : 3
    if (voices.length >= 8) {
      const lowest = voices.reduce((a, b) => a.priority <= b.priority ? a : b)
      if (lowest.priority >= priority) return
      lowest.stop(); voices.splice(voices.indexOf(lowest), 1)
    }
    const voice = { priority, stop: () => {} }
    const ended = () => { const index = voices.indexOf(voice); if (index >= 0) voices.splice(index, 1) }
    const cancel = sink.play(cue, settings.volume * (motor ? .35 : .7) * (local ? 1 : .55), ended)
    if (cancel) { voice.stop = cancel; voices.push(voice) }
  }
  return { play, stop,
    configure(next: AudioSettings) { settings = { muted: next.muted, volume: Math.max(0, Math.min(1, next.volume)) }; stop() },
    event(event: RoundEvent, id: string, playerId: string) {
      const cue = cueForEvent(event)
      if (cue) play(cue, id, event.playerId === playerId || event.sourcePlayerId === playerId)
    },
  }
}
