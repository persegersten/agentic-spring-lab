import { expect, test } from '@playwright/test'

for (const scenario of ['FINAL_TIE', 'FINAL_UNEQUAL']) {
  test(`last capture plays remaining commands and board effects before results (${scenario})`, async ({ browser, request }) => {
    const contexts = []
    try {
      const created = await request.post(`/games/test-fixtures/flags?scenario=${scenario}`)
      expect(created.ok(), await created.text()).toBeTruthy()
      const fixture = await created.json()
      const pages = []
      for (const session of fixture.sessions) {
        const context = await browser.newContext()
        contexts.push(context)
        await context.addInitScript(value => localStorage.setItem(`wreckage-session:${value.gameId}`, JSON.stringify(value)), session)
        const page = await context.newPage()
        await page.clock.install()
        await page.goto(`/game/${fixture.gameId}`)
        await expect(page.getByTestId('flag-strip')).toBeVisible()
        await expect(page.locator('[data-testid="board-checkpoint"][data-state=available]')).toHaveAttribute('data-checkpoint-id', 'CP4')
        await expect(page.locator('[data-testid="board-checkpoint"][data-state=available]')).toHaveCSS('animation-name', 'flag-available-pulse')
        await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
        pages.push(page)
      }
      async function queue(page, label) {
        await Promise.all([
          page.waitForResponse(response => response.url().endsWith('/commands') && response.status() === 200),
          page.getByRole('button', { name: label, exact: true }).click(),
        ])
      }
      async function tick(count) {
        const response = await request.post(`/games/test-fixtures/flags/${fixture.gameId}/ticks?count=${count}`)
        expect(response.ok(), await response.text()).toBeTruthy()
        return response.json()
      }
      async function poll(page) {
        await Promise.all([
          page.waitForResponse(response => response.url().includes('/players/') && response.status() === 200),
          page.clock.runFor(500),
        ])
      }
      await queue(pages[0], 'Framåt')
      await queue(pages[0], 'Höger') // A future tick must never execute this after completion.
      await queue(pages[1], 'Framåt')
      const preparing = await tick(2)
      expect(preparing.status).toBe('RUNNING')
      for (const page of pages) {
        await poll(page)
        await expect(page.locator('.tick-status')).toContainText('Tick 2')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
      }

      const finished = await tick(1)
      expect(finished.status).toBe('FINISHED')
      expect(finished.round.playback.map(event => event.type)).toEqual([
        'MOVE', 'SCORE_CHANGED', 'MOVE', 'CONVEYOR_MOVE', 'ROTATOR_TURN',
      ])
      expect(finished.round.playback[1].checkpointId).toBe('CP4')
      expect(finished.players[0].score).toBeLessThan(finished.players[1].score)
      expect(finished.players[0].crashes).not.toBe(finished.players[1].crashes)
      const tied = scenario === 'FINAL_TIE'
      expect(finished.placements.map(row => row.placement)).toEqual(tied ? [1, 1] : [1, 2])
      expect(finished.placements.map(row => row.checkpointsVisited)).toEqual(tied ? [2, 2] : [3, 1])
      expect(finished.placements.map(row => row.winner)).toEqual(tied ? [true, true] : [true, false])
      expect(finished.placements.every(row => row.distanceToNextCheckpoint == null)).toBeTruthy()

      for (const page of pages) {
        await poll(page)
        await expect(page.locator('[data-testid="flag-status"][data-checkpoint-id="CP4"]')).toHaveAttribute('data-state', 'owned')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
        const bob = page.locator(`[data-testid="player-vehicle"][data-player-id="${fixture.sessions[1].playerId}"]`)
        await page.clock.runFor(400)
        await expect(bob).toHaveAttribute('data-x', '1')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
        await page.clock.runFor(400)
        await expect(bob).toHaveAttribute('data-x', '2')
        await expect(bob).toHaveAttribute('data-direction', 'SOUTH')
        await expect(page.getByTestId('final-standings')).toHaveCount(0)
        await page.clock.runFor(800)
        await expect(page.getByTestId('final-standings')).toBeVisible()
        await expect(page.getByTestId('player-placement')).toHaveText(tied ? ['1', '1'] : ['1', '2'])
        await expect(page.getByTestId('player-score')).toHaveText(tied ? ['2', '2'] : ['3', '1'])
        await expect(page.getByTestId('draw-result')).toHaveCount(tied ? 1 : 0)
        await expect(page.getByTestId('board-checkpoint')).toHaveCount(0)
        await expect(page.getByRole('button', { name: 'Framåt', exact: true })).toBeDisabled()
      }
      const later = await tick(2)
      expect(later.round).toEqual(finished.round)
      expect(later.vehicles).toEqual(finished.vehicles)
      expect(later.placements).toEqual(finished.placements)

      // A reconnect reconstructs final standings directly without replaying the round.
      for (const page of pages) {
        await page.reload()
        await expect(page.getByTestId('final-standings')).toBeVisible()
        await expect(page.getByTestId('player-placement')).toHaveText(tied ? ['1', '1'] : ['1', '2'])
        await expect(page.getByTestId('board-checkpoint')).toHaveCount(0)
      }
    } finally { await Promise.all(contexts.map(context => context.close())) }
  })
}
