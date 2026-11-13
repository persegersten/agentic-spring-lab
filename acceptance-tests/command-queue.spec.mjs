import { expect, test } from '@playwright/test'

async function openQueue(page, { reducedMotion = false, tick = 0, queue = [null, null, 'TURN_LEFT', 'FORWARD_1', 'FORWARD_1'] } = {}) {
  const gameId = '10000000-0000-0000-0000-000000000002'
  const playerId = '20000000-0000-0000-0000-000000000002'
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
    configuration: { maxPlayers: 2, programSize: 5 },
    players: [{ id: playerId, name: 'Test player', score: 0, crashes: 0 }],
    board: { width: 10, height: 10, walls: [], pits: [], obstacles: [], checkpoints: [], spawnPoints: [], conveyors: [], rotators: [], controlPoints: [] },
    vehicles: [{ id: 'vehicle', playerId, x: 0, y: 0, direction: 'NORTH', status: 'ACTIVE' }],
    placements: [], round: null,
    continuous: { revision: 1, tick, serverTime: new Date().toISOString(), nextTick: new Date(Date.now() + 2000).toISOString(),
      queue, lastExecutedCommand: tick ? 'LASER' : null },
  }
  if (reducedMotion) await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 1366, height: 900 })
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)), { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
  await page.route(`**/games/${gameId}/commands`, async route => {
    const command = route.request().postDataJSON().command
    const index = state.continuous.queue.findIndex((command, index) => index >= 2 && command == null)
    state.continuous.queue[index] = command
    state.continuous.revision++
    await route.fulfill({ json: state })
  })
  await page.goto(`/game/${gameId}`)
  await expect(page.getByRole('heading', { name: 'Kommandokö', exact: true })).toBeVisible()
  return {
    state,
    async tick(queue, command, count = 1) {
      state.continuous.queue = queue
      state.continuous.tick += count
      state.continuous.revision++
      state.continuous.lastExecutedCommand = command
      await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick', String(state.continuous.tick))
    },
  }
}

