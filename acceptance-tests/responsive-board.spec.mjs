import { expect, test } from '@playwright/test'

const viewports = [
  { width: 320, height: 568 }, { width: 390, height: 844 },
  { width: 844, height: 390 }, { width: 768, height: 1024 },
  { width: 1366, height: 768 }, { width: 1920, height: 1080 },
  { width: 568, height: 320 }, { width: 667, height: 375 },
]

async function openGame(page, viewport, width, height, playerCount) {
  const gameId = '10000000-0000-0000-0000-000000000001'
  const playerId = '20000000-0000-0000-0000-000000000001'
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
    configuration: { maxPlayers: 10, programSize: 5 },
    players: Array.from({ length: playerCount }, (_, index) => ({ id: index ? `player-${index}` : playerId,
      name: `PlayerWithAVeryLongUnbrokenName${index}`, score: 1000 - index, capturedCheckpoints: index % 4, crashes: 0 })),
    board: { width, height, walls: [], pits: [], obstacles: [],
      checkpoints: [{ id: 'CP1', order: 1, position: { x: width - 1, y: height - 1 } }],
      spawnPoints: [], conveyors: [], rotators: [], controlPoints: [] },
    vehicles: [{ id: 'vehicle', playerId, x: 0, y: 0, direction: 'NORTH', status: 'ACTIVE' }],
    placements: [], round: null, shieldAvailable: true,
    continuous: { revision: 1, tick: 1, serverTime: new Date().toISOString(),
      nextTick: new Date(Date.now() + 2000).toISOString(), queue: [null, null, null, null, null], lastExecutedCommand: 'WAIT', shieldPending: false, shieldTicksRemaining: 0, activeShieldPlayerIds: [] },
  }
  await page.setViewportSize(viewport)
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)), { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
  let failNext = false
  await page.route(`**/games/${gameId}/commands`, async route => {
    if (failNext) { failNext = false; await route.fulfill({ status: 503, json: { message: 'A long server error explaining that the command needs to be retried safely.' } }); return }
    const index = state.continuous.queue.findIndex((command, index) => index >= 2 && command == null)
    state.continuous.queue[index] = route.request().postDataJSON().command
    state.continuous.revision++
    await route.fulfill({ json: state })
  })
  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('game-board')).toBeVisible()
  return { state, failNext: () => { failNext = true } }
}

async function expectFit(page, viewport, playerCount) {
  const essential = page.locator('.game-heading, .board-wrap, .command-machine, .machine-slot, .queue-cargo, .machine-execution, .execution-command, .queue-commands button, .shield-ability, .shield-ability button, .compact-scores, .compact-score-list li, .queue-status')
  await expect(async () => {
    for (const element of await essential.all()) {
      const box = await element.boundingBox()
      expect(box).not.toBeNull()
      expect(box.x).toBeGreaterThanOrEqual(-1)
      expect(box.y).toBeGreaterThanOrEqual(-1)
      expect(box.width).toBeGreaterThan(0)
      expect(box.height).toBeGreaterThan(0)
      expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1)
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 1)
    }
    expect(await page.locator('.compact-score-list li').count()).toBe(playerCount)
    const dimensions = await page.evaluate(() => ({
      width: document.documentElement.scrollWidth, height: document.documentElement.scrollHeight,
      scrollPanels: [...document.querySelectorAll('.game-page, .game-controls, .queue-panel, .command-pad, .compact-scores')]
        .filter(el => el.scrollHeight > el.clientHeight + 1 || el.scrollWidth > el.clientWidth + 1).map(el => el.className),
    }))
    expect(dimensions.width).toBeLessThanOrEqual(viewport.width)
    expect(dimensions.height).toBeLessThanOrEqual(viewport.height)
    expect(dimensions.scrollPanels).toEqual([])
    const board = await page.locator('.board-wrap').boundingBox()
    const controls = await page.locator('.game-controls').boundingBox()
    const scores = await page.locator('.compact-scores').boundingBox()
    for (const [a, b] of [[board, controls], [board, scores], [controls, scores]]) {
      expect(a.x + a.width <= b.x + 1 || b.x + b.width <= a.x + 1 || a.y + a.height <= b.y + 1 || b.y + b.height <= a.y + 1).toBe(true)
    }
    for (const button of await page.locator('.queue-commands button').all()) expect((await button.boundingBox()).height).toBeGreaterThanOrEqual(44)
  }).toPass()
}

for (const [width, height] of [[10, 10], [20, 10], [10, 20]]) {
  for (const playerCount of [2, 10]) {
    for (const [index, viewport] of viewports.entries()) {
      test(`${width}×${height}, ${playerCount} players: everything fits ${viewport.width}×${viewport.height} without scrolling`, async ({ page }) => {
        await openGame(page, viewport, width, height, playerCount)
        await expectFit(page, viewport, playerCount)
        const board = await page.getByTestId('game-board').boundingBox()
        expect(Math.abs(board.width - board.height * width / height)).toBeLessThan(1)
        await page.getByRole('button', { name: 'Framåt', exact: true }).click()
        await expect(page.getByTestId('slot-3')).toContainText('FORWARD_1')
        await page.getByRole('button', { name: 'Laser', exact: true }).click()
        await expect(page.getByTestId('slot-4')).toContainText('LASER')
        await page.getByRole('button', { name: 'Laser', exact: true }).click()
        await expect(page.getByTestId('slot-5')).toContainText('LASER')
        await expect(page.getByRole('button', { name: 'Framåt', exact: true })).toBeDisabled()
        await expectFit(page, viewport, playerCount)
        if (width === 10 && height === 10 && playerCount === 10) await page.screenshot({ path: `/tmp/wreckage-fit-${viewport.width}x${viewport.height}.png` })
        const resized = viewports[(index + 1) % viewports.length]
        await page.setViewportSize(resized)
        await expectFit(page, resized, playerCount)
      })
    }
  }
}

test('retry, crash and final standings stay visible with ten players on the smallest screen', async ({ page }) => {
  const viewport = viewports[0]
  const game = await openGame(page, viewport, 10, 10, 10)
  game.failNext()
  await page.getByRole('button', { name: 'Framåt', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Försök igen', exact: true })).toBeInViewport({ ratio: 1 })
  await expectFit(page, viewport, 10)
  await page.screenshot({ path: '/tmp/wreckage-fit-retry-320x568.png' })
  await page.getByRole('button', { name: 'Försök igen', exact: true }).click()
  await expect(page.getByTestId('slot-3')).toContainText('FORWARD_1')
  game.state.vehicles[0].status = 'CRASHED'
  game.state.continuous.revision++
  await expect(page.getByTestId('queue-status')).toContainText('Kraschad')
  await expectFit(page, viewport, 10)
  game.state.status = 'FINISHED'
  game.state.placements = game.state.players.map((player, index) => ({ playerId: player.id, placement: index ? index + 1 : 1, winner: index < 2 }))
  game.state.continuous.revision++
  await expect(page.getByTestId('final-standings')).toBeVisible()
  await expect(page.getByTestId('draw-result')).toBeVisible()
  await expectFit(page, viewport, 10)
})
