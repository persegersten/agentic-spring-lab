# Wreckage v2 Architecture

Wreckage is a turn-based multiplayer programming game. Players privately plan a
fixed sequence of vehicle commands, the backend resolves those programs against
the board, and every browser plays back the same authoritative event stream.

```text
React frontend -- HTTP / JSON --> Spring Boot backend --> H2 (in memory)
                                   API
                                   Application
                                   Domain
                                   Infrastructure
```

The frontend and backend are separate applications. In development, Vite proxies
`/games` to Spring Boot on port 8080. H2 in memory is the only supported database. The `in-memory` profile uses
H2 compatibility syntax for the existing migrations; no PostgreSQL server is needed.

## Game lifecycle

A game starts in `WAITING_FOR_PLAYERS`. Creation returns a one-time host token,
stored only as a hash by the server. Players join through the shareable game-lobby
link, and the authenticated host explicitly closes the lobby and starts the match.
At that point the domain derives and persists board dimensions and round limit
from the actual participant count. Normal game creation always selects Random
Corridors and assigns a fresh seed when the lobby is created. When the lobby
closes, the application uses that seed and the actual participant count to
generate and validate the board once; the persisted board is then reused for
the complete match and reconnects. Predefined map resources and their loader
remain available internally for tests and possible future use. Each joined
vehicle immediately has the default public loadout `LASER + SHIELD`; its authenticated owner may update the
weapon and ability while the lobby is open. Starting atomically fixes those
choices and initializes weapon state, including one use for equipped Rockets.
A game is `RUNNING` until all flags have owners and the current round completes,
or an existing match limit ends it.

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
The dealable set is `FORWARD_1`, `REVERSE_1`,
`TURN_LEFT`, `TURN_RIGHT`, `U_TURN`, and `LASER`; `WAIT` remains an internal legacy value.
Only the authenticated owner receives a hand through the player-specific API.

Each program may privately select the player's shield while it is still
available. The server consumes that shield when the program locks (including a
timeout lock), persists its once-per-match availability, and applies it for the
whole resolved round. There are no selectable loadouts or generic scheduled
combat actions.

## Resolution and board effects

`Board.hasWall` treats crossings of the board boundary as implicit walls in both
directions, independently of map wall definitions. Movement, reversing, conveyors,
ramming, push chains and Laser use that shared wall check. Attempting to cross
an outer edge leaves the vehicle in place without a crash, score penalty or queue
clear. Interior pits remain lethal. Existing map and historical state payloads
are unchanged; the frontend's existing arena frame represents the boundary.

`MovementEngine` applies vehicle commands one cell at a time. It enforces board
edges and edge walls, resolves chains of rams and pushes, and emits movement,
turn, ram, push, or crash events. Retired `FORWARD_2` and `FORWARD_3` remain readable in historical state and perform two or
three complete one-cell steps, so any step can interact with another vehicle or
board boundary.

Movement and board-effect results also retain transient atomic event batches.
`Round` evaluates new checkpoint entries within each batch in global flag order,
then appends capture score events to the existing flat playback stream. A push
chain is one batch; separate translation steps and separate conveyors are not
merged. Batch metadata is not added to saved playback or API event payloads.

The same one-cell chain resolver powers ramming and programming-card Laser.
Laser traces to the first vehicle in its current direction and attempts one
displacement step, emitting authoritative fire, hit, push/blocked, and turn events.
A surviving unshielded target turns to one of the four cardinal directions,
including its original direction, with equal probability. Its shield prevents
both effects; other blockers prevent only the displacement. Only the original
target turns. An injected random source is seeded from match UUID and resolution
number, making a transaction retry reproduce the same directions. It does not
invoke damage, ammunition, or combat-score effects. Every successful forced-movement event participates in checkpoint capture before
resolution continues.

`RegisterEffects` now carries only the active round shield set into the shared
movement/displacement path. Ramming, chain pushes, Laser, and conveyors consult
that same context, while shielded vehicles can still execute their own program.

