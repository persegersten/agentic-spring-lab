import { useState } from 'react'
import type { Board, Direction, Player, RoundEvent, Vehicle } from '../types/game'
const angles: Record<Direction, number> = { NORTH: 0, EAST: 90, SOUTH: 180, WEST: 270 }
function VehicleDirection({direction}: {direction: Direction}) {
 const [rotation, setRotation] = useState({direction, angle: angles[direction]})
 let angle = rotation.angle
 if (rotation.direction !== direction) {
  const delta = ((angles[direction] - angles[rotation.direction] + 540) % 360) - 180
  angle += delta
  setRotation({direction, angle})
 }
 return <span aria-hidden="true" className="vehicle-direction" style={{transform:`rotate(${angle}deg)`}} />
}
export function GameBoard({board,vehicles,players,currentPlayerId,combatEvent}:{board:Board;vehicles:Vehicle[];players:Player[];currentPlayerId:string;combatEvent?:RoundEvent}){
 const names=Object.fromEntries(players.map(p=>[p.id,p.name]));
 const currentPlayer=players.find(player=>player.id===currentPlayerId)
 const laser=combatEvent?.type==='WEAPON_FIRED'?combatEvent:undefined
 const hitVehicleId=combatEvent&&['WEAPON_HIT','DAMAGE_APPLIED','VEHICLE_CRASHED'].includes(combatEvent.type)?combatEvent.vehicleId:undefined
 const shieldVehicleId=combatEvent&&['SHIELD_ACTIVATED','DAMAGE_PREVENTED'].includes(combatEvent.type)?combatEvent.vehicleId:undefined
 const anchorVehicleId=combatEvent?.type==='ANCHOR_ACTIVATED'||combatEvent?.type==='PUSH_BLOCKED'&&combatEvent.actionType==='ANCHOR'?combatEvent.vehicleId:undefined
 const abilityMoveVehicleId=combatEvent?.type==='SIDE_STEP'||combatEvent?.actionType==='TURBO'&&['MOVE','RAM','PUSH','CRASH'].includes(combatEvent.type)?combatEvent.vehicleId:undefined
 const laserDistance=laser?Math.hypot(laser.newPosition.x-laser.oldPosition.x,laser.newPosition.y-laser.oldPosition.y):0
 const laserAngle=laserDistance?Math.atan2(-(laser!.newPosition.y-laser!.oldPosition.y),laser!.newPosition.x-laser!.oldPosition.x)*180/Math.PI:angles[laser?.oldDirection??'NORTH']-90
 const laserStyle=laser?{left:`${(laser.oldPosition.x+.5)/board.width*100}%`,top:`${(board.height-laser.oldPosition.y-.5)/board.height*100}%`,width:`${Math.max(laserDistance,.45)/board.width*100}%`,transform:`rotate(${laserAngle}deg)`}:undefined
 return <div className="board-wrap"><div className="board" role="grid" aria-label="Spelplan" data-testid="game-board" data-width={board.width} data-height={board.height} style={{gridTemplateColumns:`repeat(${board.width}, 1fr)`,aspectRatio:`${board.width}/${board.height}`}}>
  {board.walls.map(w=><div className={`wall wall-${w.direction.toLowerCase()}`} data-testid="board-wall" data-x={w.cell.x} data-y={w.cell.y} data-direction={w.direction} key={`${w.cell.x}-${w.cell.y}-${w.direction}`} style={{left:`${w.cell.x/board.width*100}%`,top:`${(board.height-1-w.cell.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {board.pits.map(p=><div className="pit" data-testid="board-pit" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {(board.conveyors??[]).map((c,i)=><div className="conveyor" data-testid="board-conveyor" data-x={c.position.x} data-y={c.position.y} data-direction={c.direction} key={i} style={{left:`${c.position.x/board.width*100}%`,top:`${(board.height-1-c.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}>➤</div>)}
  {(board.rotators??[]).map((r,i)=><div className="rotator" data-testid="board-rotator" data-x={r.position.x} data-y={r.position.y} data-rotation={r.rotation} key={i} style={{left:`${r.position.x/board.width*100}%`,top:`${(board.height-1-r.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}>{r.rotation==='CLOCKWISE'?'↻':'↺'}</div>)}
  {(board.checkpoints ?? []).map(c=><div className={`checkpoint checkpoint-${c.order}`} aria-label={`Checkpoint ${c.id}`} data-testid="board-checkpoint" data-checkpoint-id={c.id} data-checkpoint-order={c.order} data-x={c.position.x} data-y={c.position.y} key={c.id} style={{left:`${c.position.x/board.width*100}%`,top:`${(board.height-1-c.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><span>{c.id}</span></div>)}
  {(board.controlPoints ?? []).map(p=><div className="control-point" aria-label={`Control point at ${p.x}, ${p.y}`} data-testid="board-control-point" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {laser&&<div className="laser-shot" data-testid="laser-shot" data-weapon={laser.actionType ?? 'LASER'} data-start-x={laser.oldPosition.x} data-start-y={laser.oldPosition.y} data-end-x={laser.newPosition.x} data-end-y={laser.newPosition.y} style={laserStyle}/>}
  {vehicles.map(v=><div className={`vehicle${v.playerId===currentPlayerId?' own-vehicle':''}${v.id===hitVehicleId?' weapon-hit':''}${v.id===shieldVehicleId?' shield-effect':''}${v.id===anchorVehicleId?' anchor-effect':''}${v.id===abilityMoveVehicleId?' ability-move':''}`} role="gridcell" aria-label={`${names[v.playerId]} på position ${v.x}, ${v.y}, riktning ${v.direction}, skada ${v.damage ?? 0}`} data-testid="player-vehicle" data-player-id={v.playerId} data-player-name={names[v.playerId]} data-x={v.x} data-y={v.y} data-direction={v.direction} data-status={v.status} data-damage={v.damage??0} data-ability-effect={v.id===shieldVehicleId?'SHIELD':v.id===anchorVehicleId?'ANCHOR':v.id===abilityMoveVehicleId?(combatEvent?.actionType??'SIDE_STEP'):undefined} key={v.id} style={{left:`${v.x/board.width*100}%`,top:`${(board.height-1-v.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><VehicleDirection direction={v.direction} />{v.id===shieldVehicleId&&<span className="ability-badge" data-testid="shield-effect">Shield</span>}{v.id===anchorVehicleId&&<span className="ability-badge" data-testid="anchor-effect">Anchor</span>}<small>{names[v.playerId]} · ⚡ {v.damage??0}/3</small></div>)}
  <section className="checkpoint-progress" data-testid="checkpoint-progress"><strong>Checkpoints: {currentPlayer?.capturedCheckpoints ?? currentPlayer?.visitedCheckpoints.length ?? 0} / {board.checkpoints.length}</strong><span>Next: <b data-testid="next-checkpoint">{currentPlayer?.nextCheckpoint ?? 'Complete'}</b></span></section>
 </div></div>
}
