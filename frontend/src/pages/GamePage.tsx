import { useCallback, useEffect, useRef, useState } from 'react'
import { addPlayer, createGame, getGame, getPlayerGame, saveProgramDraft, startGame, startRound, submitProgram } from '../api/games'
import { CommandHand } from '../components/CommandHand'
import { GameBoard } from '../components/GameBoard'
import { RoundPlayback } from '../components/RoundPlayback'
import { RoundStatus } from '../components/RoundStatus'
import type { Game, PlayerGame, PlayerSession, RoundEvent, Vehicle } from '../types/game'

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
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [error, setError] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const [playbackDone, setPlaybackDone] = useState(false)
  const [startingRound, setStartingRound] = useState<number | null>(null)
  const [combatEvent, setCombatEvent] = useState<RoundEvent | undefined>()
  const advancingRound = useRef<number | null>(null)

  const refresh = useCallback(async () => {
    if (!session) return
    const next = await getPlayerGame(session)
    setView(next)
    setError(null)
    if (next.round?.state.phase !== 'PLAYBACK') setVehicles(next.vehicles)
  }, [session])

  useEffect(() => {
    const pathId = gameIdFromPath()
    if (pathId && session?.gameId !== pathId) void getGame(pathId).then(setGame).catch(e => setError(e.message))
  }, [session?.gameId])

  useEffect(() => {
    if (!session) return
    void refresh().catch(e => setError(e.message))
    const id = setInterval(() => void refresh().catch(e => setError(e.message)), 1500)
    return () => clearInterval(id)
  }, [session, refresh])

  useEffect(() => {
    if (!game || session || game.status !== 'WAITING_FOR_PLAYERS') return
    const id = window.setInterval(() => {
      void getGame(game.id).then(setGame).catch(e => setError(e.message))
    }, 1500)
    return () => window.clearInterval(id)
  }, [game, session])

  useEffect(() => {
    if (!session || !view?.round || !playbackDone || view.status === 'FINISHED') return
    const completedRound = view.round.state
    if (completedRound.phase !== 'PLAYBACK' || completedRound.number >= view.roundLimit || advancingRound.current === completedRound.number) return
    advancingRound.current = completedRound.number
    void startRound(session, completedRound.number).then(next => {
      const nextRound = next.round?.state.number
      setPlaybackDone(false)
      setView(next)
      setError(null)
      if (nextRound && nextRound > completedRound.number) setStartingRound(nextRound)
    }).catch(e => setError(e instanceof Error ? e.message : 'Ett oväntat fel inträffade'))
      .finally(() => { advancingRound.current = null })
  }, [session, view, playbackDone])

  useEffect(() => {
    if (startingRound === null) return
    const id = window.setTimeout(() => setStartingRound(null), 1800)
    return () => window.clearTimeout(id)
  }, [startingRound])

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
      const player = await addPlayer(game.id, name.trim())
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
    const round = view.round?.state
    const displayedVehicles = round?.phase === 'PLAYBACK' ? vehicles : view.vehicles
    const statusVehicles = round?.phase === 'PLAYBACK' && !playbackDone ? round.initialVehicles : view.vehicles
    const crashed = statusVehicles.filter(v => v.status === 'CRASHED').map(v => v.playerId)
    const ownCrashed = statusVehicles.some(v => v.playerId === view.playerId && v.status === 'CRASHED')
    const finished = view.status === 'FINISHED' && (round?.phase !== 'PLAYBACK' || playbackDone)
    const winners = (view.placements ?? []).filter(result => result.winner).map(result => view.players.find(player => player.id === result.playerId)!).filter(Boolean)
    const boardVehicles = displayedVehicles.filter(v => v.status === 'ACTIVE')
    const respawned = round?.startEvents?.some(event => event.type === 'VEHICLE_RESPAWNED' && event.playerId === view.playerId)
    const showProgram = round?.phase === 'PLANNING' && !ownCrashed && !finished
    return <main className="game-page" data-testid="game-page" data-game-status={view.status}><header><div><p className="eyebrow">Wreckage control deck</p><h1>WRECKAGE</h1></div><div className="game-identity"><span className="game-code">Spel {view.id.slice(0, 8)}</span><strong data-testid="game-status">{view.status}</strong></div></header>{error && <p className="error" role="alert">{error}</p>}{startingRound !== null && <div className="round-start-overlay" role="dialog" aria-modal="true" aria-labelledby="round-start-title" data-testid="round-start-dialog"><div className="round-start-dialog"><p className="eyebrow">Ny runda</p><h2 id="round-start-title">Runda {startingRound} startar</h2></div></div>}<div className={`game-layout${showProgram ? ' game-layout-with-program' : ''}`}>{showProgram && <CommandHand hand={view.round?.hand ?? []} program={view.round?.program ?? []} shieldSelected={view.round?.shieldSelected ?? false} shieldStatus={view.shieldStatus} programSize={view.configuration.programSize} locked={round.ready[view.playerId]} onReorder={async (orders, shield) => run(async () => setView(await saveProgramDraft(session, orders, shield)))} onSubmit={async (orders, shield) => run(async () => setView(await submitProgram(session, orders, shield)))} />}<section className="board-column"><GameBoard board={view.board} vehicles={boardVehicles} players={view.players} currentPlayerId={view.playerId} combatEvent={combatEvent} /></section><div className="sidebar">{finished && <section className="panel final-result" role="status" data-testid="finished-game"><h2>Match finished</h2><p>{winners.length === 1 ? `Winner: ${winners[0].name}` : `Draw: ${winners.map(player => player.name).join(', ')}`}</p></section>}<section className="panel" data-testid="persistent-shield-status"><h2>Shield</h2><p>Status: {{AVAILABLE:'Tillgänglig',SELECTED:'Vald',ACTIVE:'Aktiv',CONSUMED:'Förbrukad'}[view.shieldStatus]}</p><small>Kan användas en gång per match.</small></section><section className="panel checkpoint-standings" data-testid="checkpoint-standings"><h2>Checkpoints</h2>{view.players.map(player=><p key={player.id}>{player.name}: <strong>{player.capturedCheckpoints ?? player.visitedCheckpoints?.length ?? 0} / 4</strong></p>)}</section>{round && <RoundStatus round={round} roundLimit={view.roundLimit} players={view.players} crashed={crashed} />} {round && ownCrashed && <section className="panel" role="status"><h2>Du har kraschat</h2><p>Ingen spawnplats är ledig. Ett nytt försök görs nästa runda.</p></section>}{round?.phase === 'PLANNING' && respawned && <p role="status">Ditt fordon har respawnat.</p>}{round?.phase === 'PLAYBACK' && <RoundPlayback key={`${view.id}:${round.number}`} board={view.board} round={round} players={view.players} onVehicles={setVehicles} onCurrentEvent={setCombatEvent} onFinished={setPlaybackDone} />}</div></div></main>
  }

  const gameLink = lobby ? `${window.location.origin}/game/${lobby.id}/lobby` : ''
  return <main className="landing">
    <p className="eyebrow">Turn-based vehicular mayhem</p>
    <h1>WRECKAGE</h1>
    <p className="lead">Programmera. Kollidera. Se kaoset spelas upp.</p>
    {error && <p className="error" role="alert">{error}</p>}
    {!lobby && !gameIdFromPath() && <section className="panel main-lobby">
      <h2>Starta ett nytt spel</h2>
      <p>Skapa en spel-lobby och dela länken med dem du vill bjuda in.</p>
      <button disabled={working} onClick={() => void create()}>Bjud in till nytt spel</button>
    </section>}
    {lobby && <section className="panel game-lobby" data-testid="game-lobby">
      <h2>Spel-lobby</h2>
      <span className="game-code">Spel {lobby.id.slice(0, 8)}</span>
      {lobby.status === 'WAITING_FOR_PLAYERS' ? <>
        <label>Spellänk<input aria-label="Spellänk" readOnly value={gameLink} /></label>
        <button className="secondary" onClick={() => void run(() => navigator.clipboard.writeText(gameLink))}>Kopiera spellänk</button>
        <p>Brädstorlek och antal rundor bestäms när matchen startar.</p>
        <h3>Spelare anslutna</h3>
        {lobby.players.length ? <ul>{lobby.players.map(player => <li key={player.id} data-testid="lobby-player">{player.name}</li>)}</ul> : <p>Bli den första spelaren</p>}
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
