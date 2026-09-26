import { useState } from 'react'
import type { Board, Player, RoundEvent, Vehicle } from '../types/game'

export function RoundEventDebugView({ board, events, initialVehicles, players }: { board: Board; events: RoundEvent[]; initialVehicles: Vehicle[]; players: Player[] }) {
  const [copyStatus, setCopyStatus] = useState<'idle' | 'copied' | 'failed'>('idle')
  const names = Object.fromEntries(players.map(player => [player.id, player.name]))
  const initialState = JSON.stringify({ board, vehicles: initialVehicles }, null, 2)

  async function copyInitialState() {
    try {
      await navigator.clipboard.writeText(initialState)
      setCopyStatus('copied')
    } catch {
      setCopyStatus('failed')
    }
  }

  return <details className="event-debug" data-testid="event-debug-view">
    <summary>Eventsekvens (debug)</summary>
    <div className="debug-state-heading">
      <h3>Game-state före sekvensen</h3>
      <button className="secondary" type="button" onClick={() => void copyInitialState()}>Kopiera game-state</button>
    </div>
    <p className="debug-copy-status" aria-live="polite">{copyStatus === 'copied' ? 'Game-state kopierat' : copyStatus === 'failed' ? 'Kunde inte kopiera game-state' : ''}</p>
    <pre data-testid="initial-game-state"><code>{initialState}</code></pre>
    <ol>{events.map(event => <li key={event.sequence} data-testid="round-event" data-sequence={event.sequence}>
      <code>#{event.sequence} {event.type} {names[event.playerId] ?? event.playerId} {event.type === 'TURN'
        ? `${event.oldDirection} → ${event.newDirection}`
        : event.type === 'DAMAGE' ? `${event.oldDamage} → ${event.newDamage} damage`
        : `(${event.oldPosition.x},${event.oldPosition.y}) → (${event.newPosition.x},${event.newPosition.y})`}</code>
    </li>)}</ol>
  </details>
}
