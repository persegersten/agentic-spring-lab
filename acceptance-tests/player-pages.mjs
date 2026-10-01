import { expect } from '@playwright/test'

export async function joinGame(page, name) {
  await page.getByLabel('Spelarnamn', { exact: true }).fill(name)
  await page.getByRole('button', { name: 'Gå med', exact: true }).click()
}

export async function fillProgram(page, command = 'WAIT') {
  const registers = page.getByRole('combobox', { name: /^Register / })
  for (let index = 0; index < await registers.count(); index++) {
    await expect(registers.nth(index)).toBeEnabled()
    await registers.nth(index).selectOption(Array.isArray(command) ? command[index] : command)
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
