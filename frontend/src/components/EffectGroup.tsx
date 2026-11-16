import { useState, type CSSProperties, type ReactNode } from 'react'
import type { VisualEffect } from '../presentation/visualEffects'

/** Negative delay is captured on admission, so later renders don't restart or accelerate CSS. */
export function EffectGroup({ effect, children, className = '', transform }: {
  effect: VisualEffect; children: ReactNode; className?: string; transform?: string
}) {
  const [age] = useState(effect.age)
  return <g data-testid="board-effect" data-effect={effect.kind} data-event-id={effect.id}
    data-motion={effect.motion ? 'on' : 'off'} data-duration={effect.lifetime}
    className={`visual-effect effect-${effect.kind} ${className}`} transform={transform}
    style={{ '--fx-duration': `${effect.lifetime}ms`, '--fx-delay': `${-age}ms` } as CSSProperties}>
    {children}
  </g>
}
