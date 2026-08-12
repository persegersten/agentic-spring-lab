# Wreckage Architecture

Wreckage is a small web application for a turn-based multiplayer vehicle combat
game. The current system only creates games, adds players, and reads game state.
Gameplay, movement, and combat are intentionally outside the present scope.

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

- `POST /games` — create a game with an empty player list and a 20 × 20 board.
- `POST /games/{gameId}/players` — add a named player to an existing game.
- `GET /games/{gameId}` — read the current game state.

Missing games produce HTTP 404. Invalid domain input, such as a blank player
name, produces HTTP 400.

## Current domain

`Game` is the aggregate root. Code outside the aggregate adds players through
`Game.addPlayer`; it does not modify the player collection directly.

A `Game` contains:

- a stable UUID used by the REST API and domain;
- zero or more `Player` objects, each with a UUID and non-blank name;
- a `Board` value with width and height.

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

PostgreSQL is the only supported database. Flyway owns schema creation and runs
before Hibernate validates the mappings. Hibernate is configured with
`ddl-auto=validate`; it must not silently create or alter the production
schema. Schema changes therefore require a new Flyway migration.

## Testing boundaries

The project uses three complementary test levels:

- Domain/application unit tests exercise isolated rules quickly.
- Backend integration tests start Spring Boot on a random port and use a real
  PostgreSQL Testcontainer. They cover HTTP, Flyway, JPA, repository mappings,
  and responses together.
- Tests in `acceptance-tests/` treat the running application as a black box and
  describe behavior in game language. The first scenario creates a game, adds
  Alice and Bob, then reads the game and verifies both players are present.

This separation is intentional: unit tests explain individual rules,
integration tests protect technical wiring, and acceptance tests state what the
system does from a user's point of view.
