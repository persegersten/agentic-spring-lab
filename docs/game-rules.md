# Wreckage — Game Rules v2

This document is the authoritative rules specification for Wreckage v2.

It replaces the previous combat-oriented ruleset with a fast,
programmed-movement multiplayer game for 2–10 players, designed for a typical
match length of approximately 10–20 minutes. Combat v1 extends that core with
deterministic weapons and abilities that create positional problems without
restoring the old damage-elimination game.

The backend is authoritative. If a rule is not described here, the game engine
must not invent one.

When implementation, tests, other documentation, and this document disagree,
the discrepancy must be resolved explicitly. Existing implementation is not
implicitly authoritative.

---

## 1. Design goals

Wreckage v2 is built around five ideas:

1. **Simultaneous planning** — all players choose their programs at the same time.
2. **Programmed movement** — each player commits several commands before seeing how opponents move.
3. **Deterministic chaos** — interaction comes from pushing, walls, pits, board elements and initiative, not dice or random cards.
4. **Short fixed matches** — no player is permanently eliminated and the game ends after a configured number of rounds.
5. **Positional combat** — weapons and abilities change positions, future moves and access to board features more often than they merely accumulate damage.

The intended player experience is:

> I had a perfect plan until another player moved me one square.

Combat should reward predicting where vehicles will be after programmed
movement, using walls and other vehicles as cover, and pushing opponents towards
pits, open edges, conveyors and control areas. It uses no dice, random damage or
random weapon effects.

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
are defined in section 20.

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
damage
primaryWeapon
specialAbility
rocketAmmo
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

Every vehicle starts a match with `damage = 0`. Its loadout is fixed when the
match starts as described in section 11.

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
weaponCrashScore
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
weaponCrashScore = 1
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
    PRE_MOVEMENT actions
    player commands in initiative order
    POST_MOVEMENT actions in initiative order
    board effects

REGISTER 2
    PRE_MOVEMENT actions
    player commands in initiative order
    POST_MOVEMENT actions in initiative order
    board effects

...

REGISTER N
    PRE_MOVEMENT actions
    player commands in initiative order
    POST_MOVEMENT actions in initiative order
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
exactly `programSize` commands and may also schedule at most one action for the
round. A scheduled action consists of an action type and one register number in
the range `1..programSize`. Choosing no action is valid.

Other players may see whether a player is ready, but may not see that player's
program or scheduled action before resolution/playback. The action becomes
public when it resolves and appears in authoritative playback.

A player may edit the program until either:

- the player locks it, or
- the planning timeout expires.

A locked program and its optional action are immutable for that round.

If the planning timeout expires before a player has filled every register,
missing registers are filled with:

```text
WAIT
```

Timeout does not create an action. If the player did not schedule one, the
round resolves with no action for that vehicle.

When all active players have locked complete programs, resolution may start
immediately without waiting for the timeout.

---

## 9. Initiative

Programs and their optional scheduled actions are resolved one register at a
time.

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

## 10. Register resolution and action timing

Each register resolves completely in this authoritative order:

```text
1. PRE_MOVEMENT actions
2. programmed movement commands in initiative order
3. POST_MOVEMENT actions in initiative order
4. board effects
```

Then resolution continues to the next register. The action categories are:

```text
PRE_MOVEMENT:  SHIELD, ANCHOR
POST_MOVEMENT: LASER, REPULSOR, ROCKET, TURBO,
               SIDE_STEP_LEFT, SIDE_STEP_RIGHT
```

Pre-movement actions only affect their own vehicle and establish effects for
the register before any programmed command is resolved. Their relative order
therefore cannot change the result. Post-movement actions resolve sequentially
in the same initiative order used for programmed commands.

A scheduled action is skipped if its vehicle has already crashed when the
action would resolve. It is still that player's one action for the round.

Action timing uses the vehicle's state at the moment the action resolves. For
example, if A schedules `LASER` in register 2 and B moves behind a wall during
register 2 movement, the wall blocks A's post-movement shot.

