# Wreckage Architecture

Wreckage is a small web application for a turn-based multiplayer vehicle combat
game. The system creates configured games, exposes a shareable lobby link, adds
players, and reads game state.
Movement and basic automatic cannon combat are resolved authoritatively by the backend.

```text
Browser
   |
   v
React frontend
   |
   | HTTP / JSON
   v
Spring Boot backend
   |
   +-- API
   +-- Application
   +-- Domain
   +-- Infrastructure
           |
           v
       PostgreSQL
```

The frontend and backend are separate applications. During local development,
Vite proxies requests under `/games` to Spring Boot on port 8080. The browser
therefore uses the same relative REST paths regardless of whether a request is
made directly or through the development server.

## Round and player views

Joining a game returns a one-time player token. The browser stores it in
`sessionStorage` and sends it in `X-Player-Token`; mutations also identify the
player with `X-Player-Id`. Only the authenticated player endpoint returns that
player's programming hand. The public game response contains readiness but no
hands or unrevealed programs. Tokens are stored server-side only as SHA-256
hashes.

On reload, the game URL selects the matching stored player credential and the
browser rebuilds the complete view from the authenticated player endpoint. The
credential identifies the player but is not game state: the board, vehicles,
current round, readiness, dealt hand, and the player's current private card
ordering are all persisted by the server. Reordering cards during planning uses
an authenticated `PUT` to the existing current-program resource; locking the
program remains the existing `POST`. The normal polling loop retries after a
temporary connection failure and replaces the rendered view with fresh server
state without changing other players or the game aggregate.

The aggregate persists the current round, programs and an authoritative ordered
event stream. After all programs are locked, `MovementEngine` resolves the
configured card positions in stable player order. `CannonEngine` then fires
every vehicle once, tracing each shot until the first vehicle, wall, or board
boundary. Hits increment a minimal persisted damage counter. The round then
enters `BOARD_EFFECTS`, where `BoardEffectEngine` emits a PIT event for every
vehicle on a persisted PIT position. The engines emit MOVE, TURN, FIRE, HIT,
DAMAGE and PIT events with enough state for React to visualize the persisted
events in server order without predicting a result.
When the next round starts, a damaged vehicle receives one mandatory private
`MALFUNCTION_NO_OP` card per damage point, capped at the hand size; the existing hand
validation ensures it is included in the submitted program.
The same event stream is returned to every client and also drives the
development debug view.

## Backend layers

Backend code is grouped by the `game` feature and then by responsibility:

- **API** contains the REST controller, JSON response types, and exception
  handling. It translates HTTP requests into application calls and domain
  results into HTTP responses.
- **Application** contains `GameService` and application-level errors. The
  service coordinates use cases and transaction boundaries.
- **Domain** contains `Game`, `Player`, `Board`, and the `GameRepository`
  interface. Domain code owns business rules and does not depend on Spring,
  HTTP, JPA, or PostgreSQL.
- **Infrastructure** implements the domain repository with Spring Data JPA and
  maps between domain objects and persistence entities.

Dependencies point inward: API and infrastructure may use application or
domain types, while the domain does not know about outer layers. New game rules
should normally enter the domain model rather than controllers or JPA entities.

`PlayerAutomation` is an application-layer profile seam used only for local
manual testing. The default implementation is a no-op. With the
`headless-players` Spring profile, the first player remains browser-controlled,
the lobby is filled to `maxPlayers`, and every later player's program is locked
using its dealt hand without reordering. The stable aggregate player order
identifies the first player, so no client session or additional persistence
field is needed for automated players. `GameService` invokes the automation
after joining and after starting each later round, before saving the aggregate.

A typical request follows this path:

```text
POST /games/{gameId}/players
        |
        v
GameController
        |
        v
GameService.addPlayer
        |
        v
Game.addPlayer
        |
        v
GameRepository (domain interface)
        |
        v
JpaGameRepository -> Spring Data JPA -> PostgreSQL
```

The REST API currently exposes:

