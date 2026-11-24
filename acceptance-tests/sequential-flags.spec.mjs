import { expect, test } from '@playwright/test'

const flag = (page, id) => page.locator(`[data-testid="flag-status"][data-checkpoint-id="${id}"]`)
const marker = (page, id) => page.locator(`[data-testid="board-checkpoint"][data-checkpoint-id="${id}"]`)

async function scenario(browser, name, run) {
  const contexts = []
  try {
    const context = await browser.newContext()
    contexts.push(context)
    const created = await context.request.post(`/games/test-fixtures/flags?scenario=${name}`)
    expect(created.ok(), `Fixture setup failed (${created.status()}): ${await created.text()}`).toBeTruthy()
    const fixture = await created.json()
    const pages = []
    for (const [index, session] of fixture.sessions.entries()) {
      const playerContext = index === 0 ? context : await browser.newContext()
      if (index) contexts.push(playerContext)
      await playerContext.addInitScript(value => localStorage.setItem(`wreckage-session:${value.gameId}`, JSON.stringify(value)), session)
      const page = await playerContext.newPage()
      await page.goto(`/game/${fixture.gameId}`)
      await expect(page.getByTestId('flag-strip')).toBeVisible()
      pages.push(page)
    }
    async function tick(count = 1) {
      const response = await context.request.post(`/games/test-fixtures/flags/${fixture.gameId}/ticks?count=${count}`)
      expect(response.ok()).toBeTruthy()
      return response.json()
    }
    async function command(index, label) {
      await Promise.all([
        pages[index].waitForResponse(response => response.url().endsWith('/commands') && response.status() === 200),
        pages[index].getByRole('button', { name: label, exact: true }).click(),
      ])
      return tick(3)
    }
    async function ownership(first, second) {
      for (const page of pages) {
        for (const [id, owner] of [['CP1', first], ['CP2', second]]) {
          await expect(flag(page, id)).toHaveAttribute('data-state', owner ? 'owned' : id === 'CP1' || first ? 'available' : 'locked')
          if (owner) {
            await expect(flag(page, id)).toContainText(owner)
            await expect(marker(page, id)).toHaveCount(0)
          }
        }
      }
    }
    await run({ pages, fixture, command, tick, ownership })
  } finally {
    await Promise.all(contexts.map(context => context.close()))
  }
}

test('standing on Flag 2 requires leaving and returning after Flag 1 unlocks it', async ({ browser }) => {
  await scenario(browser, 'STANDING', async ({ pages, command, ownership }) => {
    await command(0, 'Framåt')
    await ownership('Alice', null)
    for (const page of pages) await expect(page.getByTestId('score-row').filter({ hasText: 'Bob' }).getByTestId('player-score')).toHaveText('0')
    await command(1, 'Backa')
    await ownership('Alice', null)
    const result = await command(1, 'Framåt')
    await ownership('Alice', 'Bob')
    for (const player of result.players) {
      expect(player.capturedCheckpoints).toBe(1)
      expect(player.nextCheckpoint).toBe('CP3')
    }
    await command(0, 'Framåt') // Re-enter Flag 1's former cell.
    await ownership('Alice', 'Bob')
    for (const page of pages) {
      await expect(flag(page, 'CP3')).toHaveAttribute('data-state', 'available')
      await expect(marker(page, 'CP3')).toHaveAttribute('data-state', 'available')
      await expect(flag(page, 'CP4')).toHaveAttribute('data-state', 'locked')
      await expect(page.getByTestId('player-score')).toHaveText(['1', '1'])
    }
  })
})

for (const variant of ['PUSH', 'REVERSED_PUSH']) {
  test(`one atomic push claims Flags 1 and 2 in numerical order (${variant})`, async ({ browser }) => {
    await scenario(browser, variant, async ({ command, ownership }) => {
      const result = await command(0, 'Framåt')
      await ownership('Alice', 'Bob')
      const captures = result.round.playback.filter(event => event.scoreReason === 'CHECKPOINT')
      expect(captures.map(event => event.checkpointId)).toEqual(['CP1', 'CP2'])
      expect(captures.map(event => result.players.find(player => player.id === event.playerId).name)).toEqual(['Alice', 'Bob'])
    })
  })
}

test('a wall blocks the whole push chain without awarding any flags', async ({ browser }) => {
  await scenario(browser, 'BLOCKED_PUSH', async ({ pages, command, ownership }) => {
    const result = await command(0, 'Framåt')
    await ownership(null, null)
    expect(result.players.map(player => player.capturedCheckpoints)).toEqual([0, 0])
    expect(result.vehicles.map(vehicle => [vehicle.x, vehicle.y])).toEqual([[0, 1], [1, 1]])
    for (const page of pages) await expect(page.getByTestId('board-checkpoint')).toHaveCount(4)
  })
})

test('Laser displacement claims sequential flags for the target', async ({ browser }) => {
  await scenario(browser, 'LASER', async ({ pages, command, ownership }) => {
    await command(0, 'Laser')
    await ownership('Bob', null)
    await command(0, 'Laser')
    await ownership('Bob', 'Bob')
    for (const page of pages) {
      await expect(page.getByTestId('score-row').filter({ hasText: 'Bob' }).getByTestId('player-score')).toHaveText('2')
      await expect(page.getByTestId('score-row').filter({ hasText: 'Alice' }).getByTestId('player-score')).toHaveText('0')
    }
  })
})

test('a conveyor claims the next globally available flag', async ({ browser }) => {
  await scenario(browser, 'CONVEYOR', async ({ tick, ownership }) => {
    const result = await tick()
    await ownership('Alice', 'Bob')
    expect(result.round.playback.map(event => event.type)).toEqual(['CONVEYOR_MOVE', 'SCORE_CHANGED'])
    expect(result.round.playback[1].checkpointId).toBe('CP2')
  })
})
