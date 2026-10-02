import { useEffect, useState } from 'react'
import type { MovementOrder } from '../types/game'

const all: MovementOrder[] = ['FORWARD_1', 'FORWARD_2', 'REVERSE_1', 'TURN_LEFT', 'TURN_RIGHT', 'U_TURN', 'WAIT']
const labels: Record<MovementOrder, string> = { FORWARD_1: 'Framåt 1', FORWARD_2: 'Framåt 2', REVERSE_1: 'Backa 1', TURN_LEFT: 'Sväng vänster', TURN_RIGHT: 'Sväng höger', U_TURN: 'U-sväng', WAIT: 'Vänta' }
const withDefaults = (program: MovementOrder[], size: number): MovementOrder[] => Array.from({ length: size }, (_, index) => program[index] ?? 'WAIT')

export function CommandHand({ program, programSize, locked, onReorder, onSubmit }: {
  program: MovementOrder[]
  programSize: number
  locked: boolean
  onReorder: (value: MovementOrder[]) => Promise<void>
  onSubmit: (value: MovementOrder[]) => Promise<void>
}) {
  const [draft, setDraft] = useState(() => withDefaults(program, programSize))
  const [saving, setSaving] = useState(false)
  useEffect(() => { if (!saving) setDraft(withDefaults(program, programSize)) }, [program, programSize, saving])
  async function save(next: MovementOrder[]) {
    setDraft(next); setSaving(true)
    try { await onReorder(next) } finally { setSaving(false) }
  }
  function select(index: number, value: string) {
    const next = [...draft]
    next[index] = value as MovementOrder
    void save(next)
  }

  return <section className="panel program-panel" data-testid="player-program" data-locked={locked}>
    <h2>Ditt program</h2>
    <p>Registren utförs uppifrån och ned. Kommandon får upprepas.</p>
    <p className="program-state" data-testid="program-lock-state">{locked ? 'Programmet är låst' : 'Programmet kan ändras'}</p>
    {saving && <p role="status">Sparar program…</p>}
    <ol className="cards" data-testid="program-slots">
      {Array.from({ length: programSize }, (_, index) => <li className="card" key={index} data-testid="program-slot" data-slot={index + 1} data-command={draft[index] ?? ''}>
        <b>Register {index + 1}</b>
        <select aria-label={`Register ${index + 1}`} disabled={locked || saving} value={draft[index] ?? 'WAIT'} onChange={event => select(index, event.target.value)}>
          {all.map(command => <option value={command} key={command}>{labels[command]}</option>)}
        </select>
      </li>)}
    </ol>
    <div className="actions">
      <button data-testid="lock-program" disabled={locked || saving || draft.length !== programSize} onClick={() => void onSubmit(draft)}>{locked ? 'Program låst' : 'Lås program'}</button>
      <button className="secondary" disabled={locked || saving || !draft.length} onClick={() => void save(withDefaults([], programSize))}>Rensa</button>
    </div>
  </section>
}
