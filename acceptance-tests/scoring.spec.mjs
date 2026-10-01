import { expect, test } from '@playwright/test'

test('score table follows authoritative score events and shows checkpoint progress', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000050'
  const playerId = '20000000-0000-0000-0000-000000000050'
  const vehicle = { id: 'vehicle-50', playerId, x: 1, y: 2, direction: 'EAST', status: 'ACTIVE' }
  const configuration = { maxPlayers: 1, joinTimeoutSeconds: 300, programSize: 3,
    planningTimeoutSeconds: 120, roundLimit: 6, checkpointScore: 2, crashPenalty: -1, pushCrashScore: 1 }
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 6, configuration,
    joinDeadline: '2026-01-01T00:05:00Z',
    players: [{ id: playerId, name: 'Per', score: 2, visitedCheckpoints: ['cp-1'] }],
    board: { width: 5, height: 5, walls: [], pits: [], checkpoints: [{ id: 'cp-1', position: { x: 2, y: 2 } }] },
    vehicles: [{ ...vehicle, x: 2 }],
    round: { state: { number: 1, phase: 'PLAYBACK', planningDeadline: '2026-01-01T00:02:00Z',
      ready: { [playerId]: true }, initiative: [playerId], initialVehicles: [vehicle], initialScores: { [playerId]: 0 },
      playback: [
        { sequence: 1, type: 'MOVE', playerId, vehicleId: vehicle.id, sourcePlayerId: playerId,
          sourceVehicleId: vehicle.id, oldPosition: { x: 1, y: 2 }, newPosition: { x: 2, y: 2 },
          oldDirection: 'EAST', newDirection: 'EAST' },
        { sequence: 2, type: 'SCORE_CHANGED', playerId, vehicleId: vehicle.id, sourcePlayerId: playerId,
          sourceVehicleId: vehicle.id, oldPosition: { x: 2, y: 2 }, newPosition: { x: 2, y: 2 },
          oldDirection: 'EAST', newDirection: 'EAST',
          oldScore: 0, newScore: 2, scoreDelta: 2, scoreReason: 'CHECKPOINT', checkpointId: 'cp-1' },
      ] }, program: [] },
  }
  await page.clock.install()
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))

  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('board-checkpoint')).toHaveAttribute('data-checkpoint-id', 'cp-1')
  await expect(page.getByTestId('player-score')).toHaveText('0')
  await page.clock.runFor(1)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'MOVE')
  await expect(page.getByTestId('player-score')).toHaveText('0')
  await page.clock.runFor(250)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'SCORE_CHANGED')
  await expect(page.getByTestId('player-score')).toHaveText('2')
  await expect(page.getByTestId('score-row')).toContainText('1')
})

test('control point is visible and its round-end event updates score after the final conveyor', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000051'
  const playerId = '20000000-0000-0000-0000-000000000051'
  const vehicle = { id: 'vehicle-51', playerId, x: 0, y: 1, direction: 'EAST', status: 'ACTIVE' }
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 6,
    configuration: { maxPlayers: 1, joinTimeoutSeconds: 300, programSize: 1,
      planningTimeoutSeconds: 120, roundLimit: 6, checkpointScore: 2, controlPointScore: 1,
      crashPenalty: -1, pushCrashScore: 1 },
    joinDeadline: '2026-01-01T00:05:00Z',
    players: [{ id: playerId, name: 'Per', score: 1, visitedCheckpoints: [] }],
    board: { width: 4, height: 3, walls: [], pits: [], checkpoints: [], spawnPoints: [],
      conveyors: [{ position: { x: 0, y: 1 }, direction: 'EAST' }], rotators: [],
      controlPoints: [{ x: 1, y: 1 }] },
    vehicles: [{ ...vehicle, x: 1 }],
    round: { state: { number: 1, phase: 'PLAYBACK', planningDeadline: '2026-01-01T00:02:00Z',
      ready: { [playerId]: true }, initiative: [playerId], initialVehicles: [vehicle],
      initialScores: { [playerId]: 0 }, startEvents: [], playback: [
        { sequence: 1, type: 'CONVEYOR_MOVE', playerId, vehicleId: vehicle.id, sourcePlayerId: playerId,
          sourceVehicleId: vehicle.id, oldPosition: { x: 0, y: 1 }, newPosition: { x: 1, y: 1 },
          oldDirection: 'EAST', newDirection: 'EAST' },
        { sequence: 2, type: 'SCORE_CHANGED', playerId, vehicleId: vehicle.id, sourcePlayerId: playerId,
          sourceVehicleId: vehicle.id, oldPosition: { x: 1, y: 1 }, newPosition: { x: 1, y: 1 },
          oldDirection: 'EAST', newDirection: 'EAST',
          oldScore: 0, newScore: 1, scoreDelta: 1, scoreReason: 'CONTROL_POINT' },
      ] }, program: [] },
  }
  await page.clock.install()
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))

  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('board-control-point')).toHaveAttribute('data-x', '1')
  await expect(page.getByTestId('board-control-point')).toHaveAttribute('data-y', '1')
  await expect(page.getByTestId('playback-event')).toHaveCount(2)
  await expect(page.getByTestId('playback-event').first()).toHaveAttribute('data-event-type', 'CONVEYOR_MOVE')
  await page.clock.runFor(251)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'SCORE_CHANGED')
  await expect(page.getByTestId('player-score')).toHaveText('1')
})
