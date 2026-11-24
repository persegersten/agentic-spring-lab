import type { Board, Player } from '../types/game'
import { flagOwnership } from './flagOwnership'

export function FlagStrip({ board, players }: { board: Board; players: Player[] }) {
  return <ol className="flag-strip" aria-label="Flaggägare" data-testid="flag-strip">
    {flagOwnership(board, players).map(flag => <li key={flag.id} data-testid="flag-status"
      data-checkpoint-id={flag.id} data-owner-id={flag.owner?.id}
      data-state={flag.owner ? 'owned' : flag.available ? 'available' : 'locked'}>
      <b>{flag.order}</b><span title={flag.owner?.name}>{flag.owner?.name ?? (flag.available ? 'Tillgänglig' : 'Låst')}</span>
    </li>)}
  </ol>
}
