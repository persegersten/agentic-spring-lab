# Sequential Flag Capture — Slice 2 Verification

These scenarios verify global unlocking, exclusive ownership, and atomic movement
entries. For slice 3 recovery, use [the recovery scenarios](flag-recovery-verification.md).
Completion/ranking rules remain deferred. All boards keep four flags. The fixture controller and scenario definitions
live under `backend/src/test/`, so they are absent from the production application.

## Automated verification

Use Java 25, the installed frontend and acceptance-test dependencies, and the
Chromium version required by the acceptance-test package. Install the browser once
if necessary:

```bash
cd acceptance-tests
npm exec -- playwright install chromium
cd ..
```

From the project root, run the backend rules tests:

```bash
cd backend
bash mvnw -Dtest=SequentialFlagCaptureTest,ExclusiveFirstFlagTest,GameRoundTest,MovementEngineTest,BoardEffectEngineTest test
cd ..
```

Run the real-client scenarios using their dedicated configuration:

```bash
npm --prefix acceptance-tests test -- --config=sequential-flags.config.mjs --workers=1 --reporter=line
```

This configuration starts a disposable H2 backend on port 18080 with the
`sequential-flags-e2e` test profile and a frontend on port 5174. It can reuse
compatible servers started with the manual commands below. It exercises two
independent browser contexts, actual command buttons, authoritative tick
resolution, ownership strips, board markers, and API capture counts. The default
Playwright configuration excludes this profile-specific file.

The backend tests also cover cases that must not be merged into one batch:
separate commands, historical multi-cell movement steps, and separate conveyors.
They assert that an earlier locked entry never receives a retroactive capture.
Existing immediate-victory regression tests stay in place until slice 4 changes
completion timing.

## Open a manual scenario

From `backend/`, start the test application in a terminal:

```bash
bash mvnw spring-boot:test-run -Dspring-boot.run.profiles=in-memory,sequential-flags-e2e '-Dspring-boot.run.arguments=--server.port=18080 --wreckage.games.max-concurrent=50'
```

From `frontend/`, start Vite in another terminal:

```bash
WRECKAGE_BACKEND_URL=http://127.0.0.1:18080 npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Create a scenario with this command, replacing `STANDING` with another name from
the table below when needed:

```bash
curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags?scenario=STANDING'
```

The response contains `gameId` and two `sessions`: Alice first, Bob second. Open
<http://127.0.0.1:5174> in two separate browser profiles. In each window's developer
console, paste its session object in place of `SESSION_OBJECT` below and run:

```javascript
const session = SESSION_OBJECT;
localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session));
location.assign(`/game/${session.gameId}`);
```

Fixture matches stay paused between explicit tick requests, so normal scheduling
cannot race the demonstration. Their next-tick timestamp is deliberately in the
future. After queuing one command with a button, advance **three ticks** to move
it through queue slots 3, 2, and 1. Replace `GAME_ID` with the response's ID:

```bash
curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags/GAME_ID/ticks?count=3'
```

Wait for both clients to display the result before queuing the next command.
The tick response also exposes the ordered `round.playback` capture events.
These fixture endpoints exist only with the explicit test profile and test
classpath; ordinary `spring-boot:run` cannot enable them.

## Cases to judge

| Scenario | Human action | Expected result in both clients |
| --- | --- | --- |
| `STANDING` | Alice clicks **Framåt**, then advance 3 ticks. | Alice claims Flag 1; Bob, already standing on Flag 2, still owns nothing. Flag 2 is highlighted. |
| Same match | Bob clicks **Backa**, advance 3 ticks; Bob clicks **Framåt**, advance 3 ticks. | Bob leaves and re-enters Flag 2 to claim it. Each player owns one flag; Flag 3 becomes available. |
| Same match | Alice clicks **Framåt**, advance 3 ticks. | Alice enters Flag 1's former cell. Ownership and counts stay unchanged. |
| `PUSH` | Alice clicks **Framåt**, advance 3 ticks. | One atomic push awards Flag 1 to Alice and Flag 2 to Bob. Both markers disappear; capture events list CP1 before CP2. |
| `REVERSED_PUSH` | Repeat the same action. | Robot storage order is reversed; positions, owners, and capture-event order match `PUSH`. |
| `BLOCKED_PUSH` | Alice clicks **Framåt**, advance 3 ticks. | A wall blocks the complete chain. Neither robot moves or captures a flag. |
| `LASER` | Alice clicks **Laser**, advance 3 ticks; repeat once. | Each hit pushes Bob onto the next flag. Bob owns Flags 1 and 2, with a displayed count of 2; Alice owns zero. |
| `CONVEYOR` | Make a tick request with `count=1`; no command is needed. | Flag 1 starts owned by Alice. The conveyor moves Bob onto Flag 2 and awards it to Bob. Flag 3 becomes available. |

For every case, captured flags retain their owner and disappear from the board;
the persistent strip retains every flag number. A player who owns Flag 2 alone
has a displayed capture count of **1**, not zero. All player snapshots name the
same globally available next flag.
