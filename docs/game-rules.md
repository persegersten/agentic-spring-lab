# Wreckage — Game Rules v2

This document is the authoritative rules specification for Wreckage v2.

It replaces the previous combat-oriented ruleset. Wreckage v2 is a fast,
programmed-movement multiplayer game for 2–10 players, designed for a typical
match length of approximately 10–20 minutes.

The backend is authoritative. If a rule is not described here, the game engine
must not invent one.

When implementation, tests, other documentation, and this document disagree,
the discrepancy must be resolved explicitly. Existing implementation is not
implicitly authoritative.

---

## 1. Design goals

Wreckage v2 is built around four ideas:

1. **Simultaneous planning** — all players choose their programs at the same time.
2. **Programmed movement** — each player commits several commands before seeing how opponents move.
3. **Deterministic chaos** — interaction comes from pushing, walls, pits, board elements and initiative, not dice or random cards.
4. **Short fixed matches** — no player is permanently eliminated and the game ends after a configured number of rounds.

The intended player experience is:

> I had a perfect plan until another player moved me one square.

---

## 2. Players and match size

A game supports:

```text
minPlayers = 2
maxPlayers = 10
```

A game may start when at least two players have joined and the player who created
the game explicitly starts it. Starting closes the lobby permanently.

The lobby and shareable game-link concept remain part of the game.

---

## 3. Board

The game is played on a rectangular grid of square cells.

A board has:

```text
width
height
```

Positions use integer coordinates:

```text
Position(x, y)
```

The bottom-left cell is `(0, 0)`.

The x-coordinate increases to the right and the y-coordinate increases towards
`NORTH`.

A position is inside the board when:

```text
0 <= x < width
0 <= y < height
```

The board may contain:

- floor cells,
- edge walls,
- pits,
- conveyors,
- rotators,
- checkpoints,
- control points,
- spawn points.

Not every map must use every board element.

### 3.1 Edge walls

Walls exist **between cells**, not as occupied cells.

A wall is therefore defined by an edge, conceptually:

```text
Wall(Position cell, Direction edge)
```

A wall blocks movement across that edge in both directions.

Example:

```text
A | B
```

A vehicle in A cannot move into B and a vehicle in B cannot move into A.

An outer board edge may also contain a wall. If an outer edge does not contain a
wall, moving beyond that edge causes a crash.

### 3.2 Pits

A pit occupies a cell. A vehicle entering a pit crashes immediately.

### 3.3 Spawn points

Each player is assigned a spawn point and initial orientation by the map.

A map used for a game must provide enough spawn points for the configured player
capacity.

Spawn points are ordinary board positions during play. Respawn collision rules
are defined in section 15.

---

## 4. Vehicle

Each player controls exactly one vehicle.

A vehicle has, at minimum:

```text
vehicleId
playerId
position
orientation
status
score
spawnPoint
spawnOrientation
visitedCheckpoints
```

Possible orientations are:

```text
NORTH
EAST
SOUTH
WEST
```

Possible gameplay statuses are:

```text
ACTIVE
CRASHED
```

A crashed vehicle is not present on the board for the remainder of the current
round.

---

## 5. Game configuration

The core configurable values are:

```text
maxPlayers
joinTimeoutSeconds
planningTimeoutSeconds
programSize
checkpointScore
controlPointScore
crashPenalty
pushCrashScore
mapId
```

Recommended defaults:

```text
maxPlayers = 9
joinTimeoutSeconds = 300
planningTimeoutSeconds = 30
programSize = 3
checkpointScore = 2
controlPointScore = 1
crashPenalty = -1
pushCrashScore = 1
```

`maxPlayers` must be between 2 and 10.

`programSize` is configurable, but **3 is the normal game mode**. The first
implementation should support values from 1 to 5 unless a narrower range is
chosen explicitly elsewhere.

The selected map must support the configured number of players.

When the lobby closes, the server fixes the match settings from the number of
players that actually joined:

| Players | Board | Rounds |
|---:|---:|---:|
| 2–3 | 10×10 | 7 |
| 4–6 | 12×12 | 6 |
| 7–10 | 16×16 | 5 |