test('machine separates input and locked slots and moves identical commands without replacing them', async ({ page }) => {
  const game = await openQueue(page)
  for (const slot of [3, 4, 5]) {
    await expect(page.getByTestId(`slot-${slot}`)).toHaveAttribute('data-slot-state', 'input')
    await expect(page.getByTestId(`slot-${slot}`)).toContainText('INMATNING')
  }
  for (const slot of [1, 2]) {
    await expect(page.getByTestId(`slot-${slot}`)).toHaveAttribute('data-slot-state', 'locked')
    await expect(page.getByTestId(`slot-${slot}`)).toContainText('LÅST')
  }
  for (const command of ['FORWARD_2', 'FORWARD_3']) await expect(page.getByRole('button', { name: command, exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Laser', exact: true })).toBeDisabled()
  const cargo = page.getByTestId('queue-cargo')
  const first = await cargo.and(page.locator('[data-command="FORWARD_1"]')).first().getAttribute('data-cargo-id')
  const second = await cargo.last().getAttribute('data-cargo-id')
  await page.evaluate(() => {
    window.queueTransitions = []
    document.addEventListener('transitionrun', event => {
      if (event.target.matches('.queue-cargo')) window.queueTransitions.push(event.propertyName)
    })
  })
  await game.tick([null, null, 'FORWARD_1', 'FORWARD_1', null], 'WAIT')
  await expect(page.locator(`[data-cargo-id="${first}"]`)).toHaveAttribute('data-slot', '3')
  await expect(page.locator(`[data-cargo-id="${second}"]`)).toHaveAttribute('data-slot', '4')
  await expect.poll(() => page.evaluate(() => window.queueTransitions)).toContain('top')
  await expect(page.getByTestId('executed-command')).toHaveText('NOP')
  // Accept another command while the shift animation is still settling.
  await page.getByRole('button', { name: 'Laser', exact: true }).click()
  await expect(page.getByTestId('slot-5')).toContainText('LASER')
  await expect(page.locator(`[data-cargo-id="${second}"]`)).toHaveAttribute('data-slot', '4')
  await expect(page.getByRole('button', { name: 'Laser', exact: true })).toBeDisabled()
  // An ordinary poll must not shift again or remount the existing bricks.
  game.state.continuous.revision++
  await expect(page.locator(`[data-cargo-id="${first}"]`)).toHaveAttribute('data-slot', '3')
})

test('empty queue accepts three commands in slots 3, 4 and 5', async ({ page }) => {
  await openQueue(page, { queue: [null, null, null, null, null] })
  await expect(page.locator('.queue-heading')).toContainText('3–5 öppna · 1–2 låsta')
  for (const [slot, label, command] of [[3, 'Framåt', 'FORWARD_1'], [4, 'Laser', 'LASER'], [5, 'Framåt', 'FORWARD_1']]) {
    await expect(page.getByTestId(`slot-${slot}`)).toContainText('Nästa')
    await expect(page.locator('.queue-destination')).toHaveText(`Mata in → ${slot}`)
    await page.getByRole('button', { name: label, exact: true }).click()
    await expect(page.getByTestId(`slot-${slot}`)).toContainText(command)
  }
  await expect(page.locator('.queue-destination')).toHaveText('Kön är full')
  await expect(page.getByRole('button', { name: 'Laser', exact: true })).toBeDisabled()
  for (const slot of [1, 2]) await expect(page.getByTestId(`slot-${slot}`)).toContainText('NOP')
  const gate = await page.locator('.machine-lock-gate').boundingBox()
  const third = await page.getByTestId('slot-3').boundingBox()
  const second = await page.getByTestId('slot-2').boundingBox()
  expect(gate.y).toBeGreaterThanOrEqual(third.y + third.height)
  expect(gate.y + gate.height).toBeLessThanOrEqual(second.y)
})

test('slot 3 remains available when slots 4 and 5 are occupied', async ({ page }) => {
  await openQueue(page, { queue: [null, null, null, 'FORWARD_1', 'FORWARD_1'] })
  await expect(page.getByTestId('slot-3')).toContainText('Nästa')
  await page.getByRole('button', { name: 'Laser', exact: true }).click()
  await expect(page.getByTestId('slot-3')).toContainText('LASER')
  for (const slot of [4, 5]) await expect(page.getByTestId(`slot-${slot}`)).toContainText('FORWARD_1')
  await expect(page.getByRole('button', { name: 'Laser', exact: true })).toBeDisabled()
})

test('execution uses the confirmed command, skipped ticks snap, and crashes clear the feed', async ({ page }) => {
  const game = await openQueue(page)
  await game.tick([null, 'FORWARD_1', 'FORWARD_1', null, null], 'LASER', 2)
  await expect(page.getByTestId('executed-command')).toHaveText('Laser')
  await expect(page.getByTestId('executed-command')).toHaveClass(/execution-pulse/)
  await expect(page.locator('.command-machine')).toHaveClass(/queue-snap/)
  game.state.vehicles[0].status = 'CRASHED'
  await game.tick([null, null, null, null, null], 'FORWARD_1')
  await expect(page.getByTestId('queue-cargo')).toHaveCount(0)
  await expect(page.getByTestId('executed-command')).toHaveText('Framåt')
  await expect(page.getByRole('button', { name: 'Laser', exact: true })).toBeDisabled()
  // The next snapshot can contain no execution when respawn was blocked.
  await game.tick([null, null, null, null, null], null)
  await expect(page.getByTestId('executed-command')).toHaveText('Väntar')
})

test('reconnect does not replay execution and reduced motion disables movement', async ({ page }) => {
  const game = await openQueue(page, { reducedMotion: true, tick: 8 })
  await expect(page.getByTestId('executed-command')).toHaveText('Laser')
  await expect(page.getByTestId('executed-command')).not.toHaveClass(/execution-pulse/)
  await game.tick([null, null, 'FORWARD_1', 'FORWARD_1', null], 'TURN_RIGHT')
  await expect(page.getByTestId('executed-command')).toHaveCSS('animation-name', 'none')
  await expect(page.getByTestId('queue-cargo').first()).toHaveCSS('transition-duration', '0s')
  await page.reload()
  await expect(page.getByTestId('executed-command')).toHaveText('Höger')
  await expect(page.getByTestId('executed-command')).not.toHaveClass(/execution-pulse/)
  await page.screenshot({ path: '/tmp/wreckage-command-queue.png', fullPage: true })
})
