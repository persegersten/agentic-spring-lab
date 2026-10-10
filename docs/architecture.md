# Wreckage v2 Architecture

Wreckage is a React client backed by a Spring Boot authoritative game server and PostgreSQL. The `in-memory` profile uses H2. Dependencies point from API and infrastructure toward the application and domain layers; `Game` is the aggregate root.

The host creates and starts a lobby. No loadout state or endpoint exists. At round start, `ProgrammingCardDealer` deals and persists eight private weighted cards per active player. Each `PlayerProgram` persists five selected cards, lock state, and a private shield-selection flag. `Player` persists whether its single match shield has been consumed. The application validates and consumes the shield during program locking and rejects reuse.

`Round` resolves five registers in rotating initiative order. `MovementEngine` owns one stepwise displacement algorithm shared by normal movement, ramming, programming-card Laser, and conveyors. A per-register resolution context is seeded with every round-shielded vehicle; the shared resolver treats those vehicles as immovable and atomically rejects any chain containing one. Voluntary movement uses the same engine but does not treat its initiating shielded robot as a forced target.

Checkpoint capture is detected from authoritative movement events, including pushes and board effects. CP4 truncates the remaining event sequence immediately. No score, damage, ammunition, loadout, or scheduled-combat model participates in resolution.

At the fixed round limit, `Game` orders placements by checkpoint progress and a deterministic breadth-first path to the next checkpoint over static traversable map cells. Equal progress and distance produce a draw.

Flyway owns schema evolution. V23 adds persistent shield consumption and removes the obsolete score, damage, ammunition, and loadout columns. `RoundEntity` stores hands, programs, shield selections, initial vehicle state, initiative, and ordered playback; its reader tolerates legacy payload layouts while discarding obsolete combat events.

The player-specific API exposes the caller's private hand, program, shield selection, and derived shield status. Public responses expose readiness and resolved playback but not private planning choices. React polls these responses, submits card order plus the shield boolean, and renders server events without predicting outcomes.

Domain and engine rules use unit tests, persistence and API behavior use Spring integration tests, and visible multiplayer behavior uses Playwright.
