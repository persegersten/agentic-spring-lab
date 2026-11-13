import type { Board, Direction, RoundEvent } from '../types/game'

const offsets: Record<Direction, [number, number]> = { NORTH: [0, -.4], EAST: [.4, 0], SOUTH: [0, .4], WEST: [-.4, 0] }

export function LaserEffects({ board, events }: { board: Board; events: RoundEvent[] }) {
  if (!events.length) return null
  return <svg className="laser-effects" viewBox={`0 0 ${board.width} ${board.height}`} preserveAspectRatio="none" aria-hidden="true">
    {events.map(event => {
      const x1 = event.oldPosition.x + .5
      const y1 = board.height - event.oldPosition.y - .5
      let x2 = event.newPosition.x + .5
      let y2 = board.height - event.newPosition.y - .5
      if (event.type === 'WEAPON_FIRED') {
        if (x1 === x2 && y1 === y2) {
          const [dx, dy] = offsets[event.oldDirection]
          x2 += dx; y2 += dy
        }
        return <g key={event.sequence} className="laser-shot" data-testid="laser-shot" data-sequence={event.sequence}
          data-start-x={event.oldPosition.x} data-start-y={event.oldPosition.y}
          data-end-x={event.newPosition.x} data-end-y={event.newPosition.y}>
          <line className="laser-glow" x1={x1} y1={y1} x2={x2} y2={y2} />
          <line className="laser-beam" x1={x1} y1={y1} x2={x2} y2={y2} />
          <line className="laser-core" x1={x1} y1={y1} x2={x2} y2={y2} />
          <circle className="laser-muzzle" cx={x1} cy={y1} r=".13" />
          <circle className="laser-spark" cx={x2} cy={y2} r=".16" />
        </g>
      }
      return <g key={event.sequence} transform={`translate(${x1},${y1})`}>
        <circle data-testid={event.type === 'PUSH_BLOCKED' ? 'laser-block' : 'laser-impact'}
          className={event.type === 'PUSH_BLOCKED' ? 'laser-block' : 'laser-impact'} r=".38" />
      </g>
    })}
  </svg>
}
