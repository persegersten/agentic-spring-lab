import { useEffect, useMemo, useState } from 'react'
import { appendCommand } from '../api/games'
import { ContinuousBoard } from './ContinuousBoard'
import { ScoreTable } from './ScoreTable'
import { CommandQueue } from './CommandQueue'
import { commandLabels } from './commandLabels'
import type { MovementOrder, PlayerGame, PlayerSession } from '../types/game'

const commands: MovementOrder[] = ['FORWARD_1','REVERSE_1','TURN_LEFT','TURN_RIGHT','U_TURN','LASER']
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
      <div className="game-heading">
        <h1>WRECKAGE <span>{view.status === 'FINISHED' ? 'Slut' : 'Spelar'}</span></h1>
        <p>Tick {c?.tick ?? 0} · {c ? Math.max(0,(Date.parse(c.nextTick)-now+offset)/1000).toFixed(1) : '…'} s</p>
      </div>
    <section className="game-viewport" aria-label="Spelöversikt">
      <div className="board-stage">
        <ContinuousBoard view={view}/>
      </div>
    </section>
    <aside className="game-controls" aria-label="Spelkontroller">
    <section className="panel queue-panel"><h2>Kommandokö</h2>
      {c && <CommandQueue snapshot={c} crashed={crashed} />}
      <div className="command-pad">
        <p className="queue-destination">{destination ? `Mata in → ${destination}` : 'Kön är full'}</p>
        <div className="queue-commands">{commands.map(command => <button key={command} aria-label={commandLabels[command]} title={command}
          disabled={!c || !destination || !!pending || busy || crashed || view.status !== 'RUNNING'} onClick={() => void send({command,requestId:crypto.randomUUID()})}>{commandLabels[command]}</button>)}</div>
        <p className="queue-help">4–5 öppna · 1–3 låsta<br />Ett steg per tick.</p>
      </div>
      <div className="queue-status" data-testid="queue-status">
        <p role={error ? 'alert' : 'status'} title={error || undefined} aria-label={error || undefined}>{error ? 'Kommandot kunde inte bekräftas.' : crashed ? 'Kraschad · återstart nästa tick.' : pending ? `Bekräftar ${commandLabels[pending.command]}…` : 'Kommandon kan inte ändras.'}</p>
        {pending && <button disabled={busy} title={error || undefined} onClick={() => void send(pending)}>Försök igen</button>}
      </div>
    </section>
    </aside>
    <ScoreTable players={view.players} placements={view.placements} compact currentPlayerId={session.playerId}/>
  </main>
}
