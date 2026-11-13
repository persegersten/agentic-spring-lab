import { useEffect, useState } from 'react'
import { GameBoard } from './GameBoard'
import { playbackVehicles } from './playbackVehicles'
import type { PlayerGame, RoundEvent } from '../types/game'

const noEvents: RoundEvent[] = []

function TickBoard({ view, animate }: { view: PlayerGame; animate: boolean }) {
  const events = view.round?.state.playback ?? noEvents
  const [frame, setFrame] = useState({ progress: 0, effects: [] as RoundEvent[], settled: !animate })
  useEffect(() => {
    if (!animate || !events.length) return
    const timers: number[] = []
    const interval = view.continuous?.intervalMillis ?? 2000
    const budget = Math.min(1000, interval * .45)
    const linger = Math.min(650, interval * .3)
    events.forEach((event, index) => {
      const start = index * budget / events.length
      const visual = event.actionType === 'LASER' && ['WEAPON_FIRED', 'WEAPON_HIT', 'PUSH_BLOCKED'].includes(event.type)
      timers.push(window.setTimeout(() => setFrame(old => ({ ...old, progress: index + 1,
        effects: visual ? [...old.effects, event] : old.effects })), start))
      if (visual) timers.push(window.setTimeout(() => setFrame(old => ({ ...old,
        effects: old.effects.filter(effect => effect.sequence !== event.sequence) })), start + linger))
    })
    timers.push(window.setTimeout(() => setFrame(old => ({ ...old, settled: true })), budget + linger))
    return () => timers.forEach(window.clearTimeout)
  }, [animate, events, view])
  const shownEvents = events.slice(0, frame.progress)
  const vehicles = frame.settled || !events.length || !view.round
    ? view.vehicles.filter(vehicle => vehicle.status === 'ACTIVE')
    : playbackVehicles(view.round.state.initialVehicles, shownEvents)
  const spins: Record<string, number> = {}
  for (const event of shownEvents) if (event.type === 'TURN' && event.actionType === 'LASER') {
    spins[event.vehicleId] = (spins[event.vehicleId] ?? 0) + 1
  }
  return <GameBoard board={view.board} vehicles={vehicles} players={view.players} currentPlayerId={view.playerId}
    laserEvents={frame.effects} laserSpins={spins} />
}

export function ContinuousBoard({ view }: { view: PlayerGame }) {
  const tick = view.continuous?.tick ?? 0
  const [timeline, setTimeline] = useState({ tick, view, animate: false })
  if (tick !== timeline.tick || view.id !== timeline.view.id) {
    setTimeline({ tick, view, animate: view.id === timeline.view.id && tick === timeline.tick + 1 })
  }
  // Freeze a tick's timeline across polls and command acknowledgements; a new tick cancels its timers.
  return <TickBoard key={`${timeline.view.id}:${timeline.tick}`} view={timeline.view} animate={timeline.animate} />
}
