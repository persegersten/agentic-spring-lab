import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('Alice creates a game and Bob joins from a separate session', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/')
    await bob.goto('/')

    expect(alice.context()).not.toBe(bob.context())
    await expect(alice.getByRole('heading', { name: 'WRECKAGE' })).toBeVisible()
    await expect(bob.getByRole('heading', { name: 'WRECKAGE' })).toBeVisible()

    await alice.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    await expect(alice.getByRole('heading', { name: 'Anslut till spelet' })).toBeVisible()
    const gameId = await alice.getByLabel('Spel-id', { exact: true }).inputValue()
    expect(gameId).not.toBe('')

    await joinGame(alice, 'Alice')
    await expect(alice.getByText('Alice', { exact: true })).toBeVisible()
    await expect(bob.getByRole('heading', { name: 'Spelare anslutna' })).not.toBeVisible()

    await bob.getByLabel('Spel-id', { exact: true }).fill(gameId)
    await bob.getByRole('button', { name: 'Öppna spel', exact: true }).click()
    await expect(bob.getByRole('heading', { name: 'Anslut till spelet' })).toBeVisible()
    await joinGame(bob, 'Bob')

    for (const page of [alice, bob]) {
      await expect(page.getByText(`Spel ${gameId.slice(0, 8)}`, { exact: true })).toBeVisible()
      await expect(page.getByText('Alice, Bob', { exact: true })).toBeVisible()
    }
  })
})
