import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('players receive and play the same server ordered event sequence', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:5173' })
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    await expect(per.getByRole('heading', { name: 'Anslut till spelet' })).toBeVisible()
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
      await page.getByText('Eventsekvens (debug)', { exact: true }).click()
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

test('playback finishes quickly across polling, stays paused and can replay', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000001'
  const playerId = '20000000-0000-0000-0000-000000000001'
  const vehicle = { id: 'vehicle-1', playerId, x: 0, y: 0, direction: 'EAST', damage: 0 }
  const playback = Array.from({ length: 12 }, (_, index) => ({
    sequence: index + 1, type: 'MOVE', playerId, vehicleId: vehicle.id,
    sourcePlayerId: playerId, sourceVehicleId: vehicle.id,
    oldPosition: { x: index, y: 0 }, newPosition: { x: index + 1, y: 0 },
    oldDirection: 'EAST', newDirection: 'EAST', oldDamage: 0, newDamage: 0,
  }))
  let polls = 0
  const state = {
    id: gameId, playerId, status: 'RUNNING',
    configuration: { maxPlayers: 1, cardsPerRound: 3, planningTimeoutSeconds: 120, joinTimeoutSeconds: 300 },
    players: [{ id: playerId, name: 'Per' }],
    board: { width: 20, height: 20, walls: [], pits: [] },
    vehicles: [{ ...vehicle, x: 12 }],
    round: { state: { number: 1, phase: 'PLAYBACK', ready: { [playerId]: true }, initialVehicles: [vehicle], playback }, hand: [] },
  }
  await page.clock.install()
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => {
    polls++
    return route.fulfill({ json: state })
  })
  await page.goto(`/game/${gameId}`)
  await expect(page.getByRole('button', { name: 'Pausa', exact: true })).toBeVisible()
  await expect(page.getByTestId('initial-game-state')).not.toBeVisible()
  await page.clock.runFor(1000)
  await page.getByRole('button', { name: 'Pausa', exact: true }).click()
  const pausedSequence = await page.getByTestId('current-playback-event').getAttribute('data-sequence')
  await page.clock.runFor(3000)
  await expect.poll(() => polls).toBeGreaterThanOrEqual(3)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-sequence', pausedSequence)
  await page.getByRole('button', { name: 'Fortsätt', exact: true }).click()
  await page.clock.runFor(2500)
  await expect(page.getByRole('button', { name: 'Starta nästa runda' })).toBeVisible()
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x', '12')
  await page.clock.runFor(3000)
  await expect(page.getByRole('heading', { name: 'Uppspelningen är klar' })).toBeVisible()
  await page.getByRole('button', { name: 'Spela om', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Starta nästa runda' })).not.toBeVisible()
  await expect(page.getByRole('button', { name: 'Pausa', exact: true })).toBeVisible()
  await page.clock.runFor(3200)
  await expect(page.getByRole('heading', { name: 'Uppspelningen är klar' })).toBeVisible()
})