Historical Flyway migrations V1–V22 remain immutable. The physical columns
`vehicle.damage`, `vehicle.rocket_ammo`, `vehicle.primary_weapon`,
`vehicle.special_ability`, and `game.weapon_crash_score` are intentionally not
dropped in this cleanup. `vehicle.primary_weapon` temporarily backs persisted
shield availability (`LASER` available, `REPULSOR` consumed) until a separate
schema migration can give the state a correctly named column. Round payloads
retain their delimiter shape; removed combat event fields are written blank and
obsolete combat playback rows are ignored when historical games are read.

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

The domain owns flag capture and all scoring. Each successful atomic movement
batch captures only globally available flags, in numerical order. Capturing the
last flag allows the rest of the round's commands, board effects, and normal
round-end scoring to finish. A vehicle also scores for occupying a control point
at round end and receives the configured crash penalty. Every score mutation
emits a `SCORE_CHANGED` event containing the old score, new score, delta, reason,
and optional checkpoint identifier.

Finished placements use only owned-flag counts, descending. Equal counts share
rank, with stable join order and competition numbering (`1, 1, 3`). Distances,
internal scores, and crash histories do not break ties. Vehicle count and crash
state never finish a game early. The existing limit values are retained; final
permitted rounds finish before results.

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

Joining returns a one-time player token. When the creator joins, the browser also
sends the one-time host token so the backend can persist that authenticated human
player's id as the game's host. Subsequent bot creation uses the normal player id
and token and is authorized against that persisted host id; no client host flag is
trusted. The browser keeps the player token in `localStorage`,
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
- `POST /games/{gameId}/shield`
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
application-layer seam for automated players. In continuous games it fills open
bot queue slots at match start and after each completed tick, including overdue
ticks reconciled before human input. Commands are selected uniformly using an
injectable random source and appended through the same continuous queue operation
as human commands, inside the existing game transaction and write lock. GET
requests never fill bot queues. Legacy round automation remains available. The authenticated
creator adds bots on demand through the lobby; bots otherwise use the same
aggregate membership, vehicles, cards, round lifecycle, and persistence as humans.

## Final flag completion

`Round` resolves the entire register sequence, including board effects and normal
round-end scoring, without capture-triggered early exits. `Game` checks global
flag ownership only at completion boundaries: after a continuous tick or when
completing a register-based round. The finished status prevents future ticks and
rounds. Placements derive solely from owned-flag counts, using shared competition
ranks and stable join order for tied rows. The legacy nullable distance field is
retained in responses but no longer populated or used as a tie-break.

The presentation hook exposes when the current timeline is settled. Final
standings wait for that boundary while input remains disabled as soon as the
server reports `FINISHED`. Initial loads and recovery snapshots snap to the
already authoritative result.

The configured register-round cap and continuous match duration are retained.
Continuous expiry resolves a pending tick scheduled at or before the deadline
once, including commands and board effects, then finishes. If the next tick is
scheduled beyond the cutoff, expiry finishes without executing it. It does not
extend the deadline or run a catch-up loop. All completion paths use the same
flag-only placements. Test-only limit fixtures keep the normal continuous
600000 ms duration and 2000 ms interval, positioning the match immediately before
its final due tick for reproducible browser verification.

## Persistence and migrations

Flyway creates the H2 schema at every backend startup. Hibernate uses
`ddl-auto=validate`, so entity changes require a versioned migration that works in
H2. Numeric database identifiers remain infrastructure
details; domain and API identities are UUIDs.

The current round, private programs, initiative, initial state, scores, and event
streams are persisted with the game. Persistence readers tolerate the previous
serialized round layout during upgrades, discard obsolete event kinds, and always
write the v2 layout.

Flag ownership is stored on players in `visited_checkpoints`, independently of
vehicle status, position, and crash penalties. Crash/respawn operations keep those
player records. Rehydration restores the same ownership before API snapshots
calculate capture counts and the globally available next flag; clients rebuild
the strip and captured-marker visibility from those snapshots rather than replaying
capture events into ownership.

