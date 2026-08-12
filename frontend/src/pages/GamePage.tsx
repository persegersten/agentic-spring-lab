import { useState } from 'react'
import { addPlayer, createGame, getGame } from '../api/games'
import { GameSummary } from '../components/GameSummary'
import type { Game } from '../types/game'

export function GamePage() {
  const [game, setGame] = useState<Game | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isWorking, setIsWorking] = useState(false)

  async function handleCreateGame() {
    setIsWorking(true)
    setError(null)

    try {
      const createdGame = await createGame()
      setGame(await getGame(createdGame.id))
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Ett oväntat fel inträffade')
    } finally {
      setIsWorking(false)
    }
  }

  async function handleAddPlayer(name: string) {
    if (!game) return false

    setIsWorking(true)
    setError(null)

    try {
      await addPlayer(game.id, name)
      setGame(await getGame(game.id))
      return true
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Ett oväntat fel inträffade')
      return false
    } finally {
      setIsWorking(false)
    }
  }

  return (
    <main className="game-page">
      <h1>WRECKAGE</h1>
      <button type="button" onClick={handleCreateGame} disabled={isWorking}>
        Create Game
      </button>
      {error && <p className="error">{error}</p>}
      {game && (
        <GameSummary game={game} onAddPlayer={handleAddPlayer} disabled={isWorking} />
      )}
    </main>
  )
}
