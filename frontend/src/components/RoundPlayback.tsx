import { useEffect, useEffectEvent, useState } from 'react'
import type { Board, Player, PublicRound, RoundEvent, Vehicle } from '../types/game'
import { RoundEventDebugView } from './RoundEventDebugView'

function describeEvent(event: RoundEvent, players: Player[]) {
  const name = players.find(player => player.id === event.playerId)?.name ?? 'Fordonet'
  const source = players.find(player => player.id === event.sourcePlayerId)?.name ?? 'Fordonet'
  switch (event.type) {
    case 'MOVE': return `${name} kör`
    case 'TURN': return `${name} svänger`
    case 'RAM': return `${name} rammar`
    case 'PUSH': return `${name} knuffas`
    case 'FIRE': return `${name} skjuter`
    case 'HIT': return `${source} träffar ${name}`
    case 'DAMAGE': return `${name} får ${event.newDamage - event.oldDamage} skada`
    case 'PIT': return `${name} faller i en grop`
  }
}

export function RoundPlayback({board,round,players,onVehicles,onEvent,onFinished}:{board:Board;round:PublicRound;players:Player[];onVehicles:(v:Vehicle[])=>void;onEvent:(event?:RoundEvent)=>void;onFinished:(done:boolean)=>void}) {
  // The parent keys this component by round. Polling must not replace its timeline.
  const [timeline] = useState(round)
  const [eventIndex, setEventIndex] = useState(0)
  const [playing, setPlaying] = useState(true)
  const [finished, setFinished] = useState(false)
  const notifyFinished = useEffectEvent(onFinished)
  const current = timeline.playback[eventIndex - 1]

  useEffect(() => {
    notifyFinished(false)
  }, [])

  useEffect(() => {
    const vehicles = timeline.initialVehicles.map(vehicle => ({...vehicle}))
    for (const event of timeline.playback.slice(0, eventIndex)) {
      const vehicle = vehicles.find(candidate => candidate.id === event.vehicleId)
      if (vehicle && ['MOVE', 'TURN', 'RAM', 'PUSH'].includes(event.type)) {
        vehicle.x = event.newPosition.x
        vehicle.y = event.newPosition.y
        vehicle.direction = event.newDirection
      }
      if (vehicle && event.type === 'DAMAGE') vehicle.damage = event.newDamage
    }
    onVehicles(vehicles)
    onEvent(timeline.playback[eventIndex - 1])
  }, [eventIndex, timeline, onVehicles, onEvent])

  useEffect(() => {
    if (!playing || finished) return
    // Keep a shot and its consequences close together, with time to read the damage.
    const duration = !current ? 0 : current.type === 'FIRE' || current.type === 'HIT' ? 120
      : current.type === 'DAMAGE' || current.type === 'PIT' ? 450 : 250
    const id = window.setTimeout(() => {
      if (eventIndex >= timeline.playback.length) {
        setFinished(true)
        setPlaying(false)
        notifyFinished(true)
      } else {
        setEventIndex(value => value + 1)
      }
    }, duration)
    return () => window.clearTimeout(id)
  }, [eventIndex, playing, finished, current, timeline])

  function replay() {
    onFinished(false)
    setEventIndex(0)
    setFinished(false)
    setPlaying(true)
  }

  return <section className="panel playback">
    <p className="eyebrow">Uppspelning</p>
    <h2>{finished ? 'Uppspelningen är klar' : eventIndex === 0 ? 'Startposition' : `Händelse ${eventIndex} av ${timeline.playback.length}`}</h2>
    {current && <p data-testid="current-playback-event" data-event-type={current.type} data-sequence={current.sequence}>{describeEvent(current, players)}</p>}
    <div className="actions">
      <button disabled={finished} onClick={() => setPlaying(value => !value)}>{playing ? 'Pausa' : 'Fortsätt'}</button>
      <button className="secondary" onClick={replay}>Spela om</button>
    </div>
    <RoundEventDebugView board={board} events={timeline.playback} initialVehicles={timeline.initialVehicles} players={players}/>
  </section>
}
