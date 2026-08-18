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

The top-left position is:

```text
(0, 0)
```

The x-coordinate increases towards the right.

The y-coordinate increases downwards.

Example:

```text
(0,0) (1,0) (2,0) (3,0)
(0,1) (1,1) (2,1) (3,1)
(0,2) (1,2) (2,2) (3,2)
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

Orientation determines the direction in which a vehicle moves when executing `FORWARD`.

---

## 5. Movement Orders

During a movement resolution each player may issue one movement order.

The currently supported orders are:

```text
FORWARD
TURN_LEFT
TURN_RIGHT
```

Movement orders describe the player's intended action.

Players cannot directly specify their resulting position or orientation.

---

## 6. Forward

`FORWARD` attempts to move a vehicle exactly one grid position in its current orientation.

The movement vectors are:

| Orientation | Change       |
| ----------- | ------------ |
| NORTH       | `(x, y - 1)` |
| EAST        | `(x + 1, y)` |
| SOUTH       | `(x, y + 1)` |
| WEST        | `(x - 1, y)` |

Example:

```text
Position:    (3, 4)
Orientation: NORTH
Order:       FORWARD

Intended position: (3, 3)
```

Executing `FORWARD` does not change the vehicle's orientation.

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

If a `FORWARD` order would result in a position outside the board, the movement is blocked.

The vehicle remains at its original position and retains its original orientation.

Example:

```text
Board:       10 x 10
Position:    (0, 0)
Orientation: NORTH
Order:       FORWARD

Result:
Position:    (0, 0)
Orientation: NORTH
```

Leaving the board does not currently cause damage or destroy the vehicle.

---

## 9. Simultaneous Movement

Movement orders from all players are resolved simultaneously.

The result must not depend on:

* player ordering
* map iteration order
* insertion order of movement orders

The engine must therefore not resolve movement by simply moving one vehicle after another.

Movement resolution conceptually consists of three phases:

```text
1. Determine intentions
2. Resolve conflicts
3. Apply results
```

### Phase 1 — Determine intentions

For every vehicle, determine its intended resulting position and orientation without modifying the game state.

### Phase 2 — Resolve conflicts

Determine whether any intended movements conflict with other vehicles.

### Phase 3 — Apply results

After all conflicts have been resolved, construct the resulting game state.

---

## 10. Collision Rules

A collision occurs when simultaneous movement would result in an invalid vehicle configuration.

The current collision rules are deliberately simple.

### 10.1 Moving to an empty position

If a vehicle moves to an empty position and no other vehicle attempts to occupy that position, the movement succeeds.

```text
Before:

A -> [ ]

After:

[ ]  A
```

---

### 10.2 Moving into a stationary vehicle

If a vehicle attempts to move into a position occupied by a vehicle that does not move away, the movement is blocked.

Both vehicles remain in their original positions.

```text
A -> B
```

Result:

```text
A    B
```

---

### 10.3 Two vehicles moving to the same position

If two or more vehicles attempt to move into the same position, all conflicting movements are blocked.

Example:

```text
    A
    |
    v

[ target ] <- B
```

Both A and B remain in their original positions.

---

### 10.4 Vehicles swapping positions

Two vehicles may not pass through each other by swapping positions during the same movement resolution.

Example:

```text
Before:

A -> <- B
```

If A intends to move to B's position and B intends to move to A's position, both movements are blocked.

Both vehicles remain in their original positions.

---

### 10.5 Moving into a position vacated by another vehicle

A vehicle may move into another vehicle's current position if that vehicle successfully moves away during the same movement resolution.

Example:

```text
Before:

A -> B -> [ ]
```

If:

```text
A intends to move into B's position
B intends to move into the empty position
```

and B's movement succeeds, both movements succeed.

Result:

```text
[ ] A B
```

If B's movement is blocked, A's movement is also blocked.

This means that blocking one movement may cause other dependent movements to become blocked.

---

## 11. Missing Movement Orders

If a player does not provide a movement order, the vehicle performs no action.

Its position and orientation remain unchanged.

A missing order is therefore equivalent to:

```text
NO_ACTION
```

`NO_ACTION` does not need to exist as a public movement order.

---

## 12. Movement Invariants

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

Changing the iteration or insertion order of movement orders must not change the result.

---

## 13. Out of Scope

The following rules are intentionally **not part of the movement engine yet**:

* weapons
* combat
* damage
* vehicle destruction
* vehicle segment destruction
* reverse movement
* acceleration
* movement distances greater than one grid position
* terrain
* obstacles
* movement costs
* initiative
* turn phases
* persistent movement orders
* AI-controlled players

These rules must not be introduced implicitly by the movement implementation.

---

## 14. Rule Authority

This document defines the intended behaviour of the current movement engine.

Tests should express these rules as executable examples.

Implementation code must satisfy both the documented rules and their invariants.

When implementation, tests and this document disagree, the discrepancy must be investigated rather than automatically treating the existing implementation as correct.

A coding agent may implement, refactor or review these rules, but it must not invent new game rules to resolve ambiguity.

Ambiguous cases should instead be made explicit in this document before implementation.
