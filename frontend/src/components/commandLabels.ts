import type { MovementOrder } from '../types/game'

export const commandLabels: Record<MovementOrder, string> = {
  FORWARD_1: 'Framåt', FORWARD_2: 'Framåt 2', FORWARD_3: 'Framåt 3', REVERSE_1: 'Backa',
  TURN_LEFT: 'Vänster', TURN_RIGHT: 'Höger', U_TURN: 'Vänd', LASER: 'Laser', WAIT: 'NOP',
}