- `GET /games/configuration/defaults` — read server-owned defaults for the create-game form.
- `POST /games` — create a waiting game with a validated configuration, empty player list and a 20 × 20 board.
- `POST /games/{gameId}/players` — add a named player to an existing game.
- `GET /games/{gameId}` — read the current game state.
- `GET /games/{gameId}/players/{playerId}` — rebuild an authenticated player's public and private game view.
- `PUT /games/{gameId}/rounds/current/program` — persist the authenticated player's private planning order without locking it.
- `GET /games/running` — list all running games, or an empty list when none exist.
- `GET /games/finished` — list all finished games, or an empty list when none exist.

Missing games produce HTTP 404. Invalid domain input, such as a blank player
name, produces HTTP 400.

## Current domain

`Game` is the aggregate root. Code outside the aggregate adds players through
`Game.addPlayer`; it does not modify the player collection directly.

A `Game` contains:

- a stable UUID used by the REST API and domain;
- zero or more `Player` objects, each with a UUID and non-blank name;
- a `Board` value with width and height.
- a persistent `GameConfiguration` containing player capacity, join timeout,
  cards per round, and planning timeout;
- a creation time and authoritative join deadline;
- a lifecycle status of `WAITING_FOR_PLAYERS`, `RUNNING`, or `FINISHED`;
  newly created games wait in the lobby.

The aggregate rejects blank or duplicate nicknames, joins after the deadline,
and joins beyond the configured capacity. The database additionally enforces
nickname uniqueness per game. A browser opens a lobby directly at
`/game/{gameId}`; the frontend does not embed configuration defaults.

The database also uses internal numeric primary keys. These are persistence
details and are not exposed through the domain or API. Domain UUIDs are stored
in unique `domain_id` columns.

`Vehicle` and `VehicleSegment` exist as placeholders in the domain model, and
corresponding tables exist in the initial schema. They are not connected to the
current aggregate behavior or returned by the API. Their eventual rules and
ownership should be decided when gameplay is introduced, rather than inferred
from the placeholder classes or schema alone.

## Frontend

The React application is deliberately thin:

- `api/` contains calls to the REST API;
- `types/` mirrors the JSON contract used by the UI;
- `pages/` coordinates the create-game and add-player flow;
- `components/` renders game state;
- `App.tsx` selects the current page.

After a mutation, the frontend fetches the game again with `GET /games/{id}`.
This makes the displayed state reflect what was persisted rather than relying
only on a locally predicted update. There is currently no router, global state
store, data-fetching framework, or board renderer.

## Database and migrations

PostgreSQL is the default database. For local development, the `in-memory`
Spring profile selects an H2 database in PostgreSQL compatibility mode; its
data is discarded when the backend process exits. Flyway owns schema creation
for both databases and runs before Hibernate validates the mappings. Hibernate
is configured with `ddl-auto=validate`; it must not silently create or alter
the schema. Schema changes therefore require a new Flyway migration that works
with both supported database modes.

## Testing boundaries

The project uses three complementary test levels:

- Domain/application unit tests exercise isolated rules quickly.
- Backend integration tests start Spring Boot on a random port and use a real
  PostgreSQL Testcontainer. They cover HTTP, Flyway, JPA, repository mappings,
  and responses together.
- Playwright tests in `acceptance-tests/` exercise user-visible behavior through
  Chromium. The first scenario creates a game through the GUI, adds Alice and
  Bob, and verifies that both players are rendered from the server-backed game
  state. Multiplayer scenarios give each player a separate browser context so
  cookies, local storage, and other client state are isolated.

This separation is intentional: unit tests explain individual rules,
integration tests protect technical wiring, and Playwright tests state what the
system does from a user's point of view. The Playwright configuration starts the
backend with the in-memory profile and the Vite frontend for repeatable local
end-to-end runs.

The separate `playwright.headless.config.mjs` configuration additionally
activates the `headless-players` profile and verifies the complete flow using
one browser.
