import { expect, test } from '@playwright/test'

async function setup(page) {
  const gameId = '10000000-0000-0000-0000-000000000004'
  const playerId = 'shield-owner'
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
    configuration: { maxPlayers: 10, programSize: 5 }, shieldAvailable: true,
    players: [{ id: playerId, name: 'Own', score: 999, capturedCheckpoints: 1, crashes: 0 },
      { id: 'other', name: 'Other', score: -99, capturedCheckpoints: 3, crashes: 0 },
      { id: 'third', name: 'Third', score: 9999, capturedCheckpoints: 1, crashes: 0 }],
    board: { width: 7, height: 7, walls: [], pits: [], obstacles: [], checkpoints: [
      { id: 'CP1', order: 1, position: { x: 5, y: 5 } }, { id: 'CP2', order: 2, position: { x: 5, y: 4 } },
      { id: 'CP3', order: 3, position: { x: 5, y: 3 } }, { id: 'CP4', order: 4, position: { x: 5, y: 2 } }],
      spawnPoints: [], conveyors: [], rotators: [], controlPoints: [] },
    vehicles: [{ id: 'own-vehicle', playerId, x: 1, y: 2, direction: 'EAST', status: 'ACTIVE' }],
    placements: [], round: null,
    continuous: { revision: 1, tick: 3, serverTime: new Date().toISOString(), nextTick: new Date(Date.now() + 2000).toISOString(),
      intervalMillis: 2000, queue: [null, null, 'TURN_LEFT', 'FORWARD_1', 'LASER'], lastExecutedCommand: 'WAIT',
      shieldPending: false, shieldTicksRemaining: 0, activeShieldPlayerIds: [] },
  }
  await page.setViewportSize({ width: 320, height: 568 })
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)), { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
  const requests = []
  let fail = false
  await page.route(`**/games/${gameId}/shield`, async route => {
    const body = route.request().postDataJSON()
    expect(route.request().headers()['x-player-id']).toBe(playerId)
    requests.push(body.requestId)
    // Simulate a committed activation whose response was lost.
    if (state.shieldAvailable) {
      state.shieldAvailable = false
      state.continuous.shieldPending = true
      state.continuous.shieldTicksRemaining = 5
      state.continuous.revision++
    }
    if (fail) { fail = false; await route.abort('failed'); return }
    await route.fulfill({ json: state })
  })
  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('activate-shield')).toBeVisible()
  return {
    state, requests, loseResponse: () => { fail = true },
    async tick(remaining) {
      state.continuous.tick++
      state.continuous.revision++
      state.continuous.shieldPending = false
      state.continuous.shieldTicksRemaining = remaining
      state.continuous.activeShieldPlayerIds = remaining ? [playerId] : []
      await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick', String(state.continuous.tick))
    },
  }
}

test('shield works with a full queue and remains visible through five ticks and reload', async ({ page }) => {
  const game = await setup(page)
  const shield = page.getByRole('button', { name: 'Aktivera sköld', exact: true })
  await expect(page.getByRole('button', { name: 'Framåt', exact: true })).toBeDisabled()
  await expect(shield).toBeEnabled()
  await expect(shield).toBeInViewport({ ratio: 1 })
  await shield.click()
  await expect(page.getByTestId('shield-status')).toHaveText('Startar nästa tick')
  await expect(shield).toBeDisabled()
  await expect(page.getByTestId('slot-4')).toContainText('FORWARD_1')
  await expect(page.getByTestId('slot-5')).toContainText('LASER')
  for (const remaining of [4, 3, 2, 1, 0]) {
    await game.tick(remaining)
    if (remaining) {
      await expect(page.getByTestId('shield-status')).toHaveText(`Aktiv · ${remaining} tick kvar`)
      await expect(page.locator('.vehicle.shield-effect')).toHaveCount(1)
    } else {
      await expect(page.getByTestId('shield-status')).toHaveText('Förbrukad')
      await expect(page.locator('.vehicle.shield-effect')).toHaveCount(0)
    }
    if (remaining === 3) {
      await page.reload()
      await expect(page.locator('.vehicle.shield-effect')).toHaveCount(1)
      await expect(shield).toBeDisabled()
    }
  }
  expect(game.requests).toHaveLength(1)
  await expect(shield).toBeInViewport({ ratio: 1 })
})

test('lost shield response can be retried after reload using the same request ID', async ({ page }) => {
  const game = await setup(page)
  game.loseResponse()
  await page.getByTestId('activate-shield').click()
  await expect(page.getByTestId('shield-status')).toHaveText('Obekräftad · försök igen')
  await page.reload()
  await expect(page.getByRole('button', { name: 'Försök sköld igen', exact: true })).toBeEnabled()
  await page.getByTestId('activate-shield').click()
  await expect(page.getByTestId('shield-status')).toHaveText('Startar nästa tick')
  expect(game.requests).toHaveLength(2)
  expect(game.requests[1]).toBe(game.requests[0])
  expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBeLessThanOrEqual(568)
})

test('standings show only flags, keep stable ties, and retain server final placement', async ({ page }) => {
  const game = await setup(page)
  await expect(page.getByRole('heading', { name: 'Flaggor', exact: true })).toBeVisible()
  await expect(page.getByTestId('score-row')).toHaveText(['Other3', 'Own1', 'Third1'])
  await expect(page.getByTestId('checkpoint-progress')).toContainText('Flaggor: 1 / 4')
  expect(await page.getByTestId('score-table').innerText()).not.toMatch(/999|krasch|poäng/i)
  game.state.status = 'FINISHED'
  game.state.placements = [{ playerId: 'third', placement: 1, winner: true }, { playerId: 'other', placement: 2, winner: false }, { playerId: 'shield-owner', placement: 3, winner: false }]
  game.state.continuous.revision++
  await expect(page.getByTestId('final-standing-row').first()).toContainText('Third1')
  await expect(page.getByTestId('activate-shield')).toBeDisabled()
  await expect(page.getByTestId('final-standings')).toBeInViewport({ ratio: 1 })
  await page.screenshot({ path: '/tmp/wreckage-shield-flags.png' })
})
