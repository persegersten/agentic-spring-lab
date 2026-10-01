import { expect, test } from '@playwright/test'

const gameId = '10000000-0000-0000-0000-000000000001'
const playerId = '20000000-0000-0000-0000-000000000001'
const otherId = '20000000-0000-0000-0000-000000000002'
const configuration = { maxPlayers: 2, programSize: 3, planningTimeoutSeconds: 30,
  joinTimeoutSeconds: 60, roundLimit: 6, checkpointScore: 2, crashPenalty: -1, pushCrashScore: 1 }
const board = { width: 5, height: 5, walls: [], pits: [], checkpoints: [], spawnPoints: [
  { position: { x: 0, y: 0 }, orientation: 'EAST' },
  { position: { x: 1, y: 0 }, orientation: 'SOUTH' },
] }
const players = [
  { id: playerId, name: 'Per', score: 7, visitedCheckpoints: ['a'], crashes: 2 },
  { id: otherId, name: 'Alice', score: 7, visitedCheckpoints: ['a'], crashes: 2 },
]

async function openPlayer(page, state) {
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
  await page.goto(`/game/${gameId}`)
}

test('finished game shows authoritative shared placements and scores', async ({ page }) => {
  await openPlayer(page, {
    id: gameId, playerId, status: 'FINISHED', configuration, players, board, vehicles: [], round: null,
    placements: [
      { playerId, placement: 1, score: 7, checkpointsVisited: 1, crashes: 2, winner: true },
      { playerId: otherId, placement: 1, score: 7, checkpointsVisited: 1, crashes: 2, winner: true },
    ],
  })

  await expect(page.getByRole('heading', { name: 'Spelet är slut' })).toBeVisible()
  await expect(page.getByText('Per och Alice delar segern.')).toBeVisible()
  await expect(page.getByTestId('player-placement')).toHaveText(['1', '1'])
  await expect(page.getByTestId('player-score')).toHaveText(['7', '7'])
})

test('crashed player is waiting for respawn and does not receive a program', async ({ page }) => {
  const crashed = { id: 'vehicle-1', playerId, x: -1, y: 0, direction: 'EAST', damage: 0,
    status: 'CRASHED', spawnPoint: { x: 0, y: 0 }, spawnOrientation: 'EAST' }
  const active = { id: 'vehicle-2', playerId: otherId, x: 0, y: 0, direction: 'SOUTH', damage: 0,
    status: 'ACTIVE', spawnPoint: { x: 1, y: 0 }, spawnOrientation: 'SOUTH' }
  await openPlayer(page, {
    id: gameId, playerId, status: 'RUNNING', configuration, players, board, vehicles: [crashed, active], placements: [],
    round: { program: [], state: { number: 2, phase: 'PLANNING', planningDeadline: '2099-01-01T00:00:00Z',
      ready: { [otherId]: false }, initiative: [otherId], initialVehicles: [active], initialScores: {},
      startEvents: [], playback: [] } },
  })

  await expect(page.getByText('Kraschad – väntar på respawn')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Du har kraschat' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Lås program' })).not.toBeVisible()
})
