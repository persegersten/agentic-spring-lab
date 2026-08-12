import { type FormEvent, useState } from 'react'
import type { Game } from '../types/game'

type GameSummaryProps = {
  game: Game
  onAddPlayer: (name: string) => Promise<boolean>
  disabled: boolean
}

export function GameSummary({ game, onAddPlayer, disabled }: GameSummaryProps) {
  const [name, setName] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const playerName = name.trim()
    if (!playerName) return

    if (await onAddPlayer(playerName)) {
      setName('')
    }
  }

  return (
    <section className="game">
      <h2>Game: {game.id}</h2>
      <p>Board: {game.board.width} × {game.board.height}</p>

      <h3>Players</h3>
      {game.players.length === 0 ? (
        <p>No players yet.</p>
      ) : (
        <ul>
          {game.players.map((player) => (
            <li key={player.id}>{player.name}</li>
          ))}
        </ul>
      )}

      <form onSubmit={handleSubmit}>
        <label htmlFor="player-name">Player name</label>
        <input
          id="player-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          disabled={disabled}
        />
        <button type="submit" disabled={disabled || !name.trim()}>
          Add Player
        </button>
      </form>
    </section>
  )
}
