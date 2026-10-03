import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('an edge wall visibly blocks authoritative vehicle movement', async ({ browser }) => {
  test.setTimeout(45_000)
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.goto('/')
    await per.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await per.getByLabel('Spellänk', { exact: true }).inputValue()
    await joinGame(per, 'Per')

    await alice.goto(gameLink)
    await joinGame(alice, 'Alice')
    await startGame(per)

    await expect(per.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    await expect(alice.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()

    const perVehicle = per.getByTestId('player-vehicle').filter({ hasText: 'Per' })
    await expect(per.getByTestId('board-wall')).toHaveAttribute('data-x', '0')
    await expect(per.getByTestId('board-wall')).toHaveAttribute('data-y', '0')
    await expect(per.getByTestId('board-wall')).toHaveAttribute('data-direction', 'NORTH')
    await expect(perVehicle).toHaveAttribute('data-x', '0')
    await expect(perVehicle).toHaveAttribute('data-y', '0')

    await Promise.all([
      fillProgram(per, 'REVERSE_1'),
      fillProgram(alice, 'WAIT'),
    ])

    await expect(per.getByRole('heading', { name: 'Uppspelningen är klar' })).toBeVisible()
    await expect(perVehicle).toHaveAttribute('data-x', '0')
    await expect(perVehicle).toHaveAttribute('data-y', '0')
  })
})
