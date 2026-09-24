# Wreckage — Game Rules

This document defines the game rules currently implemented by the Wreckage game engine.

It is intentionally limited to the rules required by the current implementation.

If a rule is not described here, the game engine must not invent one.

---

## 1. Game State

A `GameState` represents the complete state of a game at a specific point in time.

The game state contains:

* a `Board`
* the participating players
* one vehicle controlled by each player
* the position of each vehicle
* the orientation of each vehicle

Resolving movement produces a **new `GameState`**.

The previous state must not be modified.

Conceptually:

```text
GameState + MovementOrders -> GameState
```

Given the same game state and the same movement orders, the engine must always produce the same result.

---

## 2. Board

The game is played on a rectangular grid.

A board has:

```text
width
height
```

Positions are represented using integer coordinates:

```text
Position(x, y)
```

The bottom-left position is:

```text
(0, 0)
```

The x-coordinate increases towards the right.

The y-coordinate increases towards `NORTH`.

Example:

```text
(0,2) (1,2) (2,2) (3,2)
(0,1) (1,1) (2,1) (3,1)
(0,0) (1,0) (2,0) (3,0)
```

For a board with width `W` and height `H`, a position is valid when:

```text
0 <= x < W
0 <= y < H
```

A vehicle may never occupy a position outside the board.

---

## 3. Vehicle Position

Every vehicle occupies exactly one grid position.

Example:

```text
Position(3, 4)
```

At the end of movement resolution, no two vehicles may occupy the same position.

---

## 4. Orientation

Every vehicle has exactly one orientation.

The possible orientations are:

```text
NORTH
EAST
SOUTH
WEST
```

Orientation determines the direction in which a vehicle moves when executing `FORWARD` or `REVERSE`.

---

## 5. Movement Orders

During a movement resolution each player may issue one movement order.

The currently supported orders are:

```text
FORWARD
REVERSE
TURN_LEFT
TURN_RIGHT
```

Movement orders describe the player's intended action.

Players cannot directly specify their resulting position or orientation.

---

## 6. Forward and Reverse

### Forward

`FORWARD` attempts to move a vehicle exactly one grid position in its current orientation.

The movement vectors are:

| Orientation | Change       |
| ----------- | ------------ |
| NORTH       | `(x, y + 1)` |
| EAST        | `(x + 1, y)` |
| SOUTH       | `(x, y - 1)` |
| WEST        | `(x - 1, y)` |

Example:

```text
Position:    (3, 4)
Orientation: NORTH
Order:       FORWARD

Intended position: (3, 5)
```

Executing `FORWARD` does not change the vehicle's orientation.

### Reverse

`REVERSE` attempts to move a vehicle exactly one grid position opposite its current orientation.

The movement vectors are:

| Orientation | Change       |
| ----------- | ------------ |
| NORTH       | `(x, y - 1)` |
| EAST        | `(x - 1, y)` |
| SOUTH       | `(x, y + 1)` |
| WEST        | `(x + 1, y)` |

Executing `REVERSE` does not change the vehicle's orientation.

---

## 7. Turning

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

Turning does not change the vehicle's position.

---

## 8. Board Boundaries

A vehicle cannot move outside the board.

If a `FORWARD` or `REVERSE` order would result in a position outside the board, the movement is blocked.

The vehicle remains at its original position and retains its original orientation.

Example:

```text
Board:       10 x 10
Position:    (0, 9)
Orientation: NORTH
Order:       FORWARD

Result:
Position:    (0, 9)
Orientation: NORTH
```

Leaving the board does not currently cause damage or destroy the vehicle.

---

## 9. Command order

Commands are resolved sequentially in stable player order within each card
position. Every player's first card is resolved before any second card:

```text
A1 -> B1 -> C1 -> A2 -> B2 -> C2
```

Each command observes the state produced by the preceding command. A move into
an occupied position attempts to ram the occupying vehicle according to the
rules below.

### Ramming and pushing

Both `FORWARD` and `REVERSE` can ram another vehicle. The movement direction of
the active command is also the direction in which the other vehicle is pushed;
the pushed vehicle's own orientation is irrelevant and remains unchanged.

If another vehicle occupies the destination, the engine follows the contiguous
line of vehicles in the movement direction. The move succeeds only when the
position immediately beyond the line is empty and inside the board. Every
vehicle in the line then moves exactly one position, and the active vehicle
moves into the position vacated by the first vehicle.

The whole operation is atomic. If the line ends at a board boundary, no vehicle
moves and no event is produced. A successful ram produces one `PUSH` event for
each pushed vehicle, ordered from the front of the line back towards the active
vehicle, followed by one `RAM` event for the active vehicle. This ordering lets
playback apply every displacement without introducing an intermediate overlap.

