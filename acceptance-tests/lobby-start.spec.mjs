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
    for (const page of [alice, bob]) {
      await expect(page).toHaveURL(/\/game\/[0-9a-f-]+\/lobby$/)
      await expect(page.getByTestId('game-board')).not.toBeVisible()
    }
    await startGame(alice)

    await Promise.all([alice, bob, charlie, dana].map(page =>
      expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    ))
    for (const page of [alice, bob, charlie, dana]) {
      await expect(page).toHaveURL(gameLink.replace(/\/lobby$/, ''))
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
    for (const page of [alice, bob]) {
      await expect(page).toHaveURL(/\/game\/[0-9a-f-]+\/lobby$/)
      await expect(page.getByTestId('game-board')).not.toBeVisible()
    }
    await startGame(alice)

    await Promise.all([alice, bob].map(page =>
      expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible({ timeout: 20_000 })
    ))

    await expect(alice.getByTestId('game-board')).toHaveAttribute('data-width', '10')
    await expect(bob.getByTestId('game-board')).toHaveAttribute('data-width', '10')
    await charlie.goto(gameLink)
    await expect(charlie.getByText('Spelet har redan startat och lobbyn är stängd.')).toBeVisible()
    await expect(charlie.getByLabel('Spelarnamn')).not.toBeVisible()
    const gameId = gameLink.split('/').at(-2)
    const rejected = await charlie.request.post(`/games/${gameId}/players`, { data: { name: 'Charlie' } })
    expect(rejected.ok()).toBe(false)
    expect((await rejected.json()).message).toBe('The lobby is closed')
    for (const page of [alice, bob]) {
      await expect(page).toHaveURL(new RegExp(`/game/${gameId}$`))
      await page.goto(gameLink)
      await expect(page).toHaveURL(new RegExp(`/game/${gameId}$`))
      await expect(page.getByTestId('game-board')).toBeVisible()
    }
  })
})

test('the host must join before starting even when two guests have joined', async ({ browser }) => {
  await withPlayerPages(browser, ['host', 'bob', 'charlie'], async ({ host, bob, charlie }) => {
    await host.goto('/')
    await host.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await host.getByLabel('Spellänk').inputValue()
    for (const [page, name] of [[bob, 'Bob'], [charlie, 'Charlie']]) {
      await page.goto(gameLink)
      await joinGame(page, name)
    }
    await expect(host.getByRole('listitem')).toHaveText(['Bob', 'Charlie'])
    await expect(host.getByRole('button', { name: 'Starta spelet', exact: true })).toBeDisabled()
    await joinGame(host, 'Alice')
    await expect(host.getByRole('button', { name: 'Starta spelet', exact: true })).toBeEnabled()
    await startGame(host)
    for (const page of [host, bob, charlie]) {
      await expect(page).toHaveURL(gameLink.replace(/\/lobby$/, ''))
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-width', '10')
    }
  })
})
