# Wreckage v2 Architecture

Wreckage is a turn-based multiplayer programming game. Players privately plan a
fixed sequence of vehicle commands, the backend resolves those programs against
the board, and every browser plays back the same authoritative event stream.

```text
React frontend -- HTTP / JSON --> Spring Boot backend --> PostgreSQL
                                   API
                                   Application
                                   Domain
                                   Infrastructure
```

The frontend and backend are separate applications. In development, Vite proxies
`/games` to Spring Boot on port 8080. PostgreSQL is the normal database; the
`in-memory` profile uses H2 in PostgreSQL compatibility mode.

## Game lifecycle

A game starts in `WAITING_FOR_PLAYERS`. Creation returns a one-time host token,
stored only as a hash by the server. Players join through the shareable game-lobby
link, and the authenticated host explicitly closes the lobby and starts the match.
At that point the domain derives and persists board dimensions and round limit
from the actual participant count. Each joined vehicle immediately has the
default public loadout `LASER + SHIELD`; its authenticated owner may update the
weapon and ability while the lobby is open. Starting atomically fixes those
choices and initializes weapon state, including one use for equipped Rockets.
A game is `RUNNING` while rounds remain
and becomes `FINISHED` only after that fixed round limit.

Each round moves through three externally visible phases:

1. `PLANNING`: every active vehicle owner selects five cards from a private
   eight-card hand and locks their order. A planning timeout fills remaining
   registers from unused cards in dealt-hand order and locks the program.
2. `RESOLVING`: the backend synchronously resolves each register. Players act in
   the persisted initiative order, which rotates between rounds.
3. `PLAYBACK`: the completed, ordered event stream is public and immutable. Each
   client independently animates that same stream before requesting the next round.

At round start the application uses an injectable random source and configurable
relative weights to deal eight server-owned programming cards per active player.
Players submit five cards, validated as a multiset against their persisted hand.
The dealable set is `FORWARD_1`, `FORWARD_2`, `FORWARD_3`, `REVERSE_1`,
`TURN_LEFT`, `TURN_RIGHT`, `U_TURN`, and `LASER`; `WAIT` remains an internal legacy value.
Only the authenticated owner receives a hand through the player-specific API.

Each program may additionally contain one scheduled action and register. The
application validates that action against the authoritative vehicle loadout and
remaining ammunition before the aggregate accepts a draft or locked program.
`SIDE_STEP` expands to its left and right action variants; no other unequipped
action is eligible. React derives its selector from the same public vehicle
state, but hiding controls is not a security boundary.

## Resolution and board effects

`MovementEngine` applies vehicle commands one cell at a time. It enforces board
edges and edge walls, resolves chains of rams and pushes, and emits movement,
turn, ram, push, or crash events. `FORWARD_2` and `FORWARD_3` perform two or
three complete one-cell steps, so any step can interact with another vehicle or
board boundary.

The same one-cell chain resolver powers ramming and programming-card Laser.
Laser traces to the first vehicle in its current direction and makes at most two
successive displacement attempts, emitting authoritative fire, hit, and push
events. This register-card path is separate from the temporarily retained
loadout action path and therefore does not invoke legacy damage or combat-score
effects. Every successful forced-movement event participates in checkpoint
capture before resolution continues.

After every register, `BoardEffectEngine` resolves conveyors in board order and
then rotators. Conveyors reuse the movement and pushing rules and have explicit
move, ram, push, and crash event types. Rotators update orientation. A vehicle
that leaves the board or enters a pit is `CRASHED` and does not act again during
that round.

Crashed vehicles are not removed from the match. At the start of the next round,
the aggregate tries their assigned spawn first and then checks the remaining
spawn points in stable wrapped order. A successful respawn emits a
`VEHICLE_RESPAWNED` event. If every spawn is occupied, the vehicle remains
crashed and waits for a later round.

## Scoring and results

