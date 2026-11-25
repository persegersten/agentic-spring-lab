import { GameBoard } from './GameBoard'
import type { PlayerGame } from '../types/game'
import type { useTickPresentation } from '../presentation/useTickPresentation'

export function ContinuousBoard({ view, presentation }: { view: PlayerGame; presentation: ReturnType<typeof useTickPresentation> }) {
  return <div className={`tick-board ${presentation.animate ? '' : 'timeline-snap'}`}>
    <GameBoard board={presentation.board} vehicles={presentation.vehicles} players={presentation.players} currentPlayerId={view.playerId}

      shake={presentation.shake} animate={presentation.animate} duration={presentation.duration} effects={presentation.effects} laserRotations={presentation.rotations} laserSpins={presentation.spins} activeShieldPlayerIds={view.continuous?.activeShieldPlayerIds ?? []} />
  </div>
}
