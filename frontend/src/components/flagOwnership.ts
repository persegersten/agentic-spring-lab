import type { Board, Player } from '../types/game'

// Ownership comes from the authoritative player snapshots, never robot positions.
export function flagOwnership(board: Board, players: Player[]) {
  const flags = [...board.checkpoints].sort((a, b) => a.order - b.order)
  const owned = flags.map(flag => ({ ...flag,
    owner: players.find(player => player.visitedCheckpoints?.includes(flag.id)),
  }))
  const next = owned.find(flag => !flag.owner)
  return owned.map(flag => ({ ...flag, available: flag.id === next?.id }))
}
