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

for (const reducedMotion of ['no-preference', 'reduce']) {
  test(`only the available flag pulses through captures and reloads (${reducedMotion})`, async ({ browser }) => {
    await scenario(browser, 'STANDING', async ({ pages, command, ownership }) => {
      async function checkAvailable(id) {
        for (const page of pages) {
          const available = page.locator('[data-testid="board-checkpoint"][data-state=available]')
          await expect(available).toHaveCount(1)
          await expect(available).toHaveAttribute('data-checkpoint-id', id)
          const locked = page.locator('[data-testid="board-checkpoint"][data-state=locked]')
          for (const element of await locked.all()) await expect(element).toHaveCSS('animation-name', 'none')
          if (reducedMotion === 'reduce') {
            await expect(available).toHaveCSS('animation-name', 'none')
            await expect(available).toHaveCSS('transform', 'none')
            await expect(available).toHaveCSS('filter', 'none')
            expect(await available.evaluate(el => getComputedStyle(el).boxShadow)).not.toBe('none')
          } else {
            // Sample the live CSS animation at its endpoints, without relying on wall-clock timing.
            const samples = await available.evaluate(el => {
              const animation = el.getAnimations()[0]
              if (!animation) return null
              const previousTime = animation.currentTime
              animation.pause()
              const sample = time => {
                animation.currentTime = time
                const style = getComputedStyle(el)
                return { scale: new DOMMatrixReadOnly(style.transform).a, brightness: style.filter, glow: style.boxShadow }
              }
              const low = sample(0), high = sample(600)
              animation.currentTime = previousTime
              animation.play()
              return { low, high }
            })
            expect(samples).not.toBeNull()
            expect(samples.high.scale).toBeGreaterThan(samples.low.scale)
            expect(samples.high.brightness).not.toBe(samples.low.brightness)
            expect(samples.high.glow).not.toBe(samples.low.glow)
          }
        }
      }
      for (const page of pages) await page.emulateMedia({ reducedMotion })
      await checkAvailable('CP1')
      await command(0, 'Framåt')
      await ownership('Alice', null)
      await checkAvailable('CP2')
      for (const page of pages) await page.reload()
      await checkAvailable('CP2')
      await command(1, 'Backa')
      await command(1, 'Framåt')
      await ownership('Alice', 'Bob')
      await checkAvailable('CP3')
      if (reducedMotion === 'no-preference') {
        await pages[0].screenshot({ path: '/tmp/wreckage-flag-pulse.png', fullPage: true })
        // A runtime preference change must immediately replace the pulse with a static highlight.
        for (const page of pages) {
          await page.emulateMedia({ reducedMotion: 'reduce' })
          await expect(marker(page, 'CP3')).toHaveCSS('animation-name', 'none')
          await expect(marker(page, 'CP3')).toHaveCSS('transform', 'none')
        }
        await pages[0].screenshot({ path: '/tmp/wreckage-flag-static.png', fullPage: true })
      }
    })
  })
}

for (const [variant,reducedMotion] of [['STANDING','no-preference'],['PUSH','no-preference'],['PUSH','reduce']]) {
  test(`capture then activation feedback reaches both players (${variant}, ${reducedMotion})`, async ({ browser }) => {
    await scenario(browser, variant, async ({ pages, tick, ownership }) => {
      for (const page of pages) {
        await page.emulateMedia({ reducedMotion })
        await page.clock.install()
        await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
      }
      await Promise.all([
        pages[0].waitForResponse(response => response.url().endsWith('/commands') && response.status() === 200),
        pages[0].getByRole('button', { name: 'Framåt', exact: true }).click(),
      ])
      await tick(2)
      for (const page of pages) {
        await page.clock.runFor(500)
        await expect(page.locator('.tick-status')).toContainText('Tick 2')
        await page.clock.runFor(1600)
      }
      const captured=await tick()
      for (const [index,page] of pages.entries()) {
        const effect=(kind,id)=>page.locator(`[data-effect="${kind}"][data-checkpoint-id="${id}"]`)
        async function advanceUntil(locator) {
          for(let i=0;i<100&&await locator.count()===0;i++)await page.clock.runFor(20)
          await expect(locator).toHaveCount(1)
        }
        await advanceUntil(effect('capture','CP1'))
        await expect(marker(page,'CP1')).toHaveCount(0)
        const checkpoint=captured.board.checkpoints.find(flag=>flag.id==='CP1')
        await expect(effect('capture','CP1')).toHaveAttribute('transform',`translate(${checkpoint.position.x+.5},${captured.board.height-checkpoint.position.y-.5})`)
        await expect(effect('activation','CP2')).toHaveCount(0)
        await advanceUntil(effect('activation','CP2'))
        await expect(effect('capture','CP1')).toHaveCount(0)
        if(variant==='PUSH') {
          await expect(effect('capture','CP2')).toHaveCount(1)
          await expect(marker(page,'CP2')).toHaveCount(0)
          await expect(marker(page,'CP3')).toHaveAttribute('data-state','available')
        } else {
          await expect(marker(page,'CP2')).toHaveAttribute('data-state','available')
          await expect(marker(page,'CP2')).toHaveCSS('animation-name','none')
        }
        await expect(effect('activation','CP2')).toHaveAttribute('data-motion',reducedMotion==='reduce'?'off':'on')
        if(index===0)await page.screenshot({path:`/tmp/wreckage-live-flag-${variant}-${reducedMotion}.png`})
        await page.clock.runFor(1700)
        await expect(page.getByTestId('board-effect')).toHaveCount(0)
        await expect(marker(page,variant==='PUSH'?'CP3':'CP2')).toHaveCSS('animation-name',reducedMotion==='reduce'?'none':'flag-available-pulse')
        await page.clock.runFor(650)
        await expect(page.getByTestId('board-effect')).toHaveCount(0)
        await page.reload()
        await expect(page.getByTestId('game-board')).toBeVisible()
        await expect(page.getByTestId('board-effect')).toHaveCount(0)
      }
      await ownership('Alice',variant==='PUSH'?'Bob':null)
    })
  })
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

test('Laser steals the prior flag before displacement captures the next for the target', async ({ browser }) => {
  await scenario(browser, 'LASER', async ({ pages, command, ownership }) => {
    await command(0, 'Laser')
    await ownership('Bob', null)
    await command(0, 'Laser')
    await ownership('Alice', 'Bob')
    for (const page of pages) {
      await expect(page.getByTestId('score-row').filter({ hasText: 'Bob' }).getByTestId('player-score')).toHaveText('1')
      await expect(page.getByTestId('score-row').filter({ hasText: 'Alice' }).getByTestId('player-score')).toHaveText('1')
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
