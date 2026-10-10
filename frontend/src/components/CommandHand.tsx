import { useEffect, useState, type DragEvent, type KeyboardEvent } from 'react'
import type { MovementOrder, ShieldStatus } from '../types/game'

const labels: Record<MovementOrder, string> = { FORWARD_1: 'Framåt 1', FORWARD_2: 'Framåt 2', FORWARD_3: 'Framåt 3', REVERSE_1: 'Backa 1', TURN_LEFT: 'Sväng vänster', TURN_RIGHT: 'Sväng höger', U_TURN: 'U-sväng', LASER: 'Laser', WAIT: 'Vänta' }
const withDefaults = (program: MovementOrder[], size: number): MovementOrder[] => Array.from({ length: size }, (_, index) => program[index] ?? 'WAIT')
type DraggedCard = { source: 'hand'; index: number; command: MovementOrder } | { source: 'program'; index: number }

export function CommandHand({ hand, program, shieldSelected, shieldStatus, programSize, locked, onReorder, onSubmit }: {
  hand: MovementOrder[]
  program: MovementOrder[]
  shieldSelected: boolean
  shieldStatus: ShieldStatus
  programSize: number
  locked: boolean
  onReorder: (value: MovementOrder[], shieldSelected: boolean) => Promise<void>
  onSubmit: (value: MovementOrder[], shieldSelected: boolean) => Promise<void>
}) {
  const [draft, setDraft] = useState(() => program.slice(0, programSize))
  const [shield, setShield] = useState(shieldSelected)
  const [saving, setSaving] = useState(false)
  const [dragged, setDragged] = useState<DraggedCard | null>(null)
  const [dropIndex, setDropIndex] = useState<number | null>(null)
  const disabled = locked || saving
  useEffect(() => { if (!saving) { setDraft(program.slice(0, programSize)); setShield(shieldSelected) } }, [program, shieldSelected, programSize, saving])

  async function save(next: MovementOrder[]) {
    setDraft(next)
    setSaving(true)
    try { await onReorder(next, shield) } finally { setSaving(false) }
  }

  async function saveShield(next: boolean) {
    setShield(next)
    setSaving(true)
    try { await onReorder(draft, next) } finally { setSaving(false) }
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

  function isUsed(handIndex: number) {
    const command = hand[handIndex]
    const occurrence = hand.slice(0, handIndex + 1).filter(card => card === command).length
    return draft.filter(card => card === command).length >= occurrence
  }

  function startCommandDrag(event: DragEvent<HTMLButtonElement>, command: MovementOrder, index: number) {
    if (disabled || draft.length >= programSize || isUsed(index)) { event.preventDefault(); return }
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData('text/plain', command)
    setDragged({ source: 'hand', index, command })
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
    event.dataTransfer.dropEffect = dragged.source === 'hand' ? 'copy' : 'move'
    setDropIndex(index)
  }

  function drop(event: DragEvent<HTMLOListElement | HTMLLIElement>, index: number) {
    event.preventDefault()
    event.stopPropagation()
    if (!disabled && dragged?.source === 'hand' && !isUsed(dragged.index)) add(dragged.command)
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
            aria-label={`${filled ? 'Ta bort' : 'Tomt'} programsteg ${index + 1}: ${labels[command]}`}
            disabled={disabled || !filled}
            onDoubleClick={() => remove(index)}
            onKeyDown={event => { if (event.key === 'Delete' || event.key === 'Backspace') remove(index) }}
          >{labels[command]}</button>
        </li>
      })}
    </ol>
    <h3 className="command-heading">Din hand</h3>
    <ul className="command-cards" data-testid="programming-hand" aria-label="Utdelade programmeringskort">
      {hand.map((command, index) => <li key={`${command}-${index}`}>
        <button
          type="button"
          className="command-card"
          data-testid="command-card"
          data-command={command}
          data-hand-index={index}
          data-used={isUsed(index)}
          disabled={disabled || draft.length >= programSize || isUsed(index)}
          draggable={!disabled && draft.length < programSize && !isUsed(index)}
          onDoubleClick={() => { if (!isUsed(index)) add(command) }}
          onKeyDown={event => commandKeyDown(event, command)}
          onDragStart={event => startCommandDrag(event, command, index)}
          onDragEnd={stopDragging}
        >{labels[command]}</button>
      </li>)}
    </ul>
    <fieldset className="shield-control" disabled={disabled || shieldStatus === 'CONSUMED'} data-testid="shield-controls">
      <legend>Sköld — engångsresurs</legend>
      <label><input type="checkbox" data-testid="activate-shield" checked={shield}
        onChange={event => void saveShield(event.target.checked)} /> Aktivera sköld för denna runda</label>
      <p data-testid="shield-status">Status: {{AVAILABLE:'Tillgänglig',SELECTED:'Vald',ACTIVE:'Aktiv',CONSUMED:'Förbrukad'}[shieldStatus]}</p>
    </fieldset>
    <div className="actions">
      <button data-testid="lock-program" disabled={disabled || draft.length !== programSize} onClick={() => void onSubmit(draft, shield)}>{locked ? 'Program låst' : 'Lås program'}</button>
      <button className="secondary" disabled={disabled || !draft.length} onClick={() => void save([])}>Rensa</button>
    </div>
  </section>
}
