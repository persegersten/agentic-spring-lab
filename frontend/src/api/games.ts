import type { Game, Player } from '../types/game'

export async function createGame(): Promise<Game> {
  const response = await fetch('/games', { method: 'POST' })

  if (!response.ok) {
    throw new Error('Kunde inte skapa spelet')
  }

  return response.json() as Promise<Game>
}

export async function addPlayer(gameId: string, name: string): Promise<Player> {
  const response = await fetch(`/games/${gameId}/players`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })

  if (!response.ok) {
    throw new Error('Kunde inte lägga till spelaren')
  }

  return response.json() as Promise<Player>
}

export async function getGame(gameId: string): Promise<Game> {
  const response = await fetch(`/games/${gameId}`)

  if (!response.ok) {
    throw new Error('Kunde inte hämta spelet')
  }

  return response.json() as Promise<Game>
}
