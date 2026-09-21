import type { Game, GameConfiguration, MovementOrder, PlayerGame, PlayerJoin, PlayerSession } from '../types/game'
async function json<T>(response: Response, fallback: string): Promise<T> {
  if (!response.ok) { const body = await response.json().catch(() => null) as {message?:string}|null; throw new Error(body?.message ?? fallback) }
  return response.json() as Promise<T>
}
export async function getDefaultConfiguration(): Promise<GameConfiguration> { return json(await fetch('/games/configuration/defaults'),'Kunde inte hämta standardinställningar') }
export async function createGame(configuration:GameConfiguration): Promise<Game> { return json(await fetch('/games',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(configuration)}),'Kunde inte skapa spelet') }
export async function addPlayer(gameId:string,name:string):Promise<PlayerJoin> { return json(await fetch(`/games/${gameId}/players`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name})}),'Kunde inte lägga till spelaren') }
export async function getGame(gameId:string):Promise<Game> { return json(await fetch(`/games/${gameId}`),'Kunde inte hämta spelet') }
function auth(session:PlayerSession){return {'X-Player-Id':session.playerId,'X-Player-Token':session.token}}
export async function getPlayerGame(session:PlayerSession):Promise<PlayerGame>{return json(await fetch(`/games/${session.gameId}/players/${session.playerId}`,{headers:{'X-Player-Token':session.token}}),'Kunde inte hämta spelarvyn')}
export async function startRound(session:PlayerSession):Promise<PlayerGame>{return json(await fetch(`/games/${session.gameId}/rounds`,{method:'POST',headers:auth(session)}),'Kunde inte starta rundan')}
export async function submitProgram(session:PlayerSession,orders:MovementOrder[]):Promise<PlayerGame>{return json(await fetch(`/games/${session.gameId}/rounds/current/program`,{method:'POST',headers:{...auth(session),'Content-Type':'application/json'},body:JSON.stringify({orders})}),'Kunde inte spara korten')}
