import type { Board } from '../types/game'
import type { PresentedEvent } from '../presentation/timeline'

export function BoardEffects({ board, events }: { board: Board; events: PresentedEvent[] }) {
  return <svg className="board-effects" viewBox={`0 0 ${board.width} ${board.height}`} preserveAspectRatio="none" aria-hidden="true">
    {events.filter(item => !['WEAPON_FIRED', 'TURN'].includes(item.event.type)).map(item => {
      const { event } = item
      const checkpoint = event.type === 'SCORE_CHANGED' ? board.checkpoints.find(c => c.id === event.checkpointId) : undefined
      const position = checkpoint?.position ?? event.oldPosition
      const kind = event.type === 'SCORE_CHANGED' ? 'capture' : event.type === 'VEHICLE_RESPAWNED' ? 'respawn'
        : event.type === 'SHIELD_ACTIVATED' ? 'shield' : ['CRASH', 'CONVEYOR_CRASH'].includes(event.type) ? 'crash' : 'impact'
      const x = event.type === 'VEHICLE_RESPAWNED' ? event.newPosition.x : position.x
      const y = event.type === 'VEHICLE_RESPAWNED' ? event.newPosition.y : position.y
      return <g key={item.id} data-testid="board-effect" data-effect={kind} data-event-id={item.id}
        className={`effect-${kind}`} transform={`translate(${x + .5},${board.height - y - .5})`}>
        <circle className="effect-ring" r=".34" />
        {Array.from({ length: 6 }, (_, index) => {
          const angle = (index * 60 + event.sequence * 17) * Math.PI / 180
          const dx = Math.cos(angle), dy = Math.sin(angle)
          return <line className="effect-fragment" key={index} x1={dx * .19} y1={dy * .19} x2={dx * .46} y2={dy * .46} />
        })}
      </g>
    })}
  </svg>
}
