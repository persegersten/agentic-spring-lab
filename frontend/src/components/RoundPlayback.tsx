import { useEffect, useState } from 'react'
import type { Player, PublicRound, Vehicle } from '../types/game'
import { RoundEventDebugView } from './RoundEventDebugView'

export function RoundPlayback({round,players,onVehicles,onFinished}:{round:PublicRound;players:Player[];onVehicles:(v:Vehicle[])=>void;onFinished:()=>void}){
 const [eventIndex,setEventIndex]=useState(0),[playing,setPlaying]=useState(true)
 useEffect(()=>{ setEventIndex(0); setPlaying(true); onVehicles(round.initialVehicles) },[round.number, onVehicles])
 useEffect(()=>{
  const vehicles=round.initialVehicles.map(vehicle=>({...vehicle}))
  for(const event of round.playback.slice(0,eventIndex)){
   const vehicle=vehicles.find(candidate=>candidate.id===event.vehicleId)
   if(vehicle){vehicle.x=event.newPosition.x;vehicle.y=event.newPosition.y;vehicle.direction=event.newDirection}
  }
  onVehicles(vehicles)
 },[eventIndex,round.initialVehicles,round.playback,onVehicles])
 useEffect(()=>{if(!playing)return;if(eventIndex>=round.playback.length){onFinished();return}const id=window.setTimeout(()=>setEventIndex(value=>value+1),900);return()=>clearTimeout(id)},[eventIndex,playing,round.playback.length,onFinished])
 const current=round.playback[eventIndex-1]
 const player=current&&players.find(candidate=>candidate.id===current.playerId)
 return <section className="panel playback"><p className="eyebrow">Uppspelning</p><h2>{eventIndex===0?'Startposition':`Event ${eventIndex} av ${round.playback.length}`}</h2>{current&&<p data-testid="current-playback-event" data-sequence={current.sequence}><b>{player?.name}</b> {current.type}</p>}<div className="actions"><button onClick={()=>setPlaying(value=>!value)}>{playing?'Pausa':'Fortsätt'}</button><button className="secondary" onClick={()=>{setPlaying(false);setEventIndex(0)}}>Spela om</button></div><RoundEventDebugView events={round.playback} players={players}/></section>
}
