import { expect, test } from '@playwright/test'

test('authoritative special-ability events have visible playback', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000010'
  const playerId = '20000000-0000-0000-0000-000000000010'
  const vehicle = { id: 'ability-vehicle', playerId, x: 1, y: 1, direction: 'NORTH', status: 'ACTIVE', damage: 0, rocketAmmo: 0, primaryWeapon: 'LASER', specialAbility: 'SHIELD' }
  const event = (sequence, type, oldPosition, newPosition, actionType) => ({
    sequence, type, playerId, vehicleId: vehicle.id, sourcePlayerId: playerId,
    sourceVehicleId: vehicle.id, oldPosition, newPosition,
    oldDirection: 'NORTH', newDirection: 'NORTH', actionType,
  })
  const playback = [
    event(1, 'SHIELD_ACTIVATED', { x: 1, y: 1 }, { x: 1, y: 1 }, 'SHIELD'),
    { ...event(2, 'DAMAGE_PREVENTED', { x: 1, y: 1 }, { x: 1, y: 1 }, 'SHIELD'), oldDamage: 0, newDamage: 0, damageDelta: 1 },
    event(3, 'ANCHOR_ACTIVATED', { x: 1, y: 1 }, { x: 1, y: 1 }, 'ANCHOR'),
    event(4, 'PUSH_BLOCKED', { x: 1, y: 1 }, { x: 1, y: 1 }, 'ANCHOR'),
    event(5, 'TURBO_ACTIVATED', { x: 1, y: 1 }, { x: 1, y: 1 }, 'TURBO'),
    event(6, 'MOVE', { x: 1, y: 1 }, { x: 1, y: 2 }, 'TURBO'),
    event(7, 'SIDE_STEP', { x: 1, y: 2 }, { x: 0, y: 2 }, 'SIDE_STEP_LEFT'),
  ]
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
    configuration: { maxPlayers: 2, programSize: 1, planningTimeoutSeconds: 30, joinTimeoutSeconds: 30 },
    players: [{ id: playerId, name: 'Per', score: 0, visitedCheckpoints: [], crashes: 0 }],
    board: { width: 4, height: 4, walls: [], pits: [], checkpoints: [], spawnPoints: [], conveyors: [], rotators: [], controlPoints: [] },
    vehicles: [{ ...vehicle, x: 0, y: 2 }], placements: [],
    round: { state: { number: 1, phase: 'PLAYBACK', ready: { [playerId]: true }, initiative: [playerId], initialVehicles: [vehicle], initialScores: { [playerId]: 0 }, startEvents: [], playback }, program: [], scheduledAction: null },
  }
  await page.clock.install()
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))

  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('playback-event')).toHaveCount(7)
  await page.clock.runFor(1)
  await expect(page.getByTestId('shield-effect')).toBeVisible()
  await page.clock.runFor(450)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'DAMAGE_PREVENTED')
  await expect(page.getByTestId('shield-effect')).toBeVisible()
  await page.clock.runFor(450)
  await expect(page.getByTestId('anchor-effect')).toBeVisible()
  await page.clock.runFor(450)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'PUSH_BLOCKED')
  await expect(page.getByTestId('anchor-effect')).toBeVisible()
  await page.clock.runFor(900)
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-ability-effect', 'TURBO')
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-y', '2')
  await page.clock.runFor(250)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'SIDE_STEP')
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-ability-effect', 'SIDE_STEP_LEFT')
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x', '0')
})
