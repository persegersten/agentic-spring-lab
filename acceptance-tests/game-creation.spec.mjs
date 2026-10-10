import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('Alice creates a game and Bob joins from a separate session', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/')
    await bob.goto('/')

    expect(alice.context()).not.toBe(bob.context())
    await expect(alice.getByRole('heading', { name: 'WRECKAGE' })).toBeVisible()
    await expect(bob.getByRole('heading', { name: 'WRECKAGE' })).toBeVisible()

    await expect(alice.getByLabel('Max spelare')).not.toBeVisible()
    await alice.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    await expect(alice.getByRole('heading', { name: 'Spel-lobby' })).toBeVisible()
    const gameLink = await alice.getByLabel('Spellänk', { exact: true }).inputValue()
    expect(gameLink).toMatch(/\/game\/[0-9a-f-]+\/lobby$/)
    const gameId = gameLink.split('/').at(-2)

    await expect(alice.getByRole('button', { name: 'Starta spelet', exact: true })).toBeDisabled()
    await expect(alice).toHaveURL(gameLink)
    await joinGame(alice, 'Alice')
    await expect(alice.getByTestId('game-lobby')).toBeVisible()
    await expect(alice.getByTestId('game-board')).not.toBeVisible()
    await expect(alice.getByLabel('Spellänk')).toHaveValue(gameLink)
    await expect(bob.getByRole('heading', { name: 'Spelare anslutna' })).not.toBeVisible()

    await bob.goto(gameLink)
    await expect(bob.getByRole('heading', { name: 'Spel-lobby' })).toBeVisible()
    await bob.getByLabel('Spelarnamn', { exact: true }).fill('Alice')
    await bob.getByRole('button', { name: 'Gå med', exact: true }).click()
    await expect(bob.getByRole('alert')).toHaveText('Nickname is already in use')
    await joinGame(bob, 'Bob')

    for (const page of [alice, bob]) {
      await expect(page.getByText(`Spel ${gameId.slice(0, 8)}`, { exact: true })).toBeVisible()
      await expect(page.getByTestId('game-lobby').getByRole('listitem')).toHaveText(['Alice (Human)', 'Bob (Human)'])
    }
  })
})

test('the lobby stays at the root while two games remain active in one browser', async ({ browser }) => {
  const context = await browser.newContext()
  const firstGame = await context.newPage()
  const secondGame = await context.newPage()

  try {
    await firstGame.goto('/')
    await firstGame.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const firstGameLink = await firstGame.getByLabel('Spellänk', { exact: true }).inputValue()
    const firstGameId = firstGameLink.split('/').at(-2)
    await joinGame(firstGame, 'Första spelaren')

    await secondGame.goto('/')
    await expect(secondGame.getByRole('button', { name: 'Bjud in till nytt spel', exact: true })).toBeVisible()
    await expect(secondGame.getByTestId('game-page')).not.toBeVisible()
    await secondGame.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const secondGameLink = await secondGame.getByLabel('Spellänk', { exact: true }).inputValue()
    const secondGameId = secondGameLink.split('/').at(-2)
    expect(secondGameId).not.toBe(firstGameId)
    await joinGame(secondGame, 'Andra spelaren')

    await firstGame.reload()
    await secondGame.reload()

    await expect(firstGame.getByText(`Spel ${firstGameId.slice(0, 8)}`, { exact: true })).toBeVisible()
    await expect(firstGame.getByRole('listitem')).toHaveText(['Första spelaren (Human)'])
    await expect(firstGame.getByRole('button', { name: 'Starta spelet', exact: true })).toBeDisabled()
    await expect(secondGame.getByText(`Spel ${secondGameId.slice(0, 8)}`, { exact: true })).toBeVisible()
    await expect(secondGame.getByRole('listitem')).toHaveText(['Andra spelaren (Human)'])
    await expect(secondGame.getByRole('button', { name: 'Starta spelet', exact: true })).toBeDisabled()

    await firstGame.goto('/')
    await expect(firstGame.getByRole('button', { name: 'Bjud in till nytt spel', exact: true })).toBeVisible()
    await expect(firstGame.getByTestId('game-page')).not.toBeVisible()
  } finally {
    await context.close()
  }
})
