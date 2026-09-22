import { useEffect, useState } from 'react'
import type { MovementOrder } from '../types/game'
const labels:Record<MovementOrder,string>={FORWARD:'Framåt',REVERSE:'Backa',TURN_LEFT:'Sväng vänster',TURN_RIGHT:'Sväng höger'}
export function CommandHand({hand,locked,onSubmit}:{hand:MovementOrder[];locked:boolean;onSubmit:(orders:MovementOrder[])=>Promise<void>}){
 const [orders,setOrders]=useState(hand); useEffect(()=>setOrders(hand),[hand]);
 function move(index:number,delta:number){const target=index+delta;if(target<0||target>=orders.length)return;const next=[...orders];[next[index],next[target]]=[next[target],next[index]];setOrders(next)}
 return <section className="panel"><h2>Programmera dina kommandon</h2><p>Ordna korten i den följd de ska utföras.</p><ol className="cards">{orders.map((card,index)=><li className="card" key={`${card}-${index}`}><b>{index+1}</b><span>{labels[card]}</span><div><button aria-label={`Flytta ${labels[card]} tidigare`} disabled={locked||index===0} onClick={()=>move(index,-1)}>←</button><button aria-label={`Flytta ${labels[card]} senare`} disabled={locked||index===orders.length-1} onClick={()=>move(index,1)}>→</button></div></li>)}</ol><div className="actions"><button disabled={locked} onClick={()=>void onSubmit(orders)}>{locked?'Program låst':'Lås program'}</button><button className="secondary" disabled={locked} onClick={()=>setOrders(hand)}>Återställ</button></div></section>
}
