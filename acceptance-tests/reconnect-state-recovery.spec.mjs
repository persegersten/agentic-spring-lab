import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('a player reloads the waiting lobby as the same player', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
  const joinedResponse = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().endsWith('/players'))
  await joinGame(page, 'Per')
  const player = await (await joinedResponse).json()
  await expect(page.getByRole('listitem')).toHaveText(['Per'])
  const gameLink = await page.getByLabel('Spellänk').inputValue()
  const restoredResponse = page.waitForResponse(response =>
    response.request().method() === 'GET' && response.url().endsWith(`/players/${player.id}`))

  await page.reload()

  expect((await (await restoredResponse).json()).playerId).toBe(player.id)
  await expect(page).toHaveURL(gameLink)
  await expect(page.getByRole('listitem')).toHaveText(['Per'])
  await expect(page.getByLabel('Spelarnamn')).not.toBeVisible()
  await expect(page.getByTestId('game-board')).not.toBeVisible()
  await expect(page.getByRole('button', { name: 'Starta spelet', exact: true })).toBeDisabled()
})

test('reload during planning restores map, round and private planning state', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await startGame(per)

    await expect(per.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    const perVehicle = per.getByTestId('player-vehicle').filter({ hasText: 'Per' })
    const playerId = await perVehicle.getAttribute('data-player-id')
    await per.locator('[data-testid="command-card"][data-command="TURN_LEFT"]').dblclick()
    await expect(per.getByRole('status')).toHaveCount(0)
    await per.getByTestId('action-type').selectOption('SHIELD')
    await expect(per.getByRole('status')).toHaveCount(0)
    await per.getByTestId('action-register').selectOption('1')
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
    await expect(per.getByTestId('selected-action')).toHaveText('Shield · programsteg 1')
  })
})

test('a temporary player disconnect does not change another players state', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await startGame(per)
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
    await per.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await startGame(per)
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
    await per.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await startGame(per)
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
