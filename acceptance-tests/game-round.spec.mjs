import { expect, test } from '@playwright/test'
import { joinGame, withPlayerPages } from './player-pages.mjs'

test('players build a private card program with double-click and drag-and-drop', async ({ browser }) => {
  await withPlayerPages(browser, ['alice', 'bob'], async ({ alice, bob }) => {
    await alice.goto('/'); await alice.getByLabel('Max spelare', { exact: true }).fill('2')
    await alice.getByLabel('Programstorlek', { exact: true }).fill('3')
    await alice.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    const gameId = await alice.getByLabel('Spel-id', { exact: true }).inputValue(); await joinGame(alice, 'Alice')
    await bob.goto('/'); await bob.getByLabel('Spel-id', { exact: true }).fill(gameId); await bob.getByRole('button', { name: 'Öppna spel', exact: true }).click(); await joinGame(bob, 'Bob')
    const slots = alice.getByTestId('program-slot')
    await expect(slots).toHaveCount(3)
    for (let index = 0; index < 3; index++) {
      await expect(slots.nth(index)).toHaveAttribute('data-command', 'WAIT')
      await expect(slots.nth(index)).toHaveAttribute('data-filled', 'false')
    }

    const forward = alice.locator('[data-testid="command-card"][data-command="FORWARD_1"]')
    await forward.dblclick(); await expect(alice.getByRole('status')).toHaveCount(0)
    await forward.dblclick(); await expect(alice.getByRole('status')).toHaveCount(0)
    await expect(slots.nth(0)).toHaveAttribute('data-command', 'FORWARD_1')
    await expect(slots.nth(1)).toHaveAttribute('data-command', 'FORWARD_1')

    await slots.nth(0).getByRole('button').dblclick(); await expect(alice.getByRole('status')).toHaveCount(0)
    await expect(slots.nth(0)).toHaveAttribute('data-command', 'FORWARD_1')
    await expect(slots.nth(1)).toHaveAttribute('data-command', 'WAIT')
    await expect(slots.nth(1)).toHaveAttribute('data-filled', 'false')

    await alice.locator('[data-testid="command-card"][data-command="TURN_LEFT"]').dragTo(slots.nth(1))
    await expect(alice.getByRole('status')).toHaveCount(0)
    await alice.locator('[data-testid="command-card"][data-command="WAIT"]').dragTo(slots.nth(2))
    await expect(alice.getByRole('status')).toHaveCount(0)
    await slots.nth(2).dragTo(slots.nth(0)); await expect(alice.getByRole('status')).toHaveCount(0)
    await expect(slots.nth(0)).toHaveAttribute('data-command', 'WAIT')
    await expect(slots.nth(1)).toHaveAttribute('data-command', 'FORWARD_1')
    await expect(slots.nth(2)).toHaveAttribute('data-command', 'TURN_LEFT')
    expect(await alice.getByTestId('command-card').evaluateAll(cards => cards.every(card => card.disabled))).toBe(true)

    await alice.getByRole('button', { name: 'Lås program', exact: true }).click()
    await expect(alice.getByRole('button', { name: 'Program låst', exact: true })).toBeDisabled()
    await expect(slots.nth(0)).toHaveAttribute('draggable', 'false')
    await expect(bob.getByTestId('player-ready-state').filter({ hasText: 'Alice' })).toContainText('Redo')
    await expect(bob.getByTestId('program-slot')).toHaveCount(3)
    for (let index = 0; index < 3; index++) {
      await expect(bob.getByTestId('program-slot').nth(index)).toHaveAttribute('data-command', 'WAIT')
      await expect(bob.getByTestId('program-slot').nth(index)).toHaveAttribute('data-filled', 'false')
    }
  })
})
