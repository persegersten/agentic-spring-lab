import { useState } from 'react'
import { MotionConfig } from 'motion/react'
import { activateShield, appendCommand } from '../api/games'
import { ContinuousBoard } from './ContinuousBoard'
import { TickStatus } from './TickStatus'
import { ScoreTable } from './ScoreTable'
import { CommandQueue } from './CommandQueue'
import { useTickPresentation } from '../presentation/useTickPresentation'
import { gameAudio } from '../presentation/audio'
import { AudioControls } from './AudioControls'
import { Icon } from './Icon'
import { commandLabels } from './commandLabels'
import type { MovementOrder, PlayerGame, PlayerSession } from '../types/game'

const commands: MovementOrder[] = ['FORWARD_1','REVERSE_1','TURN_LEFT','TURN_RIGHT','U_TURN','LASER']
type Pending = {command:MovementOrder; requestId:string}
function loadShieldRequest(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}
export function ContinuousGame({view,session,onSnapshot,recoveryEpoch=0}:{recoveryEpoch?:number;view:PlayerGame;session:PlayerSession;onSnapshot:(v:PlayerGame)=>void}) {
  const key = `wreckage-pending:${session.gameId}:${session.playerId}`
  const [pending,setPending] = useState<Pending|null>(() => JSON.parse(localStorage.getItem(key) ?? 'null'))
  const [busy,setBusy] = useState(false)
  const [error,setError] = useState('')
  const shieldKey = `wreckage-pending-shield:${session.gameId}:${session.playerId}`
  const [shieldRequest, setShieldRequest] = useState(() => loadShieldRequest(shieldKey))
  const [shieldBusy, setShieldBusy] = useState(false)
  const [shieldError, setShieldError] = useState('')
  const presentation = useTickPresentation(view, recoveryEpoch)
  const c = view.continuous
  const finishing = view.status === 'FINISHED' && !presentation.settled

  async function send(p:Pending) {
    setPending(p); localStorage.setItem(key,JSON.stringify(p)); setBusy(true); setError('')
    try { onSnapshot(await appendCommand(session,p.command,p.requestId)); gameAudio.play('submit', `${session.gameId}:submit:${p.requestId}`); setPending(null); localStorage.removeItem(key) }
    catch(e) { setError(e instanceof Error ? e.message : 'Request failed') }
    finally { setBusy(false) }
  }
  async function activateMyShield() {
    const requestId = shieldRequest ?? crypto.randomUUID()
    setShieldRequest(requestId); localStorage.setItem(shieldKey, requestId)
    setShieldBusy(true); setShieldError('')
    try {
      onSnapshot(await activateShield(session, requestId))
      setShieldRequest(null); localStorage.removeItem(shieldKey)
    } catch (e) { setShieldError(e instanceof Error ? e.message : 'Skölden kunde inte bekräftas') }
    finally { setShieldBusy(false) }
  }
  const destination = c?.queue[2] == null ? 3 : c.queue[3] == null ? 4 : c.queue[4] == null ? 5 : null
  const crashed = view.vehicles.some(v => v.playerId === session.playerId && v.status === 'CRASHED')
  return <MotionConfig reducedMotion="user"><main className="game-page" data-testid="game-page">
      <div className="game-heading">
        <h1>WRECKAGE <span>{finishing ? 'Avslutar' : view.status === 'FINISHED' ? 'Slut' : 'Spelar'}</span></h1>
        <AudioControls />
        <TickStatus snapshot={c} running={view.status === 'RUNNING'} finishing={finishing}/>
      </div>
    <section className="game-viewport" aria-label="Spelöversikt">
      <div className="board-stage">
        <ContinuousBoard view={view} presentation={presentation}/>
      </div>
    </section>
    <aside className="game-controls" aria-label="Spelkontroller">
    <section className="panel queue-panel"><div className="queue-heading"><h2>Kommandokö</h2><span>3–5 öppna · 1–2 låsta</span></div>
      {c && <CommandQueue key={`${view.id}:${recoveryEpoch}`} snapshot={c} crashed={crashed} animate={presentation.queueAnimate} execution={presentation.execution} />}
      <div className="command-pad">
        <p className="queue-destination">{destination ? `Mata in → ${destination}` : 'Kön är full'}</p>
        <div className="queue-commands">{commands.map(command => <button key={command} aria-label={commandLabels[command]} title={command}
          disabled={!c || !destination || !!pending || busy || crashed || view.status !== 'RUNNING'} onClick={() => void send({command,requestId:crypto.randomUUID()})}><Icon name={command}/><span>{commandLabels[command]}</span></button>)}</div>
        <div className="shield-ability">
          <p data-testid="shield-status" role={shieldError ? 'alert' : 'status'} title={shieldError || undefined}>
            {shieldError ? 'Obekräftad · försök igen' : shieldRequest ? (shieldBusy ? 'Bekräftar sköld…' : 'Obekräftad · försök igen') : c?.shieldPending ? 'Startar nästa tick' : (c?.shieldTicksRemaining ?? 0) > 0 ? `Aktiv · ${c?.shieldTicksRemaining} tick kvar` : view.shieldAvailable ? 'Tillgänglig · 1 gång' : 'Förbrukad'}
          </p>
          <button data-testid="activate-shield" disabled={shieldBusy || (!shieldRequest && (!c || !view.shieldAvailable || crashed || view.status !== 'RUNNING'))}
            onClick={() => void activateMyShield()}><Icon name="shield"/>{shieldRequest ? 'Försök sköld igen' : 'Aktivera sköld'}</button>
        </div>
      </div>
      <div className="queue-status" data-testid="queue-status">
        <p role={error ? 'alert' : 'status'} title={error || undefined} aria-label={error || undefined}>{error ? 'Kommandot kunde inte bekräftas.' : crashed ? 'Kraschad · väntar på återstart.' : pending ? `Bekräftar ${commandLabels[pending.command]}…` : 'Kommandon kan inte ändras.'}</p>
        {pending && <button disabled={busy} title={error || undefined} onClick={() => void send(pending)}>Försök igen</button>}
      </div>
    </section>
    </aside>
    <ScoreTable board={view.board} checkpointCount={view.board.checkpoints.length} captureEffects={presentation.effects.filter(effect=>effect.kind==='capture')} players={presentation.players} transferredFlagIds={presentation.transferredFlagIds} placements={presentation.settled ? view.placements : []} compact currentPlayerId={session.playerId}/>
  </main></MotionConfig>
}