`maxPlayers` is only the lobby capacity and does not select these settings.

---

## 6. Round lifecycle

A match consists of a fixed number of rounds.

The externally meaningful round lifecycle is:

```text
PLANNING
   ↓
RESOLVING
   ↓
PLAYBACK
```

Resolution is atomic from the client's point of view. The server calculates the
complete authoritative result before playback begins.

Internally, `RESOLVING` processes each program register in order:

```text
REGISTER 1
    player commands in initiative order
    board effects

REGISTER 2
    player commands in initiative order
    board effects

...

REGISTER N
    player commands in initiative order
    board effects

ROUND-END SCORING
```

Playback must never influence game results.

---

## 7. Commands

Every active player always has access to the same command set.

There is no random card draw and no private dealt hand.

The core commands are:

```text
FORWARD_1
FORWARD_2
REVERSE_1
TURN_LEFT
TURN_RIGHT
U_TURN
WAIT
```

Commands may be repeated within the same program.

Example program:

```text
FORWARD_2
TURN_RIGHT
FORWARD_1
```

---

## 8. Planning

During `PLANNING`, every active player constructs an ordered program containing
exactly `programSize` commands.

Other players may see whether a player is ready, but may not see that player's
program before resolution/playback.

A player may edit the program until either:

- the player locks it, or
- the planning timeout expires.

A locked program is immutable for that round.

If the planning timeout expires before a player has filled every register,
missing registers are filled with:

```text
WAIT
```

When all active players have locked complete programs, resolution may start
immediately without waiting for the timeout.

---

## 9. Initiative

Programs are resolved one register at a time.

Within one register, player commands are resolved sequentially according to the
round's initiative order.

The initial initiative order is the stable player join order.

Example:

```text
Round 1: A B C D
Round 2: B C D A
Round 3: C D A B
Round 4: D A B C
```

Initiative rotates one position after every round.

The initiative order for a round is stored as part of the authoritative round
state so replay and debugging never depend on recalculating it.

---

## 10. Rotation commands

`TURN_LEFT` rotates the vehicle 90 degrees counter-clockwise.

```text
NORTH -> WEST
WEST  -> SOUTH
SOUTH -> EAST
EAST  -> NORTH
```

`TURN_RIGHT` rotates the vehicle 90 degrees clockwise.

```text
NORTH -> EAST
EAST  -> SOUTH
SOUTH -> WEST
WEST  -> NORTH
```

`U_TURN` rotates the vehicle 180 degrees.

Rotation does not change position.

---

## 11. Translation commands

`FORWARD_1` performs one forward movement step.

`REVERSE_1` performs one backward movement step without changing orientation.

`FORWARD_2` performs **two consecutive forward movement steps**.

It must not teleport two cells.

Conceptually:

```text
FORWARD_2 = stepForward() + stepForward()
```

Each step independently resolves:

- walls,
- pushes,
- pits,
- leaving the board,
- checkpoint entry.

If the active vehicle crashes during the first step, the second step is not
executed.

`WAIT` changes neither position nor orientation.

---

## 12. Movement across walls

Before a vehicle or pushed vehicle crosses from one cell to an adjacent cell,
the engine checks the edge between those cells.

If that edge contains a wall, movement across it is blocked.

For a simple move, the active vehicle remains in its current position.

For a push chain, if **any required displacement** in the chain is blocked by a
wall, the entire movement step fails atomically and no vehicle in the chain
moves.

The command is still consumed.

---

## 13. Pushing

Both forward and reverse translation may push another vehicle.

If active vehicle A tries to enter a cell occupied by B, B is pushed one cell in
the movement direction.

Example:

```text
A -> B .
```

becomes:

```text
. A B
```

Pushes may form chains.

```text
A -> B C .
```

becomes:

```text
. A B C
```

The orientation of pushed vehicles does not change.

A push chain is one atomic movement step. It either resolves to a valid result
or, when blocked by a wall, does not move any vehicle.

