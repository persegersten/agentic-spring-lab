import { expect, test } from '@playwright/test'
import { joinGame, startGame, withPlayerPages } from './player-pages.mjs'

const flag = (page, id) => page.locator(`[data-testid="flag-status"][data-checkpoint-id="${id}"]`)
const marker = (page, id) => page.locator(`[data-testid="board-checkpoint"][data-checkpoint-id="${id}"]`)

async function drive(page, commands, name, x, y) {
  for (const label of commands) {
    const button = page.getByRole('button', { name: label, exact: true })
    await expect(button).toBeEnabled({ timeout: 10_000 })
    await button.click()
  }
  const robot = page.locator(`[data-testid="player-vehicle"][data-player-name="${name}"]`)
  await expect(robot).toHaveAttribute('data-x', String(x), { timeout: 20_000 })
  await expect(robot).toHaveAttribute('data-y', String(y), { timeout: 20_000 })
}

test('two real clients see exclusive ownership and sequential flag captures', async ({ browser }) => {
  test.setTimeout(120_000)
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/')
    await alice.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const link = await alice.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(alice, 'Alice')
    await bob.goto(link)
    await joinGame(bob, 'Bob')
    await startGame(alice)
    for (const page of [alice, bob]) {
      await expect(flag(page, 'CP1')).toHaveAttribute('data-state', 'available')
      await expect(flag(page, 'CP2')).toHaveAttribute('data-state', 'locked')
      await expect(marker(page, 'CP1')).toHaveAttribute('data-x', '2')
    }

    // Existing deterministic-e2e board: starts (0,0) and (1,0), facing SOUTH.
    // Clear Bob out of Alice's path, then put Alice one step from Flag 1.
    await drive(bob, ['Vänd', 'Framåt'], 'Bob', 1, 1)
    await drive(alice, ['Vänster', 'Framåt'], 'Alice', 1, 0)
    await drive(alice, ['Framåt'], 'Alice', 2, 0)
    for (const page of [alice, bob]) {
      await expect(flag(page, 'CP1')).toContainText('Alice')
      await expect(flag(page, 'CP1')).toHaveAttribute('data-state', 'owned')
      await expect(marker(page, 'CP1')).toHaveCount(0)
      await expect(flag(page, 'CP2')).toHaveAttribute('data-state', 'available')
      await expect(marker(page, 'CP2')).toHaveAttribute('data-state', 'available')
      await expect(flag(page, 'CP3')).toHaveAttribute('data-state', 'locked')
      await expect(flag(page, 'CP4')).toHaveAttribute('data-state', 'locked')
    }

    // Cross Flag 2, immediately unlocking Flag 3 before entering it.
    await drive(alice, ['Framåt', 'Framåt', 'Vänster', 'Framåt', 'Framåt'], 'Alice', 4, 2)
    // Bob now enters Flag 1's former cell without colliding with Alice.
    await drive(bob, ['Vänd', 'Framåt', 'Vänster', 'Framåt'], 'Bob', 2, 0)
    for (const page of [alice, bob]) {
      await expect(flag(page, 'CP1')).toContainText('Alice')
      await expect(marker(page, 'CP1')).toHaveCount(0)
      await expect(flag(page, 'CP2')).toHaveAttribute('data-state', 'owned')
      await expect(marker(page, 'CP2')).toHaveCount(0)
      await expect(flag(page, 'CP3')).toHaveAttribute('data-state', 'owned')
      await expect(marker(page, 'CP3')).toHaveCount(0)
      await expect(flag(page, 'CP4')).toHaveAttribute('data-state', 'available')
      await expect(page.getByTestId('score-row').filter({ hasText: 'Alice' }).getByTestId('player-score')).toHaveText('3')
      await expect(page.getByTestId('score-row').filter({ hasText: 'Bob' }).getByTestId('player-score')).toHaveText('0')
      const session = await page.evaluate(() => JSON.parse(localStorage.getItem(
        Object.keys(localStorage).find(key => key.startsWith('wreckage-session:')))))
      const response = await page.request.get(`/games/${session.gameId}/players/${session.playerId}`, {
        headers: { 'X-Player-Token': session.token },
      })
      expect(response.ok()).toBeTruthy()
      const snapshot = await response.json()
      expect(snapshot.players.find(player => player.name === 'Alice').visitedCheckpoints).toEqual(expect.arrayContaining(['CP1', 'CP2', 'CP3']))
      expect(snapshot.players.find(player => player.name === 'Bob').visitedCheckpoints).toEqual([])
    }
    await alice.screenshot({ path: '/tmp/wreckage-first-flag.png' })
  })
})

test('slice 1: ownership strip fits a narrow viewport and renders owner names as text', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000099'
  const ownerName = '<b>Alice & Bob</b> with a long name'
  const state = {
    id: gameId, playerId: 'bob', status: 'RUNNING', configuration: { maxPlayers: 2, programSize: 5 },
    players: [{ id: 'alice', name: ownerName, score: 2, visitedCheckpoints: ['CP1'] },
      { id: 'bob', name: 'Bob', score: 0, visitedCheckpoints: [] }],
    board: { width: 10, height: 10, walls: [], pits: [], obstacles: [], conveyors: [], rotators: [],
      controlPoints: [], checkpoints: [4, 2, 1, 3].map(order => ({ id: `CP${order}`, order, position: { x: order, y: 0 } })) },
    vehicles: [], placements: [], continuous: null, round: null,
  }
  await page.setViewportSize({ width: 320, height: 568 })
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)),
    { gameId, playerId: 'bob', token: 'test' })
  await page.route(`**/games/${gameId}/players/bob`, route => route.fulfill({ json: state }))
  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('flag-status').locator('b')).toHaveText(['1', '2', '3', '4'])
  await expect(flag(page, 'CP1')).toContainText(ownerName)
  await expect(flag(page, 'CP1').locator('span b')).toHaveCount(0)
  await expect(flag(page, 'CP2')).toHaveAttribute('data-state', 'available')
  await expect(marker(page, 'CP1')).toHaveCount(0)
  await expect(page.getByTestId('flag-strip')).toBeInViewport({ ratio: 1 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320)
  expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBeLessThanOrEqual(568)
})
