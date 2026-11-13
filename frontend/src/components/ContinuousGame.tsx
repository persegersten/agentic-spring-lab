import { useEffect, useMemo, useState } from 'react'
import { appendCommand } from '../api/games'
import { GameBoard } from './GameBoard'
import { ScoreTable } from './ScoreTable'
import type { MovementOrder, PlayerGame, PlayerSession } from '../types/game'

const commands: MovementOrder[] = ['FORWARD_1','FORWARD_2','FORWARD_3','REVERSE_1','TURN_LEFT','TURN_RIGHT','U_TURN','LASER']
type Pending = {command:MovementOrder; requestId:string}
export function ContinuousGame({view,session,onSnapshot}:{view:PlayerGame;session:PlayerSession;onSnapshot:(v:PlayerGame)=>void}) {
  const key = `wreckage-pending:${session.gameId}:${session.playerId}`
  const [pending,setPending] = useState<Pending|null>(() => JSON.parse(localStorage.getItem(key) ?? 'null'))
  const [busy,setBusy] = useState(false)
  const [error,setError] = useState('')
  const [now,setNow] = useState(Date.now())
  const c = view.continuous
  const offset = useMemo(() => c ? Date.now() - Date.parse(c.serverTime) : 0, [c])
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()),100); return () => clearInterval(timer) }, [])
  async function send(p:Pending) {
    setPending(p); localStorage.setItem(key,JSON.stringify(p)); setBusy(true); setError('')
    try { onSnapshot(await appendCommand(session,p.command,p.requestId)); setPending(null); localStorage.removeItem(key) }
    catch(e) { setError(e instanceof Error ? e.message : 'Request failed') }
    finally { setBusy(false) }
  }
  const destination = c?.queue[3] == null ? 4 : c.queue[4] == null ? 5 : null
  const crashed = view.vehicles.some(v => v.playerId === session.playerId && v.status === 'CRASHED')
  return <main className="game-page" data-testid="game-page">
    <section className="game-viewport" aria-label="Spelöversikt">
      <div className="game-heading">
        <h1>WRECKAGE — {view.status}</h1>
        <p>Tick {c?.tick ?? 0} · Next tick in {c ? Math.max(0,(Date.parse(c.nextTick)-now+offset)/1000).toFixed(1) : '…'}s</p>
      </div>
      <div className="board-stage">
        <GameBoard board={view.board} vehicles={view.vehicles.filter(v => v.status === 'ACTIVE')} players={view.players} currentPlayerId={view.playerId}/>
      </div>
    </section>
    <aside className="game-controls" aria-label="Spelkontroller">
    <section className="panel"><h2>Your queue → execution</h2>
      <p>All slots start empty. A command in slot 4 executes after four ticks (about 8 seconds). Empty slots execute NOP. Commands cannot be edited or removed.</p>
      <div className="queue-slots">{[5,4,3,2,1].map(slot => <div key={slot} data-testid={`slot-${slot}`}><strong>Slot {slot} {slot <= 3 ? '🔒' : ''}</strong><p>{c?.queue[slot-1] ?? 'NOP'}</p></div>)}</div>
      <p>{destination ? `Next append: slot ${destination}` : 'Queue full'}</p>
      <div className="queue-commands">{commands.map(command => <button key={command} disabled={!c || !destination || !!pending || busy || crashed || view.status !== 'RUNNING'} onClick={() => void send({command,requestId:crypto.randomUUID()})}>{command}</button>)}</div>
      {pending && <p>Unconfirmed command: {pending.command}. <button disabled={busy} onClick={() => void send(pending)}>Retry same request</button></p>}
      {error && <p role="alert">{error}</p>}{crashed && <p>Crashed — respawn attempted next tick.</p>}
    </section>
    <ScoreTable players={view.players} placements={view.placements}/>
    </aside>
  </main>
}
