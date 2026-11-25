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

test('slice 3: saved theft windows survive reload, process restart, crash/respawn and final standings', async ({ browser, request }) => {
  test.setTimeout(360_000)
  const contexts = []
  const copies = []
  async function snapshot(fixture) {
    const session = fixture.sessions[1]
    const response = await request.get(`${recoveryBackendUrl}/games/${fixture.gameId}/players/${session.playerId}`, {
      headers: { 'X-Player-Token': session.token },
    })
    expect(response.ok(), await response.text()).toBeTruthy()
    return response.json()
  }
  const flag = page => page.locator('[data-testid="flag-status"][data-checkpoint-id="CP1"]')
  async function check(page, fixture, owner, finished) {
    await expect(flag(page)).toHaveAttribute('data-owner-id', owner.playerId)
    await expect(flag(page)).not.toHaveAttribute('data-transferred', 'true')
    await expect(page.locator('[data-testid="board-checkpoint"][data-checkpoint-id="CP1"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="flag-status"][data-checkpoint-id="CP2"]')).toHaveAttribute('data-state', 'available')
    for (const session of fixture.sessions) {
      await expect(page.locator(`[data-testid="${finished ? 'final-standing-row' : 'score-row'}"][data-player-id="${session.playerId}"] [data-testid="player-score"]`))
        .toHaveText(session.playerId === owner.playerId ? '1' : '0')
    }
    await expect(page.getByTestId('final-standings')).toHaveCount(finished ? 1 : 0)
    if (finished) for (const session of fixture.sessions) {
      await expect(page.locator(`[data-testid="final-standing-row"][data-player-id="${session.playerId}"] [data-testid="player-placement"]`))
        .toHaveText(session.playerId === owner.playerId ? '1' : '2')
    }
  }
  try {
    for (const scenario of ['THEFT_RECOVERY_16', 'THEFT_RECOVERY_20', 'THEFT_RECOVERY_21',
      'THEFT_RESPAWN_16', 'THEFT_RESPAWN_20', 'THEFT_RESPAWN_21', 'THEFT_FINISHED']) {
      const response = await request.post(`${recoveryBackendUrl}/games/test-fixtures/flags?scenario=${scenario}`)
      expect(response.ok(), await response.text()).toBeTruthy()
      const fixture = await response.json()
      const before = await snapshot(fixture)
      const bob = fixture.sessions[1]
      expect(before.players.find(player => player.id === bob.playerId).flagAcquisitionTicks).toEqual({ CP1: 15 })
      const pages = []
      // Independent sessions for the current owner and next shooter; Alice remains in the match.
      for (const session of fixture.sessions.slice(1)) {
        const context = await browser.newContext()
        contexts.push(context)
        await context.addInitScript(value => localStorage.setItem(`wreckage-session:${value.gameId}`, JSON.stringify(value)), session)
        const page = await context.newPage()
        await page.clock.install()
        await page.goto(`/game/${fixture.gameId}`)
        await check(page, fixture, bob, scenario === 'THEFT_FINISHED')
        await page.reload()
        await check(page, fixture, bob, scenario === 'THEFT_FINISHED')
        await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
        await page.clock.runFor(2000)
        await check(page, fixture, bob, scenario === 'THEFT_FINISHED')
        if (scenario === 'THEFT_RECOVERY_16') {
          await context.setOffline(true)
          await Promise.all([
            page.waitForEvent('requestfailed', req => req.url().includes('/players/')),
            page.clock.runFor(500),
          ])
          await context.setOffline(false)
          await Promise.all([
            page.waitForResponse(res => res.url().includes('/players/') && res.status() === 200),
            page.clock.runFor(1000),
          ])
          await page.clock.runFor(2000)
          await check(page, fixture, bob, false)
        }
        pages.push(page)
      }
      copies.push({ scenario, fixture, before, pages })
    }
    await backend.stop()
    await backend.start() // Same file database; all seven saved copies predate this new JVM.
    for (const { scenario, fixture, before, pages } of copies) {
      const bob = fixture.sessions[1]
      const carol = fixture.sessions[2]
      const restored = await snapshot(fixture)
      // serverTime is response metadata, not saved match state; entity row order is unspecified.
      const persisted = view => ({ ...view,
        vehicles: [...view.vehicles].sort((a, b) => a.id.localeCompare(b.id)),
        continuous: { ...view.continuous, serverTime: undefined },
      })
      expect(persisted(restored)).toEqual(persisted(before))
      for (const page of pages) {
        await page.reload()
        await check(page, fixture, bob, scenario === 'THEFT_FINISHED')
        await page.clock.runFor(2000)
        await check(page, fixture, bob, scenario === 'THEFT_FINISHED')
      }
      if (scenario === 'THEFT_FINISHED') {
        expect(restored.status).toBe('FINISHED')
        expect(restored.placements.map(p => [p.playerId, p.placement, p.checkpointsVisited]))
          .toEqual([[bob.playerId, 1, 1], [fixture.sessions[0].playerId, 2, 0], [carol.playerId, 2, 0]])
        continue
      }
      const hitTick = Number(scenario.split('_').at(-1))
      const response = await request.post(`${recoveryBackendUrl}/games/test-fixtures/flags/${fixture.gameId}/ticks?count=1`)
      expect(response.ok(), await response.text()).toBeTruthy()
      const result = await response.json()
      const owner = hitTick <= 20 ? carol : bob
      expect((await snapshot(fixture)).continuous.tick).toBe(hitTick)
      expect(result.players.find(p => p.id === owner.playerId).flagAcquisitionTicks).toEqual({ CP1: hitTick <= 20 ? hitTick : 15 })
      expect(result.round.playback.filter(e => e.type === 'WEAPON_HIT')).toHaveLength(1)
      expect(result.round.playback.filter(e => e.type === 'FLAG_TRANSFERRED')).toHaveLength(hitTick <= 20 ? 1 : 0)
      if (scenario.startsWith('THEFT_RESPAWN')) {
        expect(result.players.find(p => p.id === bob.playerId).crashes).toBe(1)
        expect(result.vehicles.find(v => v.playerId === bob.playerId).status).toBe('ACTIVE')
      }
      for (const page of pages) {
        await Promise.all([
          page.waitForResponse(response => response.url().includes('/players/') && response.status() === 200),
          page.clock.runFor(500),
        ])
        await page.clock.runFor(2000)
        await check(page, fixture, owner, false)
        await page.reload()
        await check(page, fixture, owner, false)
      }
    }
  } finally { await Promise.all(contexts.map(context => context.close())) }
})
