import type { Player, RoundEvent } from '../types/game'

export function RoundEventDebugView({ events, players }: { events: RoundEvent[]; players: Player[] }) {
  const names = Object.fromEntries(players.map(player => [player.id, player.name]))
  return <details className="event-debug" data-testid="event-debug-view" open>
    <summary>Eventsekvens (debug)</summary>
    <ol>{events.map(event => <li key={event.sequence} data-testid="round-event" data-sequence={event.sequence}>
      <code>#{event.sequence} {event.type} {names[event.playerId] ?? event.playerId} {event.type === 'TURN'
        ? `${event.oldDirection} → ${event.newDirection}`
        : event.type === 'DAMAGE' ? `${event.oldDamage} → ${event.newDamage} damage`
        : `(${event.oldPosition.x},${event.oldPosition.y}) → (${event.newPosition.x},${event.newPosition.y})`}</code>
    </li>)}</ol>
  </details>
}
