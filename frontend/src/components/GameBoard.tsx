import type { Board, Player, Vehicle } from '../types/game'
export function GameBoard({board,vehicles,players,currentPlayerId}:{board:Board;vehicles:Vehicle[];players:Player[];currentPlayerId:string}){
 const names=Object.fromEntries(players.map(p=>[p.id,p.name]));
 return <div className="board-wrap"><div className="board" role="grid" aria-label="Spelplan" data-testid="game-board" data-width={board.width} data-height={board.height} style={{gridTemplateColumns:`repeat(${board.width}, 1fr)`,aspectRatio:`${board.width}/${board.height}`}}>
  {vehicles.map(v=><div className={`vehicle${v.playerId===currentPlayerId?' own-vehicle':''}`} role="gridcell" aria-label={`${names[v.playerId]} på position ${v.x}, ${v.y}`} data-testid="player-vehicle" data-player-id={v.playerId} data-player-name={names[v.playerId]} data-x={v.x} data-y={v.y} data-direction={v.direction} key={v.id} style={{left:`${v.x/board.width*100}%`,top:`${v.y/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><span className="vehicle-direction" style={{transform:`rotate(${({NORTH:0,EAST:90,SOUTH:180,WEST:270})[v.direction]}deg)`}}>▲</span><small>{names[v.playerId]}</small></div>)}
 </div></div>
}
