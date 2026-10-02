import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, withPlayerPages } from './player-pages.mjs'

test('a player reloads the waiting lobby as the same player', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Max spelare', { exact: true }).fill('3')
  await page.getByRole('button', { name: 'Skapa spel', exact: true }).click()
  await joinGame(page, 'Per')
  const playerId = await page.getByTestId('player-vehicle').filter({ hasText: 'Per' })
    .getAttribute('data-player-id')

  await page.reload()

  await expect(page.getByRole('heading', { name: 'Spelare anslutna', exact: true })).toBeVisible()
  await expect(page.getByTestId('player-vehicle').filter({ hasText: 'Per' })).toBeVisible()
  await expect(page.getByTestId('player-vehicle').filter({ hasText: 'Per' }))
    .toHaveAttribute('data-player-id', playerId)
})

test('reload during planning restores map, round and private planning state', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')

    await expect(per.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    const perVehicle = per.getByTestId('player-vehicle').filter({ hasText: 'Per' })
    const playerId = await perVehicle.getAttribute('data-player-id')
    await per.locator('[data-testid="command-card"][data-command="TURN_LEFT"]').dblclick()
    await expect(per.getByRole('status')).toHaveCount(0)

    await per.reload()

    await expect(per.getByTestId('game-board')).toHaveAttribute('data-width', '10')
    await expect(per.getByTestId('board-pit')).toHaveAttribute('data-x', '4')
    await expect(per.getByTestId('round-number')).toHaveText('Round 1')
    await expect(per.getByTestId('game-phase')).toHaveText('Fas PLANNING')
    await expect(per.getByTestId('player-vehicle').filter({ hasText: 'Per' }))
      .toHaveAttribute('data-player-id', playerId)
    await expect(per.getByTestId('program-slot').nth(0)).toHaveAttribute('data-command', 'TURN_LEFT')
    await expect(per.getByTestId('program-slot').nth(0)).toHaveAttribute('data-filled', 'true')
  })
})

test('a temporary player disconnect does not change another players state', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await expect(alice.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    const positionsBefore = await alice.getByTestId('player-vehicle').evaluateAll(vehicles =>
      vehicles.map(vehicle => `${vehicle.dataset.playerId}:${vehicle.dataset.x}:${vehicle.dataset.y}`).sort())

    await per.context().setOffline(true)
    await alice.reload()

    await expect(alice.getByTestId('game-phase')).toHaveText('Fas PLANNING')
    await expect(alice.getByTestId('player-vehicle')).toHaveCount(2)
    const positionsAfter = await alice.getByTestId('player-vehicle').evaluateAll(vehicles =>
      vehicles.map(vehicle => `${vehicle.dataset.playerId}:${vehicle.dataset.x}:${vehicle.dataset.y}`).sort())
    expect(positionsAfter).toEqual(positionsBefore)
    await expect(alice.locator('[data-testid="command-card"][data-command="WAIT"]')).toBeEnabled()
  })
})

test('reload restores a locked private program', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await expect(per.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    await fillProgram(per)
    await expect(per.getByRole('button', { name: 'Program låst', exact: true })).toBeDisabled()

    await per.reload()

    await expect(per.getByRole('button', { name: 'Program låst', exact: true })).toBeDisabled()
    await expect(per.getByTestId('program-slot').nth(0)).toHaveAttribute('data-command', 'WAIT')
    await expect(per.getByTestId('program-slot').nth(0)).toHaveAttribute('data-filled', 'true')
  })
})

test('reload after automatic round transition does not advance twice', async ({ browser }) => {
  test.setTimeout(60_000)
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await expect(per.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    await fillProgram(per)
    await fillProgram(alice)
    await expect(per.getByTestId('round-number')).toHaveText('Round 2', { timeout: 20_000 })

    await per.reload()

    await expect(per.getByTestId('round-number')).toHaveText('Round 2')
    await expect(per.getByTestId('game-phase')).toHaveText('Fas PLANNING')
    await expect(per.getByTestId('player-vehicle')).toHaveCount(2)
    await expect(per.getByRole('button', { name: 'Starta nästa runda', exact: true })).not.toBeVisible()
    await per.waitForTimeout(2000)
    await expect(per.getByTestId('round-number')).toHaveText('Round 2')
  })
})