---

## 11. Combat loadouts and action eligibility

Each vehicle has exactly one primary weapon and one special ability.

Combat v1 primary weapons are:

```text
LASER
REPULSOR
ROCKET
```

Combat v1 special abilities are:

```text
TURBO
SHIELD
SIDE_STEP
ANCHOR
```

A vehicle may schedule its equipped weapon or its equipped special ability as
its one action for the round. `SIDE_STEP` is scheduled as either
`SIDE_STEP_LEFT` or `SIDE_STEP_RIGHT`. A vehicle cannot schedule an unequipped
weapon or ability.

Every player receives the following valid default loadout immediately on joining
the lobby:

```text
primaryWeapon = LASER
specialAbility = SHIELD
```

While the game is `WAITING_FOR_PLAYERS`, a player may change either part of
their own loadout. Loadouts are public to every lobby participant; scheduled
round actions remain private during planning. No explicit confirmation is
required before the host starts the match.

The selected loadout is fixed when the match starts and cannot change during
the match. At that point Rocket ammunition is initialized to one use for a
vehicle equipped with `ROCKET`. Laser and Repulsor are unlimited and do not use
Rocket ammunition. Loadout and remaining ammunition are authoritative persisted
state and survive reconnects.

---

## 12. Weapon targeting and damage

### 12.1 Line of sight

Weapons fire from the vehicle's current position in its current orientation.
They inspect cells one at a time in a straight cardinal line. A shot stops at:

- the first active vehicle,
- an edge wall blocking passage between two cells,
- the board boundary, or
- the weapon's maximum range.

The first active vehicle in the line is the only possible target, so vehicles
provide cover for vehicles behind them. Weapons never fire diagonally. A wall
on the edge out of the firing vehicle's cell blocks the first inspected cell;
an open board boundary ends the shot without causing the firing vehicle to
move or crash.

### 12.2 Weapon damage

Weapon damage is a non-negative value that starts at:

```text
damage = 0
```

Damage persists between rounds until the vehicle crashes. Whenever applied
weapon damage makes the value satisfy:

```text
damage >= 3
```

the vehicle crashes immediately under the normal crash rules. Weapon damage
does not remove commands, create malfunction cards, reduce `programSize`, or
permanently eliminate a player.

Shield may prevent damage as described in section 14.1. Repulsor deals no
damage. Environmental crashes from pits, open edges and board effects do not
depend on the damage value.

---

## 13. Primary weapons

### 13.1 Laser

```text
range = 6
damage = 1
uses = unlimited
```

`LASER` is a post-movement action. It fires straight ahead using the shared
line-of-sight rules. If the first visible vehicle is within range, it receives
1 weapon damage. It crashes immediately if its resulting damage is at least 3.

### 13.2 Repulsor

```text
range = 3
damage = 0
uses = unlimited
```

`REPULSOR` is a post-movement action. It targets the first visible vehicle in a
straight line and attempts to push that vehicle exactly one cell directly away
from the firing vehicle.

The displacement uses the existing push-chain rules: it may push other
vehicles as a chain, any required wall crossing or anchored vehicle blocks the
complete push atomically, and pits or open edges are lethal destinations rather
than blockers. Orientations do not change. A Repulsor push can therefore be
more dangerous than a damage weapon.

### 13.3 Rocket

```text
range = 5
damage = 2
uses = 1 per match
```

`ROCKET` is a post-movement action. It fires straight ahead using the shared
line-of-sight rules. If the first visible vehicle is within range, it receives
2 weapon damage and crashes immediately if its resulting damage is at least 3.

Firing consumes the vehicle's single Rocket use whether the shot hits a
vehicle or is stopped by a wall, boundary or range. After firing, that vehicle
has no Rocket ammunition for the rest of the match and cannot schedule Rocket
again. A Rocket action skipped because its vehicle already crashed does not
fire and does not consume ammunition. Combat v1 has no Rocket area damage.

---

