import type { Placement, Player } from '../types/game'

export function ScoreTable({ players, scores, placements = [] }: { players: Player[]; scores?: Record<string, number>; placements?: Placement[] }) {
  const placementByPlayer = Object.fromEntries(placements.map(result => [result.playerId, result]))
  const rows = players.map(player => ({ ...player, shownScore: scores?.[player.id] ?? player.score }))
    .sort((left, right) => (placementByPlayer[left.id]?.placement ?? Number.MAX_SAFE_INTEGER)
      - (placementByPlayer[right.id]?.placement ?? Number.MAX_SAFE_INTEGER)
      || right.shownScore - left.shownScore || left.name.localeCompare(right.name))
  return <section className="panel score-table" aria-label="Poängställning">
    <h2>Poäng</h2>
    <table><thead><tr>{placements.length > 0 && <th>Placering</th>}<th>Spelare</th><th>Poäng</th><th>Checkpoints</th><th>Krascher</th></tr></thead>
      <tbody>{rows.map(player => <tr key={player.id} data-testid="score-row" data-player-id={player.id}>
        {placements.length > 0 && <td data-testid="player-placement">{placementByPlayer[player.id]?.placement}</td>}<td>{player.name}</td><td data-testid="player-score">{player.shownScore}</td><td>{player.visitedCheckpoints?.length ?? 0}</td><td>{player.crashes ?? 0}</td>
      </tr>)}</tbody></table>
  </section>
}
