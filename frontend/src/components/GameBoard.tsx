import { memo, useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import type { Board, Direction, Player, RoundEvent, Vehicle } from '../types/game'
import { motion, useAnimationControls } from 'motion/react'
import { useReducedMotionPreference } from '../presentation/useReducedMotionPreference'
import { usePresentationTween } from '../presentation/usePresentationTween'
import { LaserEffects } from './LaserEffects'
import { Icon } from './Icon'
import { BoardEffects } from './BoardEffects'
import { isImpact } from '../presentation/timeline'
import { describeEffect, type VisualEffect, type ShakeCue } from '../presentation/visualEffects'
const angles: Record<Direction, number> = { NORTH: 0, EAST: 90, SOUTH: 180, WEST: 270 }
function VehicleDirection({direction, animate, duration}: {direction: Direction; animate: boolean; duration: number}) {
 const [rotation, setRotation] = useState({direction, angle: angles[direction]})
 let angle = rotation.angle
 if (rotation.direction !== direction) {
  const delta = ((angles[direction] - angles[rotation.direction] + 540) % 360) - 180
  angle += delta
  setRotation({direction, angle})
 }
 const rotate = usePresentationTween(angle, animate, duration)
 return <motion.svg data-rotation={angle} aria-hidden="true" viewBox="0 0 40 52" className="vehicle-direction" style={{rotate}}>
   <rect className="robot-track" x="2" y="13" width="9" height="34" rx="2"/><rect className="robot-track" x="29" y="13" width="9" height="34" rx="2"/>
   <path className="robot-tread" d="M3 19h7M3 27h7M3 35h7M3 43h7M30 19h7M30 27h7M30 35h7M30 43h7"/>
   <path className="robot-chassis" d="m12 9 16 0 4 9v22l-5 6H13l-5-6V18z"/>
   <path className="robot-plate" d="M13 15h14v17H13z"/><path className="robot-barrel" d="M17 3h6v19h-6z"/>
   <path className="robot-seam" d="M12 37h16M16 41h8"/><path className="robot-eye" d="M13 29h14"/>
  </motion.svg>
}
function Recoil({ impactId, animate, children }: { impactId?: string; animate: boolean; children: React.ReactNode }) {
 const controls = useAnimationControls()
 const reducedMotion = useReducedMotionPreference()
 useLayoutEffect(() => {
  controls.stop()
  controls.set({ y: 0, rotate: 0 })
  if (impactId && animate && !reducedMotion) void controls.start({ y: [0, 2, -1, 0], rotate: [0, -3, 0, 0], transition: { duration: .15 } })
  return () => controls.stop()
 }, [impactId, animate, reducedMotion, controls])
 return <motion.span className="vehicle-recoil" initial={false} animate={controls} data-impact={impactId ? 'true' : 'false'}>{children}</motion.span>
}
const RobotVehicle = memo(function RobotVehicle({ vehicle: v, board, name, own, shield, impactId, spins, rotations, animate, duration, legacySpin, activated, respawn }: {
 vehicle: Vehicle; board: Board; name: string; own: boolean; shield: boolean; impactId?: string;
 spins: number; rotations: number; animate: boolean; duration: number; legacySpin: boolean; activated: boolean; respawn?: VisualEffect
}) {
 const x = usePresentationTween(`${v.x * 100}%`, animate, duration)
 const y = usePresentationTween(`${(board.height - 1 - v.y) * 100}%`, animate, duration)
 const rotate = usePresentationTween(rotations * 360, animate, Math.min(.45, duration * 2))
 return <motion.div className={`vehicle${own ? ' own-vehicle' : ''}${shield ? ' shield-effect' : ''}`} role="gridcell"
   aria-label={`${name} på position ${v.x}, ${v.y}, riktning ${v.direction}`} data-testid="player-vehicle"
   data-respawning={respawn ? (respawn.motion ? 'on' : 'static') : undefined} data-player-id={v.playerId} data-player-name={name} data-x={v.x} data-y={v.y} data-direction={v.direction} data-status={v.status}
   style={{ x, y, width: `${100/board.width}%`, height: `${100/board.height}%` }}>
   <Recoil impactId={impactId} animate={animate}>
     <motion.span className={`vehicle-spin${legacySpin ? ' legacy-laser-spin' : ''}`} data-testid="vehicle-spin"
       data-spin-count={spins} data-rotation={rotations * 360} style={{ rotate }}>
       <VehicleDirection direction={v.direction} animate={animate} duration={duration}/>
     </motion.span>
   </Recoil>
   {activated && <span className="ability-badge" data-testid="shield-effect">Shield</span>}
   <small>{name}</small>
 </motion.div>
})
const StaticBoard = memo(function StaticBoard({board}: {board: Board}) {
 return <>
  {board.walls.map(w=><div className={`wall wall-${w.direction.toLowerCase()}`} data-testid="board-wall" data-x={w.cell.x} data-y={w.cell.y} data-direction={w.direction} key={`${w.cell.x}-${w.cell.y}-${w.direction}`} style={{left:`${w.cell.x/board.width*100}%`,top:`${(board.height-1-w.cell.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {board.pits.map(p=><div className="pit" data-testid="board-pit" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {(board.obstacles??[]).map(p=><div className="obstacle" data-testid="board-obstacle" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
  {(board.conveyors??[]).map((c,i)=><div className="conveyor" data-testid="board-conveyor" data-x={c.position.x} data-y={c.position.y} data-direction={c.direction} key={i} style={{left:`${c.position.x/board.width*100}%`,top:`${(board.height-1-c.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><Icon name="FORWARD_1"/></div>)}
  {(board.rotators??[]).map((r,i)=><div className="rotator" data-testid="board-rotator" data-x={r.position.x} data-y={r.position.y} data-rotation={r.rotation} key={i} style={{left:`${r.position.x/board.width*100}%`,top:`${(board.height-1-r.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><Icon name={r.rotation==='CLOCKWISE'?'TURN_RIGHT':'TURN_LEFT'}/></div>)}
  {(board.checkpoints ?? []).map(c=><div className={`checkpoint checkpoint-${c.order}`} aria-label={`Checkpoint ${c.order}`} data-testid="board-checkpoint" data-checkpoint-id={c.id} data-checkpoint-order={c.order} data-x={c.position.x} data-y={c.position.y} key={c.id} style={{left:`${c.position.x/board.width*100}%`,top:`${(board.height-1-c.position.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><span>{c.order}</span></div>)}
  {(board.controlPoints ?? []).map(p=><div className="control-point" aria-label={`Control point at ${p.x}, ${p.y}`} data-testid="board-control-point" data-x={p.x} data-y={p.y} key={`${p.x}-${p.y}`} style={{left:`${p.x/board.width*100}%`,top:`${(board.height-1-p.y)/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}} />)}
 </>
})
export const GameBoard = memo(function GameBoard({board,vehicles,players,currentPlayerId,playbackEvent,laserEvents,laserSpins={},laserRotations=laserSpins,activeShieldPlayerIds=[],effects,shake,animate=true,duration=.2}:{board:Board;vehicles:Vehicle[];players:Player[];currentPlayerId:string;playbackEvent?:RoundEvent;laserEvents?:RoundEvent[];laserSpins?:Record<string,number>;laserRotations?:Record<string,number>;activeShieldPlayerIds?:string[];effects?:VisualEffect[];shake?:ShakeCue;animate?:boolean;duration?:number}){
 const boardElement = useRef<HTMLDivElement>(null)
 const reducedMotion = useReducedMotionPreference()
 const names=Object.fromEntries(players.map(p=>[p.id,p.name]));
 const shieldVehicleId=playbackEvent?.type==='SHIELD_ACTIVATED'?playbackEvent.vehicleId:undefined
 const visualEffects = effects ?? (laserEvents ?? (playbackEvent ? [playbackEvent] : [])).flatMap(event => {
  const descriptor = describeEffect({id:`legacy:${event.sequence}`,event,at:0,until:650,stream:'playback'},board,currentPlayerId)
  return descriptor ? [{...descriptor,motion:!reducedMotion,age:0}] : []
 })
 const shakeId = shake?.id
 useEffect(() => {
  if (!shakeId || reducedMotion) return
  const animation = boardElement.current?.animate([{ transform:'none' }, { transform:'translate(2px,-1px)',offset:.25 }, { transform:'translate(-1px,1px)',offset:.7 }, { transform:'none' }], { duration:120, easing:'ease-out' })
  return () => animation?.cancel()
 }, [shakeId, reducedMotion])
 return <div className="board-wrap" style={{'--board-ratio':board.width/board.height} as CSSProperties}><div ref={boardElement} className="board" role="grid" aria-label="Spelplan" data-testid="game-board" data-width={board.width} data-height={board.height} style={{gridTemplateColumns:`repeat(${board.width}, 1fr)`,aspectRatio:`${board.width}/${board.height}`,backgroundSize:`${100/board.width}% ${100/board.height}%`}}>
  <StaticBoard board={board}/>
  <LaserEffects board={board} events={visualEffects}/>
  <BoardEffects board={board} events={visualEffects}/>
  {vehicles.map(v=><RobotVehicle key={v.id} vehicle={v} board={board} name={names[v.playerId]} own={v.playerId===currentPlayerId}
    shield={v.id===shieldVehicleId||activeShieldPlayerIds.includes(v.playerId)} activated={v.id===shieldVehicleId}
    respawn={visualEffects.find(item=>item.kind==='respawn'&&item.event.vehicleId===v.id)}
    impactId={visualEffects.find(item=>item.event.vehicleId===v.id&&isImpact(item.event))?.id}
    spins={laserSpins[v.id]??0} rotations={laserRotations[v.id]??0} animate={animate} duration={duration}
    legacySpin={playbackEvent?.type==='TURN'&&playbackEvent.actionType==='LASER'&&playbackEvent.vehicleId===v.id}/>) }
 </div></div>
})
