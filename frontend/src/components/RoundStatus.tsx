import { useEffect, useState } from 'react'
import type { Player, PublicRound } from '../types/game'

export function RoundStatus({ round, roundLimit, players, crashed = [] }: {
  round: PublicRound
  roundLimit: number
  players: Player[]
  crashed?: string[]
}) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 250)
    return () => window.clearInterval(id)
  }, [])
  const left = Math.max(0, Math.ceil((new Date(round.planningDeadline).getTime() - now) / 1000))
  const byId = Object.fromEntries(players.map(player => [player.id, player]))

  return <aside className="round-status panel" data-testid="round-status">
    <p className="eyebrow" data-testid="round-progress" data-round={round.number} data-round-limit={roundLimit}>
      <span data-testid="round-number">Round {round.number}</span> / <span data-testid="round-limit">{roundLimit}</span>
    </p>
    <h2>{round.phase === 'PLANNING' ? 'Planering' : round.phase === 'RESOLVING' ? 'Rundan beräknas' : 'Uppspelning'}</h2>
    <p data-testid="game-phase" data-phase={round.phase}>Fas {round.phase}</p>
    {round.phase === 'PLANNING' && <p data-testid="planning-countdown" data-seconds={left}>Planeringstid kvar: {left} sekunder</p>}
    <h3>Spelarstatus</h3>
    <ul data-testid="player-readiness">
      {players.map(player => {
        const state = crashed.includes(player.id) ? 'CRASHED' : round.ready[player.id] ? 'LOCKED' : 'PLANNING'
        const label = state === 'CRASHED' ? 'Kraschad – väntar på respawn' : state === 'LOCKED' ? 'Redo · program låst' : 'Väljer…'
        return <li key={player.id} data-testid="player-ready-state" data-player-id={player.id} data-ready-state={state}>
          <span>{player.name}</span><strong>{label}</strong>
        </li>
      })}
    </ul>
    <h3>Initiativ</h3>
    <ol className="initiative" data-testid="initiative-order">
      {round.initiative.map((playerId, index) => <li key={playerId} data-testid="initiative-player" data-player-id={playerId} data-position={index + 1}>
        {index + 1}. {byId[playerId]?.name ?? 'Okänd spelare'}
      </li>)}
    </ol>
  </aside>
}
