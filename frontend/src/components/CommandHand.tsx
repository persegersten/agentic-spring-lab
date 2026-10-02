import { useEffect, useState, type DragEvent, type KeyboardEvent } from 'react'
import type { MovementOrder } from '../types/game'

const all: MovementOrder[] = ['FORWARD_1', 'FORWARD_2', 'REVERSE_1', 'TURN_LEFT', 'TURN_RIGHT', 'U_TURN', 'WAIT']
const labels: Record<MovementOrder, string> = { FORWARD_1: 'Framåt 1', FORWARD_2: 'Framåt 2', REVERSE_1: 'Backa 1', TURN_LEFT: 'Sväng vänster', TURN_RIGHT: 'Sväng höger', U_TURN: 'U-sväng', WAIT: 'Vänta' }
const withDefaults = (program: MovementOrder[], size: number): MovementOrder[] => Array.from({ length: size }, (_, index) => program[index] ?? 'WAIT')
type DraggedCard = { source: 'commands'; command: MovementOrder } | { source: 'program'; index: number }

export function CommandHand({ program, programSize, locked, onReorder, onSubmit }: {
  program: MovementOrder[]
  programSize: number
  locked: boolean
  onReorder: (value: MovementOrder[]) => Promise<void>
  onSubmit: (value: MovementOrder[]) => Promise<void>
}) {
  const [draft, setDraft] = useState(() => program.slice(0, programSize))
  const [saving, setSaving] = useState(false)
  const [dragged, setDragged] = useState<DraggedCard | null>(null)
  const [dropIndex, setDropIndex] = useState<number | null>(null)
  const disabled = locked || saving

  useEffect(() => { if (!saving) setDraft(program.slice(0, programSize)) }, [program, programSize, saving])

  async function save(next: MovementOrder[]) {
    setDraft(next)
    setSaving(true)
    try { await onReorder(next) } finally { setSaving(false) }
  }

  function add(command: MovementOrder) {
    if (disabled || draft.length >= programSize) return
    void save([...draft, command])
  }

  function remove(index: number) {
    if (disabled || index >= draft.length) return
    void save(draft.filter((_, current) => current !== index))
  }

  function move(fromIndex: number, requestedIndex: number) {
    if (disabled || fromIndex === requestedIndex || fromIndex >= draft.length) return
    const next = [...draft]
    const [command] = next.splice(fromIndex, 1)
    const targetIndex = Math.min(requestedIndex, next.length)
    next.splice(targetIndex, 0, command)
    void save(next)
  }

  function startCommandDrag(event: DragEvent<HTMLButtonElement>, command: MovementOrder) {
    if (disabled || draft.length >= programSize) { event.preventDefault(); return }
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData('text/plain', command)
    setDragged({ source: 'commands', command })
  }

  function startProgramDrag(event: DragEvent<HTMLLIElement>, index: number) {
    if (disabled || index >= draft.length) { event.preventDefault(); return }
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
    setDragged({ source: 'program', index })
  }

  function dragOver(event: DragEvent<HTMLOListElement | HTMLLIElement>, index: number) {
    if (disabled || !dragged) return
    event.preventDefault()
    event.stopPropagation()
    event.dataTransfer.dropEffect = dragged.source === 'commands' ? 'copy' : 'move'
    setDropIndex(index)
  }

  function drop(event: DragEvent<HTMLOListElement | HTMLLIElement>, index: number) {
    event.preventDefault()
    event.stopPropagation()
    if (!disabled && dragged?.source === 'commands') add(dragged.command)
    if (!disabled && dragged?.source === 'program') move(dragged.index, index)
    stopDragging()
  }

  function stopDragging() {
    setDragged(null)
    setDropIndex(null)
  }

  function commandKeyDown(event: KeyboardEvent<HTMLButtonElement>, command: MovementOrder) {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    add(command)
  }

  const displayed = withDefaults(draft, programSize)

  return <section className="panel program-panel" data-testid="player-program" data-locked={locked}>
    <h2>Ditt program</h2>
    <p>Välj kort nedan. Programmet fylls från vänster och registren utförs i ordning.</p>
    <p className="program-state" data-testid="program-lock-state">{locked ? 'Programmet är låst' : 'Programmet kan ändras'}</p>
    {saving && <p role="status">Sparar program…</p>}
    <ol
      className={`program-stack${dragged ? ' program-stack-active' : ''}`}
      data-testid="program-slots"
      aria-label="Program-stack"
      onDragOver={event => dragOver(event, Math.max(0, draft.length - 1))}
      onDrop={event => drop(event, Math.max(0, draft.length - 1))}
    >
      {displayed.map((command, index) => {
        const filled = index < draft.length
        return <li
          className={`program-card${filled ? '' : ' program-card-default'}${dragged?.source === 'program' && dragged.index === index ? ' card-dragging' : ''}${dropIndex === index ? ' card-drop-target' : ''}`}
          key={index}
          data-testid="program-slot"
          data-slot={index + 1}
          data-command={command}
          data-filled={filled}
          draggable={filled && !disabled}
          onDragStart={event => startProgramDrag(event, index)}
          onDragOver={event => dragOver(event, index)}
          onDrop={event => drop(event, index)}
          onDragEnd={stopDragging}
        >
          <span className="program-card-register">{index + 1}</span>
          <button
            type="button"
            aria-label={`${filled ? 'Ta bort' : 'Tomt'} register ${index + 1}: ${labels[command]}`}
            disabled={disabled || !filled}
            onDoubleClick={() => remove(index)}
            onKeyDown={event => { if (event.key === 'Delete' || event.key === 'Backspace') remove(index) }}
          >{labels[command]}</button>
        </li>
      })}
    </ol>
    <h3 className="command-heading">Kommandon</h3>
    <ul className="command-cards" data-testid="command-cards" aria-label="Tillgängliga kommandon">
      {all.map(command => <li key={command}>
        <button
          type="button"
          className="command-card"
          data-testid="command-card"
          data-command={command}
          disabled={disabled || draft.length >= programSize}
          draggable={!disabled && draft.length < programSize}
          onDoubleClick={() => add(command)}
          onKeyDown={event => commandKeyDown(event, command)}
          onDragStart={event => startCommandDrag(event, command)}
          onDragEnd={stopDragging}
        >{labels[command]}</button>
      </li>)}
    </ul>
    <div className="actions">
      <button data-testid="lock-program" disabled={disabled} onClick={() => void onSubmit(withDefaults(draft, programSize))}>{locked ? 'Program låst' : 'Lås program'}</button>
      <button className="secondary" disabled={disabled || !draft.length} onClick={() => void save([])}>Rensa</button>
    </div>
  </section>
}
