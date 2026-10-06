import { expect, test } from '@playwright/test'
import { chooseLoadout, fillProgram, joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('scheduled Repulsor playback pushes the first target without moving the shooter', async ({ browser }) => {
  await withPlayerPages(browser, ['shooter', 'target'], async ({ shooter, target }) => {
    await shooter.goto('/')
    await shooter.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await shooter.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(shooter, 'Shooter')
    await target.goto(gameLink)
    await joinGame(target, 'Target')
    await chooseLoadout(shooter, 'REPULSOR', 'TURBO')
    await startGame(shooter)

    await shooter.getByTestId('action-type').selectOption('REPULSOR')
    await fillProgram(shooter, ['TURN_LEFT', 'WAIT', 'WAIT'])
    await fillProgram(target, ['WAIT', 'WAIT', 'WAIT'])

    for (const page of [shooter, target]) {
      await expect(page.getByTestId('round-playback')).toBeVisible()
      await expect(page.getByTestId('playback-event').filter({ hasText: 'avfyrar Repulsor' })).toHaveCount(1)
      await expect(page.getByTestId('playback-event').filter({ hasText: 'knuffas av Repulsor' })).toHaveCount(1)
    }

    await expect(shooter.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'WEAPON_FIRED', { timeout: 10000 })
    await expect(shooter.getByTestId('laser-shot')).toHaveAttribute('data-weapon', 'REPULSOR')
    await expect(shooter.getByTestId('player-vehicle').filter({ hasText: 'Shooter' })).toHaveAttribute('data-x', '0')
    await expect(shooter.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'PUSH', { timeout: 10000 })
    await expect(shooter.getByTestId('player-vehicle').filter({ hasText: 'Target' })).toHaveAttribute('data-x', '2')
  })
})
