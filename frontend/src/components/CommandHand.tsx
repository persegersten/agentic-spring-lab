import { useEffect, useState, type DragEvent } from 'react'
import type { MovementOrder } from '../types/game'

const labels: Record<MovementOrder, string> = {
  FORWARD: 'Framåt',
  REVERSE: 'Backa',
  TURN_LEFT: 'Sväng vänster',
  TURN_RIGHT: 'Sväng höger',
  MALFUNCTION_NO_OP: 'Felfunktion: no-op',
}

export function CommandHand({
  hand,
  locked,
  onReorder,
  onSubmit,
}: {
  hand: MovementOrder[]
  locked: boolean
  onReorder: (orders: MovementOrder[]) => Promise<void>
  onSubmit: (orders: MovementOrder[]) => Promise<void>
}) {
  const [orders, setOrders] = useState(hand)
  const [saving, setSaving] = useState(false)
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null)
  const [dropIndex, setDropIndex] = useState<number | null>(null)

  useEffect(() => {
    if (!saving) setOrders(hand)
  }, [hand, saving])

  async function save(next: MovementOrder[]) {
    setOrders(next)
    setSaving(true)
    try {
      await onReorder(next)
    } finally {
      setSaving(false)
    }
  }

  function move(index: number, delta: number) {
    const target = index + delta
    if (target < 0 || target >= orders.length) return
    const next = [...orders]
    ;[next[index], next[target]] = [next[target], next[index]]
    void save(next)
  }

  function startDragging(event: DragEvent<HTMLLIElement>, index: number) {
    if (locked || saving) {
      event.preventDefault()
      return
    }
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
    setDraggedIndex(index)
  }

  function dragOver(event: DragEvent<HTMLLIElement>, index: number) {
    if (draggedIndex === null || locked || saving) return
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
    setDropIndex(index)
  }

  function drop(event: DragEvent<HTMLLIElement>, targetIndex: number) {
    event.preventDefault()
    if (draggedIndex === null || locked || saving) return

    const next = [...orders]
    const [draggedCard] = next.splice(draggedIndex, 1)
    next.splice(targetIndex, 0, draggedCard)
    setDraggedIndex(null)
    setDropIndex(null)
    if (draggedIndex !== targetIndex) void save(next)
  }

  function stopDragging() {
    setDraggedIndex(null)
    setDropIndex(null)
  }

  return <section className="panel">
    <h2>Programmera dina kommandon</h2>
    <p>Ordna korten i den följd de ska utföras.</p>
    {saving && <p role="status">Sparar kortordning…</p>}
    <ol className="cards">
      {orders.map((card, index) => <li
        className={`card${draggedIndex === index ? ' card-dragging' : ''}${dropIndex === index && draggedIndex !== index ? ' card-drop-target' : ''}`}
        draggable={!locked && !saving}
        key={`${card}-${index}`}
        onDragStart={event => startDragging(event, index)}
        onDragOver={event => dragOver(event, index)}
        onDrop={event => drop(event, index)}
        onDragEnd={stopDragging}
      >
        <b>{index + 1}</b>
        <span>{labels[card]}</span>
        <div>
          <button aria-label={`Flytta ${labels[card]} tidigare`} disabled={locked || saving || index === 0} onClick={() => move(index, -1)}>←</button>
          <button aria-label={`Flytta ${labels[card]} senare`} disabled={locked || saving || index === orders.length - 1} onClick={() => move(index, 1)}>→</button>
        </div>
      </li>)}
    </ol>
    <div className="actions">
      <button disabled={locked || saving} onClick={() => void onSubmit(orders)}>{locked ? 'Program låst' : 'Lås program'}</button>
      <button className="secondary" disabled={locked || saving} onClick={() => void save(hand)}>Återställ</button>
    </div>
  </section>
}
