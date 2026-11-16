import { memo, useEffect, useRef, useState, type CSSProperties } from 'react'
import type { Board, Direction, Player, RoundEvent, Vehicle } from '../types/game'
import { LaserEffects } from './LaserEffects'
import { BoardEffects } from './BoardEffects'
import { isImpact, type PresentedEvent } from '../presentation/timeline'
const angles: Record<Direction, number> = { NORTH: 0, EAST: 90, SOUTH: 180, WEST: 270 }
function VehicleDirection({direction}: {direction: Direction}) {
 const [rotation, setRotation] = useState({direction, angle: angles[direction]})
 let angle = rotation.angle
 if (rotation.direction !== direction) {
  const delta = ((angles[direction] - angles[rotation.direction] + 540) % 360) - 180
  angle += delta
  setRotation({direction, angle})
 }
 return <svg aria-hidden="true" viewBox="0 0 40 52" className="vehicle-direction" style={{transform:`rotate(${angle}deg)`}}>
   <rect className="robot-track" x="2" y="13" width="9" height="34" rx="2"/><rect className="robot-track" x="29" y="13" width="9" height="34" rx="2"/>
   <path className="robot-tread" d="M3 19h7M3 27h7M3 35h7M3 43h7M30 19h7M30 27h7M30 35h7M30 43h7"/>
   <path className="robot-chassis" d="m12 9 16 0 4 9v22l-5 6H13l-5-6V18z"/>
   <path className="robot-plate" d="M13 15h14v17H13z"/><path className="robot-barrel" d="M17 3h6v19h-6z"/>
   <path className="robot-seam" d="M12 37h16M16 41h8"/><path className="robot-eye" d="M13 29h14"/>
  </svg>
}
function Recoil({ impactId, children }: { impactId?: string; children: React.ReactNode }) {
 const element = useRef<HTMLSpanElement>(null)
 useEffect(() => {
  if (!impactId || matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const animation = element.current?.animate([{ transform: 'none' }, { transform: 'translateY(2px) rotate(-3deg)', offset: .3 }, { transform: 'translateY(-1px)', offset: .7 }, { transform: 'none' }], { duration: 150, easing: 'ease-out' })
  return () => animation?.cancel()
 }, [impactId])
 return <span className="vehicle-recoil" ref={element}>{children}</span>
}
const StaticBoard = memo(function StaticBoard({board}: {board: Board}) {
 return <>
  {board.walls.map(w=><div className={`wall wall-${w.direction.toLowerCase()}`} data-testid="board-wall" data-x={w.cell.x} data-y={w.cell.y} data-direction={w.direction} key={`${w.cell.x}-${w.cell.y}-${w.direction}`} style={{left:`${w.cell.x/board.width*100}%`,top:`${(board.height-1-w.cell.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {board.pits.map(p=><div className="pit" data-testid="board-pit" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {(board.obstacles??[]).map(p=><div className="obstacle" data-testid="board-obstacle" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {(board.conveyors??[]).map((c,i)=><div className="conveyor" data-testid="board-conveyor" data-x={c.position.x} data-y={c.position.y} data-direction={c.direction} key={i} style={{left:`${c.position.x/board.width*100}%`,top:`${(board.height-1-c.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}>➤</div>)}
  {(board.rotators??[]).map((r,i)=><div className="rotator" data-testid="board-rotator" data-x={r.position.x} data-y={r.position.y} data-rotation={r.rotation} key={i} style={{left:`${r.position.x/board.width*100}%`,top:`${(board.height-1-r.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}>{r.rotation==='CLOCKWISE'?'↻':'↺'}</div>)}
  {(board.checkpoints ?? []).map(c=><div className={`checkpoint checkpoint-${c.order}`} aria-label={`Checkpoint ${c.order}`} data-testid="board-checkpoint" data-checkpoint-id={c.id} data-checkpoint-order={c.order} data-x={c.position.x} data-y={c.position.y} key={c.id} style={{left:`${c.position.x/board.width*100}%`,top:`${(board.height-1-c.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><span>{c.order}</span></div>)}
  {(board.controlPoints ?? []).map(p=><div className="control-point" aria-label={`Control point at ${p.x}, ${p.y}`} data-testid="board-control-point" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
 </>
})
export function GameBoard({board,vehicles,players,currentPlayerId,playbackEvent,laserEvents,laserSpins={},laserRotations=laserSpins,activeShieldPlayerIds=[],effects=[]}:{board:Board;vehicles:Vehicle[];players:Player[];currentPlayerId:string;playbackEvent?:RoundEvent;laserEvents?:RoundEvent[];laserSpins?:Record<string,number>;laserRotations?:Record<string,number>;activeShieldPlayerIds?:string[];effects?:PresentedEvent[]}){
 const boardElement = useRef<HTMLDivElement>(null)
 const names=Object.fromEntries(players.map(p=>[p.id,p.name]));
 const currentPlayer=players.find(player=>player.id===currentPlayerId)
 const shieldVehicleId=playbackEvent?.type==='SHIELD_ACTIVATED'?playbackEvent.vehicleId:undefined
 const visualEvents=laserEvents??(playbackEvent&&['WEAPON_FIRED','WEAPON_HIT','PUSH_BLOCKED'].includes(playbackEvent.type)&&playbackEvent.actionType==='LASER'?[playbackEvent]:[])
 const shakeId = effects.find(item => item.event.playerId === currentPlayerId && ['RAM','CONVEYOR_RAM','CRASH','CONVEYOR_CRASH'].includes(item.event.type))?.id
 useEffect(() => {
  if (!shakeId || matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const animation = boardElement.current?.animate([{ transform:'none' }, { transform:'translate(2px,-1px)',offset:.25 }, { transform:'translate(-1px,1px)',offset:.7 }, { transform:'none' }], { duration:120, easing:'ease-out' })
  return () => animation?.cancel()
 }, [shakeId])
 return <div className="board-wrap" style={{'--board-ratio':board.width/board.height} as CSSProperties}><div ref={boardElement} className="board" role="grid" aria-label="Spelplan" data-testid="game-board" data-width={board.width} data-height={board.height} style={{gridTemplateColumns:`repeat(${board.width}, 1fr)`,aspectRatio:`${board.width}/${board.height}`,backgroundSize:`${100/board.width}% ${100/board.height}%`}}>
  <StaticBoard board={board}/>
  <LaserEffects board={board} events={visualEvents}/>
  <BoardEffects board={board} events={effects}/>
  {vehicles.map(v=><div className={`vehicle${v.playerId===currentPlayerId?' own-vehicle':''}${(v.id===shieldVehicleId||activeShieldPlayerIds.includes(v.playerId))?' shield-effect':''}`} role="gridcell" aria-label={`${names[v.playerId]} på position ${v.x}, ${v.y}, riktning ${v.direction}`} data-testid="player-vehicle" data-player-id={v.playerId} data-player-name={names[v.playerId]} data-x={v.x} data-y={v.y} data-direction={v.direction} data-status={v.status} key={v.id} style={{transform:`translate(${v.x*100}%,${(board.height-1-v.y)*100}%)`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><Recoil impactId={effects.find(item => item.event.vehicleId === v.id && isImpact(item.event))?.id}><span className={`vehicle-spin${playbackEvent?.type==='TURN'&&playbackEvent.actionType==='LASER'&&playbackEvent.vehicleId===v.id?' legacy-laser-spin':''}`} data-testid="vehicle-spin" data-spin-count={laserSpins[v.id]??0} style={{transform:`rotate(${(laserRotations[v.id]??0)*360}deg)`}}><VehicleDirection direction={v.direction} /></span></Recoil>{v.id===shieldVehicleId&&<span className="ability-badge" data-testid="shield-effect">Shield</span>}<small>{names[v.playerId]}</small></div>)}
  <section className="checkpoint-progress" data-testid="checkpoint-progress"><strong>Flaggor: {currentPlayer?.capturedCheckpoints ?? currentPlayer?.visitedCheckpoints?.length ?? 0} / {board.checkpoints?.length ?? 0}</strong><span>Nästa: <b data-testid="next-checkpoint">{currentPlayer?.nextCheckpoint ?? 'Alla tagna'}</b></span></section>
 </div></div>
}