Pits and open board edges are not blockers. They are lethal destinations and are
resolved as crashes.

---

## 14. Crash

A vehicle crashes when it:

- enters a pit,
- is pushed into a pit,
- moves beyond an open board edge,
- is pushed beyond an open board edge,
- is affected by another board element explicitly defined as lethal.

A crash has the following effects:

1. The vehicle is removed from the active board state for the remainder of the round.
2. Its remaining commands in the current round are skipped.
3. Its score changes by `crashPenalty`.
4. Its status becomes `CRASHED`.
5. It is scheduled to respawn at the start of the next round.

Scores are allowed to become negative.

### 14.1 Push-caused crash score

If a crash occurs during another player's translation command because of that
command's push chain, the player executing the command receives
`pushCrashScore`.

The active player receives the score even when the crashed opponent is not the
first vehicle in the push chain.

If more than one opponent crashes during the same command, the active player
receives the score once for each crashed opponent.

A player never receives a push-crash score for crashing their own vehicle.

Crashes caused later by conveyors, rotators, or other board effects do not award
a push-crash score in the v2 core rules.

---

## 15. Respawn

A crashed vehicle respawns at the start of the next round before planning
begins.

The engine first attempts to place it on its assigned spawn point with its
assigned spawn orientation.

If that spawn point is occupied, the engine chooses the first available spawn
point using the map's stable spawn-point order, starting from the vehicle's own
spawn point and wrapping around.

If every map spawn point is occupied, the vehicle remains `CRASHED` for that
round and the engine retries at the next round start.

A vehicle that cannot respawn does not submit a program and does not block
planning readiness.

No player is permanently eliminated from the match.

---

## 16. Checkpoints

A checkpoint occupies a board cell and has a stable checkpoint identifier.

When a vehicle enters or is moved onto a checkpoint for the first time in that
match, that player receives:

```text
checkpointScore
```

The same player may score the same checkpoint only once per match.

Different players may score the same checkpoint independently.

Checkpoints do not need to be visited in a predefined order.

Checkpoint scoring applies when the vehicle reaches the cell through:

- its own movement,
- a push,
- a conveyor or other board movement effect.

---

## 17. Control points

A control point occupies a board cell.

At the end of each round, every active vehicle standing on a control point gains:

```text
controlPointScore
```

Control-point scoring occurs after the last register and its board effects.

A map may contain zero or more control points.

---

## 18. Board effects

Board effects are resolved after **every register**, after all player commands in
that register have completed.

The v2 core board-effect order is:

```text
1. conveyors
2. rotators
3. checkpoint detection caused by board movement
4. lethal-position/crash resolution where needed
```

A future board effect must define its place in this order before it is
implemented.

### 18.1 Conveyors

A conveyor has a position and direction.

A vehicle on a conveyor is moved one cell in the conveyor direction when
conveyors activate.

Conveyor movement uses the same wall, push, pit and open-edge rules as normal
movement.

Conveyors are resolved in a deterministic map-defined order.

A conveyor-caused push does not award `pushCrashScore` in the v2 core rules.

### 18.2 Rotators

A rotator occupies a cell and has one of two effects:

```text
CLOCKWISE
COUNTER_CLOCKWISE
```

A surviving vehicle on that cell rotates 90 degrees when rotators activate.

A rotator does not move the vehicle.

---

## 19. Scoring summary

Default scoring is:

```text
first visit to checkpoint  +2
control point at round end +1
opponent crashes from your push command +1
own crash                  -1
```

All values are configuration values; the rule is the event that causes the
score change, not the numeric default.

Every score change must be represented by an authoritative event.

---

## 20. End of game

The game ends after `roundLimit` rounds have completed.

There is no last-vehicle-standing victory condition and no permanent player
elimination.

The player or players with the highest score win. Equal highest scores always
produce a shared victory; checkpoints, crashes, initiative and identifiers are
not tie breakers.

No sudden-death round is created automatically.

---

## 21. Match-length guidance

The rules must support short games.

The fixed match settings by actual player count are:

