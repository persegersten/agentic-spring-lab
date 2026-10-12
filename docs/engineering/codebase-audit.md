# Wreckage Codebase Audit — Engineering Cleanup Stage 1

Audit date: 2026-10-12  
Repository revision inspected: `101ea74` (before the task branch report change)

## A. Executive Summary

Wreckage has a coherent small-application architecture: authoritative game resolution lives in the backend domain/engine, JPA persists complete game aggregates, REST polling drives multiplayer synchronization, and maps are validated when loaded. Movement, ramming, chain pushing, conveyors, card-based laser displacement, ordered checkpoints, bots, and completion rules all have meaningful automated coverage. Same-game writes are serialized with pessimistic database locks.

The main health problem is overlapping generations of rules. The current programming-card LASER, which displaces robots without damage, coexists with the earlier selectable-loadout and scheduled-combat system (damage, rockets, shields, combat scoring, and special abilities). This obsolete vertical slice crosses the domain, service, REST contract, frontend, persistence payloads, migrations, and tests. It materially enlarges `Round` and the event model and makes it difficult to tell which rules are authoritative. Repository history explicitly describes the loadout/scheduled-combat path as legacy when programming-card LASER was introduced.

The audit also found a confirmed five-register validation inconsistency and a timing-dependent integration-test failure caused by production scheduling being active during tests. No Critical production defect was demonstrated. The most valuable immediate work is therefore a narrow deletion and validation cleanup, followed by test isolation—not a broad redesign.

The five most important findings are:

1. The obsolete scheduled-combat/loadout vertical slice remains active beside card LASER.
2. The server still permits one-to-five-register games even though the current card rules and migration establish five registers.
3. The backend suite is not green because an enabled scheduler races an API integration test.
4. `Round` combines too many resolution and scoring responsibilities, largely because both rule generations coexist.
5. The 22-version Flyway history contains enough transitional churn to justify a `B22` baseline migration for new databases, after exact schema-equivalence verification.

## B. Findings

Risk in the table means implementation risk. Expected benefit and finding type are stated in the evidence sections below.

| ID | Finding | Location | Severity | Risk | Effort | Recommendation |
|---|---|---|---|---|---|---|
| F-01 | Obsolete scheduled-combat and selectable-loadout rules coexist with programming-card LASER | `ActionEngine`, `ActionType`, `PrimaryWeapon`, `SpecialAbility`, `ScheduledAction`, `GameService`, `GameController`, `VehicleEntity`, `RoundEntity`, `LoadoutSelector.tsx`, `CommandHand.tsx` | High | Medium | Large | Remove this vertical slice in Stage 2 while retaining card LASER displacement and movement rules. |
| F-02 | Non-five-register games bypass dealt-hand enforcement | `GameConfiguration.java:53-58`, `Game.java:160-163`, `PlayerProgram.java`, `V22__programming_cards.sql` | High | Low | Small | Enforce exactly five registers at the public/domain boundary and update tests that preserve earlier variable-size rules. |
| F-03 | Production scheduling makes an integration test timing-dependent and currently failing | `WreckageApplication.java:5-8`, `LobbyScheduler.java:15-18`, `GameApiIntegrationTest.java:343-378` | High | Low | Small | Disable/control scheduling in API integration tests and test scheduler behavior through a deterministic service boundary. |
| F-04 | Round resolution has excessive, mixed responsibilities | `Round.java:88-229` | Medium | Medium | Medium | First delete obsolete combat branches; then extract only cohesive helpers whose benefit is demonstrated by the reduced code. |
| F-05 | Multiplayer locking is sound in implementation but important races are not explicitly tested | `GameService.java:106-218,261-275`, `SpringDataGameRepository.java:18-24`, `LobbyScheduler.java` | Medium | Medium | Medium | Add concurrent last-submit, scheduler-versus-submit, and duplicate-round-start tests without changing the locking design first. |
| F-06 | Delimiter-encoded aggregate payloads are brittle and bounded by fixed `VARCHAR` lengths | `RoundEntity.java:13-19,33-75`, migrations `V3`, `V9`, `V12`, `V13` | Medium | High | Large | Defer storage redesign; in Stage 2 remove only fields made obsolete by combat deletion and preserve compatibility. |
| F-07 | Legacy combat and scoring tests consume substantial suite and acceptance-test scope | `ActionEngineTest`, combat sections of `GameRoundTest`/`GameApiIntegrationTest`, `combat-loadout-flow.spec.mjs`, `laser-combat.spec.mjs`, `repulsor-combat.spec.mjs`, `special-abilities.spec.mjs` | Medium | Low | Medium | Delete tests only with the obsolete feature; retain tests for card LASER, ramming, pushing, checkpoints, bots, synchronization, and completion. |
| F-08 | Frontend game orchestration is concentrated in one polling/state component | `GamePage.tsx:55-135,202-225` | Medium | Medium | Medium | In Stage 2 remove obsolete loadout/combat state only. Defer broader state-management extraction until current semantics have concurrency tests. |
| F-09 | Open Session in View and public API documentation are enabled implicitly | `application.properties`, backend test startup warnings | Low | Low | Small | Decide and set explicit production policy in a later configuration task; do not mix it into rule cleanup. |
| F-10 | Card weight configuration omits an explicit LASER property even though code supports it | `application.properties`, `ProgrammingCardConfiguration.java:23-41` | Low | Low | Small | Add an explicit LASER weight only in a later configuration cleanup if operators are expected to tune weights. Current default behavior is correct. |
| F-11 | No CI workflow is present to enforce backend tests, lint, build, or migrations | repository root (no `.github/workflows`) | Medium | Low | Medium | Add CI after stabilizing the scheduler test so the known race is not institutionalized as noise. |
| F-12 | Runtime/dependency inputs are only partly reproducible | `pom.xml`, `Dockerfile`, `compose.yml` | Low | Low | Small | Keep Maven/npm lock mechanisms; consider pinning Docker images by digest in a separate deployment-hardening task. |

