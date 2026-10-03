import { expect, test } from '@playwright/test'
import { joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('the host starts planning for every connected player', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob', 'charlie', 'dana'], async players => {
    const { alice, bob, charlie, dana } = players
    await alice.goto('/')
    await alice.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await alice.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(alice, 'Alice')

    for (const [page, name] of [[bob, 'Bob'], [charlie, 'Charlie'], [dana, 'Dana']]) {
      await page.goto(gameLink)
      await joinGame(page, name)
    }

    await expect(alice.getByRole('heading', { name: 'Spelare anslutna', exact: true })).toBeVisible()
    await expect(bob.getByRole('button', { name: 'Starta spelet', exact: true })).not.toBeVisible()
    await startGame(alice)

    await Promise.all([alice, bob, charlie, dana].map(page =>
      expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    ))
    for (const page of [alice, bob, charlie, dana]) {
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-width', '12')
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-height', '12')
      await expect(page.getByTestId('round-progress')).toContainText('Round 1 / 6')
    }
  })
})

test('a waiting lobby stays open until the host starts and then rejects late joins', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob', 'charlie'], async ({ alice, bob, charlie }) => {
    await alice.goto('/')
    await alice.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await alice.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(alice, 'Alice')
    await bob.goto(gameLink)
    await joinGame(bob, 'Bob')
    await alice.waitForTimeout(3500)
    await expect(alice.getByRole('heading', { name: 'Spelare anslutna', exact: true })).toBeVisible()
    await startGame(alice)

    await Promise.all([alice, bob].map(page =>
      expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible({ timeout: 20_000 })
    ))

    await charlie.goto(gameLink)
    await charlie.getByLabel('Spelarnamn', { exact: true }).fill('Charlie')
    await charlie.getByRole('button', { name: 'Gå med', exact: true }).click()
    await expect(charlie.getByRole('alert')).toHaveText('The lobby is closed')
  })
})
