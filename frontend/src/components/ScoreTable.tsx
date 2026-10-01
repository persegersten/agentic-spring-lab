import type { Player } from '../types/game'

export function ScoreTable({ players, scores }: { players: Player[]; scores?: Record<string, number> }) {
  const rows = players.map(player => ({ ...player, shownScore: scores?.[player.id] ?? player.score }))
    .sort((left, right) => right.shownScore - left.shownScore || left.name.localeCompare(right.name))
  return <section className="panel score-table" aria-label="Poängställning">
    <h2>Poäng</h2>
    <table><thead><tr><th>Spelare</th><th>Poäng</th><th>Checkpoints</th></tr></thead>
      <tbody>{rows.map(player => <tr key={player.id} data-testid="score-row" data-player-id={player.id}>
        <td>{player.name}</td><td data-testid="player-score">{player.shownScore}</td><td>{player.visitedCheckpoints?.length ?? 0}</td>
      </tr>)}</tbody></table>
  </section>
}
