import type { Player, RoundEvent } from '../types/game'

/** Delay transferred ownership until its event; retain existing presentation of cell-entry captures. */
export function playbackPlayers(finalPlayers: Player[], events: RoundEvent[], shown: RoundEvent[]): Player[] {
  const owned = new Map(finalPlayers.map(player => [player.id, new Set(player.visitedCheckpoints)]))
  const change = (event: RoundEvent, reverse: boolean) => {
    if (!event.checkpointId) return
    if (event.type === 'FLAG_TRANSFERRED') {
      owned.get(reverse ? event.sourcePlayerId : event.playerId)?.delete(event.checkpointId)
      owned.get(reverse ? event.playerId : event.sourcePlayerId)?.add(event.checkpointId)
    }
  }
  for (const event of [...events].reverse()) change(event, true)
  for (const event of shown) change(event, false)
  return finalPlayers.map(player => ({ ...player, visitedCheckpoints: [...owned.get(player.id)!], capturedCheckpoints: owned.get(player.id)!.size }))
}
