import { expect, test } from '@playwright/test'
import { fillProgram, startGame } from './player-pages.mjs'

test('one browser can play against headless players', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Bjud in till nytt spel', exact: true }).click()
  await page.getByLabel('Spelarnamn', { exact: true }).fill('Alice')
  await page.getByRole('button', { name: 'Gå med', exact: true }).click()
  await expect(page.getByText('Headless 1', { exact: true })).toBeVisible({ timeout: 6000 })
  await expect(page.getByText('Headless 2', { exact: true })).toBeVisible({ timeout: 6000 })
  await expect(page.getByText('Headless 3', { exact: true })).toBeVisible({ timeout: 6000 })
  await startGame(page)

  await expect(page.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
  for (const name of ['Alice', 'Headless 1', 'Headless 2', 'Headless 3']) {
    await expect(page.getByText(name, { exact: true })).toBeVisible()
  }
  for (const name of ['Headless 1', 'Headless 2', 'Headless 3']) {
    await expect(page.getByTestId('player-ready-state').filter({ hasText: name })).toContainText('Redo')
  }

  await fillProgram(page)
  await expect(page.getByRole('heading', { name: 'Uppspelning', exact: true }).first()).toBeVisible()
  await expect(page.getByTestId('round-number')).toHaveText('Round 2', { timeout: 30_000 })
  await expect(page.getByTestId('round-start-dialog')).toContainText('Runda 2 startar')
  await expect(page.getByRole('button', { name: 'Starta nästa runda', exact: true })).not.toBeVisible()
  await expect(page.getByRole('button', { name: 'Lås program', exact: true })).toBeEnabled()
  for (const name of ['Headless 1', 'Headless 2', 'Headless 3']) {
    await expect(page.getByTestId('player-ready-state').filter({ hasText: name })).toContainText('Redo')
  }
})