### F-01 — Duplicate rule generations (technical debt)

Expected benefit: high. Removing the obsolete path reduces the public API and frontend controls, deletes a second weapon interpretation, simplifies persistence and playback events, and makes the current rules unambiguous.

Evidence:

- `MovementOrder.LASER` is a programming card. `MovementEngine.applyLaser` traces in the current direction and delegates displacement to the shared push resolver. It does not apply damage, ammunition, or combat score.
- In parallel, `ScheduledAction` and `ActionEngine` resolve a loadout-selected LASER, REPULSOR, ROCKET, TURBO, SHIELD, ANCHOR, or SIDE_STEP in a chosen register. That path applies damage, consumes rocket ammunition, and can award weapon-crash score.
- `GameService.validateAction`, the `/players/{playerId}/loadout` endpoint, `LoadoutSelector`, scheduled-action controls, `VehicleEntity` loadout/damage/ammunition fields, and `RoundEntity` action/damage/ammunition event fields expose and persist the older rules end-to-end.
- Commit `0db66f2` states that programming-card LASER is separate from the “legacy lobby loadout and scheduled-combat system” and must not trigger legacy damage, ammunition, or combat scoring. This is direct repository evidence of obsolescence rather than an inference from naming.
- The old headless-player profile itself is no longer present. Bots are persisted lobby players created through `/games/{gameId}/bots`; no cleanup of a headless profile is needed.

The cleanup must trace references before deletion. `RegisterEffects` is currently shared by legacy Shield/Anchor and movement, so it should be removed or narrowed only after obsolete action references disappear. Historical Flyway migrations must not be edited or deleted.

### F-02 — Five-register rule is not enforced consistently (confirmed defect)

Expected benefit: high. A small validation change eliminates two server behaviors for what should be one rule and ensures every current game uses authoritative dealt cards.

`GameConfiguration` accepts `programSize` from 1 through 5, and the create-game request passes that value directly into the domain. In `Game.startRound`, a dealt hand is used only when `programSize == 5`; otherwise `PlayerProgram.empty` is created. Empty-hand programs are the compatibility path that allows commands—including `WAIT`—without checking them against a dealt hand. Meanwhile `V22__programming_cards.sql` changes all existing rows to five. The API can therefore create new games that do not follow the migrated/current five-register card rules.

Tests using one- or three-register configurations represent earlier rules or compact fixtures. Stage 2 should distinguish fixture convenience from supported behavior and make production construction unambiguously five-register.

### F-03 — Scheduler test race (confirmed defect in test isolation)

Expected benefit: high. The suite becomes deterministic and suitable for CI without changing production scheduling.

The full backend run failed `GameApiIntegrationTest.persistsAndReturnsLaserProgrammingCardsToTheirOwnerOnly`. The test constructs a `Round` with a constructor whose planning deadline is `Instant.EPOCH`. Because `@EnableScheduling` is active in the full Spring context and `LobbyScheduler` runs every 500 ms, the scheduler can complete and lock that round before the test submits the program. The endpoint then returns `409 Conflict` instead of the expected `200 OK`. The failure is consistent with the code path and was observed during this audit; it is not evidence that the program-submission transaction itself lacks locking.

