import { useCallback, useEffect, useState } from 'react'
import { addPlayer, createGame, getDefaultConfiguration, getGame, getPlayerGame, saveProgramDraft, startRound, submitProgram } from '../api/games'
import { CommandHand } from '../components/CommandHand'
import { GameBoard } from '../components/GameBoard'
import { RoundPlayback } from '../components/RoundPlayback'
import { RoundStatus } from '../components/RoundStatus'
import { ScoreTable } from '../components/ScoreTable'
import type { Game, GameConfiguration, PlayerGame, PlayerSession, Vehicle } from '../types/game'

function gameIdFromPath() {
  return window.location.pathname.match(/^\/game\/([0-9a-f-]+)\/?$/i)?.[1] ?? ''
}

function LobbyCountdown({ joinDeadline }: { joinDeadline: string }) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 250)
    return () => window.clearInterval(id)
  }, [])
  const remaining = Math.max(0, Math.ceil((new Date(joinDeadline).getTime() - now) / 1000))
  return <p data-testid="join-countdown" aria-live="polite">Tid kvar att ansluta: {remaining} sekunder</p>
}

export function GamePage() {
  const [game, setGame] = useState<Game | null>(null)
  const [view, setView] = useState<PlayerGame | null>(null)
  const [session, setSession] = useState<PlayerSession | null>(() => {
    const stored = JSON.parse(sessionStorage.getItem('wreckage-session') ?? 'null') as PlayerSession | null
    const pathId = gameIdFromPath()
    return !pathId || stored?.gameId === pathId ? stored : null
  })
  const [configuration, setConfiguration] = useState<GameConfiguration | null>(null)
  const [name, setName] = useState('')
  const [gameId, setGameId] = useState(gameIdFromPath)
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [error, setError] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const [playbackDone, setPlaybackDone] = useState(false)
  const [scores, setScores] = useState<Record<string, number>>({})

  const refresh = useCallback(async () => {
    if (!session) return
    const next = await getPlayerGame(session)
    setView(next)
    if (next.round?.state.phase !== 'PLAYBACK') setScores(Object.fromEntries(next.players.map(player => [player.id, player.score])))
    setError(null)
    if (next.round?.state.phase !== 'PLAYBACK') setVehicles(next.vehicles)
  }, [session])

  useEffect(() => {
    void getDefaultConfiguration().then(setConfiguration).catch(e => setError(e.message))
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

  async function run(action: () => Promise<void>) {
    setWorking(true); setError(null)
    try { await action() } catch (e) { setError(e instanceof Error ? e.message : 'Ett oväntat fel inträffade') }
    finally { setWorking(false) }
  }

  async function create() {
    if (!configuration) return
    await run(async () => {
      const created = await createGame(configuration)
      setGame(created); setGameId(created.id)
      window.history.pushState({}, '', `/game/${created.id}`)
    })
  }

  async function load() {
    await run(async () => {
      const loaded = await getGame(gameId.trim())
      setGame(loaded)
      window.history.pushState({}, '', `/game/${loaded.id}`)
    })
  }

  async function join() {
    if (!game || !name.trim()) return
    await run(async () => {
      const player = await addPlayer(game.id, name.trim())
      const nextSession = { gameId: game.id, playerId: player.id, token: player.token }
      sessionStorage.setItem('wreckage-session', JSON.stringify(nextSession))
      setSession(nextSession); setView(await getPlayerGame(nextSession))
    })
  }

  if (view && session) {
    const round = view.round?.state
    const displayedVehicles = round?.phase === 'PLAYBACK' ? vehicles : view.vehicles
    const statusVehicles = round?.phase === 'PLAYBACK' && !playbackDone ? round.initialVehicles : view.vehicles
    const crashed = statusVehicles.filter(v => v.status === 'CRASHED').map(v => v.playerId)
    const ownCrashed = statusVehicles.some(v => v.playerId === view.playerId && v.status === 'CRASHED')
    const finished = view.status === 'FINISHED' && (round?.phase !== 'PLAYBACK' || playbackDone)
    const winners = (view.placements ?? []).filter(result => result.winner).map(result => view.players.find(player => player.id === result.playerId)!).filter(Boolean)
    const boardVehicles = displayedVehicles.filter(v => v.status === 'ACTIVE')
    const respawned = round?.startEvents?.some(event => event.type === 'VEHICLE_RESPAWNED' && event.playerId === view.playerId)
    return <main className="game-page" data-testid="game-page" data-game-status={view.status}><header><div><p className="eyebrow">Wreckage control deck</p><h1>WRECKAGE</h1></div><div className="game-identity"><span className="game-code">Spel {view.id.slice(0, 8)}</span><strong data-testid="game-status">{view.status}</strong></div></header>{error && <p className="error" role="alert">{error}</p>}<div className="game-layout"><section><GameBoard board={view.board} vehicles={boardVehicles} players={view.players} currentPlayerId={view.playerId} /></section><div className="sidebar"><ScoreTable players={view.players} scores={round?.phase === 'PLAYBACK' ? scores : undefined} placements={finished ? view.placements : []} />{!round && <section className="panel"><h2>Spelare anslutna</h2><p>{view.players.map(p => p.name).join(', ')}</p><p>{view.players.length} / {view.configuration.maxPlayers} spelare</p><LobbyCountdown joinDeadline={view.joinDeadline} /></section>}{round && <RoundStatus round={round} roundLimit={view.configuration.roundLimit} players={view.players} crashed={crashed} />} {round && ownCrashed && <section className="panel" role="status"><h2>Du har kraschat</h2><p>Ingen spawnplats är ledig. Ett nytt försök görs nästa runda.</p></section>}{round?.phase === 'PLANNING' && respawned && <p role="status">Ditt fordon har respawnat.</p>}{finished && <section className="panel final-result" role="status" data-testid="finished-game"><h2>Spelet är slut</h2><p>{winners.length === 1 ? `${winners[0].name} vann!` : `${winners.map(player => player.name).join(' och ')} delar segern.`}</p></section>}{round?.phase === 'PLANNING' && !ownCrashed && !finished && <CommandHand program={view.round?.program ?? []} programSize={view.configuration.programSize} locked={round.ready[view.playerId]} onReorder={async orders => run(async () => setView(await saveProgramDraft(session, orders)))} onSubmit={async orders => run(async () => setView(await submitProgram(session, orders)))} />} {round?.phase === 'PLAYBACK' && <RoundPlayback key={`${view.id}:${round.number}`} board={view.board} round={round} players={view.players} onVehicles={setVehicles} onScores={setScores} onFinished={setPlaybackDone} />} {round?.phase === 'PLAYBACK' && playbackDone && !finished && <button data-testid="start-next-round" onClick={() => void run(async () => { setPlaybackDone(false); setView(await startRound(session)) })}>Starta nästa runda</button>}</div></div></main>
  }

  const gameLink = game ? `${window.location.origin}/game/${game.id}` : ''
  return <main className="landing"><p className="eyebrow">Turn-based vehicular mayhem</p><h1>WRECKAGE</h1><p className="lead">Programmera. Kollidera. Se kaoset spelas upp.</p>{error && <p className="error" role="alert">{error}</p>}{!game && <><section className="panel create-game"><h2>Skapa spel</h2>{configuration ? <><label>Max spelare<input aria-label="Max spelare" type="number" min="1" value={configuration.maxPlayers} onChange={e => setConfiguration({ ...configuration, maxPlayers: Number(e.target.value) })} /></label><label>Anslutningstid (sekunder)<input aria-label="Anslutningstid" type="number" min="1" value={configuration.joinTimeoutSeconds} onChange={e => setConfiguration({ ...configuration, joinTimeoutSeconds: Number(e.target.value) })} /></label><label>Programstorlek<input aria-label="Programstorlek" type="number" min="1" max="5" value={configuration.programSize} onChange={e => setConfiguration({ ...configuration, programSize: Number(e.target.value) })} /></label><label>Planeringstid (sekunder)<input aria-label="Planeringstid" type="number" min="1" value={configuration.planningTimeoutSeconds} onChange={e => setConfiguration({ ...configuration, planningTimeoutSeconds: Number(e.target.value) })} /></label><label>Antal rundor<input aria-label="Antal rundor" type="number" min="1" value={configuration.roundLimit} onChange={e => setConfiguration({ ...configuration, roundLimit: Number(e.target.value) })} /></label><button disabled={working} onClick={() => void create()}>Skapa spel</button></> : <p>Laddar inställningar…</p>}</section><div className="lobby panel"><span>Har du ett spel-id?</span><input aria-label="Spel-id" placeholder="Klistra in spel-id" value={gameId} onChange={e => setGameId(e.target.value)} /><button className="secondary" disabled={working || !gameId.trim()} onClick={() => void load()}>Öppna spel</button></div></>}{game && <section className="panel join"><h2>Anslut till spelet</h2><label>Spellänk<input aria-label="Spellänk" readOnly value={gameLink} /></label><input aria-label="Spel-id" readOnly className="visually-hidden" value={game.id} /><button className="secondary" onClick={() => void navigator.clipboard.writeText(gameLink)}>Kopiera spellänk</button><p>Max {game.configuration.maxPlayers} spelare · {game.configuration.programSize} kort per runda</p><p>{game.configuration.roundLimit} rundor</p><p>{game.players.length ? `${game.players.length} spelare väntar` : 'Bli den första spelaren'}</p>{game.status === 'WAITING_FOR_PLAYERS' && <LobbyCountdown joinDeadline={game.joinDeadline} />}<input aria-label="Spelarnamn" placeholder="Ditt namn" value={name} onChange={e => setName(e.target.value)} /><button disabled={working || !name.trim()} onClick={() => void join()}>Gå med</button></section>}</main>
}
