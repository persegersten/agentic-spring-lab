import { expect, test } from '@playwright/test'
import { joinGame, startGame, withPlayerPages } from './player-pages.mjs'

test('players select five of eight private programming cards and reorder them', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/'); await alice.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
    const gameLink = await alice.getByLabel('Spellänk', { exact: true }).inputValue(); await joinGame(alice, 'Alice')
    await bob.goto(gameLink); await joinGame(bob, 'Bob'); await startGame(alice)

    const slots = alice.getByTestId('program-slot')
    const hand = alice.getByTestId('command-card')
    await expect(slots).toHaveCount(5)
    await expect(hand).toHaveCount(8)
    await expect(alice.getByTestId('lock-program')).toBeDisabled()

    for (let index = 0; index < 5; index++) {
      await alice.locator('[data-testid="command-card"]:enabled').first().dblclick()
      await expect(alice.getByRole('status')).toHaveCount(0)
    }
    await expect(alice.getByTestId('lock-program')).toBeEnabled()
    const before = await slots.evaluateAll(items => items.map(item => item.dataset.command))
    await slots.nth(4).dragTo(slots.nth(0))
    await expect(alice.getByRole('status')).toHaveCount(0)
    const after = await slots.evaluateAll(items => items.map(item => item.dataset.command))
    expect(after).toEqual([before[4], ...before.slice(0, 4)])

    await alice.getByTestId('lock-program').click()
    await expect(alice.getByRole('button', { name: 'Program låst', exact: true })).toBeDisabled()
    await expect(bob.getByTestId('player-ready-state').filter({ hasText: 'Alice' })).toContainText('Redo')
    await expect(bob.getByTestId('command-card')).toHaveCount(8)
    await expect(bob.locator('[data-testid="program-slot"][data-filled="true"]')).toHaveCount(0)
  })
})
