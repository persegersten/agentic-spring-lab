import { expect, test } from '@playwright/test'

const viewports = [
  { width: 320, height: 568 },
  { width: 390, height: 844 },
  { width: 844, height: 390 },
  { width: 768, height: 1024 },
  { width: 1366, height: 768 },
  { width: 1920, height: 1080 },
]

for (const [width, height] of [[10, 10], [20, 10], [10, 20]]) {
  for (const [index, viewport] of viewports.entries()) {
    test(`${width}×${height} board fits ${viewport.width}×${viewport.height} and resizes`, async ({ page }) => {
      const gameId = '10000000-0000-0000-0000-000000000001'
      const playerId = '20000000-0000-0000-0000-000000000001'
      const state = {
        id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
        configuration: { maxPlayers: 2, programSize: 5 },
        players: [{ id: playerId, name: 'PlayerWithAVeryLongUnbrokenName', score: 0, crashes: 0 }],
        board: {
          width, height, walls: [], pits: [], obstacles: [],
          checkpoints: [{ id: 'CP1', order: 1, position: { x: width - 1, y: height - 1 } }],
          spawnPoints: [], conveyors: [], rotators: [], controlPoints: [],
        },
        vehicles: [{ id: 'vehicle', playerId, x: 0, y: 0, direction: 'NORTH', status: 'ACTIVE' }],
        placements: [], round: null,
        continuous: {
          revision: 1, tick: 1, serverTime: new Date().toISOString(),
          nextTick: new Date(Date.now() + 2000).toISOString(), queue: [null, null, null, null, null],
        },
      }
      await page.setViewportSize(viewport)
      await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)),
        { gameId, playerId, token: 'test-token' })
      await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
      await page.route(`**/games/${gameId}/commands`, async route => {
        expect(route.request().postDataJSON().command).toBe('FORWARD_1')
        state.continuous.queue[3] = 'FORWARD_1'
        state.continuous.revision++
        await route.fulfill({ json: state })
      })
      await page.goto(`/game/${gameId}`)

      const board = page.getByTestId('game-board')
      const frame = page.locator('.board-wrap')
      const controls = page.getByRole('complementary', { name: 'Spelkontroller' })
      async function expectFit(size) {
        await expect(board).toBeVisible()
        await expect(async () => {
          const bounds = await frame.boundingBox()
          expect(bounds.x).toBeGreaterThanOrEqual(0)
          expect(bounds.y).toBeGreaterThanOrEqual(0)
          expect(bounds.width).toBeGreaterThan(0)
          expect(bounds.height).toBeGreaterThan(0)
          expect(bounds.x + bounds.width).toBeLessThanOrEqual(size.width + 1)
          expect(bounds.y + bounds.height).toBeLessThanOrEqual(size.height + 1)
          const grid = await board.boundingBox()
          expect(Math.abs(grid.width - grid.height * width / height)).toBeLessThan(1)
          const side = await controls.boundingBox()
          if (size.width > 1050) {
            expect(side.x).toBeGreaterThanOrEqual(bounds.x + bounds.width)
          } else {
            expect(side.y).toBeGreaterThanOrEqual(bounds.y + bounds.height)
          }
          expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(size.width)
        }).toPass()
      }
      await expectFit(viewport)
      const resized = viewports[(index + 1) % viewports.length]
      await page.setViewportSize(resized)
      await expectFit(resized)

      await page.getByRole('button', { name: 'FORWARD_1', exact: true }).click()
      await expect(page.getByTestId('slot-4')).toContainText('FORWARD_1')
      await page.getByTestId('score-table').scrollIntoViewIfNeeded()
      await expect(page.getByTestId('score-table')).toBeInViewport()
      // Scrolling the desktop sidebar must leave the full board visible.
      if (resized.width > 1050) await expectFit(resized)
    })
  }
}