Continuous-play captures also store per-flag acquisition ticks in the player's
`flag_acquisition_ticks` column (Flyway V26). On an unshielded Laser hit, the round
resolver processes ownership transfers before displacement entry batches or crash
scoring. `FLAG_TRANSFERRED` uses the existing event fields: subject/vehicle are
the previous owner, source/vehicle are the shooter, and checkpoint ID is the flag.
The existing round-event codec persists it without a new payload shape. API
player snapshots expose acquisition ticks alongside current ownership. Missing
historical timing is not reconstructed.

During uninterrupted tick playback, clients reconstruct pre-event ownership from
the final snapshot by reversing transfer events, then apply shown transfers in
order. The strip and owned counts therefore change at the transfer boundary;
cell-entry captures retain their existing snapshot presentation.
A temporary strip highlight uses the existing presentation clock, without new
sound or motion. Initial snapshots and recovery still display authoritative
ownership directly. This slice adds no new register-mode theft semantics.

The normal in-memory H2 profile loses all matches when its process stops. The
test-only `flag-recovery-e2e` profile uses a retained H2 file for local restart
verification, with the same Flyway migrations and entity mappings. It does not
change runtime/deployment profiles or introduce a schema migration. Its fixture
code and configuration are excluded from the production artifact. See
[the recovery verification guide](dev/flag-recovery-verification.md).

## Testing boundaries

Domain and application tests exercise rules directly. Spring integration tests
use H2 in memory to cover Flyway, Hibernate validation, repository
mapping, authentication, and JSON responses. The in-memory integration test
covers the H2 profile. Playwright starts the H2-backed backend and Vite frontend
to test planning, resolution, board effects, scoring, playback, reconnects,
results, respawning, and adding automated players from the lobby.

## Continuous command queue presentation

The continuous UI presents a horizontal mechanical conveyor, left to right as
5, 4, 3, 2, 1, with a locking gate between slots 3 and 2 and the execution
station after slot 1. Slots 3–5 retain their existing input eligibility. Confirmed
consecutive ticks move commands one slot right. Initial loads, skipped ticks and
crash clears snap to the authoritative snapshot without fictional intermediate
steps. `lastExecutedCommand` is derived from the latest resolved program; WAIT
means NOP, while null means no command executed.

New queue input, bot choices and card deals exclude `FORWARD_2` and `FORWARD_3`.
Their serialized names and engine behavior remain supported for existing state.

The continuous match view uses one `100dvh` grid for the board, command feed,
command buttons and scores. Portrait screens place the feed beside the buttons
below a proportionally sized board; short landscape screens use separate columns
for board, controls and scores. All six buttons and all participants' scores stay
visible without document or panel scrolling, including ten-player matches. The
compact score variant shows names and scores, preserves standings and winner
markers, and highlights the current player. Full statistics remain in the regular
score table. Queue cargo positions are calculated from the track's actual width
so animated shifts remain aligned when the viewport changes. A reserved status
row holds errors and retry controls without moving the other regions.

Continuous presentation freezes each accepted tick's initial vehicles, start events
and playback events. `useTickPresentation` supplies one cancellable monotonic
schedule to board feedback and sound; queue advancement starts on acceptance of
the same tick. Event identity is game id + tick + stream (start/playback) +
sequence, because sequences are local to their stream. Respawn start events add
cues at their already-authoritative initial positions. Same-tick revisions update
live queue, shields and status without restarting playback. The board keeps robot
DOM identity across ticks, applies confirmed events, and reconciles to final
server vehicles. Static geometry is memoized separately.

Robot translation, accumulated rotation and collision recoil use Motion for React
on separate transform layers. Queue cargo uses the measured horizontal track
width, with stable identities even for adjacent identical commands. These bounded
tweens never dispatch gameplay. Slot 1 overlays `lastExecutedCommand` for up to
300 ms on a confirmed tick: the new queue head is the *next* command, not the
one that just executed. Locked empty slots remain NOPs; append slots retain their
input/empty states. Resizing immediately realigns cargo rather than replaying a
shift.

