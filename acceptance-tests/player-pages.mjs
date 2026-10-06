import { expect } from '@playwright/test'

export async function joinGame(page, name) {
  await page.getByLabel('Spelarnamn', { exact: true }).fill(name)
  await page.getByRole('button', { name: 'Gå med', exact: true }).click()
}

export async function startGame(page) {
  await page.getByRole('button', { name: 'Starta spelet', exact: true }).click()
}

export async function chooseLoadout(page, weapon, ability) {
  await page.getByTestId('loadout-weapon').selectOption(weapon)
  await expect(page.getByRole('status')).toHaveCount(0)
  await page.getByTestId('loadout-ability').selectOption(ability)
  await expect(page.getByRole('status')).toHaveCount(0)
}

export async function fillProgram(page, command = 'WAIT') {
  const slots = page.getByTestId('program-slot')
  for (let index = 0; index < await slots.count(); index++) {
    const selected = Array.isArray(command) ? command[index] : command
    const card = page.locator(`[data-testid="command-card"][data-command="${selected}"]`)
    await expect(card).toBeEnabled()
    await card.dblclick()
    await expect(page.getByRole('status')).toHaveCount(0)
  }
  await page.getByRole('button', { name: 'Lås program', exact: true }).click()
}

export async function withPlayerPages(browser, playerNames, runScenario) {
  const players = {}
  const contexts = []

  try {
    for (const playerName of playerNames) {
      const context = await browser.newContext()
      contexts.push(context)
      players[toPropertyName(playerName)] = await context.newPage()
    }

    await runScenario(players)
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
}

function toPropertyName(playerName) {
  return playerName.replace(/\s+(.)/g, (_, character) => character.toUpperCase())
}
