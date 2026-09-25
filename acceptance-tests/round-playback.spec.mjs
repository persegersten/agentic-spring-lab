import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('players receive and play the same server ordered event sequence', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:5173' })
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameId = await per.getByLabel('Spel-id', { exact: true }).inputValue()
    await joinGame(per, 'Per')

    await alice.goto('/')
    await alice.getByLabel('Spel-id', { exact: true }).fill(gameId)
    await alice.getByRole('button', { name: 'Öppna spel', exact: true }).click()
    await joinGame(alice, 'Alice')

    await expect(per.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    await per.getByRole('button', { name: 'Lås program', exact: true }).click()
    await alice.getByRole('button', { name: 'Lås program', exact: true }).click()

    for (const page of [per, alice]) {
      await expect(page.getByRole('heading', { name: 'Uppspelning', exact: true }).first()).toBeVisible()
      await expect(page.getByTestId('event-debug-view')).toBeVisible()
    }
    const perEvents = await per.getByTestId('round-event').allTextContents()
    const aliceEvents = await alice.getByTestId('round-event').allTextContents()
    expect(aliceEvents).toEqual(perEvents)
    expect(perEvents.some(event => event.includes('FIRE'))).toBe(true)
    const sequences = await per.getByTestId('round-event').evaluateAll(events =>
      events.map(event => Number(event.getAttribute('data-sequence'))))
    expect(sequences).toEqual(sequences.map((_, index) => index + 1))

    const displayedState = JSON.parse(await per.getByTestId('initial-game-state').textContent())
    expect(displayedState.board).toMatchObject({ width: 20, height: 20 })
    expect(displayedState.vehicles).toHaveLength(2)

    await per.getByRole('button', { name: 'Kopiera game-state', exact: true }).click()
    await expect(per.getByText('Game-state kopierat', { exact: true })).toBeVisible()
    const copiedState = await per.evaluate(() => navigator.clipboard.readText())
    expect(JSON.parse(copiedState)).toEqual(displayedState)
  })
})