## 14. Special abilities

Each special ability may be used once per round, subject to the universal limit
of one action per player per round. Scheduling an ability therefore means the
vehicle cannot fire its weapon or use another ability that round.

### 14.1 Shield

`SHIELD` is a pre-movement action. When activated, Shield remains active for
the rest of that register and prevents the first 1 point of weapon damage the
vehicle would receive during that register. It is then spent for the register;
later weapon damage in the same register is applied normally.

A Laser hit for 1 against an unspent Shield applies 0 damage. A Rocket hit for
2 applies 1 damage. Shield does not protect against pushes, pits, board edges,
conveyors or any other environmental crash.

### 14.2 Anchor

`ANCHOR` is a pre-movement action. When activated, its vehicle cannot be pushed
for the rest of that register. This applies to pushes caused by normal vehicle
movement, Repulsor, conveyors, and any other board effect that uses the normal
push rules.

If an anchored vehicle is anywhere in a required push chain, the entire push
is blocked atomically. Anchor does not prevent weapon damage, the anchored
vehicle's own movement, or a crash caused when its own movement enters a pit or
leaves an open board edge.

### 14.3 Turbo

`TURBO` is a post-movement action that performs one additional forward movement
step. The step uses all normal movement rules, including walls, pushes, pits,
open edges, checkpoints and crashes. It uses the vehicle's position and
orientation when the action resolves.

### 14.4 Side Step

`SIDE_STEP_LEFT` and `SIDE_STEP_RIGHT` are post-movement actions. They attempt
to move the vehicle exactly one cell to the corresponding side relative to its
current orientation. The vehicle's orientation does not change.

Side Step respects edge walls, may enter a pit or leave an open edge and crash,
and may trigger a checkpoint. It never pushes. If the destination cell contains
another active vehicle, the action is blocked and neither vehicle moves.

---

## 15. Rotation commands

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

## 16. Translation commands

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

## 17. Movement across walls

Before a vehicle or pushed vehicle crosses from one cell to an adjacent cell,
the engine checks the edge between those cells.

If that edge contains a wall, movement across it is blocked.

For a simple move, the active vehicle remains in its current position.

For a push chain, if **any required displacement** in the chain is blocked by a
wall, the entire movement step fails atomically and no vehicle in the chain
moves.

The command is still consumed.

---

## 18. Pushing

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
or, when blocked by a wall or an active Anchor, does not move any vehicle.

Pits and open board edges are not blockers. They are lethal destinations and are
resolved as crashes.

---

## 19. Crash

A vehicle crashes when it:

- reaches `damage >= 3` from weapon damage,
- enters a pit,
- is pushed into a pit,
- moves beyond an open board edge,
- is pushed beyond an open board edge,
- is affected by another board element explicitly defined as lethal.

A crash has the following effects:

1. The vehicle is removed from the active board state for the remainder of the round.
2. Its remaining commands in the current round are skipped.
3. Its scheduled action is skipped if it has not yet resolved.
4. Its score changes by `crashPenalty`.
5. Its status becomes `CRASHED`.
6. It is scheduled to respawn at the start of the next round.

Scores are allowed to become negative.

### 19.1 Push-caused crash score

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

### 19.2 Weapon-caused crash score

If a player's weapon directly causes an opponent to crash during resolution of
that weapon action, the firing player gains `weaponCrashScore`. This applies
when Laser or Rocket raises the target's damage to at least 3, and when a
Repulsor push moves an opponent directly into a pit or beyond an open board
edge. The crashed player also receives the normal `crashPenalty`.

If one Repulsor action directly crashes more than one opponent through its push
chain, the firing player receives the score once for each crashed opponent.

The award is limited to the immediate resolution of the weapon action; the
engine does not track longer causal chains. For example, if Repulsor pushes B
onto a conveyor and that conveyor later moves B into a pit during board effects,
the shooter receives no `weaponCrashScore`.

A player never receives weapon-crash score for crashing their own vehicle.

---

