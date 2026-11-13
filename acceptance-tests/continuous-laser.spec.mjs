import { expect, test } from '@playwright/test'

async function setup(page, { reducedMotion = false, mobile = false } = {}) {
  const gameId = '10000000-0000-0000-0000-000000000003'
  const playerId = 'shooter-a'
  const vehicle = (id, playerId, x, y, direction) => ({ id, playerId, x, y, direction, status: 'ACTIVE' })
  const initial = [vehicle('a', playerId, 1, 2, 'EAST'), vehicle('b', 'shooter-b', 6, 4, 'WEST'),
    vehicle('c', 'target-c', 3, 2, 'NORTH'), vehicle('d', 'target-d', 4, 4, 'SOUTH')]
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
    configuration: { maxPlayers: 4, programSize: 5 },
    players: initial.map(v => ({ id: v.playerId, name: v.playerId, score: 0, crashes: 0 })),
    board: { width: 8, height: 7, walls: [], pits: [], obstacles: [], checkpoints: [], spawnPoints: [], conveyors: [], rotators: [], controlPoints: [] },
    vehicles: structuredClone(initial), placements: [], round: null,
    continuous: { revision: 1, tick: 3, serverTime: new Date().toISOString(), nextTick: new Date(Date.now() + 2000).toISOString(),
      intervalMillis: 2000, queue: [null, null, null, null, null], lastExecutedCommand: 'WAIT' },
  }
  const events = []
  function event(type, subject, source, x, y, direction = subject.direction) {
    const result = { sequence: events.length + 1, type, playerId: subject.playerId, vehicleId: subject.id,
      sourcePlayerId: source.playerId, sourceVehicleId: source.id, oldPosition: { x: subject.x, y: subject.y },
      newPosition: { x, y }, oldDirection: subject.direction, newDirection: direction, actionType: 'LASER', registerIndex: 1 }
    events.push(result)
    if (['PUSH', 'TURN'].includes(type)) Object.assign(subject, { x, y, direction })
  }
  const [a, b, c, d] = structuredClone(initial)
  event('WEAPON_FIRED', a, a, c.x, c.y)
  event('WEAPON_HIT', c, a, c.x, c.y)
  event('PUSH', c, a, 4, 2)
  event('TURN', c, a, 4, 2, 'EAST')
  event('WEAPON_FIRED', b, b, d.x, d.y)
  event('WEAPON_HIT', d, b, d.x, d.y)
  event('PUSH', d, b, 3, 4)
  event('TURN', d, b, 3, 4, 'SOUTH')
  if (reducedMotion) await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize(mobile ? { width: 390, height: 844 } : { width: 1366, height: 768 })
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)), { gameId, playerId, token: 'test-token' })
  let requests = 0
  await page.route(`**/games/${gameId}/players/${playerId}`, route => { requests++; return route.fulfill({ json: state }) })
  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('player-vehicle')).toHaveCount(4)
  await page.evaluate(() => {
    window.maxLaserCount = 0
    window.laserAppearances = 0
    new MutationObserver(records => {
      window.maxLaserCount = Math.max(window.maxLaserCount, document.querySelectorAll('[data-testid="laser-shot"]').length)
      for (const record of records) for (const node of record.addedNodes) {
        if (node.nodeType === 1) {
          if (node.matches('[data-testid="laser-shot"]')) window.laserAppearances++
          window.laserAppearances += node.querySelectorAll('[data-testid="laser-shot"]').length
        }
      }
    }).observe(document.querySelector('.board-stage'), { childList: true, subtree: true })
  })
  return {
    state, events, initial, requests: () => requests,
    fire() {
      state.continuous.tick++
      state.continuous.revision++
      state.continuous.lastExecutedCommand = 'LASER'
      state.vehicles = structuredClone([a, b, c, d])
      state.round = { state: { number: 4, phase: 'PLAYBACK', initialVehicles: initial, startEvents: [], playback: events } }
    },
  }
}

for (const mobile of [false, true]) {
  test(`continuous laser shows beams, a one-step push and full spins (${mobile ? 'mobile' : 'desktop'})`, async ({ page }) => {
    const game = await setup(page, { mobile })
    game.fire()
    await expect(page.getByTestId('laser-shot').first()).toBeVisible()
    await expect(page.locator('.laser-beam').first()).toHaveCSS('stroke-width', '6px')
    await expect(page.getByTestId('laser-shot').first()).toHaveAttribute('data-start-x', '1')
    await page.screenshot({ path: `/tmp/wreckage-laser-${mobile ? 'mobile' : 'desktop'}.png` })
    await expect.poll(() => page.evaluate(() => window.maxLaserCount)).toBe(2)
    for (const [id, x, direction] of [['target-c', '4', 'EAST'], ['target-d', '3', 'SOUTH']]) {
      const target = page.locator(`[data-testid="player-vehicle"][data-player-id="${id}"]`)
      await expect(target).toHaveAttribute('data-x', x)
      await expect(target).toHaveAttribute('data-direction', direction)
      await expect(target.getByTestId('vehicle-spin')).toHaveAttribute('data-spin-count', '1')
    }
    await expect(page.getByTestId('laser-shot')).toHaveCount(0)
    const appearances = await page.evaluate(() => window.laserAppearances)
    const polls = game.requests()
    game.state.continuous.revision++
    await expect.poll(game.requests).toBeGreaterThan(polls + 1)
    expect(await page.evaluate(() => window.laserAppearances)).toBe(appearances)
    expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBeLessThanOrEqual(mobile ? 844 : 768)
    await page.reload()
    await expect(page.getByTestId('player-vehicle')).toHaveCount(4)
    await expect(page.getByTestId('laser-shot')).toHaveCount(0)
    await expect(page.locator('[data-player-id="target-c"][data-testid="player-vehicle"]')).toHaveAttribute('data-x', '4')
  })
}

test('blocked shots show protection, and reduced motion disables spins while retaining the beam', async ({ page }) => {
  const game = await setup(page, { reducedMotion: true })
  game.events.splice(2)
  game.events.push({ ...game.events[1], sequence: 3, type: 'PUSH_BLOCKED' })
  game.fire()
  game.initial[3].status = 'CRASHED'
  game.state.vehicles = structuredClone(game.initial)
  await expect(page.getByTestId('laser-shot')).toBeVisible()
  await expect(page.getByTestId('laser-shot')).toHaveCSS('animation-name', 'none')
  await expect(page.getByTestId('laser-block')).toBeVisible()
  await expect(page.locator('[data-player-id="target-d"][data-testid="player-vehicle"]')).toHaveCount(0)
  const target = page.locator('[data-player-id="target-c"][data-testid="player-vehicle"]')
  await expect(target).toHaveAttribute('data-x', '3')
  await expect(target.getByTestId('vehicle-spin')).toHaveCSS('transition-duration', '0s')
  await expect(page.getByTestId('laser-shot')).toHaveCount(0)
  game.state.continuous.tick += 3
  game.state.continuous.revision++
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick', '7')
  await expect(page.getByTestId('laser-shot')).toHaveCount(0)
})
