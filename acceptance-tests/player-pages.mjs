export async function joinGame(page, name) {
  await page.getByLabel('Spelarnamn', { exact: true }).fill(name)
  await page.getByRole('button', { name: 'Gå med', exact: true }).click()
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
