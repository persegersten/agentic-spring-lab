import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('shield is selected outside the five-card program and activates for the round', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/')
    await alice.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const link = await alice.getByLabel('Spellänk').inputValue()
    await joinGame(alice, 'Alice')
    await bob.goto(link); await joinGame(bob, 'Bob'); await startGame(alice)

    await expect(alice.getByTestId('command-card')).toHaveCount(8)
    await expect(alice.getByTestId('program-slot')).toHaveCount(5)
    await expect(alice.getByTestId('shield-status')).toContainText('Tillgänglig')
    await fillProgram(alice, { shield: true })
    await fillProgram(bob)

    await expect(alice.getByTestId('round-playback')).toBeVisible({ timeout: 20_000 })
    await expect(alice.getByTestId('persistent-shield-status')).toContainText('Aktiv')
    await expect(alice.getByTestId('playback-events')).toContainText('aktiverar Shield')
  })
})
