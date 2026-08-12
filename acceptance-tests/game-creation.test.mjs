import assert from 'node:assert/strict'
import { test } from 'node:test'

const baseUrl = process.env.BASE_URL ?? 'http://localhost:8080'

test('Given no game exists, when I create one and add Alice and Bob, then the game contains both players', async () => {
  // Given no game exists

  // When I create a game
  const game = await request('/games', { method: 'POST' }, 201)

  // And add player "Alice"
  await addPlayer(game.id, 'Alice')

  // And add player "Bob"
  await addPlayer(game.id, 'Bob')

  // Then the game contains Alice and Bob
  const savedGame = await request(`/games/${game.id}`, {}, 200)
  assert.deepEqual(
    savedGame.players.map((player) => player.name),
    ['Alice', 'Bob'],
  )
})

async function addPlayer(gameId, name) {
  return request(
    `/games/${gameId}/players`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    },
    201,
  )
}

async function request(path, options, expectedStatus) {
  const response = await fetch(`${baseUrl}${path}`, options)
  const body = await response.json()

  assert.equal(
    response.status,
    expectedStatus,
    `${options.method ?? 'GET'} ${path} returned ${response.status}: ${JSON.stringify(body)}`,
  )

  return body
}
