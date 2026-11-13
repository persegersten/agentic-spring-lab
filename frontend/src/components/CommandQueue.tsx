import { useState } from 'react'
import type { CSSProperties } from 'react'
import type { MovementOrder, PlayerGame } from '../types/game'
import { commandLabels } from './commandLabels'

type Snapshot = NonNullable<PlayerGame['continuous']>
type Cargo = { id: number; command: MovementOrder; slot: number; entering: boolean }
type Frame = { signature: string; tick: number; cargo: Cargo[]; nextId: number; slide: boolean; pulse: boolean }

function signature(snapshot: Snapshot, crashed: boolean) {
  return `${snapshot.tick}:${crashed}:${snapshot.queue.join(',')}`
}

function initialFrame(snapshot: Snapshot, crashed: boolean): Frame {
  const cargo = snapshot.queue.flatMap((command, slot) => command ? [{ id: slot, command, slot, entering: false }] : [])
  return { signature: signature(snapshot, crashed), tick: snapshot.tick, cargo, nextId: 5, slide: false, pulse: false }
}

export function CommandQueue({ snapshot, crashed }: { snapshot: Snapshot; crashed: boolean }) {
  const [frame, setFrame] = useState(() => initialFrame(snapshot, crashed))
  const nextSignature = signature(snapshot, crashed)
  if (frame.signature !== nextSignature) {
    const advanced = snapshot.tick > frame.tick
    const slide = snapshot.tick === frame.tick + 1 && !crashed
    let nextId = frame.nextId
    const cargo = snapshot.queue.flatMap((command, slot) => {
      if (!command) return []
      const previousSlot = slide ? slot + 1 : slot
      const previous = (!advanced || slide) && frame.cargo.find(item => item.slot === previousSlot && item.command === command)
      return [{ id: previous ? previous.id : nextId++, command, slot, entering: !previous && (!advanced || slide) }]
    })
    // Derive the presentation from the newest snapshot before painting. No animation timer owns game state.
    setFrame({ signature: nextSignature, tick: snapshot.tick, cargo, nextId, slide,
      pulse: advanced ? snapshot.lastExecutedCommand != null : frame.pulse })
  }
  const destination = snapshot.queue[2] == null ? 3 : snapshot.queue[3] == null ? 4 : snapshot.queue[4] == null ? 5 : null
  const executed = snapshot.lastExecutedCommand
  return <section className={`command-machine ${frame.slide ? '' : 'queue-snap'}`} aria-label="Kommandokö">
    <div className="machine-header"><span className="machine-light" /> KÖ <span>↓</span></div>
    <div className="machine-track" data-testid="command-track">
      {[5, 4, 3, 2, 1].map(slot => {
        const command = snapshot.queue[slot - 1]
        const open = slot >= 3
        return <div key={slot} data-testid={`slot-${slot}`} data-slot-state={open ? 'input' : 'locked'}
          className={`machine-slot ${open ? 'slot-input' : 'slot-locked'} ${destination === slot && !crashed ? 'slot-next' : ''}`}>
          <span className="slot-number">{slot.toString().padStart(2, '0')}</span>
          <span className="slot-state" aria-label={open ? 'INMATNING' : 'LÅST'}><span aria-hidden="true">{open ? '＋' : '🔒'}</span><span className="queue-sr-only">{open ? 'INMATNING' : 'LÅST'}</span></span>
          {command ? <span className="queue-sr-only">{command}</span>
            : <span className="slot-empty">{open ? (destination === slot ? 'Nästa' : 'Ledig') : 'NOP'}</span>}
        </div>
      })}
      <div className="machine-lock-gate" aria-hidden="true"><span>▼ LÅSSPÄRR ▼</span></div>
      <div className="machine-cargo-layer" aria-hidden="true">
        {frame.cargo.map(item => <div key={item.id} data-testid="queue-cargo" data-command={item.command} data-cargo-id={item.id} data-slot={item.slot + 1}
          className={`queue-cargo ${item.entering ? 'cargo-entering' : ''}`}
          style={{ '--cargo-row': 4 - item.slot } as CSSProperties}>{commandLabels[item.command]}</div>)}
      </div>
    </div>
    <div className="machine-output-arrow" aria-hidden="true">▼</div>
    <div className="machine-execution" data-testid="execution-station" aria-live="polite" aria-atomic="true">
      <span className="execution-label">⚡ KÖR</span>
      <div key={snapshot.tick} data-testid="executed-command" data-execution-tick={snapshot.tick}
        className={`execution-command ${frame.pulse ? (executed === 'WAIT' ? 'execution-nop' : 'execution-pulse') : ''}`}>
        {executed == null ? 'Väntar' : commandLabels[executed]}
      </div>
      <small>{snapshot.tick > 0 ? `Senast utfört · tick ${snapshot.tick}` : 'Väntar på första tick'}</small>
    </div>
  </section>
}