### F-04 — Round orchestration complexity (technical debt)

Expected benefit: medium after F-01; implementation risk is higher before deletion.

`Round.resolve` owns register iteration, pre/post actions, initiative, movement, board effects, checkpoint victory, event sequencing, score mutation, and early termination. `addScoredEvents` classifies movement and crash event types and applies checkpoint, crash, push-crash, and weapon-crash scoring. `finalVehicleStates` then reconstructs current state by interpreting the playback event stream. Any new event type can therefore require coordinated changes in multiple condition chains.

This is concrete coupling, but a broad state-machine rewrite is not justified in Stage 2. Deleting legacy damage/actions/scoring removes many branches with lower behavioral risk. Reassess the remaining method afterward.

### F-05 — Multiplayer concurrency coverage gap (potential risk)

Expected benefit: medium. Tests would validate the existing ownership model and make later changes safer.

Mutable domain objects are transaction-local reconstructions from JPA rather than global in-memory singletons. Mutating service methods load games through `findByIdForUpdate`, and scheduled timeout processing uses `findAllByStatusForUpdate`; Spring Data marks both queries with `PESSIMISTIC_WRITE`. This is a clear ownership boundary and no unlocked production mutation was demonstrated.

However, tests do not explicitly run competing requests for the last two submissions, a scheduler timeout against a submission, or two clients starting the next round. The duplicate next-round behavior is tested sequentially, not concurrently. These are investigation risks, not confirmed production races.

### F-06 — Persistence payload format (technical debt)

Expected benefit of eventual replacement: medium; migration risk: high.

`RoundEntity` stores programs, initiative, initial vehicle state, playback, initial scores, and start events as hand-built delimited strings. Decoding contains length- and offset-based compatibility paths for prior event formats. This avoids a larger relational model but has no escaping/version field and is constrained by `VARCHAR(20000)`/`VARCHAR(30000)` columns. A sufficiently long playback can fail at persistence time, and changing fields requires positional compatibility logic.

No observed test exceeded these limits, so this is not classified as a confirmed failure. Replacing the format would require careful live-data migration and is not appropriate for Stage 2.

### Build and configuration observations

- Maven uses the wrapper and Spring Boot parent dependency management. Frontend dependencies have an npm lock file and the Docker build uses `npm ci`.
- The backend targets Java 25; the Docker build/runtime also use Temurin 25. Node 24 is consistent between the Dockerfile and the frontend build.
- `in-memory`, `postgres`, and test-only `deterministic-e2e` profiles have clear purposes. No legacy headless-player profile or code remains.
- Compose's default backend uses H2 in memory; the PostgreSQL service is opt-in under the `database` profile. This is documented and intentional for the alpha deployment, though data loss on restart makes it unsuitable for durable hosting.
- The Docker base images use mutable tags rather than digests. Builds are repeatable at the package-lock/Maven level but not byte-for-byte pinned at the OS image level.
- No WebSocket implementation or dependency exists. The frontend intentionally polls REST every 1.5 seconds; nginx therefore needs no upgrade/proxy WebSocket configuration.
- No CI workflow was found. This leaves tests, frontend lint, and clean migration validation dependent on local execution.
- Startup logs show implicit Open Session in View and enabled Springdoc endpoints. These are explicit-policy opportunities, not demonstrated correctness defects.
- `application.properties` explicitly configures weights for movement/turn cards but relies on the code default for LASER. The effective dealer still includes LASER with weight 10.

## C. Test Assessment

### Executed results

| Command | Result | Notes |
|---|---|---|
| `cd backend && ./mvnw test` | Failed: 183 run, 1 failure, 0 errors, 0 skipped | 182 tests passed. The single failure was the scheduler race described in F-03. |
| `cd frontend && npm run lint` | Passed | `oxlint` exited successfully with no reported findings. |
| Playwright acceptance suite | Not executed | The suite starts application services and includes several scenarios for the obsolete combat/loadout rules. It was inspected, but executing it was not necessary to establish the Stage 1 findings. |

The backend run safely used ephemeral stores only. Flyway applied all 22 migrations to H2 in memory and to a PostgreSQL 16.14 Testcontainers database. Hibernate schema validation completed on both. It did not connect to or alter a persistent application database.

Warnings observed during the run:

