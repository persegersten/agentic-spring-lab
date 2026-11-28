import type { Board, Player } from '../types/game'
import { flagOwnership } from './flagOwnership'
import { useState, type CSSProperties } from 'react'
import type { FlagTransfer } from '../presentation/flagTransfers'

function TransferHighlight({ transfer }: { transfer: FlagTransfer }) {
  // Retain admission age across ordinary renders, matching board EffectGroup behavior.
  const [age] = useState(transfer.age)
  return <span className="flag-transfer" aria-hidden="true" data-testid="flag-transfer"
    data-event-id={transfer.id} data-motion={transfer.motion ? 'on' : 'off'}
    style={{ '--transfer-duration': `${transfer.until - transfer.at}ms`, '--transfer-delay': `${-age}ms` } as CSSProperties}/>
}

export function FlagStrip({ board, players, flagTransfers = [], currentPlayerId }: { board: Board; players: Player[]; flagTransfers?: FlagTransfer[]; currentPlayerId?: string }) {
  // Match the existing board palette: the local robot is amber, other robots are steel.
  const playerColour = (id: string) => id === currentPlayerId ? 'var(--amber)' : '#afbfc4'
  return <ol className="flag-strip" aria-label="Flaggägare" data-testid="flag-strip">
    {flagOwnership(board, players).map(flag => {
      const transfer = flagTransfers.find(item => item.event.checkpointId === flag.id)
      return <li key={flag.id} data-testid="flag-status"
      data-transferred={transfer ? 'true' : undefined}
      style={transfer ? { '--transfer-from': playerColour(transfer.event.playerId), '--transfer-to': playerColour(transfer.event.sourcePlayerId) } as CSSProperties : undefined}
      data-checkpoint-id={flag.id} data-owner-id={flag.owner?.id}
      data-state={flag.owner ? 'owned' : flag.available ? 'available' : 'locked'}>
      {transfer && <TransferHighlight key={transfer.id} transfer={transfer}/>}
      <b>{flag.order}</b><span className="flag-owner-name" title={flag.owner?.name}>{flag.owner?.name ?? (flag.available ? 'Tillgänglig' : 'Låst')}</span>
    </li>})}
  </ol>
}
