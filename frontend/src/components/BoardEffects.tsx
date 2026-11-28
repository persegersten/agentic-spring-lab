import { memo, type CSSProperties } from 'react'
import type { Board } from '../types/game'
import type { VisualEffect } from '../presentation/visualEffects'
import { EffectGroup } from './EffectGroup'

const angles = { NORTH: 0, EAST: 90, SOUTH: 180, WEST: 270 }
export const BoardEffects = memo(function BoardEffects({ board, events }: { board: Board; events: VisualEffect[] }) {
  if (!events.some(item => item.kind !== 'beam')) return null
  return <svg className="board-effects" viewBox={`0 0 ${board.width} ${board.height}`} preserveAspectRatio="none" aria-hidden="true">
    {events.filter(item => item.kind !== 'beam').map(effect => {
      const { kind, anchor } = effect
      return <EffectGroup key={effect.id} effect={effect} transform={`translate(${anchor.x + .5},${board.height - anchor.y - .5})`}>
        {kind === 'crash' ? <>
          <g transform={`rotate(${angles[effect.event.oldDirection]})`}><path className="effect-chassis" d="M-.21-.3h.42l.1.12v.36l-.1.12h-.42l-.1-.12v-.36z M-.1-.34v-.12h.2v.12"/></g>
          <circle className="effect-shock" r=".33"/>
        </> : kind === 'respawn' ? <>
          <path className="effect-deployment" d="M-.42-.18v-.22h.22M.2-.4h.22v.22M.42.18V.4H.2M-.2.4h-.22V.18"/>
          <path className="effect-scan" d="M-.32 0h.64"/>
          <circle className="effect-ring" r=".35"/>
        </> : kind === 'capture' ? <>
          <rect className="effect-capture-flash" x="-.44" y="-.44" width=".88" height=".88" rx=".06"/>
          <path className="effect-capture-bracket" d="M-.46-.25v-.2h.2M.26-.45h.2v.2M.46.25v.2h-.2M-.26.45h-.2v-.2"/>
          <circle className="effect-ring" r=".37"/>
        </> : kind === 'activation' ? <>
          <rect className="effect-activation-frame" x="-.4" y="-.4" width=".8" height=".8" rx=".04"/>
          <path className="effect-activation-rays" d="M0-.5v-.12M.5 0h.12M0 .5v.12M-.5 0h-.12"/>
        </> : kind === 'shield' ? <path className="effect-deployment" d="M0-.4 .34-.2v.34L0 .43-.34.14V-.2z"/>
          : <circle className={`effect-contact ${kind === 'blocked' ? 'effect-block' : ''}`} data-testid={effect.event.actionType === 'LASER' ? (kind === 'blocked' ? 'laser-block' : 'laser-impact') : undefined} r={kind === 'blocked' ? '.32' : '.36'}/>}
        {effect.fragments.map((fragment, index) => <g key={index} className={`effect-fragment ${fragment.plate ? 'fragment-plate' : 'fragment-spark'}`}
          style={{ '--fragment-x': `${fragment.x}px`, '--fragment-y': `${fragment.y}px`, '--fragment-angle': `${fragment.angle}deg` } as CSSProperties}>
          {fragment.plate ? <path d="M-.05-.025 .04-.04 .055.025-.035.045z"/> : <path d="M-.025 0h.11"/>}
        </g>)}
      </EffectGroup>
    })}
  </svg>
})
