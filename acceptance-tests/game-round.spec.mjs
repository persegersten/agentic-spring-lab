import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('players lock private programs and watch the same three-card playback', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/')
    await alice.getByLabel('Max spelare', { exact: true }).fill('2')
    await alice.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    await expect(alice.getByRole('heading', { name: 'Anslut till spelet' })).toBeVisible()
    const gameId = await alice.getByLabel('Spel-id', { exact: true }).inputValue()
    await joinGame(alice, 'Alice')

    await bob.goto('/')
    await bob.getByLabel('Spel-id', { exact: true }).fill(gameId)
    await bob.getByRole('button', { name: 'Öppna spel', exact: true }).click()
    await joinGame(bob, 'Bob')
    await expect(alice.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()

    const programs = {}
    for (const [name, page] of [['Alice', alice], ['Bob', bob]]) {
      await expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
      const hand = page.getByRole('list').filter({ has: page.getByRole('button', { name: /^Flytta/ }) })
      await expect(hand).toHaveCount(1)
      await expect(hand.getByRole('listitem')).toHaveCount(3)
      programs[name] = await hand.getByRole('listitem').locator('span').allTextContents()
    }

    await alice.getByRole('button', { name: 'Lås program', exact: true }).click()
    await expect(alice.getByRole('button', { name: 'Program låst', exact: true })).toBeDisabled()
    await expect(bob.getByRole('listitem').filter({ hasText: 'Alice' })).toHaveText('AliceRedo')
    await expect(bob.getByRole('button', { name: 'Lås program', exact: true })).toBeEnabled()
    await expect(bob.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    await bob.getByRole('button', { name: 'Lås program', exact: true }).click()

    await Promise.all([alice, bob].map(async page => {
      await expect(page.getByRole('heading', { name: 'Uppspelning', exact: true })).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Programmera dina kommandon' })).not.toBeVisible()
      for (let step = 1; step <= 3; step++) {
        await expect(page.getByRole('heading', { name: `Kort ${step} av 3`, exact: true })).toBeVisible()
        for (const name of ['Alice', 'Bob']) {
          const command = programs[name][step - 1].replace('Sväng ', '')
          const label = command.charAt(0).toUpperCase() + command.slice(1)
          await expect(page.getByText(`${name}${label}`, { exact: true })).toBeVisible()
        }
      }
      await expect(page.getByRole('button', { name: 'Starta nästa runda', exact: true })).toBeVisible()
    }))
  })
})
