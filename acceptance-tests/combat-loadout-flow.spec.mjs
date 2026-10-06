import { expect, test } from '@playwright/test'
import { chooseLoadout, fillProgram, joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('selected loadout drives authoritative combat playback and survives reload', async ({ browser }) => {
  await withPlayerPages(browser, ['shooter', 'target'], async ({ shooter, target }) => {
    await shooter.goto('/')
    await shooter.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await shooter.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(shooter, 'Shooter')
    await target.goto(gameLink)
    await joinGame(target, 'Target')

    await expect(shooter.getByTestId('public-loadout').filter({ hasText: 'LASER + SHIELD' })).toHaveCount(2)
    await chooseLoadout(shooter, 'REPULSOR', 'TURBO')
    await expect(target.getByTestId('lobby-player').filter({ hasText: 'Shooter' }))
      .toContainText('REPULSOR + TURBO')
    await startGame(shooter)

    await expect(shooter.getByTestId('player-loadout')).toHaveText('Loadout: REPULSOR + TURBO')
    await expect(shooter.getByTestId('action-type').locator('option')).toHaveText(['Ingen action', 'Repulsor', 'Turbo'])
    await shooter.getByTestId('action-type').selectOption('REPULSOR')
    await shooter.getByTestId('action-register').selectOption('1')
    await fillProgram(shooter, ['TURN_LEFT', 'WAIT', 'WAIT'])
    await fillProgram(target, ['WAIT', 'WAIT', 'WAIT'])

    await expect(shooter.getByTestId('playback-event').filter({ hasText: 'avfyrar Repulsor' })).toHaveCount(1)
    await expect(shooter.getByTestId('playback-event').filter({ hasText: 'knuffas av Repulsor' })).toHaveCount(1)
    await expect(shooter.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'PUSH', { timeout: 15_000 })
    await expect(shooter.getByTestId('player-vehicle').filter({ hasText: 'Target' })).toHaveAttribute('data-x', '2')

    await shooter.reload()
    await expect(shooter.getByTestId('match-loadout').filter({ hasText: 'Shooter' }))
      .toHaveText('Shooter: REPULSOR + TURBO')
    await expect(shooter.getByTestId('player-vehicle').filter({ hasText: 'Target' })).toHaveAttribute('data-x', '2')
  })
})
