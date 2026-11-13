import type { Placement, Player } from '../types/game'

export function ScoreTable({ players, scores, placements = [], compact = false, currentPlayerId }: { players: Player[]; scores?: Record<string, number>; placements?: Placement[]; compact?: boolean; currentPlayerId?: string }) {
  const placementByPlayer = Object.fromEntries(placements.map(result => [result.playerId, result]))
  const winnerCount = placements.filter(result => result.winner).length
  const rows = players.map(player => ({ ...player, shownScore: scores?.[player.id] ?? player.score }))
    .sort((left, right) => (placementByPlayer[left.id]?.placement ?? Number.MAX_SAFE_INTEGER)
      - (placementByPlayer[right.id]?.placement ?? Number.MAX_SAFE_INTEGER)
      || right.shownScore - left.shownScore || left.name.localeCompare(right.name))
  if (compact) return <section className="panel score-table compact-scores" aria-label="Poängställning" data-testid={placements.length ? 'final-standings' : 'score-table'}>
    <h2>{placements.length ? 'Slutpoäng' : 'Poäng'}{winnerCount > 1 && <span data-testid="draw-result"> · Delad vinst</span>}</h2>
    <ol className="compact-score-list">{rows.map(player => <li key={player.id}
      className={player.id === currentPlayerId ? 'score-own-player' : ''}
      data-testid={placements.length ? 'final-standing-row' : 'score-row'} data-player-id={player.id}
      title={`${player.name} · ${player.shownScore} poäng · ${player.capturedCheckpoints ?? player.visitedCheckpoints?.length ?? 0} checkpoints · ${player.crashes ?? 0} krascher`}>
      {placementByPlayer[player.id] && <span data-testid="player-placement">{placementByPlayer[player.id].placement}</span>}
      {placementByPlayer[player.id]?.winner && <span aria-label="Vinnare">★</span>}
      <span className="score-player-name">{player.name}</span><strong data-testid="player-score">{player.shownScore}</strong>
    </li>)}</ol>
  </section>
  return <section className="panel score-table" aria-label="Poängställning" data-testid={placements.length ? 'final-standings' : 'score-table'}>
    <h2>{placements.length ? 'Final scores' : 'Poäng'}</h2>
    {winnerCount > 1 && <p data-testid="draw-result">Draw</p>}
    <table><thead><tr>{placements.length > 0 && <th>Placering</th>}<th>Spelare</th><th>Poäng</th><th>Checkpoints</th><th>Krascher</th></tr></thead>
      <tbody>{rows.map(player => <tr key={player.id} data-testid={placements.length ? 'final-standing-row' : 'score-row'} data-player-id={player.id}>
        {placements.length > 0 && <td data-testid="player-placement">{placementByPlayer[player.id]?.placement}</td>}<td>{player.name}</td><td data-testid="player-score">{player.shownScore}</td><td>{player.capturedCheckpoints ?? player.visitedCheckpoints?.length ?? 0}</td><td>{player.crashes ?? 0}</td>
      </tr>)}</tbody></table>
  </section>
}
