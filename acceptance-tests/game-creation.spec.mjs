import { expect, test } from '@playwright/test'

test('a user creates a game and adds Alice and Bob', async ({ browser }) => {
  await withPlayerPages(browser, ['creator', 'second player'], async ({ creator, secondPlayer }) => {
    await creator.goto('/')
    await secondPlayer.goto('/')

    expect(creator.context()).not.toBe(secondPlayer.context())
    await expect(creator.getByRole('heading', { name: 'WRECKAGE' })).toBeVisible()
    await expect(secondPlayer.getByRole('heading', { name: 'WRECKAGE' })).toBeVisible()

    await creator.getByRole('button', { name: 'Create Game' }).click()
    await expect(creator.getByRole('heading', { name: /^Game:/ })).toBeVisible()

    await addPlayer(creator, 'Alice')
    await addPlayer(creator, 'Bob')

    await expect(creator.getByRole('listitem')).toHaveText(['Alice', 'Bob'])
    await expect(secondPlayer.getByRole('heading', { name: /^Game:/ })).not.toBeVisible()
  })
})

async function addPlayer(page, name) {
  await page.getByLabel('Player name').fill(name)
  await page.getByRole('button', { name: 'Add Player' }).click()
  await expect(page.getByRole('listitem', { name })).toBeVisible()
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
