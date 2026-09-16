import type { Board, Player, Vehicle } from '../types/game'
export function GameBoard({board,vehicles,players}:{board:Board;vehicles:Vehicle[];players:Player[]}){
 const names=Object.fromEntries(players.map(p=>[p.id,p.name]));
 return <div className="board-wrap"><div className="board" role="grid" aria-label="Spelplan" style={{gridTemplateColumns:`repeat(${board.width}, 1fr)`,aspectRatio:`${board.width}/${board.height}`}}>
  {vehicles.map(v=><div className="vehicle" key={v.id} title={names[v.playerId]} style={{left:`${v.x/board.width*100}%`,top:`${v.y/board.height*100}%`,width:`${100/board.width}%`,height:`${100/board.height}%`}}><span style={{transform:`rotate(${({NORTH:0,EAST:90,SOUTH:180,WEST:270})[v.direction]}deg)`}}>▲</span><small>{names[v.playerId]?.slice(0,2)}</small></div>)}
 </div></div>
}
