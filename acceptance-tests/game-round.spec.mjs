import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('players fill private repeatable programs and share readiness only', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/'); await alice.getByLabel('Max spelare', { exact: true }).fill('2')
    await alice.getByLabel('Programstorlek', { exact: true }).fill('3')
    await alice.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameId = await alice.getByLabel('Spel-id', { exact: true }).inputValue(); await joinGame(alice, 'Alice')
    await bob.goto('/'); await bob.getByLabel('Spel-id', { exact: true }).fill(gameId); await bob.getByRole('button', { name: 'Öppna spel', exact: true }).click(); await joinGame(bob, 'Bob')
    const registers = alice.getByRole('combobox', { name: /^Register / }); await expect(registers).toHaveCount(3)
    for (let i=0;i<3;i++) await registers.nth(i).selectOption('WAIT')
    await alice.getByRole('button', { name: 'Lås program', exact: true }).click()
    await expect(alice.getByRole('button', { name: 'Program låst', exact: true })).toBeDisabled()
    await expect(bob.getByTestId('player-ready-state').filter({ hasText: 'Alice' })).toContainText('Redo')
    await expect(bob.getByRole('combobox', { name: /^Register / })).toHaveCount(3)
  })
})
