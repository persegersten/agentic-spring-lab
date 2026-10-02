import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, withPlayerPages } from './player-pages.mjs'

test('all players see the same server-owned game board in planning', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')

    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')

    for (const page of [per, alice]) {
      await expect(page.getByTestId('game-board')).toBeVisible()
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-width', '10')
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-height', '10')
      await expect(page.getByTestId('board-pit')).toHaveCount(1)
      await expect(page.getByTestId('board-pit')).toHaveAttribute('data-x', '4')
      await expect(page.getByTestId('board-pit')).toHaveAttribute('data-y', '5')
      await expect(page.getByTestId('board-wall')).toHaveCount(1)
      await expect(page.getByTestId('board-wall')).toHaveAttribute('data-x', '0')
      await expect(page.getByTestId('board-wall')).toHaveAttribute('data-y', '0')
      await expect(page.getByTestId('board-wall')).toHaveAttribute('data-direction', 'NORTH')
      await expect(page.getByTestId('round-number')).toHaveText('Round 1')
      await expect(page.getByTestId('game-phase')).toHaveText('Fas PLANNING')
      await expect(page.getByTestId('player-vehicle')).toHaveCount(2)
      await expect(page.getByTestId('player-vehicle').filter({ hasText: 'Per' })).toHaveCount(1)
      await expect(page.getByTestId('player-vehicle').filter({ hasText: 'Alice' })).toHaveCount(1)
    }

    const positions = async page => page.getByTestId('player-vehicle').evaluateAll(vehicles =>
      vehicles.map(vehicle => ({
        playerId: vehicle.dataset.playerId,
        playerName: vehicle.dataset.playerName,
        x: vehicle.dataset.x,
        y: vehicle.dataset.y,
        direction: vehicle.dataset.direction,
      })).sort((left, right) => left.playerId.localeCompare(right.playerId))
    )

    expect(await positions(per)).toEqual(await positions(alice))
  })
})

test('vehicle arrow tips point in the server forward direction', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000001'
  const playerId = '20000000-0000-0000-0000-000000000001'
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7, createdAt: '2026-01-01T00:00:00Z', joinDeadline: '2026-01-01T00:05:00Z',
    configuration: { maxPlayers: 2, programSize: 3, planningTimeoutSeconds: 120, joinTimeoutSeconds: 300 },
    players: [
      { id: playerId, name: 'North' },
      { id: 'player-east', name: 'East' },
      { id: 'player-south', name: 'South' },
      { id: 'player-west', name: 'West' },
    ],
    board: { width: 5, height: 5, walls: [{ cell: { x: 1, y: 1 }, direction: 'NORTH' }], pits: [] },
    vehicles: [
      { id: 'vehicle-north', playerId, x: 1, y: 1, direction: 'NORTH', status: 'ACTIVE' },
      { id: 'vehicle-east', playerId: 'player-east', x: 3, y: 1, direction: 'EAST', status: 'ACTIVE' },
      { id: 'vehicle-south', playerId: 'player-south', x: 3, y: 3, direction: 'SOUTH', status: 'ACTIVE' },
      { id: 'vehicle-west', playerId: 'player-west', x: 1, y: 3, direction: 'WEST', status: 'ACTIVE' },
    ],
    round: null,
  }
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))

  await page.goto(`/game/${gameId}`)

  await expect(page.getByTestId('board-wall')).toHaveClass(/wall-north/)

  const northArrow = page.getByTestId('player-vehicle').filter({ hasText: 'North' }).locator('.vehicle-direction')
  const eastArrow = page.getByTestId('player-vehicle').filter({ hasText: 'East' }).locator('.vehicle-direction')
  const southArrow = page.getByTestId('player-vehicle').filter({ hasText: 'South' }).locator('.vehicle-direction')
  const westArrow = page.getByTestId('player-vehicle').filter({ hasText: 'West' }).locator('.vehicle-direction')
  await expect(northArrow).toHaveCSS('transform', 'matrix(-1, 0, 0, -1, 0, 0)')
  await expect(eastArrow).toHaveCSS('transform', 'matrix(0, 1, -1, 0, 0, 0)')
  await expect(southArrow).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await expect(westArrow).toHaveCSS('transform', 'matrix(0, -1, 1, 0, 0, 0)')

  const northBox = await page.getByTestId('player-vehicle').filter({ hasText: 'North' }).boundingBox()
  const southBox = await page.getByTestId('player-vehicle').filter({ hasText: 'South' }).boundingBox()
  expect(southBox.y).toBeLessThan(northBox.y)
  await expect(page.getByTestId('player-vehicle').filter({ hasText: 'South' })).toHaveAttribute('data-y', '3')
  await expect(page.getByTestId('player-vehicle').filter({ hasText: 'North' })).toHaveAttribute('data-y', '1')
})

test('turn playback is shown from the vehicle perspective', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000002'
  const playerId = '20000000-0000-0000-0000-000000000002'
  const vehicle = { id: 'vehicle', playerId, x: 2, y: 2, direction: 'SOUTH', status: 'ACTIVE' }
  const turn = (sequence, oldDirection, newDirection) => ({
    sequence, type: 'TURN', playerId, vehicleId: vehicle.id, sourcePlayerId: playerId,
    sourceVehicleId: vehicle.id, oldPosition: { x: 2, y: 2 }, newPosition: { x: 2, y: 2 },
    oldDirection, newDirection,
  })
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 2,
    configuration: { maxPlayers: 1, programSize: 2, planningTimeoutSeconds: 30, joinTimeoutSeconds: 30 },
    players: [{ id: playerId, name: 'Per', score: 0 }],
    board: { width: 5, height: 5, walls: [], pits: [] },
    vehicles: [{ ...vehicle, direction: 'SOUTH' }],
    round: { state: {
      number: 1, phase: 'PLAYBACK', ready: { [playerId]: true }, initiative: [playerId],
      initialVehicles: [vehicle], initialScores: { [playerId]: 0 }, startEvents: [],
      playback: [turn(1, 'SOUTH', 'EAST'), turn(2, 'EAST', 'SOUTH')],
    }, program: ['TURN_LEFT', 'TURN_RIGHT'] },
  }

  await page.clock.install()
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
  await page.goto(`/game/${gameId}`)

  const renderedVehicle = page.getByTestId('player-vehicle')
  await page.clock.runFor(1)
  await expect(renderedVehicle).toHaveAttribute('data-direction', 'EAST')
  await expect(renderedVehicle.locator('.vehicle-direction')).toHaveCSS('transform', 'matrix(0, 1, -1, 0, 0, 0)')

  await page.clock.runFor(250)
  await expect(renderedVehicle).toHaveAttribute('data-direction', 'SOUTH')
  await expect(renderedVehicle.locator('.vehicle-direction')).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
})
