import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

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