---

## 10. Missing Movement Orders

If a player does not provide a movement order, the vehicle performs no action.

Its position and orientation remain unchanged.

A missing order is therefore equivalent to:

```text
NO_ACTION
```

`NO_ACTION` does not need to exist as a public movement order.

---

## 11. Movement Invariants

After every movement resolution, the following conditions must always hold.

### Board invariant

Every vehicle must occupy a valid board position.

```text
board.contains(vehicle.position) == true
```

### Uniqueness invariant

No two vehicles may occupy the same position.

### Vehicle invariant

Movement resolution must neither create nor remove vehicles.

The set of vehicles before and after movement must be identical.

### Orientation invariant

Every vehicle must have exactly one valid orientation.

### Immutability invariant

The input `GameState` must not be modified.

### Determinism invariant

The same game state and movement orders must always produce the same resulting game state.

### Ordering invariant

Resolution always follows the stable player order recorded by the round.

---

## 12. Automatic cannons and damage

After every programmed command has been resolved, each vehicle fires its
forward-facing cannon once in the stable vehicle order recorded by the round.
The server follows the shot one board position at a time. The shot stops at the
first vehicle, wall, or board boundary. A wall blocks the shot and the first
vehicle shields any vehicles behind it.

Every shot produces a `FIRE` event. A vehicle hit additionally produces `HIT`
and `DAMAGE` events in that order. Damage is deliberately minimal: every hit
increments the target vehicle's non-negative damage counter by one. Damage does
not currently destroy a vehicle. A vehicle with damage of at least one receives
one mandatory `MALFUNCTION_REVERSE` card when its next Planning phase starts.
The authoritative event stream contains the source, target, shot endpoints,
and damage before and after the event so clients only visualize the computed
result.

## 13. Board effects

After all programmed movement and automatic cannon actions have completed, the
round enters `BOARD_EFFECTS`. Board effects observe the resulting game state and
are resolved in the stable vehicle order recorded by the round.

The only currently supported board effect is `PIT`. A vehicle ending
Movement/Actions on a PIT position produces one `PIT` event. The event identifies
the affected player and vehicle and retains its position and orientation. PIT
does not yet destroy, move, damage, or respawn the vehicle because those rules
have not been defined.

PIT events are appended after every movement and cannon event and before the
round enters `PLAYBACK`.

## 14. Out of Scope

The following rules are intentionally **not part of the movement engine yet**:

* board effects other than PIT
* vehicle destruction
* vehicle segment destruction
* acceleration
* movement distances greater than one grid position
* terrain
* obstacle effects other than walls blocking cannon shots
* movement costs
* initiative
* AI-controlled players

* malfunction types other than `MALFUNCTION_REVERSE`
* repair or removal of malfunction cards while damage remains

## 15. Rounds and command cards

Each round has four phases: `PLANNING`, `MOVEMENT_ACTIONS`, `BOARD_EFFECTS`, and
`PLAYBACK`. In `PLANNING`, the server randomly deals the
configured number of cards to every participating player. A normal card is one
of the four movement orders. For a vehicle whose persisted damage is at least
one, exactly one normal card is replaced by `MALFUNCTION_REVERSE`. The total
hand size remains unchanged. `MALFUNCTION_REVERSE` executes with the same
movement rules as `REVERSE`.
Only its owner may retrieve the hand. The player submits all dealt cards
in the desired order; every dealt card, including a malfunction, must be
included exactly once. A submitted program is immutable. A malfunction remains
private during Planning because public round responses expose readiness but not
hands or unrevealed programs.

When every player is ready, the server enters `MOVEMENT_ACTIONS`. For card positions
one through the configured card count it resolves every player's card in stable
player order, then resolves automatic cannons. It next enters `BOARD_EFFECTS`
and resolves PIT positions. The initial state and authoritative event stream are
retained. Resolution completes atomically and exposes no partial result.

In `PLAYBACK`, all commands and the resulting states are public so every
client can reproduce the same animation. A new round may start only after the
current round has reached playback, and starts from its final vehicle state.

These rules must not be introduced implicitly by the movement implementation.

---

## 16. Rule Authority

This document defines the intended behaviour of the current movement engine.

Tests should express these rules as executable examples.

Implementation code must satisfy both the documented rules and their invariants.

When implementation, tests and this document disagree, the discrepancy must be investigated rather than automatically treating the existing implementation as correct.

A coding agent may implement, refactor or review these rules, but it must not invent new game rules to resolve ambiguity.

Ambiguous cases should instead be made explicit in this document before implementation.
