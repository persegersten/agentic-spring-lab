import { useEffect, useState } from 'react'
import type { PlaybackStep, Player, PublicRound, Vehicle } from '../types/game'
const names={FORWARD:'Framåt',REVERSE:'Backa',TURN_LEFT:'Vänster',TURN_RIGHT:'Höger'}
export function RoundPlayback({round,players,onVehicles,onFinished}:{round:PublicRound;players:Player[];onVehicles:(v:Vehicle[])=>void;onFinished:()=>void}){
 const [step,setStep]=useState(0),[playing,setPlaying]=useState(true); const current:PlaybackStep|undefined=round.playback[step-1];
 useEffect(()=>{onVehicles(step===0?round.initialVehicles:round.playback[step-1].vehicles)},[step,round,onVehicles]);
 useEffect(()=>{if(!playing)return; if(step>=round.playback.length){onFinished();return} const id=window.setTimeout(()=>setStep(s=>s+1),900);return()=>clearTimeout(id)},[step,playing,round.playback.length,onFinished]);
 return <section className="panel playback"><p className="eyebrow">Uppspelning</p><h2>{step===0?'Startposition':`Kort ${step} av 3`}</h2>{current&&<div className="revealed">{players.map(p=><span key={p.id}><b>{p.name}</b>{names[current.commands[p.id]]}</span>)}</div>}<div className="actions"><button onClick={()=>setPlaying(v=>!v)}>{playing?'Pausa':'Fortsätt'}</button><button className="secondary" onClick={()=>{setPlaying(false);setStep(0)}}>Spela om</button></div></section>
}