| Players | Suggested board | Suggested rounds |
|---:|---:|---:|
| 2–3 | 10×10 | 7 |
| 4–6 | 12×12 | 6 |
| 7–10 | 16×16 | 5 |

The target timing for normal play is approximately:

```text
planning:   20–30 seconds
resolution: <1 second server-side
playback:    5–15 seconds
```

A complete match should normally fit within approximately 10–20 minutes.

---

## 22. Authoritative resolution and events

The server owns all game state and computes all results.

The client sends intentions such as:

```text
submitProgram(...)
lockProgram(...)
```

The client does not decide:

- final movement,
- push results,
- crashes,
- board effects,
- score changes,
- initiative,
- winner.

Round resolution produces both:

```text
final game state
ordered event stream
```

The event stream is authoritative and is used for playback, debugging and
reconnection.

Events should represent meaningful facts such as:

```text
COMMAND_STARTED
VEHICLE_MOVED
VEHICLE_TURNED
VEHICLE_PUSHED
MOVE_BLOCKED
VEHICLE_CRASHED
VEHICLE_RESPAWNED
CONVEYOR_MOVED
ROTATOR_TURNED
CHECKPOINT_REACHED
SCORE_CHANGED
REGISTER_COMPLETED
ROUND_COMPLETED
GAME_FINISHED
```

The exact Java representation may evolve, but the event stream must contain
enough information for a client to replay the result without predicting rules.

---

## 23. Determinism

The game engine is deterministic.

Given the same:

```text
game state
board/map
round number
initiative order
player programs
configuration
```

it must always produce the same:

```text
resulting state
score changes
event sequence
```

The v2 core rules require no gameplay randomness.

If randomness is introduced by a later feature, it must use an injectable or
persisted seed/source so replays and tests remain deterministic.

---

## 24. Core invariants

After every resolved atomic step, all applicable invariants must hold.

### Position uniqueness

No two active vehicles may occupy the same board cell.

### Active-position validity

Every `ACTIVE` vehicle has exactly one position inside the board and is not on a
pit after lethal effects have been resolved.

### Crashed-state invariant

A `CRASHED` vehicle does not participate in movement, pushing or board effects
for the remainder of that round.

### Orientation invariant

Every vehicle has exactly one valid orientation.

### Score invariant

Every score mutation is caused by a documented rule and represented by an
authoritative event.

### Program invariant

Every active, successfully respawned player has exactly `programSize` resolved
commands for the round after timeout handling.

### Determinism invariant

The same authoritative input produces the same state and event sequence.

### Server-authority invariant

Client-side animation never changes authoritative game state.

---

## 25. Explicitly removed v1 rules

The following v1 mechanics are **not part of Wreckage v2 core gameplay** and
must not influence resolution:

- random command-card dealing,
- mandatory use of a dealt hand,
- cannon fire,
- hit points or damage counters,
- malfunction cards,
- damage-based elimination,
- last-surviving-player victory,
- acceleration,
- handling,
- armour,
- weapon equipment,
- permanent elimination.

Legacy code for these mechanics may exist temporarily during refactoring, but
it is not authoritative and must eventually be removed.

---

## 26. Initial implementation scope

The minimum playable v2 slice is:

```text
2–10 players
square grid
programSize = 3 by default
FORWARD_1
FORWARD_2
REVERSE_1
TURN_LEFT
TURN_RIGHT
U_TURN
WAIT
rotating initiative
edge walls
push chains
pits and open-edge crashes
respawn
checkpoints
score
fixed round limit
authoritative playback events
```

Conveyors, rotators and control points are the next board features after this
minimum slice.

Weapons, damage, robot classes, mines and special abilities are future features
and must not be implemented implicitly.

---

## 27. Rule authority

This document defines intended Wreckage v2 gameplay.

Tests should express these rules as executable examples at the lowest useful
level.

A coding agent may implement, refactor or review these rules, but it must not
invent additional gameplay rules to resolve ambiguity.

When an ambiguous case is discovered, make the rule explicit before relying on
an implementation-specific answer.
