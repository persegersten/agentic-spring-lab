export type Player = {
  id: string
  name: string
}

export type Board = {
  width: number
  height: number
}

export type Game = {
  id: string
  players: Player[]
  board: Board
}
