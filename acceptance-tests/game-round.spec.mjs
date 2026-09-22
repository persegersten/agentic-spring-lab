import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('players receive private five-card hands and share readiness only', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/')
    await alice.getByLabel('Max spelare', { exact: true }).fill('2')
    await alice.getByLabel('Kort per runda', { exact: true }).fill('5')
    await alice.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    await expect(alice.getByRole('heading', { name: 'Anslut till spelet' })).toBeVisible()
    const gameId = await alice.getByLabel('Spel-id', { exact: true }).inputValue()
    await joinGame(alice, 'Alice')

    await bob.goto('/')
    await bob.getByLabel('Spel-id', { exact: true }).fill(gameId)
    await bob.getByRole('button', { name: 'Öppna spel', exact: true }).click()
    await joinGame(bob, 'Bob')
    await expect(alice.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()

    for (const page of [alice, bob]) {
      await expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
      const hand = page.getByRole('list').filter({ has: page.getByRole('button', { name: /^Flytta/ }) })
      await expect(hand).toHaveCount(1)
      await expect(hand.getByRole('listitem')).toHaveCount(5)
    }

    const aliceCards = alice.locator('.cards').getByRole('listitem')
    const firstCard = await aliceCards.nth(0).locator('span').textContent()
    const secondCard = await aliceCards.nth(1).locator('span').textContent()
    await aliceCards.nth(0).getByRole('button', { name: /senare$/ }).click()
    await expect(aliceCards.nth(0).locator('span')).toHaveText(secondCard ?? '')
    await expect(aliceCards.nth(1).locator('span')).toHaveText(firstCard ?? '')

    await alice.getByRole('button', { name: 'Lås program', exact: true }).click()
    await expect(alice.getByRole('button', { name: 'Program låst', exact: true })).toBeDisabled()
    await expect(bob.getByRole('listitem').filter({ hasText: 'Alice' })).toHaveText('AliceRedo')
    await expect(bob.getByRole('button', { name: 'Lås program', exact: true })).toBeEnabled()
    await expect(bob.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    await expect(bob.locator('.cards').getByRole('listitem')).toHaveCount(5)
    await expect(bob.getByRole('heading', { name: 'Uppspelning', exact: true })).not.toBeVisible()
  })
})
