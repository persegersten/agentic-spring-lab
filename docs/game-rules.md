# Wreckage — Game Rules v2

This is the authoritative rules specification for Wreckage 2.0. The backend is authoritative and playback never changes game state.

## Match

Wreckage is a checkpoint race for 2–10 players. The host starts the match; no player chooses a weapon or loadout. Match settings are fixed from the number of joined players: 2–3 players use a 10×10 board and 7 rounds, 4–6 use 12×12 and 6 rounds, and 7–10 use 16×16 and 5 rounds.

Each map supplies spawn points and CP1, CP2, CP3, and CP4. Checkpoints must be captured in that order. Entering the next checkpoint through voluntary movement, ramming, pushing, or a board effect captures it. Capturing CP4 ends resolution and the match immediately.

If nobody captures CP4 before the fixed round limit, players are ranked by checkpoints captured and then by the shortest traversable distance to their next checkpoint. Distance uses deterministic pathfinding over the static map, respecting bounds, walls, obstacles, and pits while ignoring vehicles. Players tied on progress and distance draw.

There are no hit points, weapon damage, ammunition, combat points, control-point scores, crash penalties, or damage-based victory conditions. Crashes caused by pits and open edges remain positional hazards; crashed robots may respawn in a later round.

## Programming

Every active player receives a private hand of eight cards at the start of each round and programs exactly five registers. Cards are sampled with replacement using these relative weights:

| Card | Weight |
|---|---:|
| FORWARD_1 | 20 |
| FORWARD_2 | 15 |
| FORWARD_3 | 10 |
| REVERSE_1 | 10 |
| TURN_LEFT | 15 |
| TURN_RIGHT | 15 |
| U_TURN | 5 |
| LASER | 10 |

The five selected cards must occur in the dealt hand with sufficient multiplicity. `WAIT` is internal and is never dealt. A planning timeout completes an unfinished program from unused dealt cards. Initiative is deterministic and rotates between rounds.

Each register resolves in initiative order, followed by conveyors and rotators. Translation is stepwise. A robot entering an occupied cell rams it; the shared push resolver moves the full chain atomically or moves nobody. Walls, obstacles, and immovable targets block a chain. Pits and open edges crash an unprotected displaced robot. Orientations do not change when a robot is pushed.

`LASER` occupies a register. It traces in the robot's facing direction until a wall, obstacle, board edge, or the first robot. A hit attempts up to two one-cell displacements through the same shared push resolver. Laser hits never deal damage or award points.

## Shield

Each player owns one shield charge for the entire match. During planning the player may select “Activate shield for this round”; it does not occupy a card slot. The server consumes the charge when the program is locked, including timeout locking, even when no attack occurs. A consumed shield cannot be selected again.

The shield is active for all five registers of that round and expires at round end. A shielded robot is immovable to Laser, ramming, chain pushing, and conveyor displacement. If it occurs anywhere in a required push chain, the complete displacement is blocked atomically. Shield protection is implemented as the immovable-target policy of the shared push resolver.

A shielded robot still executes its own cards normally, may ram and push unshielded robots, rotates normally, and captures checkpoints. Shield state shown to its owner is `AVAILABLE`, `SELECTED`, `ACTIVE`, or `CONSUMED`. A planning selection is private.

## Lifecycle and playback

Rounds move through `PLANNING → RESOLVING → PLAYBACK`. Hands, unfinished programs, and shield selection are private during planning. Resolution produces one persisted ordered event stream for all clients. Movement, Laser fire and hits, blocked pushes, crashes, shield activation, checkpoint capture, respawning, conveyors, and rotators are represented by authoritative events. Reconnecting clients restore the persisted program, shield state, board state, and playback.
