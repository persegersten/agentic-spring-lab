import { expect, test } from '@playwright/test'

const cases = [
  { scenario: 'LIMIT_TIE', counts: [1, 1], ranks: [1, 1], next: 'CP3' },
  { scenario: 'LIMIT_ZERO', counts: [0, 0], ranks: [1, 1], next: 'CP1' },
  { scenario: 'LIMIT_UNEQUAL', counts: [2, 1], ranks: [1, 2], next: 'CP4' },
  { scenario: 'LIMIT_FINAL', counts: [2, 2], ranks: [1, 1], next: null },
]

for (const { scenario, counts, ranks, next } of cases) {
  test(`the existing limit finishes the final due round once (${scenario})`, async ({ browser, request }) => {
    const contexts = []
    try {
      const created = await request.post(`/games/test-fixtures/flags?scenario=${scenario}`)
      expect(created.ok(), await created.text()).toBeTruthy()
      const fixture = await created.json()
      const pages = []
      async function snapshot(session) {
        const response = await request.get(`/games/${fixture.gameId}/players/${session.playerId}`, {
          headers: { 'X-Player-Token': session.token },
        })
        expect(response.ok()).toBeTruthy()
        return response.json()
      }
      async function tick(count) {
        const response = await request.post(`/games/test-fixtures/flags/${fixture.gameId}/ticks?count=${count}`)
        expect(response.ok(), await response.text()).toBeTruthy()
        return response.json()
      }
      for (const session of fixture.sessions) {
        const context = await browser.newContext()
        contexts.push(context)
        await context.addInitScript(value => localStorage.setItem(`wreckage-session:${value.gameId}`, JSON.stringify(value)), session)
        const page = await context.newPage()
        await page.clock.install()
        await page.goto(`/game/${fixture.gameId}`)
        await expect(page.getByTestId('flag-strip')).toBeVisible()
        await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
        await expect(page.locator('.tick-status')).toContainText('Tick 299')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
        pages.push(page)
      }

      const before = await snapshot(fixture.sessions[0])
      expect(before.status).toBe('RUNNING')
      expect(before.continuous.nextTick).toBe(before.continuous.deadline)
      const finished = await tick(1)
      expect(finished.status).toBe('FINISHED')
      expect(finished.round.number).toBe(300)
      // Tick 300's rotating initiative puts Bob's command before Alice's final capture.
      expect(finished.round.playback.map(event => event.type)).toEqual(scenario === 'LIMIT_FINAL'
        ? ['MOVE', 'MOVE', 'SCORE_CHANGED', 'CONVEYOR_MOVE', 'ROTATOR_TURN']
        : ['MOVE', 'MOVE', 'CONVEYOR_MOVE', 'ROTATOR_TURN'])
      expect(finished.round.playback.filter(event => event.scoreReason === 'CHECKPOINT')).toHaveLength(scenario === 'LIMIT_FINAL' ? 1 : 0)
      expect(finished.placements.map(row => row.checkpointsVisited)).toEqual(counts)
      expect(finished.placements.map(row => row.placement)).toEqual(ranks)
      expect(finished.placements.filter(row => row.winner)).toHaveLength(scenario === 'LIMIT_UNEQUAL' ? 1 : 2)
      expect(finished.players[0].score).toBeLessThan(finished.players[1].score)
      for (const player of finished.players) expect(player.nextCheckpoint).toBe(next)
      for (const session of fixture.sessions) {
        const view = await snapshot(session)
        expect(view.continuous.tick).toBe(300)
        expect(view.continuous.revision).toBe(before.continuous.revision + 1)
        expect(view.continuous.deadline).toBe(before.continuous.deadline)
      }

      for (const page of pages) {
        await Promise.all([
          page.waitForResponse(response => response.url().includes('/players/') && response.status() === 200),
          page.clock.runFor(500),
        ])
        await expect(page.locator('.tick-status')).toContainText('Tick 300')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
        await page.clock.runFor(800)
        const bob = page.locator(`[data-testid="player-vehicle"][data-player-id="${fixture.sessions[1].playerId}"]`)
        await expect(bob).toHaveAttribute('data-x', '2')
        await expect(bob).toHaveAttribute('data-direction', 'SOUTH')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
        await page.clock.runFor(800)
        await expect(page.getByTestId('final-standings')).toBeVisible()
        await expect(page.getByTestId('player-placement')).toHaveText(ranks.map(String))
        await expect(page.getByTestId('player-score')).toHaveText(counts.map(String))
        await expect(page.getByTestId('draw-result')).toHaveCount(scenario === 'LIMIT_UNEQUAL' ? 0 : 1)
        await expect(page.getByTestId('board-checkpoint')).toHaveCount(4 - counts.reduce((a, b) => a + b, 0))
        for (const player of finished.players) for (const id of player.visitedCheckpoints) {
          await expect(page.locator(`[data-testid="flag-status"][data-checkpoint-id="${id}"]`)).toHaveAttribute('data-owner-id', player.id)
        }
        if (next) {
          await expect(page.locator(`[data-testid="flag-status"][data-checkpoint-id="${next}"]`)).toHaveAttribute('data-state', 'available')
          await expect(page.locator(`[data-testid="board-checkpoint"][data-checkpoint-id="${next}"]`)).toBeVisible()
        }
        await expect(page.getByRole('button', { name: 'Framåt', exact: true })).toBeDisabled()
      }

      const after = await tick(2)
      expect(after.round).toEqual(finished.round)
      expect(after.vehicles).toEqual(finished.vehicles)
      expect(after.placements).toEqual(finished.placements)
      const restored = await snapshot(fixture.sessions[0])
      expect(restored.continuous.tick).toBe(300)
      expect(restored.continuous.revision).toBe(before.continuous.revision + 1)
      for (const page of pages) {
        await page.reload()
        await expect(page.getByTestId('final-standings')).toBeVisible()
        await expect(page.getByTestId('player-placement')).toHaveText(ranks.map(String))
      }
    } finally { await Promise.all(contexts.map(context => context.close())) }
  })
}
