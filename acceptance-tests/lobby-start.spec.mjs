import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('a full lobby starts planning for every player', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob', 'charlie', 'dana'], async players => {
    const { alice, bob, charlie, dana } = players
    await alice.goto('/')
    await alice.getByLabel('Max spelare', { exact: true }).fill('4')
    await alice.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await alice.getByLabel('Spellänk', { exact: true }).inputValue()
    await expect(alice.getByTestId('join-countdown')).toBeVisible()
    await joinGame(alice, 'Alice')
    await expect(alice.getByTestId('join-countdown')).toBeVisible()

    for (const [page, name] of [[bob, 'Bob'], [charlie, 'Charlie'], [dana, 'Dana']]) {
      await page.goto(gameLink)
      await expect(page.getByTestId('join-countdown')).toBeVisible()
      await joinGame(page, name)
    }

    await Promise.all([alice, bob, charlie, dana].map(page =>
      expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    ))
  })
})

test('the server starts planning at the join deadline and rejects late joins', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob', 'charlie'], async ({ alice, bob, charlie }) => {
    await alice.goto('/')
    await alice.getByLabel('Max spelare', { exact: true }).fill('3')
    await alice.getByLabel('Anslutningstid', { exact: true }).fill('3')
    await alice.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await alice.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(alice, 'Alice')
    await bob.goto(gameLink)
    await joinGame(bob, 'Bob')
    await expect(alice.getByTestId('join-countdown')).toBeVisible()
    await expect(bob.getByTestId('join-countdown')).toBeVisible()

    await Promise.all([alice, bob].map(page =>
      expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible({ timeout: 10_000 })
    ))

    await charlie.goto(gameLink)
    await charlie.getByLabel('Spelarnamn', { exact: true }).fill('Charlie')
    await charlie.getByRole('button', { name: 'Gå med', exact: true }).click()
    await expect(charlie.getByRole('alert')).toHaveText('The lobby is closed')
  })
})