- Flyway reported that H2 2.4.240 is newer than its latest verified H2 version, 2.3.232.
- Spring reported that Open Session in View is enabled by default.
- Springdoc reported that API docs and Swagger UI are enabled by default.
- Mockito self-attached its inline mock-maker agent; the JVM warned that future releases will disallow dynamic agent loading by default.

### Coverage strengths

- `MovementEngineTest` covers turns, multi-step movement, walls/edges, obstacles, ramming, chain pushes, pits, conveyor pushes, anchored/blocked movement, card LASER targeting/displacement, and uniqueness of active positions.
- `GameRoundTest` covers register-major execution, checkpoint ordering, capture on intermediate and forced movement, immediate CP4 victory, round persistence of checkpoint progress, scoring order, respawn, board effects, and completion/tie behavior.
- `ProgramPlanningTest` and `ProgrammingCardDealerTest` cover hand size, multiplicity validation, locking, timeout completion, and weighted dealing.
- `GameServiceTest` covers authenticated lobby operations, bot creation/program locking, program submission, timeout completion, and round progression.
- API/in-memory integration tests exercise persistence, private-hand visibility, reconnect behavior, migrations, and DTO mapping.

### Obsolete or misleading coverage

Tests centered on loadout selection, scheduled weapons/abilities, damage thresholds, rocket ammunition, combat crash scoring, repulsor, and Shield/Anchor/Turbo/Side Step protect the obsolete F-01 behavior. This includes all of `ActionEngineTest`, relevant portions of `GameRoundTest`, `GameServiceTest`, and `GameApiIntegrationTest`, and the acceptance specifications `combat-loadout-flow`, `laser-combat`, `repulsor-combat`, and `special-abilities`. `scoring.spec.mjs` and `results-and-respawn.spec.mjs` mix current checkpoint/completion behavior with older score rules and must be split or narrowed rather than deleted wholesale.

### Missing or weak coverage

- There is no true concurrent-request coverage for the last program submissions, timeout processing versus submission, or next-round creation.
- Bot tests confirm that bots lock programs but do not provide strong strategy assertions; that is acceptable while bots intentionally choose the first five dealt cards.
- The five-register rule is tested as one allowed value, but earlier variable-size tests also legitimize one- and three-register games. There is no API assertion that a non-five size is rejected.
- The scheduler is indirectly active inside broad `@SpringBootTest` contexts, which created the observed nondeterminism. Scheduler orchestration should have focused deterministic tests.
- Frontend behavior is covered mostly through Playwright; there is no component/unit-test runner. Introducing one solely for Stage 2 is not justified unless removed-combat state proves difficult to verify through existing acceptance tests.
- `GameApiIntegrationTest` starts a full web server and PostgreSQL container for 26 tests. This provides valuable end-to-end persistence coverage, but pure DTO/error-mapping cases could later use narrower tests. Splitting it is optional and should not delay F-03.

## D. Flyway Assessment

### Current migration structure

Flyway is supplied through `spring-boot-starter-flyway` with the PostgreSQL database module; its exact version is managed by Spring Boot 4.1.0 rather than declared directly. Migrations are standard versioned scripts `V1` through `V22` under `backend/src/main/resources/db/migration`.

The history contains several legitimate intermediate states:

- V3 introduces rounds, vehicles, cards/playback, and token hashes.
- V5 renames the programming phase to planning.
- V6 adds walls and vehicle damage; V10 replaces cell walls with edge walls.
- V7–V9 introduce and then collapse intermediate resolution phases and replace cards with programs.
- V12–V15 add scoring, checkpoints, respawn/results, conveyors/rotators, and control points.
- V16 removes damage; V18 restores damage for the later combat system.
- V17 adds host and automated-player metadata.
- V19–V20 add rocket ammunition and loadouts.
- V21 adds map metadata and obstacles.
- V22 normalizes stored program size to five.

Entity/schema compatibility was demonstrated on clean H2 and PostgreSQL databases by successful migration followed by Hibernate validation. This does not prove compatibility with every historical data value, but no schema mismatch was observed.

### Baseline recommendation

A baseline migration is justified because 22 migrations now recreate several discarded intermediate representations and rule generations. The proposed artifact is `B22__baseline.sql`, representing the schema after V22. This is a Flyway baseline migration for new environments; it is not the `flyway baseline` command. Stage 3 must not enable `baselineOnMigrate`.

Existing databases with V1–V22 recorded in `flyway_schema_history` continue using their history and must not be repaired, re-baselined, or altered. Historical V files remain immutable because changing or deleting them breaks checksum validation and the upgrade path.

