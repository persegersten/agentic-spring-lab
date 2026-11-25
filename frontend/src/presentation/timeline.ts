import type { PlayerGame, RoundEvent } from '../types/game'

export type PresentedEvent = { id: string; event: RoundEvent; at: number; until: number; stream: 'start' | 'playback' }
export type TickPresentation = { key: string; events: PresentedEvent[]; duration: number; step: number }

/** Presentation order only; the server supplies every position and outcome. */
export function createTimeline(view: PlayerGame): TickPresentation {
  const interval = view.continuous?.intervalMillis ?? 2000
  const budget = Math.min(1000, interval * .45)
  const linger = Math.min(650, interval * .3)
  const key = `${view.id}:${view.continuous?.tick ?? 0}`
  const playback = view.round?.state.playback ?? []
  const step = playback.length ? budget / playback.length : budget
  const wrap = (event: RoundEvent, stream: PresentedEvent['stream'], at: number): PresentedEvent => ({
    id: `${key}:${stream}:${event.sequence}`, event, stream, at, until: at + linger,
  })
  // Consecutive transfers belong to one hit and reveal all changed owners together.
  let transferStart = 0
  const presentedPlayback = playback.map((event, index) => {
    if (event.type !== 'FLAG_TRANSFERRED' || playback[index - 1]?.type !== 'FLAG_TRANSFERRED') transferStart = index
    return wrap(event, 'playback', (event.type === 'FLAG_TRANSFERRED' ? transferStart : index) * step)
  })
  return { key, step, duration: budget + linger, events: [
    ...(view.round?.state.startEvents ?? []).map(event => wrap(event, 'start', 0)),
    ...presentedPlayback,
  ] }
}
export function shouldAnimate(previous: PlayerGame, next: PlayerGame, visible: boolean) {
  return visible && previous.id === next.id && next.continuous?.tick === (previous.continuous?.tick ?? 0) + 1
}
export function isImpact(event: RoundEvent) {
  return ['RAM', 'CONVEYOR_RAM', 'MOVE_BLOCKED', 'PUSH_BLOCKED', 'CRASH', 'CONVEYOR_CRASH', 'WEAPON_HIT'].includes(event.type)
}
export function hasEffect(event: RoundEvent) {
  return isImpact(event) || event.type === 'WEAPON_FIRED' || event.type === 'VEHICLE_RESPAWNED'
    || event.type === 'SHIELD_ACTIVATED' || (event.type === 'TURN' && event.actionType === 'LASER')
    || (event.type === 'SCORE_CHANGED' && event.scoreReason === 'CHECKPOINT')
}
