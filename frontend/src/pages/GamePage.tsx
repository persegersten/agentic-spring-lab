import { useCallback, useEffect, useState } from 'react'
import { addPlayer, createGame, getGame, getPlayerGame, startGame } from '../api/games'
import { ContinuousGame } from '../components/ContinuousGame'
import type { Game, PlayerGame, PlayerSession } from '../types/game'

function gameIdFromPath() {
  return window.location.pathname.match(/^\/game\/([0-9a-f-]+)(?:\/lobby)?\/?$/i)?.[1] ?? ''
}

function playerSessionKey(gameId: string) {
  return `wreckage-session:${gameId}`
}

function loadPlayerSession(gameId: string): PlayerSession | null {
  if (!gameId) return null
  try {
    const session = JSON.parse(localStorage.getItem(playerSessionKey(gameId)) ?? 'null') as PlayerSession | null
    return session?.gameId === gameId && typeof session.playerId === 'string' && typeof session.token === 'string'
      ? session
      : null
  } catch {
    return null
  }
}

function savePlayerSession(session: PlayerSession) {
  localStorage.setItem(playerSessionKey(session.gameId), JSON.stringify(session))
}

function hostTokenKey(gameId: string) {
  return `wreckage-host:${gameId}`
}

export function GamePage() {
  const [game, setGame] = useState<Game | null>(null)
  const [view, setView] = useState<PlayerGame | null>(null)
  const [session, setSession] = useState<PlayerSession | null>(() => loadPlayerSession(gameIdFromPath()))
  const [name, setName] = useState('')
  const [hostToken] = useState(() => {
    const id = gameIdFromPath()
    return id ? localStorage.getItem(hostTokenKey(id)) ?? '' : ''
  })
  const [error, setError] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const apply = useCallback((next: PlayerGame) => setView(old =>
    old?.continuous && (!next.continuous || old.continuous.revision >= next.continuous.revision) ? old : next), [])

  const refresh = useCallback(async () => {
    if (!session) return
    const next = await getPlayerGame(session)
    apply(next)
    setError(null)
  }, [session, apply])

  useEffect(() => {
    const pathId = gameIdFromPath()
    if (pathId && session?.gameId !== pathId) void getGame(pathId).then(setGame).catch(e => setError(e.message))
  }, [session?.gameId])

  useEffect(() => {
    if (!session) return
    void refresh().catch(e => setError(e.message))
    const id = setInterval(() => void refresh().catch(e => setError(e.message)), 500)
    return () => clearInterval(id)
  }, [session, refresh])

  useEffect(() => {
    if (!game || session || game.status !== 'WAITING_FOR_PLAYERS') return
    const id = window.setInterval(() => {
      void getGame(game.id).then(setGame).catch(e => setError(e.message))
    }, 1500)
    return () => window.clearInterval(id)
  }, [game, session])

  async function run(action: () => Promise<void>) {
    setWorking(true); setError(null)
    try { await action() } catch (e) { setError(e instanceof Error ? e.message : 'Ett oväntat fel inträffade') }
    finally { setWorking(false) }
  }

  async function create() {
    await run(async () => {
      const created = await createGame()
      localStorage.setItem(hostTokenKey(created.id), created.hostToken)
      window.location.assign(`/game/${created.id}/lobby`)
    })
  }

  async function join() {
    if (!game || !name.trim()) return
    await run(async () => {
      const player = await addPlayer(game.id, name.trim(), hostToken || undefined)
      const nextSession = { gameId: game.id, playerId: player.id, token: player.token }
      savePlayerSession(nextSession)
      setSession(nextSession); setView(await getPlayerGame(nextSession))
    })
  }

  async function start() {
    const gameId = view?.id ?? game?.id
    if (!gameId || !session || !hostToken) return
    await run(async () => {
      const started = await startGame(gameId, hostToken)
      setGame(started)
      if (session) await refresh()
    })
  }

  const lobby = view ?? game

  useEffect(() => {
    if (!lobby) return
    const path = lobby.status === 'WAITING_FOR_PLAYERS' || !session
      ? `/game/${lobby.id}/lobby`
      : `/game/${lobby.id}`
    if (window.location.pathname !== path) window.history.replaceState({}, '', path)
  }, [lobby, session])

  if (view && session && view.status !== 'WAITING_FOR_PLAYERS') {
    return <ContinuousGame view={view} session={session} onSnapshot={apply} />
  }

  const gameLink = lobby ? `${window.location.origin}/game/${lobby.id}/lobby` : ''
  return <main className="landing">
    <p className="eyebrow">Continuous robot programming</p>
    <h1>WRECKAGE</h1>
    <p className="lead">Programmera. Kollidera. Se kaoset spelas upp.</p>
    {error && <p className="error" role="alert">{error}</p>}
    {!lobby && !gameIdFromPath() && <section className="panel main-lobby">
      <h2>Starta ett nytt spel</h2>
      <p>Skapa en spel-lobby och dela länken med dem du vill bjuda in.</p>
      <p>Map: Random Corridors</p>
      <button disabled={working} onClick={() => void create()}>Bjud in till nytt spel</button>
    </section>}
    {lobby && <section className="panel game-lobby" data-testid="game-lobby">
      <h2>Spel-lobby</h2>
      <span className="game-code">Spel {lobby.id.slice(0, 8)}</span>
      {lobby.status === 'WAITING_FOR_PLAYERS' ? <>
        <label>Spellänk<input aria-label="Spellänk" readOnly value={gameLink} /></label>
        <button className="secondary" disabled={working} onClick={() => void run(() => navigator.clipboard.writeText(gameLink))}>Kopiera spellänk</button>

        <p>Brädstorleken bestäms när matchen startar. Servern kör ett kommando varannan sekund.</p>
        <h3>Spelare anslutna</h3>
        {lobby.players.length ? <ul>{lobby.players.map(player => <li key={player.id}>{player.name} <span data-testid="player-kind">({player.automated ? 'Bot' : 'Human'})</span></li>)}</ul> : <p>Bli den första spelaren</p>}
        <p>{lobby.players.length} / {lobby.configuration.maxPlayers} spelare</p>
        {!session && <>
          <input aria-label="Spelarnamn" placeholder="Ditt namn" value={name} onChange={e => setName(e.target.value)} />
          <button disabled={working || !name.trim() || lobby.players.length >= lobby.configuration.maxPlayers} onClick={() => void join()}>Gå med</button>
        </>}
        {hostToken ? <button disabled={working || !session || lobby.players.length < 2} onClick={() => void start()}>Starta spelet</button>
          : <p>Väntar på att värden startar spelet…</p>}
      </> : <p>Spelet har redan startat och lobbyn är stängd.</p>}
    </section>}
  </main>
}