The domain owns checkpoint progress and all scoring. Movement events capture
only the player's next map-defined checkpoint; capturing the fourth checkpoint
ends resolution immediately. A vehicle also scores for occupying a control point at round end, loses the
configured crash penalty, and may award the configured push-crash score to the
responsible player. Every mutation emits a `SCORE_CHANGED` event containing the
old score, new score, delta, reason, and optional checkpoint identifier.

When the round limit is reached without a completed checkpoint sequence,
placements are ordered by captured checkpoint count and then distance to the
next checkpoint. Equal progress and distance share first place as a draw.
Vehicle count and crash state never finish a game early.

## Playback model

`RoundEvent` is generic playback infrastructure shared by movement, board effects,
respawning, crashes, and scoring. Events carry their sequence, subject and source
identities, old and new position and orientation, plus optional scoring details.
The round persists its initial vehicle state, initial scores, start events, and
resolution playback so reconnecting clients reconstruct the same timeline without
predicting domain behavior.

React's `RoundPlayback` copies the initial state and applies events in sequence.
`GameBoard` renders the current derived positions and orientations, while the
debug view exposes the persisted initial state and ordered events. Once playback
finishes, the server's current aggregate remains the source of truth.

## API and authentication

Joining returns a one-time player token. The browser keeps it in `localStorage`,
keyed by game id so several games can remain active in the same browser, and sends
it as `X-Player-Token`; mutations also send `X-Player-Id`. A direct `/game/{id}`
link restores only that game's player session, while `/` always displays the
lobby. Only the authenticated player response contains that player's private
program. Public responses expose readiness and resolved playback but never
unrevealed programs. The server stores only SHA-256 token hashes.

The principal endpoints are:

- `GET /games/configuration/defaults`
- `POST /games`
- `POST /games/{gameId}/players`
- `POST /games/{gameId}/bots`
- `POST /games/{gameId}/start`
- `GET /games/{gameId}`
- `GET /games/{gameId}/players/{playerId}`
- `PUT /games/{gameId}/players/{playerId}/loadout`
- `PUT /games/{gameId}/rounds/current/program`
- `POST /games/{gameId}/rounds/current/program`
- `POST /games/{gameId}/rounds`
- `GET /games/running`
- `GET /games/finished`

Polling and reconnects replace the browser view with authenticated server state.
The player credential identifies the caller; it is not a second source of game
state.

## Backend boundaries

- `api` maps HTTP requests and domain results to JSON.
- `application` coordinates use cases, authentication, automation, transactions,
  and repository calls.
- `domain` contains the `Game` aggregate, round rules, board model, scoring, and
  the repository interface. It has no Spring, HTTP, JPA, or database dependency.
- `infrastructure` maps aggregates to JPA entities and implements persistence.

Dependencies point inward. `Game` is the aggregate root and owns player, vehicle,
round, scoring, respawn, and completion invariants. `PlayerAutomation` is an
application-layer seam that locks programs for automated players. Authenticated
human players add bots on demand through the lobby; bots otherwise use the same
aggregate membership, vehicles, cards, round lifecycle, and persistence as humans.

## Persistence and migrations

Flyway owns schema creation and evolution for PostgreSQL and H2. Hibernate uses
`ddl-auto=validate`, so entity changes require a versioned migration that works in
both database profiles. Numeric database identifiers remain infrastructure
details; domain and API identities are UUIDs.

The current round, private programs, initiative, initial state, scores, and event
streams are persisted with the game. Persistence readers tolerate the previous
serialized round layout during upgrades, discard obsolete event kinds, and always
write the v2 layout.

## Testing boundaries

Domain and application tests exercise rules directly. Spring integration tests
use PostgreSQL Testcontainers to cover Flyway, Hibernate validation, repository
mapping, authentication, and JSON responses. The in-memory integration test
covers the H2 profile. Playwright starts the H2-backed backend and Vite frontend
to test planning, resolution, board effects, scoring, playback, reconnects,
results, respawning, and adding automated players from the lobby.
