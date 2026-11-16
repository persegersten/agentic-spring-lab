import { GameBoard } from './GameBoard'
import type { PlayerGame } from '../types/game'
import type { useTickPresentation } from '../presentation/useTickPresentation'

export function ContinuousBoard({ view, presentation }: { view: PlayerGame; presentation: ReturnType<typeof useTickPresentation> }) {
  return <div className={`tick-board ${presentation.animate ? '' : 'timeline-snap'}`} style={presentation.style}>
    <GameBoard board={presentation.board} vehicles={presentation.vehicles} players={view.players} currentPlayerId={view.playerId}
      laserEvents={presentation.effects.filter(item => item.event.actionType === 'LASER' && ['WEAPON_FIRED','WEAPON_HIT','PUSH_BLOCKED'].includes(item.event.type)).map(item => item.event)}
      effects={presentation.effects} laserRotations={presentation.rotations} laserSpins={presentation.spins} activeShieldPlayerIds={view.continuous?.activeShieldPlayerIds ?? []} />
  </div>
}
