import { memo } from 'react'
import type { Board } from '../types/game'
import type { VisualEffect } from '../presentation/visualEffects'
import { EffectGroup } from './EffectGroup'

export const LaserEffects = memo(function LaserEffects({ board, events }: { board: Board; events: VisualEffect[] }) {
  if (!events.some(event => event.kind === 'beam')) return null
  return <svg className="laser-effects" viewBox={`0 0 ${board.width} ${board.height}`} preserveAspectRatio="none" aria-hidden="true">
    {events.filter(effect => effect.kind === 'beam').map(effect => {
      const { event } = effect
      const x1 = event.oldPosition.x + .5, y1 = board.height - event.oldPosition.y - .5
      const x2 = event.newPosition.x + .5, y2 = board.height - event.newPosition.y - .5
      return <EffectGroup key={effect.id} effect={effect} className="laser-shot">
        <g data-testid="laser-shot" data-sequence={event.sequence} data-start-x={event.oldPosition.x} data-start-y={event.oldPosition.y}
          data-end-x={event.newPosition.x} data-end-y={event.newPosition.y}>
          <line className="laser-glow" x1={x1} y1={y1} x2={x2} y2={y2}/>
          <line className="laser-beam" x1={x1} y1={y1} x2={x2} y2={y2}/>
          <line className="laser-core" x1={x1} y1={y1} x2={x2} y2={y2}/>
          <circle className="laser-muzzle" cx={x1} cy={y1} r=".11"/>
        </g>
      </EffectGroup>
    })}
  </svg>
})
