import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, withPlayerPages } from './player-pages.mjs'

test('two players complete a deterministic authoritative match', async ({ browser }) => {
  test.setTimeout(120_000)
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByLabel('Programstorlek', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')
    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')

    expect(per.context()).not.toBe(alice.context())
    for (const page of [per, alice]) {
      await expect(page.getByTestId('game-status')).toHaveText('RUNNING')
      await expect(page.getByTestId('round-number')).toHaveText('Round 1')
      await expect(page.getByTestId('round-limit')).toHaveText('7')
      await expect(page.getByTestId('planning-countdown')).toBeVisible()
      await expect(page.getByTestId('program-slot')).toHaveCount(2)
      await expect(page.getByTestId('initiative-player')).toHaveCount(2)
      await expect(page.getByTestId('player-ready-state')).toHaveCount(2)
      await expect(page.getByTestId('game-board')).toHaveAttribute('data-width', '10')
      await expect(page.getByTestId('board-wall')).toHaveCount(1)
      await expect(page.getByTestId('board-pit')).toHaveCount(1)
      await expect(page.getByTestId('board-checkpoint')).toHaveCount(1)
      await expect(page.getByTestId('board-conveyor')).toHaveCount(1)
      await expect(page.getByTestId('board-rotator')).toHaveCount(1)
      await expect(page.getByTestId('board-control-point')).toHaveCount(1)
      await expect(page.getByTestId('player-vehicle')).toHaveCount(2)
    }

    const perId = await per.getByTestId('player-vehicle').filter({ hasText: 'Per' }).getAttribute('data-player-id')
    const aliceId = await per.getByTestId('player-vehicle').filter({ hasText: 'Alice' }).getAttribute('data-player-id')
    await expect(per.getByTestId('player-vehicle').filter({ hasText: 'Per' })).toHaveAttribute('data-direction', 'SOUTH')

    await fillProgram(per, ['TURN_LEFT', 'FORWARD_1'])
    await expect(alice.getByTestId('player-ready-state').filter({ hasText: 'Per' })).toHaveAttribute('data-ready-state', 'LOCKED')
    await expect(alice.getByTestId('program-slot').nth(0)).toHaveAttribute('data-command', '')
    await fillProgram(alice, ['WAIT', 'WAIT'])

    await expect(per.getByTestId('round-playback')).toBeVisible()
    await expect(per.getByTestId('playback-event').filter({ hasText: 'Alice knuffas' })).toHaveAttribute('data-event-type', 'PUSH')
    await expect(per.getByTestId('playback-event').filter({ hasText: 'Alice +2 poäng' })).toHaveAttribute('data-event-type', 'SCORE_CHANGED')

    await per.reload()
    await expect(per.getByTestId('game-phase')).toHaveAttribute('data-phase', 'PLAYBACK')
    await expect(per.getByTestId('round-number')).toHaveText('Round 1')
    await expect(per.getByTestId('playback-event')).toHaveCount(4)
    await expect(per.locator(`[data-testid="score-row"][data-player-id="${aliceId}"]`).getByTestId('player-score')).toHaveText('2', { timeout: 15_000 })
    await expect(per.getByTestId('start-next-round')).toBeVisible({ timeout: 15_000 })

    for (let round = 2; round <= 7; round++) {
      await per.getByTestId('start-next-round').click()
      await expect(per.getByTestId('round-number')).toHaveText(`Round ${round}`)
      await expect(alice.getByTestId('round-number')).toHaveText(`Round ${round}`)
      if (round === 2) await expect(per.getByTestId('initiative-player').first()).toHaveAttribute('data-player-id', aliceId)
      await fillProgram(per, ['WAIT', 'WAIT'])
      await fillProgram(alice, ['WAIT', 'WAIT'])
      if (round < 7) await expect(per.getByTestId('start-next-round')).toBeVisible({ timeout: 15_000 })
    }

    for (const page of [per, alice]) {
      await expect(page.getByTestId('finished-game')).toBeVisible({ timeout: 15_000 })
      await expect(page.getByTestId('game-status')).toHaveText('FINISHED')
      await expect(page.getByTestId('start-next-round')).not.toBeVisible()
      await expect(page.getByTestId('final-standings')).toBeVisible()
      await expect(page.getByTestId('final-standing-row')).toHaveCount(2)
      await expect(page.locator(`[data-testid="final-standing-row"][data-player-id="${aliceId}"]`).getByTestId('player-score')).toHaveText('2')
      await expect(page.locator(`[data-testid="final-standing-row"][data-player-id="${perId}"]`).getByTestId('player-score')).toHaveText('0')
    }
    await per.reload()
    await expect(per.getByTestId('game-status')).toHaveText('FINISHED')
    await expect(per.getByTestId('round-playback')).toBeVisible()
    await expect(per.getByTestId('finished-game')).toBeVisible({ timeout: 15_000 })
  })
})
