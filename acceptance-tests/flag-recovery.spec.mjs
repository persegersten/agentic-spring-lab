import { expect, test } from '@playwright/test'
import { recoveryBackend, recoveryBackendUrl } from './recovery-backend.mjs'

test.setTimeout(240_000)
let backend
test.beforeAll(async () => {
  backend = recoveryBackend()
  await backend.start()
})
test.afterAll(async ({}, info) => {
  if (backend) {
    try { await backend.stop() }
    finally { await info.attach('recovery-backend', { path: backend.logPath, contentType: 'text/plain' }) }
  }
})

test('ownership survives crashes, respawn, reconnect, reload and a real backend restart', async ({ browser, request }) => {
  const contexts = []
  const pages = []
  try {
    const created = await request.post(`${recoveryBackendUrl}/games/test-fixtures/flags?scenario=RECOVERY`)
    expect(created.ok(), await created.text()).toBeTruthy()
    const fixture = await created.json()
    const [alice, bob] = fixture.sessions
    for (const session of fixture.sessions) {
      const context = await browser.newContext()
      contexts.push(context)
      await context.addInitScript(value => localStorage.setItem(`wreckage-session:${value.gameId}`, JSON.stringify(value)), session)
      const page = await context.newPage()
      await page.goto(`/game/${fixture.gameId}`)
      await expect(page.getByTestId('flag-strip')).toBeVisible()
      pages.push(page)
    }
    async function tick(count) {
      const response = await request.post(`${recoveryBackendUrl}/games/test-fixtures/flags/${fixture.gameId}/ticks?count=${count}`)
      expect(response.ok(), await response.text()).toBeTruthy()
      return response.json()
    }
    async function command(index, label) {
      await Promise.all([
        pages[index].waitForResponse(response => response.url().endsWith('/commands') && response.status() === 200),
        pages[index].getByRole('button', { name: label, exact: true }).click(),
      ])
      return tick(3)
    }
    async function owners() {
      for (const page of pages) {
        const status = id => page.locator(`[data-testid="flag-status"][data-checkpoint-id="${id}"]`)
        await expect(status('CP1')).toHaveAttribute('data-owner-id', alice.playerId)
        await expect(status('CP1')).toContainText('Alice')
        await expect(status('CP2')).toHaveAttribute('data-owner-id', bob.playerId)
        await expect(status('CP2')).toContainText('Bob')
        await expect(status('CP3')).toHaveAttribute('data-state', 'available')
        await expect(status('CP4')).toHaveAttribute('data-state', 'locked')
        await expect(page.getByTestId('board-checkpoint')).toHaveCount(2)
        await expect(page.locator('[data-testid="board-checkpoint"][data-checkpoint-id="CP1"], [data-testid="board-checkpoint"][data-checkpoint-id="CP2"]')).toHaveCount(0)
        await expect(page.locator('[data-testid="board-checkpoint"][data-checkpoint-id="CP3"]')).toHaveAttribute('data-state', 'available')
        await expect(page.locator('[data-testid="board-checkpoint"][data-checkpoint-id="CP4"]')).toHaveAttribute('data-state', 'locked')
        await expect(page.getByTestId('player-score')).toHaveText(['1', '1'])
      }
    }
    async function snapshot(session) {
      const response = await request.get(`${recoveryBackendUrl}/games/${fixture.gameId}/players/${session.playerId}`, {
        headers: { 'X-Player-Token': session.token },
      })
      expect(response.ok()).toBeTruthy()
      return response.json()
    }
    function savedState(view) {
      return { id: view.id, status: view.status, players: view.players, board: view.board,
        vehicles: [...view.vehicles].sort((a, b) => a.id.localeCompare(b.id)),
        tick: view.continuous.tick, revision: view.continuous.revision }
    }

    await command(0, 'Framåt')
    await owners()
    const crashed = await command(1, 'Framåt')
    expect(crashed.vehicles.find(vehicle => vehicle.playerId === bob.playerId).status).toBe('CRASHED')
    await owners()
    await expect(pages[1].getByTestId('queue-status')).toContainText('Kraschad')
    for (const page of pages) await expect(page.locator(`[data-testid="player-vehicle"][data-player-id="${bob.playerId}"]`)).toHaveCount(0)

    await Promise.all(pages.map(page => page.reload()))
    await owners()
    const respawned = await tick(1)
    expect(respawned.vehicles.find(vehicle => vehicle.playerId === bob.playerId)).toMatchObject({ status: 'ACTIVE', x: 0, y: 1 })
    for (const page of pages) await expect(page.locator(`[data-testid="player-vehicle"][data-player-id="${bob.playerId}"]`)).toHaveAttribute('data-status', 'ACTIVE')
    await owners()

    const failedPoll = pages[1].waitForEvent('requestfailed', request => request.url().includes(`/players/${bob.playerId}`))
    await contexts[1].setOffline(true)
    await failedPoll
    await tick(1)
    const advanced = await snapshot(alice)
    await owners()
    await contexts[1].setOffline(false)
    await expect(pages[1].locator('.tick-status')).toContainText(`Tick ${advanced.continuous.tick}`)
    await owners()
    await Promise.all(pages.map(page => page.reload()))
    await owners()

    const before = savedState(await snapshot(alice))
    await backend.stop()
    // Keep both clients open through the outage to exercise automatic reconnect too.
    await owners()
    await Promise.all([backend.start(), ...pages.map(page => page.waitForResponse(
      response => response.url().includes('/players/') && response.status() === 200, { timeout: 120_000 }))])
    await owners()
    expect(savedState(await snapshot(alice))).toEqual(before)
    expect(savedState(await snapshot(bob))).toEqual(before)
    await Promise.all(pages.map(page => page.reload()))
    await owners()

    // Bob enters Alice's captured Flag 1 cell and pushes Alice onto Bob's captured Flag 2 cell.
    const reentry = await command(1, 'Framåt')
    await owners()
    expect(reentry.vehicles.find(vehicle => vehicle.playerId === bob.playerId)).toMatchObject({ x: 1, y: 1 })
    expect(reentry.vehicles.find(vehicle => vehicle.playerId === alice.playerId)).toMatchObject({ x: 2, y: 1 })
    expect(reentry.round.playback.filter(event => event.scoreReason === 'CHECKPOINT')).toEqual([])
  } finally { await Promise.all(contexts.map(context => context.close())) }
})
