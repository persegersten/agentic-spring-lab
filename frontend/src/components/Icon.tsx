import type { MovementOrder } from '../types/game'

type IconName = MovementOrder | 'lock' | 'plus' | 'execute' | 'sound' | 'muted' | 'flag' | 'shield' | 'winner'
const paths: Record<IconName, string> = {
  FORWARD_1: 'M12 20V4m-6 6 6-6 6 6', FORWARD_2: 'M12 20V4m-6 6 6-6 6 6', FORWARD_3: 'M12 20V4m-6 6 6-6 6 6',
  REVERSE_1: 'M12 4v16m-6-6 6 6 6-6', TURN_LEFT: 'M19 20v-9a5 5 0 0 0-5-5H4m5-5L4 6l5 5',
  TURN_RIGHT: 'M5 20v-9a5 5 0 0 1 5-5h10m-5-5 5 5-5 5', U_TURN: 'M5 20V9a7 7 0 0 1 14 0v11m-5-5 5 5 5-5',
  LASER: 'M3 21 9 15m0-6 6 6m-8 2 4-4m4-4 5-5M15 3v3m6 3h-3M5 5l2 2', WAIT: 'M8 5v14m8-14v14',
  lock: 'M7 10V7a5 5 0 0 1 10 0v3M5 10h14v11H5zM12 14v3', plus: 'M12 5v14M5 12h14',
  execute: 'm14 2-9 12h7l-2 8 9-12h-7z', sound: 'M3 9h4l5-5v16l-5-5H3zm13-1a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14',
  muted: 'M3 9h4l5-5v16l-5-5H3zm13 0 6 6m0-6-6 6', flag: 'M5 22V3h14l-3 5 3 5H5',
  shield: 'M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6z', winner: 'm12 2 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z',
}
export function Icon({ name }: { name: IconName }) {
  return <svg className="game-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>
}
