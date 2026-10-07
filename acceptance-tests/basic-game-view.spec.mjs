import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('all players see the same server-owned game board in planning', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')

    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await startGame(per)

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
    board: { mapId: 'test-map', mapName: 'Test Map', width: 5, height: 5,
      walls: [{ cell: { x: 1, y: 1 }, direction: 'NORTH' }], pits: [],
      obstacles: [{ x: 2, y: 2 }],
      checkpoints: [
        { id: 'CP1', order: 1, position: { x: 0, y: 4 } },
        { id: 'CP2', order: 2, position: { x: 1, y: 4 } },
        { id: 'CP3', order: 3, position: { x: 2, y: 4 } },
        { id: 'CP4', order: 4, position: { x: 3, y: 4 } },
      ], spawnPoints: [], conveyors: [], rotators: [], controlPoints: [] },
    vehicles: [
      { id: 'vehicle-north', playerId, x: 1, y: 1, direction: 'NORTH', status: 'ACTIVE' },
      { id: 'vehicle-east', playerId: 'player-east', x: 3, y: 1, direction: 'EAST', status: 'ACTIVE' },
      { id: 'vehicle-south', playerId: 'player-south', x: 3, y: 3, direction: 'SOUTH', status: 'ACTIVE' },
      { id: 'vehicle-west', playerId: 'player-west', x: 1, y: 3, direction: 'WEST', status: 'ACTIVE' },
    ],
    round: null,
  }
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))

  await page.goto(`/game/${gameId}`)

  await expect(page.getByTestId('board-wall')).toHaveClass(/wall-north/)
  await expect(page.getByTestId('game-board')).toHaveAttribute('data-width', '5')
  await expect(page.getByTestId('game-board')).toHaveAttribute('data-height', '5')
  await expect(page.getByTestId('board-obstacle')).toHaveAttribute('data-x', '2')
  await expect(page.getByTestId('board-checkpoint')).toHaveText(['1', '2', '3', '4'])

  const northArrow = page.getByTestId('player-vehicle').filter({ hasText: 'North' }).locator('.vehicle-direction')
  const eastArrow = page.getByTestId('player-vehicle').filter({ hasText: 'East' }).locator('.vehicle-direction')
  const southArrow = page.getByTestId('player-vehicle').filter({ hasText: 'South' }).locator('.vehicle-direction')
  const westArrow = page.getByTestId('player-vehicle').filter({ hasText: 'West' }).locator('.vehicle-direction')
  await expect(northArrow).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await expect(eastArrow).toHaveCSS('transform', 'matrix(0, 1, -1, 0, 0, 0)')
  await expect(southArrow).toHaveCSS('transform', 'matrix(-1, 0, 0, -1, 0, 0)')
  await expect(westArrow).toHaveCSS('transform', 'matrix(0, -1, 1, 0, 0, 0)')

  const northVehicle = page.getByTestId('player-vehicle').filter({ hasText: 'North' })
  const southVehicle = page.getByTestId('player-vehicle').filter({ hasText: 'South' })
  expect(await northVehicle.evaluate(el => el.offsetTop)).toBeGreaterThan(await southVehicle.evaluate(el => el.offsetTop))

  // Right turns increase the angle even across the full-circle boundary.
  for (const [direction, angle] of [['EAST', 90], ['SOUTH', 180], ['WEST', 270], ['NORTH', 360]]) {
    state.vehicles[0].direction = direction
    await expect(northArrow).toHaveAttribute('style', `transform: rotate(${angle}deg);`)
  }
  state.vehicles[0].direction = 'WEST'
  await expect(northArrow).toHaveAttribute('style', 'transform: rotate(270deg);')
})
