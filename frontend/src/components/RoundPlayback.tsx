import { useEffect, useEffectEvent, useState } from 'react'
import type { Board, Player, PublicRound, RoundEvent, Vehicle } from '../types/game'
import { RoundEventDebugView } from './RoundEventDebugView'

function describeEvent(event: RoundEvent, players: Player[]) {
  const name = players.find(player => player.id === event.playerId)?.name ?? 'Fordonet'
  switch (event.type) {
    case 'MOVE': return `${name} kör`
    case 'TURN': return `${name} svänger`
    case 'RAM': return `${name} rammar`
    case 'PUSH': return `${name} knuffas${event.actionType === 'LASER' ? ' av Laser' : ''}`
    case 'PUSH_BLOCKED': return `Knuffen mot ${name} blockeras`
    case 'MOVE_BLOCKED': return `${name}s rörelse blockeras`
    case 'CONVEYOR_MOVE':case 'CONVEYOR_RAM':return `Transportbandet flyttar ${name}`
    case 'CONVEYOR_PUSH':return `${name} knuffas av transportbandet`
    case 'CONVEYOR_CRASH':return `${name} kraschar på transportbandet`
    case 'ROTATOR_TURN':return `Rotatorn vrider ${name}`
    case 'CRASH': return `${name} kraschar`
    case 'WEAPON_FIRED': return `${name} avfyrar Laser`
    case 'WEAPON_HIT': return `${name} träffas av Laser`
    case 'VEHICLE_RESPAWNED': return `${name} respawnar`
    case 'SHIELD_ACTIVATED': return `${name} aktiverar Shield`
    case 'CHECKPOINT_CAPTURED': return `${name} tar ${event.checkpointId}`
  }
}

export function RoundPlayback({board,round,players,onVehicles,onCurrentEvent,onFinished}:{board:Board;round:PublicRound;players:Player[];onVehicles:(v:Vehicle[])=>void;onCurrentEvent:(event?:RoundEvent)=>void;onFinished:(done:boolean)=>void}) {
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
      const vehicleIndex = vehicles.findIndex(candidate => candidate.id === event.vehicleId)
      const vehicle = vehicles[vehicleIndex]
      if(vehicle&&['MOVE','TURN','RAM','PUSH','CONVEYOR_MOVE','CONVEYOR_RAM','CONVEYOR_PUSH','ROTATOR_TURN'].includes(event.type)){
        vehicle.x = event.newPosition.x
        vehicle.y = event.newPosition.y
        vehicle.direction = event.newDirection
      }
      if(vehicle&&['CRASH','CONVEYOR_CRASH'].includes(event.type))vehicles.splice(vehicleIndex,1)
    }
    onVehicles(vehicles)
    onCurrentEvent(current)
  }, [eventIndex, timeline, onVehicles, onCurrentEvent, current])

  useEffect(() => {
    if (!playing || finished) return
    const duration = !current ? 0
      : ['CRASH','WEAPON_FIRED','WEAPON_HIT','SHIELD_ACTIVATED','CHECKPOINT_CAPTURED'].includes(current.type) ? 450 : 350
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
    onCurrentEvent(undefined)
    setEventIndex(0)
    setFinished(false)
    setPlaying(true)
  }

  return <section className="panel playback" data-testid="round-playback" data-playback-state={finished ? 'FINISHED' : playing ? 'PLAYING' : 'PAUSED'}>
    <p className="eyebrow">Uppspelning</p>
    <h2>{finished ? 'Uppspelningen är klar' : eventIndex === 0 ? 'Startposition' : `Händelse ${eventIndex} av ${timeline.playback.length}`}</h2>
    {current?.registerIndex && <p data-testid="current-register">Register {current.registerIndex} av 5</p>}
    {current && <p data-testid="current-playback-event" data-event-type={current.type} data-sequence={current.sequence}>{describeEvent(current, players)}</p>}
    <ol className="playback-events" data-testid="playback-events" aria-label="Rundans händelser">
      {timeline.playback.map(event => <li key={event.sequence} data-testid="playback-event" data-event-type={event.type} data-sequence={event.sequence} className={event.sequence === current?.sequence ? 'current' : ''}>
        <span>{event.sequence}</span> {describeEvent(event, players)}
      </li>)}
    </ol>
    <div className="actions">
      <button disabled={finished} onClick={() => setPlaying(value => !value)}>{playing ? 'Pausa' : 'Fortsätt'}</button>
      <button className="secondary" onClick={replay}>Spela om</button>
    </div>
    <RoundEventDebugView board={board} events={timeline.playback} initialVehicles={timeline.initialVehicles} players={players}/>
  </section>
}
