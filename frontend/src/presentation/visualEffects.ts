import type { Board, Position } from '../types/game'
import type { PresentedEvent, TickPresentation } from './timeline'

export const effectDurations = { beam: 160, hit: 240, collision: 240, blocked: 180, crash: 450, respawn: 500, capture: 300, activation: 300, shield: 300 } as const
export type EffectKind = keyof typeof effectDurations
export type Fragment = { x: number; y: number; angle: number; plate: boolean }
export type EffectDescriptor = PresentedEvent & {
  kind: EffectKind; anchor: Position; lifetime: number; priority: number; fragments: Fragment[]; checkpointId?: string
}
export type VisualEffect = EffectDescriptor & { motion: boolean; age: number }
export type ShakeCue = { id: string; at: number; until: number }
export const maxEffectGroups = 8

function seedFor(id: string) {
  let seed = 2166136261
  for (const char of id) seed = Math.imul(seed ^ char.charCodeAt(0), 16777619)
  return seed >>> 0
}
export function describeEffect(item: PresentedEvent, board: Board, playerId: string): EffectDescriptor | null {
  const { event } = item
  let kind: EffectKind
  let anchor = event.oldPosition
  switch (event.type) {
    case 'WEAPON_FIRED': if (event.actionType !== 'LASER') return null; kind = 'beam'; break
    case 'WEAPON_HIT': if (event.actionType !== 'LASER') return null; kind = 'hit'; break
    case 'RAM': case 'CONVEYOR_RAM': kind = 'collision'; break
    case 'MOVE_BLOCKED': case 'PUSH_BLOCKED': kind = 'blocked'; break
    case 'CRASH': case 'CONVEYOR_CRASH': kind = 'crash'; break
    case 'VEHICLE_RESPAWNED': kind = 'respawn'; anchor = event.newPosition; break
    case 'SHIELD_ACTIVATED': kind = 'shield'; break
    case 'SCORE_CHANGED': {
      if (event.scoreReason !== 'CHECKPOINT') return null
      const checkpoint = board.checkpoints.find(checkpoint => checkpoint.id === event.checkpointId)
      if (!checkpoint) return null
      kind = 'capture'; anchor = checkpoint.position; break
    }
    default: return null
  }
  if (!anchor || !Number.isFinite(anchor.x) || !Number.isFinite(anchor.y)) return null
  const lifetime = Math.min(effectDurations[kind], item.until - item.at)
  const important = kind === 'crash' || kind === 'respawn' || kind === 'capture'
  const local = event.playerId === playerId || event.sourcePlayerId === playerId
  const priority = (important ? 6 : kind === 'beam' ? 4 : kind === 'hit' || kind === 'shield' ? 3 : 2) + (local ? 1 : 0)
  const count = ['collision', 'hit', 'crash'].includes(kind) ? 6 : kind === 'blocked' ? 3 : 0
  const seed = seedFor(item.id)
  const fragments = Array.from({ length: count }, (_, index) => {
    const angle = ((seed % 360) + index * 360 / count) * Math.PI / 180
    const distance = .38 + ((seed >>> (index * 3)) % 14) / 100
    return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance,
      angle: (seed + index * 73) % 180, plate: kind === 'crash' || (kind === 'collision' && index % 2 === 0) }
  })
  return { ...item, kind, anchor: { ...anchor }, lifetime, until: item.at + lifetime, priority, fragments,
    checkpointId: kind === 'capture' ? event.checkpointId : undefined }
}

/** Admit once in authoritative order. Eviction truncates life; dropped events never backfill. */
export function planEffects(schedule: TickPresentation, board: Board, playerId: string, availableFlagId: string | null = null): EffectDescriptor[] {
  const flags = [...board.checkpoints].sort((a, b) => a.order - b.order)
  const candidates = schedule.events.flatMap(item => {
    const effect = describeEffect(item, board, playerId)
    if (!effect || effect.lifetime <= 0) return []
    if (effect.kind !== 'capture' || !availableFlagId) return [effect]
    const next = flags[flags.findIndex(flag => flag.id === effect.checkpointId) + 1]
    if (!next || !Number.isFinite(next.position.x) || !Number.isFinite(next.position.y)) return [effect]
    // A historical successor may itself be captured in this tick. Only its transient
    // flash is retained; persistent availability always comes from the snapshot.
    const at = effect.until
    const until = Math.min(at + effectDurations.activation, item.until, schedule.duration)
    if (until <= at) return [effect]
    return [effect, { ...effect, id: `${effect.id}:activation`, kind: 'activation' as const,
      checkpointId: next.id, anchor: { ...next.position }, at, until, lifetime: until - at }]
  }).sort((a, b) => a.at - b.at || b.priority - a.priority || a.id.localeCompare(b.id))
  const admitted: EffectDescriptor[] = []
  let active: EffectDescriptor[] = []
  for (const effect of candidates) {
    active = active.filter(item => item.until > effect.at)
    if (active.length >= maxEffectGroups) {
      const lowest = active.reduce((a, b) => a.priority <= b.priority ? a : b)
      if (lowest.priority >= effect.priority) continue
      lowest.until = effect.at
      active = active.filter(item => item !== lowest)
    }
    admitted.push(effect); active.push(effect)
  }
  return admitted.filter(item => item.until > item.at)
}

export function planShakes(schedule: TickPresentation, playerId: string): ShakeCue[] {
  const cues: ShakeCue[] = []
  let availableAt = -Infinity
  for (const item of schedule.events) {
    if (item.event.playerId !== playerId || !['RAM', 'CONVEYOR_RAM', 'CRASH', 'CONVEYOR_CRASH'].includes(item.event.type)) continue
    if (item.at < availableAt) continue
    availableAt = item.at + 120
    cues.push({ id: item.id, at: item.at, until: availableAt })
  }
  return cues
}
