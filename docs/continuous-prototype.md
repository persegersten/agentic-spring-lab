# Continuous command prototype

This is the first playable slice of `continuous-gameplay-plan.txt`, not final migration cutover.

## Start locally

Prerequisites: Java 25, Node 22.12+ (or Node 24), npm. From the repository root:

```sh
cd backend
bash mvnw spring-boot:run -Dspring-boot.run.profiles=in-memory
```

In a second terminal, from the repository root:

```sh
cd frontend
npm ci
npm run dev -- --host 0.0.0.0
```

Open http://localhost:5173. Create a game, enter a name and join. Copy the lobby link into a separate browser profile or private window, enter a different name and join. Use separate storage contexts: two ordinary tabs share the same player identity. In the host window, click `Starta spelet`.

Both players can click any of the eight command buttons. The server appends to slot 4, then 5; accepted commands have no edit/remove action. Slots display 5, 4, 3, 2, 1, with 1–3 locked. Every server tick executes slot 1 (empty means NOP), applies board effects and shifts toward slot 1. A command accepted into slot 4 before tick one executes at tick four, approximately eight seconds after start. Both browsers poll authoritative snapshots every 500ms; closing them does not pause the match. Reopening the game link in the original browser restores credentials and queue. An unconfirmed request can be retried with its saved request ID.

The server defaults to 2000ms ticks and a 600000ms match duration, frozen at match start. Override with Spring properties `wreckage.tick-interval-ms` and `wreckage.match-duration-ms`. The scheduler checks deadlines every 500ms; timing can be late under load and never catches up with a burst.

## Verification

```sh
cd backend
bash mvnw -Dtest=ContinuousExecutionTest,ContinuousSliceIntegrationTest,ContinuousSchedulerIntegrationTest,MovementEngineTest,BoardEffectEngineTest test
```

```sh
cd frontend
npm run build
npm run lint
```

## Remaining work

- F1: full mocked interaction suite and gameplay review; shield UI.
- F2: tick animation, automatic uncertain-request recovery, comprehensive out-of-order/reconnect/browser tests. This slice directly renders authoritative positions and supports manual retry after uncertain responses.
- B1: shield activation and five-tick protection; broader crash, checkpoint and initiative tests. Existing movement/scoring resolver is reused; its existing checkpoint stopping behavior needs the planned full audit.
- B2: deterministic bot filling, stronger rollback/concurrency coverage and timing instrumentation. Use two human players for this slice; bot controls are hidden.
- B3: full original-acceptance receipt response and explicit HTTP conflict contracts. Receipts currently retain accepted command identity independently of consumed queue slots, and retries return the current snapshot.
- F3: replace the legacy round-centric suites and documentation, remove obsolete APIs and complete integrated playtests. Legacy round mutation APIs reject continuous matches.
- Control-point scoring remains deferred and is disabled for ticks. Timeout ranking and checkpoint/crash scoring reuse existing rules.

Queue/timing/revision/receipt data are stored atomically with the game in a new Flyway column. The supported in-memory database loses all matches on backend restart. The legacy round machinery is reused as the single-tick resolver; it does not drive continuous timing or round limits.

## Verification performed for this slice

- Full Maven test suite: 192 tests, zero failures/errors/skips.
- Frontend production build and lint: passed.
- `git diff --check`: passed.
- Standalone Java smoke checks: passed.
- Browser Playwright suite and a manual two-human playtest were not run. HTTP integration tests cover two authenticated players and private queues; the real scheduler test verifies execution without browser requests.