## 20. Respawn

A crashed vehicle respawns at the start of the next round before planning
begins.

The engine first attempts to place it on its assigned spawn point with its
assigned spawn orientation.

On successful respawn, its damage is reset to `0`. Expended Rocket ammunition
is not restored because Rocket usage is per match.

If that spawn point is occupied, the engine chooses the first available spawn
point using the map's stable spawn-point order, starting from the vehicle's own
spawn point and wrapping around.

If every map spawn point is occupied, the vehicle remains `CRASHED` for that
round and the engine retries at the next round start.

A vehicle that cannot respawn does not submit a program and does not block
planning readiness.

No player is permanently eliminated from the match.

---

## 21. Checkpoints

A map defines four checkpoints with stable identities, explicit order, and board positions:
`CP1`, `CP2`, `CP3`, and `CP4`.

When a vehicle enters or is moved onto its player's next required checkpoint,
that player captures it and receives:

```text
checkpointScore
```

Progress is retained for the match. Future checkpoints have no effect until all
earlier checkpoints have been captured, and previously captured checkpoints do
not need to be revisited.

Different players may score the same checkpoint independently.

Checkpoint scoring applies when the vehicle reaches the cell through:

- its own movement,
- a push,
- a conveyor or other board movement effect.

---

## 22. Control points

A control point occupies a board cell.

At the end of each round, every active vehicle standing on a control point gains:

```text
controlPointScore
```

Control-point scoring occurs after the last register and its board effects.

A map may contain zero or more control points.

---

## 23. Board effects

Board effects are resolved after **every register**, after all player commands
and post-movement actions in that register have completed.

The v2 core board-effect order is:

```text
1. conveyors
2. rotators
3. checkpoint detection caused by board movement
4. lethal-position/crash resolution where needed
```

A future board effect must define its place in this order before it is
implemented.

### 23.1 Conveyors

A conveyor has a position and direction.

A vehicle on a conveyor is moved one cell in the conveyor direction when
conveyors activate.

Conveyor movement uses the same wall, push, pit and open-edge rules as normal
movement.

Conveyors are resolved in a deterministic map-defined order.

A conveyor-caused push does not award `pushCrashScore` in the v2 core rules.

### 23.2 Rotators

A rotator occupies a cell and has one of two effects:

```text
CLOCKWISE
COUNTER_CLOCKWISE
```

A surviving vehicle on that cell rotates 90 degrees when rotators activate.

A rotator does not move the vehicle.

---

## 24. Scoring summary

Default scoring is:

```text
next checkpoint captured  +2
control point at round end +1
opponent crashes from your push command +1
opponent crashes directly from your weapon +1
own crash                  -1
```

All values are configuration values; the rule is the event that causes the
score change, not the numeric default.

Every score change must be represented by an authoritative event.

---

## 25. End of game

The first player to capture `CP4` after `CP1` through `CP3` wins immediately.
No later movement, action, or board effect is resolved.

The game also ends after `roundLimit` rounds have completed. If nobody completed
the sequence, the player with the most checkpoints wins. Ties use Manhattan
distance from the player's final vehicle position to their next checkpoint;
equal progress and equal distance produce a draw.

There is no last-vehicle-standing victory condition and no permanent player
elimination.

No sudden-death round is created automatically.

---

## 26. Match-length guidance

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

## 27. Authoritative resolution and events

The server owns all game state and computes all results.

The client sends intentions such as:

```text
submitProgram(...)
scheduleAction(...)
lockProgram(...)
```

The client does not decide:

