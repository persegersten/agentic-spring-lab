import { useEffect, useState, type DragEvent, type KeyboardEvent } from 'react'
import type { ActionType, MovementOrder, ScheduledAction, PrimaryWeapon, SpecialAbility } from '../types/game'

const labels: Record<MovementOrder, string> = { FORWARD_1: 'Framåt 1', FORWARD_2: 'Framåt 2', FORWARD_3: 'Framåt 3', REVERSE_1: 'Backa 1', TURN_LEFT: 'Sväng vänster', TURN_RIGHT: 'Sväng höger', U_TURN: 'U-sväng', WAIT: 'Vänta' }
const actionLabels: Record<ActionType, string> = { LASER: 'Laser', REPULSOR: 'Repulsor', ROCKET: 'Rocket', TURBO: 'Turbo', SHIELD: 'Shield', ANCHOR: 'Anchor', SIDE_STEP_LEFT: 'Side Step vänster', SIDE_STEP_RIGHT: 'Side Step höger' }
const withDefaults = (program: MovementOrder[], size: number): MovementOrder[] => Array.from({ length: size }, (_, index) => program[index] ?? 'WAIT')
type DraggedCard = { source: 'hand'; index: number; command: MovementOrder } | { source: 'program'; index: number }

export function CommandHand({ hand, program, scheduledAction, programSize, locked, rocketAmmo, primaryWeapon, specialAbility, onReorder, onSubmit }: {
  hand: MovementOrder[]
  program: MovementOrder[]
  scheduledAction: ScheduledAction | null
  programSize: number
  locked: boolean
  rocketAmmo: number
  primaryWeapon: PrimaryWeapon
  specialAbility: SpecialAbility
  onReorder: (value: MovementOrder[], action: ScheduledAction | null) => Promise<void>
  onSubmit: (value: MovementOrder[], action: ScheduledAction | null) => Promise<void>
}) {
  const [draft, setDraft] = useState(() => program.slice(0, programSize))
  const [action, setAction] = useState<ScheduledAction | null>(scheduledAction)
  const [saving, setSaving] = useState(false)
  const [dragged, setDragged] = useState<DraggedCard | null>(null)
  const [dropIndex, setDropIndex] = useState<number | null>(null)
  const disabled = locked || saving
  const actionTypes: ActionType[] = [
    ...(primaryWeapon === 'ROCKET' && rocketAmmo === 0 ? [] : [primaryWeapon]),
    ...(specialAbility === 'SIDE_STEP' ? ['SIDE_STEP_LEFT', 'SIDE_STEP_RIGHT'] as ActionType[] : [specialAbility]),
  ]

  useEffect(() => { if (!saving) { setDraft(program.slice(0, programSize)); setAction(scheduledAction) } }, [program, scheduledAction, programSize, saving])

  async function save(next: MovementOrder[]) {
    setDraft(next)
    setSaving(true)
    try { await onReorder(next, action) } finally { setSaving(false) }
  }

  async function saveAction(next: ScheduledAction | null) {
    setAction(next)
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
    <p data-testid="player-loadout">Loadout: {primaryWeapon} + {specialAbility.replace('_', ' ')}</p>
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
    <fieldset className="scheduled-action" disabled={disabled} data-testid="scheduled-action-controls">
      <legend>Valfri action</legend>
      <label>Action
        <select data-testid="action-type" value={action?.actionType ?? ''} onChange={event => {
          const actionType = event.target.value as ActionType
          void saveAction(actionType ? { actionType, registerIndex: action?.registerIndex ?? 1 } : null)
        }}>
          <option value="">Ingen action</option>
          {actionTypes.map(value => <option key={value} value={value}>{actionLabels[value]}</option>)}
        </select>
      </label>
      <label>Programsteg
        <select data-testid="action-register" disabled={disabled || !action} value={action?.registerIndex ?? 1} onChange={event => {
          if (action) void saveAction({ ...action, registerIndex: Number(event.target.value) })
        }}>
          {Array.from({ length: programSize }, (_, index) => <option key={index + 1} value={index + 1}>{index + 1}</option>)}
        </select>
      </label>
      <button type="button" className="secondary" data-testid="clear-action" disabled={disabled || !action} onClick={() => void saveAction(null)}>Rensa action</button>
      <p data-testid="selected-action">{action ? `${actionLabels[action.actionType]} · programsteg ${action.registerIndex}` : 'Ingen action vald'}</p>
      {primaryWeapon === 'ROCKET' && <p data-testid="rocket-ammo">Rocket ammunition: {rocketAmmo}</p>}
    </fieldset>
    <div className="actions">
      <button data-testid="lock-program" disabled={disabled || draft.length !== programSize} onClick={() => void onSubmit(draft, action)}>{locked ? 'Program låst' : 'Lås program'}</button>
      <button className="secondary" disabled={disabled || !draft.length} onClick={() => void save([])}>Rensa</button>
    </div>
  </section>
}