Initial loads, skipped ticks and return from hidden tabs establish a silent
baseline; obsolete animation frames and Motion tweens are cancelled. A polling
failure marks snapshot ingestion disconnected; the first non-stale successful
snapshot increments a frontend presentation recovery epoch, including when its
revision is unchanged. An older response cannot clear pending recovery. That
epoch resets board and queue presentation without changing server contracts. Reduced motion disables
travel, spins and recoil, including preference changes while playback is active,
while retaining short visible event markers.
Laser beams use server endpoints; hit bursts require hit events. M3's pure
`visualEffects` planner admits at most eight simultaneous SVG groups (including
beams) and 48 fragments. Full event IDs seed deterministic debris geometry.
Higher priority local/destruction/capture effects can truncate lower priority
ones; dropped effects never backfill after capacity frees. Lifetimes are 160 ms
for beams, 180 ms for blocked actions, 240 ms for hits/collisions, 300 ms for
shield activation, 450 ms for destruction, 500 ms for respawn, and 550 ms for
checkpoint capture, shortened to the existing tick linger budget. Capture
requires the CHECKPOINT reason and a valid board checkpoint ID. These cosmetic
lifetimes do not change event application or sound timing.

Laser flashes, contact sparks, torn plates, collapsing chassis, deployment
brackets and capture markers use original SVG/CSS without a particle library.
Effects sit below robots and checkpoint numbers and never receive pointer input.
The checkpoint progress summary lives in the standings rather than over the
board. Gunmetal panels, amber command warnings, muted cyan protection and pale
green capture feedback share the existing icon vocabulary.

Board shake is limited to fresh confirmed local rams/crashes: at most two pixels
for 120 ms, with overlapping cues suppressed rather than stacked. Reduced motion
cancels active shake and decorative animation and retains static event markers;
turning motion back on does not replay already-visible effects. Negative CSS
delays are captured at admission so other effect expirations cannot restart them.
The countdown owns its 100 ms timer in `TickStatus`; it does not rerender the board.
Robot and static board layers are memoized, and the presentation scheduler stops
requesting frames when the tick settles. Sound uses a bounded,
deduplicated Howler adapter; settings and asset provenance are in `docs/assets.md`.

M2 separates audio policy from browser playback. `audioController` consumes cue
IDs even when suppressed, caps audible voices at eight and motor voices at two,
coalesces motors per vehicle/cue/tick, and applies remote cue cooldowns. Master
volume updates active voice gains; mute stops them without creating a backlog.
The Howler adapter preloads without playing, checks actual context readiness,
and silently primes HTML5 elements only during trusted gestures. Failed or
pending starts are unloaded rather than queued for later unlocking. Tick
cancellation stops only that tick's voices, preserving a separately acknowledged
submission cue when the response also advances the tick. Page exit unloads the
game's Howls and clears its playback listeners and watchdogs. No sound callback
changes game state or server timing.

## Continuous shield and flag standings

`POST /games/{gameId}/shield` uses the usual player headers and a UUID
`requestId`. The application reconciles an overdue tick under the game write lock
before accepting activation. The aggregate consumes shield availability and
schedules five protected ticks beginning with the next tick. Round resolution
passes active shields into the existing register effects without occupying a
command queue slot; SHIELD_ACTIVATED is recorded only when protection starts.
Crashes do not pause or refund duration. The serialized continuous payload adds
`S|playerId|requestId|startTick` rows; old payloads remain readable. Activation
receipts survive expiration and make accepted-request retries harmless even after
match completion. Request IDs cannot be reused across command and shield actions.
No database migration is needed.

The authenticated continuous snapshot adds `shieldPending`,
`shieldTicksRemaining` (future protected ticks, excluding the resolved tick), and
`activeShieldPlayerIds` for public protection rings. Pending activation reports
five remaining ticks; after the first protected tick there are four, and after
the fifth the shield is inactive. The browser keeps uncertain activation request
IDs in local storage, independently of pending commands. The shield control
remains visible even when the queue is full and shares the viewport budget with
all command buttons and standings.

Standings display authoritative `capturedCheckpoints` as flags, rather than the
internal score. Running ties retain join order; final standings retain server
placements and winner markers. Scores remain in persistence and responses for
compatibility but are not shown in standings, including their tooltips.
