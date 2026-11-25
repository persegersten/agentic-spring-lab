import { expect, test } from '@playwright/test'

for (const scenario of ['THEFT', 'THEFT_SHIELD', 'THEFT_BLOCKED', 'THEFT_PIT']) {
  test(`slice 1: both clients play the ownership transfer before Laser displacement (${scenario})`, async ({ browser, request }) => {
    const contexts = []
    try {
      const created = await request.post(`/games/test-fixtures/flags?scenario=${scenario}`)
      expect(created.ok(), await created.text()).toBeTruthy()
      const fixture = await created.json()
      const [alice, bob] = fixture.sessions
      const pages = []
      const flag = page => page.locator('[data-testid="flag-status"][data-checkpoint-id="CP1"]')
      for (const session of fixture.sessions) {
        const context = await browser.newContext()
        contexts.push(context)
        await context.addInitScript(value => localStorage.setItem(`wreckage-session:${value.gameId}`, JSON.stringify(value)), session)
        const page = await context.newPage()
        await page.clock.install()
        await page.goto(`/game/${fixture.gameId}`)
        await expect(page.getByTestId('flag-strip')).toBeVisible()
        await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
        await expect(page.locator('.tick-status')).toContainText('Tick 10')
        await expect(flag(page)).toHaveAttribute('data-owner-id', alice.playerId)
        await expect(flag(page)).toContainText('Alice')
        pages.push(page)
      }
      const response = await request.post(`/games/test-fixtures/flags/${fixture.gameId}/ticks?count=1`)
      expect(response.ok(), await response.text()).toBeTruthy()
      const result = await response.json()
      const shield = scenario === 'THEFT_SHIELD'
      const transfers = result.round.playback.filter(event => event.type === 'FLAG_TRANSFERRED')
      expect(transfers).toHaveLength(shield ? 0 : 1)
      expect(result.players.map(player => player.capturedCheckpoints)).toEqual(shield ? [1, 0] : [0, 1])
      expect(result.players.every(player => player.nextCheckpoint === 'CP2')).toBeTruthy()
      if (!shield) {
        expect(result.players[1].flagAcquisitionTicks.CP1).toBe(11)
        expect(result.round.playback.map(event => event.type).slice(0, 3)).toEqual(['WEAPON_FIRED', 'WEAPON_HIT', 'FLAG_TRANSFERRED'])
      }
      for (const page of pages) {
        // Deliver the real authoritative response without advancing presentation time.
        await Promise.all([
          page.waitForResponse(response => response.url().includes('/players/') && response.status() === 200),
          page.clock.runFor(500),
        ])
        await expect(page.locator('.tick-status')).toContainText('Tick 11')
        await expect(flag(page)).toHaveAttribute('data-owner-id', alice.playerId)
        await page.clock.runFor(400)
        await expect(flag(page)).toHaveAttribute('data-owner-id', shield ? alice.playerId : bob.playerId)
        await expect(flag(page)).toContainText(shield ? 'Alice' : 'Bob')
        if (!shield) await expect(flag(page)).toHaveAttribute('data-transferred', 'true')
        const aliceVehicle = page.locator(`[data-testid="player-vehicle"][data-player-id="${alice.playerId}"]`)
        if (!shield) await expect(aliceVehicle).toHaveAttribute('data-x', '2')
        await page.clock.runFor(1300)
        await expect(flag(page)).not.toHaveAttribute('data-transferred', 'true')
        await expect(page.locator('[data-testid="board-checkpoint"][data-checkpoint-id="CP1"]')).toHaveCount(0)
        await expect(page.locator('[data-testid="flag-status"][data-checkpoint-id="CP2"]')).toHaveAttribute('data-state', 'available')
        await expect(page.locator(`[data-testid="score-row"][data-player-id="${alice.playerId}"] [data-testid="player-score"]`)).toHaveText(shield ? '1' : '0')
        await expect(page.locator(`[data-testid="score-row"][data-player-id="${bob.playerId}"] [data-testid="player-score"]`)).toHaveText(shield ? '0' : '1')
        if (scenario === 'THEFT_PIT') await expect(aliceVehicle).toHaveCount(0)
        else {
          await expect(aliceVehicle).toHaveAttribute('data-x', shield || scenario === 'THEFT_BLOCKED' ? '2' : '3')
          const target = result.vehicles.find(vehicle => vehicle.playerId === alice.playerId)
          await expect(aliceVehicle).toHaveAttribute('data-direction', target.direction)
        }
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
      }
    } finally { await Promise.all(contexts.map(context => context.close())) }
  })
}

const boundaryScenarios = ['THEFT_MIXED', 'THEFT_CAPTURE_10', 'THEFT_CAPTURE_11', 'THEFT_CAPTURE_15', 'THEFT_CAPTURE_16',
  'THEFT_CHAIN_15', 'THEFT_CHAIN_16', 'THEFT_CHAIN_20', 'THEFT_CHAIN_21']
