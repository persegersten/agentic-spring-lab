import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('a player adds bots and every connected lobby sees them', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/')
    await alice.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await alice.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(alice, 'Alice')
    await bob.goto(gameLink)
    await joinGame(bob, 'Bob')

    const addBot = alice.getByRole('button', { name: 'Add Bot', exact: true })
    await expect(addBot).toBeVisible()
    await expect(bob.getByRole('button', { name: 'Add Bot', exact: true })).not.toBeVisible()
    await addBot.click()
    await expect(alice.getByRole('listitem').filter({ hasText: 'Bot 1' })).toContainText('(Bot)')
    await expect(bob.getByRole('listitem').filter({ hasText: 'Bot 1' })).toContainText('(Bot)')
    await expect(alice.getByRole('listitem').filter({ hasText: 'Bot 1' })).toContainText('(Bot)')

    await addBot.click()
    await expect(alice.getByRole('listitem').filter({ hasText: 'Bot 2' })).toContainText('(Bot)')

    const gameId = gameLink.split('/').at(-2)
    const guestSession = await bob.evaluate(id => JSON.parse(localStorage.getItem(`wreckage-session:${id}`)), gameId)
    const rejected = await bob.request.post(`/games/${gameId}/bots`, {
      headers: { 'X-Player-Id': guestSession.playerId, 'X-Player-Token': guestSession.token },
    })
    expect(rejected.status()).toBe(403)
    expect((await rejected.json()).message).toBe('Only the host can add bots')

    await alice.reload()
    await expect(alice.getByRole('button', { name: 'Add Bot', exact: true })).toBeVisible()
  })
})

test('a full mixed lobby disables bot addition for the host', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
  await joinGame(page, 'Alice')
  const addBot = page.getByRole('button', { name: 'Add Bot', exact: true })

  for (let index = 0; index < 8; index++) await addBot.click()

  await expect(page.getByText('9 / 9 spelare', { exact: true })).toBeVisible()
  await expect(addBot).toBeDisabled()
})

test('one browser can play successive rounds against a lobby bot', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
  await joinGame(page, 'Alice')
  await page.getByRole('button', { name: 'Add Bot', exact: true }).click()
  await expect(page.getByRole('listitem').filter({ hasText: 'Bot 1' })).toContainText('(Bot)')
  await startGame(page)

  await expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
  await expect(page.getByTestId('player-ready-state').filter({ hasText: 'Bot 1' })).toContainText('Redo')
  await expect(page.getByRole('button', { name: 'Add Bot', exact: true })).not.toBeVisible()

  await fillProgram(page)
  await expect(page.getByRole('heading', { name: 'Uppspelning', exact: true }).first()).toBeVisible()
  await expect(page.getByTestId('round-number')).toHaveText('Round 2', { timeout: 30_000 })
  await expect(page.getByTestId('player-ready-state').filter({ hasText: 'Bot 1' })).toContainText('Redo')
})