### Risks and prerequisites

- Baseline migrations depend on the Flyway edition/version behavior provided by the resolved Spring Boot dependency set; Stage 3 must first confirm `B` migration support in that exact version.
- PostgreSQL is authoritative for equivalence. H2 PostgreSQL mode is useful application coverage but not a schema-equivalence oracle.
- Identity/sequence details, defaults, check constraints, foreign keys, unique constraints, indexes, nullability, timestamp types, and column lengths must match—not merely table and column names.
- If Stage 2 removes legacy fields, the baseline version must move to the then-current migration version and include the later forward migration results. Do not publish `B22` concurrently with unversioned schema changes.
- A baseline cannot safely encode assumptions about legacy data conversion; it is for empty schemas only.

### Schema-equivalence verification

1. Start two isolated, disposable PostgreSQL containers of the same supported version.
2. Apply V1–V22 to database A.
3. Configure database B so Flyway selects `B22__baseline.sql` for an empty schema, then apply all later V migrations, if any.
4. Export schema-only definitions from both databases with stable options.
5. Normalize non-semantic ownership, generated names, ordering, and environment-specific sequence values.
6. Compare tables, columns, SQL types, sizes, nullability, defaults, identity/sequences, primary/unique/check constraints, foreign keys, indexes, and Flyway-visible version state.
7. Run Hibernate `ddl-auto=validate` and the repository/API integration suite against both databases.
8. Separately test an existing V22 database upgrading through every post-baseline V migration; the baseline script must not execute there.

No migration, baseline, repair, destructive database command, or persistent database change was performed in Stage 1.

## E. Recommended Cleanup — Stage 2

Stage 2 should be limited to the highest-value, lowest-risk cleanup and fit in three reviewable commits:

1. **Remove the obsolete combat/loadout vertical slice.** Delete scheduled actions, selectable weapons/abilities, damage/ammunition/combat scoring behavior, related REST fields/endpoints, frontend loadout/action controls and combat-only playback UI, and tests exclusively protecting those rules. Preserve `MovementOrder.LASER`, its shared displacement path, ramming, chain pushing, checkpoints, bots, polling, and completion.
2. **Normalize the current five-register contract and simplify residue.** Require program size five in API/domain construction, remove variable-size compatibility behavior that is no longer reachable, and simplify DTO/event/persistence mapping fields made obsolete by commit 1. Add only forward migrations for physical column removal; never edit V1–V22. If migration work cannot be cleanly separated, retain unused columns until Stage 3 rather than expanding this cleanup.
3. **Make synchronization tests deterministic.** Disable or control scheduling in broad integration tests, add focused scheduler-versus-submit and concurrent duplicate-round tests, and run backend, lint, build, and the retained acceptance suite.

This scope deliberately avoids rewriting `Round`, changing transport, replacing persistence encoding, changing bot strategy, or altering map/gameplay rules.

## F. Recommended Flyway Work — Stage 3

Stage 3 should be a separate database-only effort:

1. Confirm the resolved Flyway version and baseline-migration support; choose a baseline version matching the latest released schema after Stage 2 (currently `B22`, but advance it if Stage 2 adds migrations).
2. Author one baseline script from the verified PostgreSQL end schema. Preserve every historical V migration unchanged.
3. Add an automated disposable-PostgreSQL equivalence check covering historical construction, baseline construction, Hibernate validation, and schema metadata comparison.
4. Test three paths: empty schema through the baseline, existing V22 schema through later migrations, and the complete historical path from V1.
5. Document release prerequisites and recovery. Do not use `flyway baseline`, `repair`, `clean`, or `baselineOnMigrate` against application databases.

## G. Deferred Improvements

- Replace delimiter-encoded `RoundEntity` payloads with a versioned structured format or relational model. This requires a dedicated data-compatibility design and migration plan.
- Further decompose `Round` and `Game` after obsolete branches are gone and concurrency tests fix current semantics. Extract only responsibilities that remain demonstrably coupled.
- Consider push transport (SSE/WebSocket) if polling produces measured latency/load problems. Current REST polling is simple and functional.
- Introduce a dedicated frontend state layer or split `GamePage` only after the combat-state deletion shows what complexity remains.
- Review Open Session in View, Springdoc exposure, logging policy, and production persistence in a deployment-hardening task.
- Add CI and container-image digest pinning as build/release work, not gameplay cleanup.
- Revisit bot strategy only as a product feature; the current first-five-card behavior is simple, deterministic, and covered.
