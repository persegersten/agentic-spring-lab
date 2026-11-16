import { Icon } from './Icon'
import type { VisualEffect } from '../presentation/visualEffects'
import type { Placement, Player } from '../types/game'

export function ScoreTable({ players, placements = [], compact = false, currentPlayerId, checkpointCount, captureEffects = [] }: { players: Player[]; scores?: Record<string, number>; placements?: Placement[]; compact?: boolean; currentPlayerId?: string; checkpointCount?: number; captureEffects?: VisualEffect[] }) {
  const placementByPlayer = Object.fromEntries(placements.map(result => [result.playerId, result]))
  const winnerCount = placements.filter(result => result.winner).length
  const rows = players.map((player, joinIndex) => ({ ...player, joinIndex,
    flags: player.capturedCheckpoints ?? player.visitedCheckpoints?.length ?? 0 }))
    .sort((left, right) => (placementByPlayer[left.id]?.placement ?? Number.MAX_SAFE_INTEGER)
      - (placementByPlayer[right.id]?.placement ?? Number.MAX_SAFE_INTEGER)
      || right.flags - left.flags || left.joinIndex - right.joinIndex)
  const ownPlayer = rows.find(player=>player.id===currentPlayerId)
  if (compact) return <section className="panel score-table compact-scores" aria-label="Flaggställning" data-testid={placements.length ? 'final-standings' : 'score-table'}>
    <h2>{placements.length ? 'Slutställning · flaggor' : 'Flaggor'}{winnerCount > 1 && <span data-testid="draw-result"> · Delad vinst</span>}</h2>
    {ownPlayer && checkpointCount != null && <p className="checkpoint-progress" data-testid="checkpoint-progress">
      <span><Icon name="flag"/> Flaggor: {ownPlayer.flags} / {checkpointCount}</span>
      <span>Nästa: <b data-testid="next-checkpoint">{ownPlayer.nextCheckpoint ?? 'Alla tagna'}</b></span>
    </p>}
    <ol className="compact-score-list">{rows.map(player => <li key={player.id}
      className={player.id === currentPlayerId ? 'score-own-player' : ''}
      data-testid={placements.length ? 'final-standing-row' : 'score-row'} data-player-id={player.id}
      title={`${player.name} · ${player.flags} flaggor`}>
      {placementByPlayer[player.id] && <span data-testid="player-placement">{placementByPlayer[player.id].placement}</span>}
      {placementByPlayer[player.id]?.winner && <span aria-label="Vinnare"><Icon name="winner"/></span>}
      {captureEffects.filter(effect=>effect.event.playerId===player.id).slice(-1).map(effect=><span key={effect.id}
        className="capture-highlight" data-testid="capture-highlight" data-motion={effect.motion ? 'on' : 'off'} aria-hidden="true"/>)}
      <span className="score-player-name">{player.name}</span><strong data-testid="player-score">{player.flags}</strong>
    </li>)}</ol>
  </section>
  return <section className="panel score-table" aria-label="Flaggställning" data-testid={placements.length ? 'final-standings' : 'score-table'}>
    <h2>{placements.length ? 'Slutställning · flaggor' : 'Flaggor'}</h2>
    {winnerCount > 1 && <p data-testid="draw-result">Draw</p>}
    <table><thead><tr>{placements.length > 0 && <th>Placering</th>}<th>Spelare</th><th>Flaggor</th></tr></thead>
      <tbody>{rows.map(player => <tr key={player.id} data-testid={placements.length ? 'final-standing-row' : 'score-row'} data-player-id={player.id}>
        {placements.length > 0 && <td data-testid="player-placement">{placementByPlayer[player.id]?.placement}</td>}<td>{player.name}</td><td data-testid="player-score">{player.flags}</td>
      </tr>)}</tbody></table>
  </section>
}