for (const scenario of boundaryScenarios) {
  test(`slice 2: exact flag windows and exclusive ownership on every client (${scenario})`, async ({ browser, request }) => {
    const contexts = []
    try {
      const created = await request.post(`/games/test-fixtures/flags?scenario=${scenario}`)
      expect(created.ok(), await created.text()).toBeTruthy()
      const fixture = await created.json()
      const [alice, bob, carol] = fixture.sessions
      const mixed = scenario === 'THEFT_MIXED'
      const chain = scenario.startsWith('THEFT_CHAIN_')
      const hitTick = mixed ? 15 : Number(scenario.split('_').at(-1))
      const succeeds = mixed || (chain ? [16, 20].includes(hitTick) : [11, 15].includes(hitTick))
      const initialOwner = chain && hitTick > 15 ? bob : alice
      const finalOwner = chain ? (succeeds ? carol : bob) : (succeeds ? bob : alice)
      const flag = (page, id = 'CP1') => page.locator(`[data-testid="flag-status"][data-checkpoint-id="${id}"]`)
      const pages = []
      for (const session of fixture.sessions) {
        const context = await browser.newContext()
        contexts.push(context)
        await context.addInitScript(value => localStorage.setItem(`wreckage-session:${value.gameId}`, JSON.stringify(value)), session)
        const page = await context.newPage()
        await page.clock.install()
        await page.goto(`/game/${fixture.gameId}`)
        await expect(page.getByTestId('flag-strip')).toBeVisible()
        await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
        await expect(page.locator('.tick-status')).toContainText(`Tick ${hitTick - 1}`)
        await expect(flag(page)).toHaveAttribute('data-owner-id', initialOwner.playerId)
        pages.push(page)
      }
      const response = await request.post(`/games/test-fixtures/flags/${fixture.gameId}/ticks?count=1`)
      expect(response.ok(), await response.text()).toBeTruthy()
      const result = await response.json()
      const transfers = result.round.playback.filter(event => event.type === 'FLAG_TRANSFERRED')
      expect(transfers).toHaveLength(mixed ? 2 : succeeds || chain && hitTick === 15 ? 1 : 0)
      const expectedTick = succeeds ? hitTick : chain ? 15 : 10
      expect(result.players.find(player => player.id === finalOwner.playerId).flagAcquisitionTicks.CP1).toBe(expectedTick)
      for (const id of mixed ? ['CP1', 'CP2', 'CP3'] : ['CP1']) {
        expect(result.players.filter(player => player.visitedCheckpoints.includes(id))).toHaveLength(1)
      }
      if (mixed) {
        expect(result.players.find(player => player.id === bob.playerId).flagAcquisitionTicks).toEqual({ CP1: 15, CP2: 15 })
        expect(result.players.find(player => player.id === alice.playerId).flagAcquisitionTicks).toEqual({ CP3: 9 })
      }
      for (const page of pages) {
        await Promise.all([
          page.waitForResponse(response => response.url().includes('/players/') && response.status() === 200),
          page.clock.runFor(500),
        ])
        await expect(page.locator('.tick-status')).toContainText(`Tick ${hitTick}`)
        await expect(flag(page)).toHaveAttribute('data-owner-id', initialOwner.playerId)
        if (mixed) {
          await page.clock.runFor(350)
          // Both entries change and highlight at one boundary, before displacement.
          for (const id of ['CP1', 'CP2']) {
            await expect(flag(page, id)).toHaveAttribute('data-owner-id', bob.playerId)
            await expect(flag(page, id)).toHaveAttribute('data-transferred', 'true')
          }
          await expect(flag(page, 'CP3')).toHaveAttribute('data-owner-id', alice.playerId)
          await expect(flag(page, 'CP3')).not.toHaveAttribute('data-transferred', 'true')
          await expect(page.locator(`[data-testid="player-vehicle"][data-player-id="${alice.playerId}"]`)).toHaveAttribute('data-x', '2')
        }
        await page.clock.runFor(1700)
        await expect(flag(page)).toHaveAttribute('data-owner-id', finalOwner.playerId)
        for (const player of result.players) {
          await expect(page.locator(`[data-testid="score-row"][data-player-id="${player.id}"] [data-testid="player-score"]`))
            .toHaveText(String(player.capturedCheckpoints))
        }
        for (const id of mixed ? ['CP1', 'CP2', 'CP3'] : ['CP1']) {
          await expect(page.locator(`[data-testid="board-checkpoint"][data-checkpoint-id="${id}"]`)).toHaveCount(0)
          await expect(flag(page, id)).not.toHaveAttribute('data-transferred', 'true')
        }
        await expect(flag(page, mixed ? 'CP4' : 'CP2')).toHaveAttribute('data-state', 'available')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
      }
    } finally { await Promise.all(contexts.map(context => context.close())) }
  })
}
