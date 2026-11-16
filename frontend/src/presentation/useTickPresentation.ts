import { useEffect, useMemo, useRef, useState } from 'react'
import type { PlayerGame } from '../types/game'
import { playbackVehicles } from '../components/playbackVehicles'
import { gameAudio } from './audio'
import { createTimeline, hasEffect, shouldAnimate } from './timeline'

export function useTickPresentation(view: PlayerGame, recoveryEpoch = 0) {
  const [timeline, setTimeline] = useState(() => ({ view, recoveryEpoch, schedule: createTimeline(view), animate: false, spinBase: {} as Record<string, number> }))
  const [clock, setClock] = useState({ key: '', elapsed: Infinity })
  const [visible, setVisible] = useState(!document.hidden)
  const consumed = useRef(new Set<string>())
  const lastStatus = useRef(view.status)
  const latestStatus = useRef(view.status)
  useEffect(() => { latestStatus.current = view.status }, [view.status])
  const nextKey = `${view.id}:${view.continuous?.tick ?? 0}`
  if (nextKey !== timeline.schedule.key || recoveryEpoch !== timeline.recoveryEpoch) {
    const nextAnimate = recoveryEpoch === timeline.recoveryEpoch && shouldAnimate(timeline.view, view, visible && !document.hidden)
    const spinBase = nextAnimate ? { ...timeline.spinBase } : {}
    if (nextAnimate && timeline.animate) for (const item of timeline.schedule.events) {
      if (item.event.type === 'TURN' && item.event.actionType === 'LASER') spinBase[item.event.vehicleId] = (spinBase[item.event.vehicleId] ?? 0) + 1
    }
    setTimeline({ view, recoveryEpoch, schedule: createTimeline(view), animate: nextAnimate, spinBase })
  }
  const { schedule } = timeline
  const elapsed = clock.key === schedule.key ? clock.elapsed : 0
  const executionDuration = Math.min(300, (view.continuous?.intervalMillis ?? 2000) * .2)
  const animate = timeline.animate && visible
  const settled = !animate || elapsed >= schedule.duration
  useEffect(() => {
    const change = () => {
      lastStatus.current = latestStatus.current
      setVisible(!document.hidden)
      setTimeline(old => ({ ...old, animate: false, spinBase: {} }))
      gameAudio.stop()
    }
    document.addEventListener('visibilitychange', change)
    return () => document.removeEventListener('visibilitychange', change)
  }, [])
  useEffect(() => { consumed.current.clear() }, [schedule.key])
  useEffect(() => {
    if (!animate) return
    const started = performance.now()
    let timer = 0
    let previousBoundary = ''
    const update = () => {
      const time = performance.now() - started
      if (!document.hidden) {
        const tickId = `${schedule.key}:tick`
        if (!consumed.current.has(tickId)) { consumed.current.add(tickId); if (time < 150) gameAudio.play('tick', tickId) }
        for (const item of schedule.events) if (item.at <= time && !consumed.current.has(item.id)) {
          consumed.current.add(item.id)
          if (time - item.at < 150) gameAudio.event(item.event, item.id, view.playerId)
        }
      }
      const boundary = `${schedule.events.filter(item => item.at <= time).length}:${schedule.events.filter(item => item.until <= time).length}:${time >= schedule.duration}:${time >= executionDuration}`
      if (boundary !== previousBoundary) { previousBoundary = boundary; setClock({ key: schedule.key, elapsed: time }) }
      if (time < schedule.duration) timer = requestAnimationFrame(update)
    }
    timer = requestAnimationFrame(update)
    return () => { cancelAnimationFrame(timer); gameAudio.stop(`${schedule.key}:`) }
  }, [animate, schedule, view.playerId, executionDuration])
  useEffect(() => {
    if (!timeline.animate && timeline.view.status === 'FINISHED') lastStatus.current = 'FINISHED'
    if (lastStatus.current !== 'FINISHED' && view.status === 'FINISHED' && settled && visible) {
      gameAudio.play(view.placements.some(p => p.playerId === view.playerId && p.winner) ? 'victory' : 'complete', `${view.id}:finished`)
      lastStatus.current = view.status
    } else if (view.status !== 'FINISHED') lastStatus.current = view.status
  }, [view, settled, visible, timeline.animate, timeline.view.status])
  const shown = useMemo(() => schedule.events.filter(item => item.at <= elapsed), [schedule, elapsed])
  const vehicles = useMemo(() => settled ? view.vehicles.filter(v => v.status === 'ACTIVE')
    : playbackVehicles(timeline.view.round?.state.initialVehicles ?? timeline.view.vehicles,
      shown.filter(item => item.stream === 'playback').map(item => item.event)), [settled, view.vehicles, timeline.view, shown])
  const effects = settled ? [] : shown.filter(item => hasEffect(item.event) && item.until > elapsed).slice(-8)
  const spins: Record<string, number> = {}
  if (timeline.animate) for (const item of shown) if (item.event.type === 'TURN' && item.event.actionType === 'LASER') {
    spins[item.event.vehicleId] = (spins[item.event.vehicleId] ?? 0) + 1
  }
  const rotations = { ...timeline.spinBase }
  for (const [id, count] of Object.entries(spins)) rotations[id] = (rotations[id] ?? 0) + count
  const execution = animate && elapsed < executionDuration && timeline.view.continuous?.lastExecutedCommand != null
    ? { id: schedule.key, command: timeline.view.continuous.lastExecutedCommand } : null
  return { execution, duration: Math.min(200, schedule.step) / 1000, board: timeline.view.board, vehicles, effects, spins, rotations, animate: animate && !settled, queueAnimate: timeline.animate && visible }
}
