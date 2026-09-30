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
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-width', '20')
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-height', '20')
      await expect(page.getByTestId('board-pit')).toHaveCount(1)
      await expect(page.getByTestId('board-pit')).toHaveAttribute('data-x', '4')
      await expect(page.getByTestId('board-pit')).toHaveAttribute('data-y', '5')
      await expect(page.getByTestId('round-number')).toHaveText('Runda 1')
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
    id: gameId, playerId, status: 'RUNNING', createdAt: '2026-01-01T00:00:00Z', joinDeadline: '2026-01-01T00:05:00Z',
    configuration: { maxPlayers: 2, programSize: 3, planningTimeoutSeconds: 120, joinTimeoutSeconds: 300 },
    players: [
      { id: playerId, name: 'North' },
      { id: 'player-east', name: 'East' },
      { id: 'player-south', name: 'South' },
      { id: 'player-west', name: 'West' },
    ],
    board: { width: 5, height: 5, walls: [], pits: [] },
    vehicles: [
      { id: 'vehicle-north', playerId, x: 1, y: 1, direction: 'NORTH', damage: 0 },
      { id: 'vehicle-east', playerId: 'player-east', x: 3, y: 1, direction: 'EAST', damage: 0 },
      { id: 'vehicle-south', playerId: 'player-south', x: 3, y: 3, direction: 'SOUTH', damage: 0 },
      { id: 'vehicle-west', playerId: 'player-west', x: 1, y: 3, direction: 'WEST', damage: 0 },
    ],
    round: null,
  }
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))

  await page.goto(`/game/${gameId}`)

  const northArrow = page.getByTestId('player-vehicle').filter({ hasText: 'North' }).locator('.vehicle-direction')
  const eastArrow = page.getByTestId('player-vehicle').filter({ hasText: 'East' }).locator('.vehicle-direction')
  const southArrow = page.getByTestId('player-vehicle').filter({ hasText: 'South' }).locator('.vehicle-direction')
  const westArrow = page.getByTestId('player-vehicle').filter({ hasText: 'West' }).locator('.vehicle-direction')
  await expect(northArrow).toHaveCSS('transform', 'matrix(-1, 0, 0, -1, 0, 0)')
  await expect(eastArrow).toHaveCSS('transform', 'matrix(0, 1, -1, 0, 0, 0)')
  await expect(southArrow).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await expect(westArrow).toHaveCSS('transform', 'matrix(0, -1, 1, 0, 0, 0)')
})
