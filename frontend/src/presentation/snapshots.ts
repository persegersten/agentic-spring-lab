import type { PlayerGame } from '../types/game'

export type SnapshotState = { view: PlayerGame | null; recoveryEpoch: number; disconnected: boolean }
export type SnapshotAction = { type: 'disconnected' } | { type: 'snapshot'; snapshot: PlayerGame }
export const initialSnapshotState: SnapshotState = { view: null, recoveryEpoch: 0, disconnected: false }

/** Revisions protect game state; a separate epoch resets presentation after recovery. */
export function acceptSnapshot(state: SnapshotState, action: SnapshotAction): SnapshotState {
  if (action.type === 'disconnected') return state.disconnected ? state : { ...state, disconnected: true }
  const next = action.snapshot
  const old = state.view
  const sameGame = old?.id === next.id
  if (sameGame && old?.continuous && (!next.continuous || next.continuous.revision < old.continuous.revision)) return state
  const duplicate = sameGame && old?.continuous && old.continuous.revision === next.continuous?.revision
  if (duplicate && !state.disconnected) return state
  return { view: duplicate ? old : next, disconnected: false,
    recoveryEpoch: state.recoveryEpoch + (state.disconnected || (old && !sameGame) ? 1 : 0) }
}
