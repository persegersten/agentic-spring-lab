import { useLayoutEffect, useRef, useState } from 'react'
import { motion } from 'motion/react'
import { useReducedMotionPreference } from '../presentation/useReducedMotionPreference'
import type { MovementOrder, PlayerGame } from '../types/game'
import { usePresentationTween } from '../presentation/usePresentationTween'
import { Icon } from './Icon'
import { commandLabels } from './commandLabels'

type Snapshot = NonNullable<PlayerGame['continuous']>
type Cargo = { id: number; command: MovementOrder; slot: number; entering: boolean }
type Execution = { id: string; command: MovementOrder } | null
type Frame = { signature: string; tick: number; cargo: Cargo[]; nextId: number; slide: boolean; pulse: boolean; width: number }

function signature(snapshot: Snapshot, crashed: boolean) {
  return `${snapshot.tick}:${crashed}:${snapshot.queue.join(',')}`
}
function initialFrame(snapshot: Snapshot, crashed: boolean): Frame {
  const cargo = snapshot.queue.flatMap((command, slot) => command ? [{ id: slot, command, slot, entering: false }] : [])
  return { signature: signature(snapshot, crashed), tick: snapshot.tick, cargo, nextId: 5, slide: false, pulse: false, width: 0 }
}
function QueueCargo({ item, width, travel, insert }: { item: Cargo; width: number; travel: boolean; insert: boolean }) {
  const reducedMotion = useReducedMotionPreference()
  const x = usePresentationTween((4 - item.slot) * (width + 4) / 5, travel, .3)
  return <motion.div data-testid="queue-cargo" data-command={item.command} data-cargo-id={item.id} data-slot={item.slot + 1}
    className="queue-cargo" style={{ x }} initial={insert && !reducedMotion ? { opacity: 0 } : false}
    animate={{ opacity: 1 }} transition={{ duration: reducedMotion || !insert ? 0 : .15 }}>
    <Icon name={item.command}/><span>{commandLabels[item.command]}</span>
  </motion.div>
}

export function CommandQueue({ snapshot, crashed, animate = true, execution = null }: {
  snapshot: Snapshot; crashed: boolean; animate?: boolean; execution?: Execution
}) {
  const track = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const [frame, setFrame] = useState(() => initialFrame(snapshot, crashed))
  useLayoutEffect(() => {
    const element = track.current
    if (!element) return
    const measure = () => setWidth(element.clientWidth)
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const nextSignature = signature(snapshot, crashed)
  if (frame.signature !== nextSignature) {
    const advanced = snapshot.tick > frame.tick
    const slide = snapshot.tick === frame.tick + 1 && !crashed && animate
    let nextId = frame.nextId
    const cargo = snapshot.queue.flatMap((command, slot) => {
      if (!command) return []
      const previousSlot = slide ? slot + 1 : slot
      const previous = (!advanced || slide) && frame.cargo.find(item => item.slot === previousSlot && item.command === command)
      return [{ id: previous ? previous.id : nextId++, command, slot, entering: !previous && (!advanced || slide) }]
    })
    setFrame({ signature: nextSignature, tick: snapshot.tick, cargo, nextId, width,
      slide: advanced ? slide : frame.slide,
      pulse: advanced ? animate && snapshot.lastExecutedCommand != null : frame.pulse })
  }
  const destination = snapshot.queue[2] == null ? 3 : snapshot.queue[3] == null ? 4 : snapshot.queue[4] == null ? 5 : null
  const executed = snapshot.lastExecutedCommand
  const travel = frame.slide && animate && width === frame.width
  return <section className={`command-machine ${travel ? '' : 'queue-snap'}`} aria-label="Kommandokö" data-queue-status={crashed ? 'crashed' : destination == null ? 'full' : 'ready'}>
    <div className="machine-header"><span className="machine-light" /> KÖ <span>→</span></div>
    <div ref={track} className="machine-track" data-testid="command-track">
      {[5, 4, 3, 2, 1].map(slot => {
        const command = snapshot.queue[slot - 1]
        const open = slot >= 3
        return <div key={slot} data-testid={`slot-${slot}`} data-slot-state={open ? 'input' : 'locked'}
          data-executing={slot === 1 && execution != null ? 'true' : 'false'}
          className={`machine-slot ${open ? 'slot-input' : 'slot-locked'} ${destination === slot && !crashed ? 'slot-next' : ''} ${slot === 1 && execution ? 'slot-executing' : ''}`}>
          <span className="slot-number">{slot.toString().padStart(2, '0')}</span>
          <span className="slot-state" aria-label={open ? 'INMATNING' : 'LÅST'}><span aria-hidden="true"><Icon name={open ? 'plus' : 'lock'}/></span><span className="queue-sr-only">{open ? 'INMATNING' : 'LÅST'}</span></span>
          {command ? <span className="queue-sr-only">{command}</span>
            : <span className="slot-empty">{open ? (destination === slot ? 'Nästa' : 'Ledig') : 'NOP'}</span>}
          {slot === 1 && execution && <span key={execution.id} className="slot-execution" data-testid="slot-execution"
            data-executed-command={execution.command} data-execution-id={execution.id} aria-hidden="true">
            <Icon name={execution.command}/><span>{commandLabels[execution.command]}</span>
          </span>}
        </div>
      })}
      <div className="machine-lock-gate" aria-hidden="true"><span>LÅSSPÄRR</span></div>
      <div className="machine-cargo-layer" aria-hidden="true">
        {width > 0 && frame.cargo.map(item => <QueueCargo key={item.id} item={item} width={width} travel={travel} insert={item.entering && animate}/>) }
      </div>
    </div>
    <div className="machine-output-arrow" aria-hidden="true">→</div>
    <div className="machine-execution" data-testid="execution-station" aria-live="polite" aria-atomic="true">
      <span className="execution-label"><Icon name="execute"/> KÖR</span>
      <div key={snapshot.tick} data-testid="executed-command" data-execution-tick={snapshot.tick}
        className={`execution-command ${frame.pulse && animate ? (executed === 'WAIT' ? 'execution-nop' : 'execution-pulse') : ''}`}>
        {executed == null ? 'Väntar' : commandLabels[executed]}
      </div>
      <small>{snapshot.tick > 0 ? `Senast utfört · tick ${snapshot.tick}` : 'Väntar på första tick'}</small>
    </div>
  </section>
}