- final movement,
- action results,
- weapon targeting or damage,
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
ACTION_STARTED
VEHICLE_MOVED
VEHICLE_TURNED
VEHICLE_PUSHED
MOVE_BLOCKED
WEAPON_FIRED
WEAPON_BLOCKED
WEAPON_HIT
DAMAGE_APPLIED
DAMAGE_PREVENTED
VEHICLE_CRASHED
VEHICLE_RESPAWNED
SHIELD_ACTIVATED
ANCHOR_ACTIVATED
TURBO_ACTIVATED
SIDE_STEP
AMMO_CHANGED
CONVEYOR_MOVED
ROTATOR_TURNED
CHECKPOINT_REACHED
SCORE_CHANGED
REGISTER_COMPLETED
ROUND_COMPLETED
GAME_FINISHED
```

The exact Java names and representation are implementation details, but the
event stream must contain enough information for a client to replay the result
without calculating line of sight, damage, pushes, crashes or other rules.

---

## 28. Determinism

The game engine is deterministic.

Given the same:

```text
game state
board/map
round number
initiative order
player programs
scheduled actions
vehicle loadouts, damage and ammunition
configuration
```

it must always produce the same:

```text
resulting state
score changes
event sequence
```

Wreckage v2, including Combat v1, requires no gameplay randomness.

If randomness is introduced by a later feature, it must use an injectable or
persisted seed/source so replays and tests remain deterministic.

---

## 29. Core invariants

After every resolved atomic step, all applicable invariants must hold.

### Position uniqueness

No two active vehicles may occupy the same board cell.

### Active-position validity

Every `ACTIVE` vehicle has exactly one position inside the board and is not on a
pit after lethal effects have been resolved.

### Crashed-state invariant

A `CRASHED` vehicle does not participate in movement, pushing or board effects
for the remainder of that round, and its unresolved action is skipped.

### Orientation invariant

Every vehicle has exactly one valid orientation.

### Score invariant

Every score mutation is caused by a documented rule and represented by an
authoritative event.

### Program invariant

Every active, successfully respawned player has exactly `programSize` resolved
commands for the round after timeout handling.

### Action invariant

Every active player has zero or one scheduled action for the round. An action
names one valid register and is eligible under the vehicle's fixed loadout and
remaining ammunition.

### Damage invariant

Every active vehicle has damage from `0` through `2`. Reaching damage `3` or
greater causes an immediate crash, and successful respawn resets damage to `0`.

### Determinism invariant

The same authoritative input produces the same state and event sequence.

### Server-authority invariant

Client-side animation never changes authoritative game state.

---

## 30. Explicitly removed v1 rules

The following old v1 mechanics are **not part of Wreckage v2 or Combat v1** and
must not influence resolution:

- random command-card dealing,
- mandatory use of a dealt hand,
- old cannon rules,
- the old hit-point and damage-counter model,
- malfunction cards,
- damage-based elimination,
- last-surviving-player victory,
- acceleration,
- handling,
- armour statistics or inventory,
- the old multi-weapon equipment system,
- permanent elimination.

Combat v1's single `damage` value, fixed one-weapon loadout and immediate crash
threshold are explicitly different from those removed mechanics. Damage never
degrades a program or eliminates a player from the match.

Legacy code for these mechanics may exist temporarily during refactoring, but
it is not authoritative and must eventually be removed.

---

## 31. Implementation scope

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

Combat v1 extends this ruleset with one scheduled action per round, Laser,
Repulsor, Rocket, Turbo, Shield, Side Step, Anchor, simple weapon damage, fixed
loadouts and direct weapon-crash scoring. This document defines those rules;
their gameplay implementation belongs to later implementation features.

### 31.1 Combat v1 non-goals

Combat v1 does not define:

- dice or other random combat results,
- random damage,
- armour inventory,
- repair kits,
- area explosions,
- homing missiles,
- diagonal weapons,
- weapon pickups or upgrades,
- multiple actions per round,
- multiple weapons per vehicle,
- cooldown systems,
- mines,
- temporary map pickups, or
- special robot classes.

---

## 32. Rule authority

This document defines intended Wreckage v2 gameplay.

Tests should express these rules as executable examples at the lowest useful
level.

A coding agent may implement, refactor or review these rules, but it must not
invent additional gameplay rules to resolve ambiguity.

When an ambiguous case is discovered, make the rule explicit before relying on
an implementation-specific answer.
