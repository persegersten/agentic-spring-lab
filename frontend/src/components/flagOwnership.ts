import type { Board, Player } from '../types/game'

// Ownership comes from the authoritative player snapshots, never robot positions.
export function flagOwnership(board: Board, players: Player[]) {
  const flags = [...board.checkpoints].sort((a, b) => a.order - b.order)
  const first = flags.find(flag => flag.order === 1)
  const firstOwner = first && players.find(player => player.visitedCheckpoints?.includes(first.id))
  return flags.map(flag => ({
    ...flag,
    owner: flag.order === 1 ? firstOwner : undefined,
    available: flag.order === (firstOwner ? 2 : 1),
  }))
}
