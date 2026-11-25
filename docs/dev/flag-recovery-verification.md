# Flag Ownership Recovery — Slice 3 Verification

Ownership already uses persisted player records, independently of robot state.
This slice verifies the contract through crashes, respawns, authenticated reads,
reloads, network reconnection, and a real backend process restart. It adds a
reproducible recovery scenario and a test-only retained development database.
For final-capture completion and ranking, see [slice 4 verification](final-flag-verification.md). Match-limit triggers are unchanged.

The normal `in-memory` development profile loses matches when the backend stops.
For a restart with the **same database**, use the file-backed H2 profile below.
It uses the existing schema and Flyway migrations, is available only on the test
classpath, and does not change production configuration. No migration of legacy
matches is included.

## Automated verification

Use Java 25 and installed frontend/acceptance-test dependencies. If the matching
Chromium browser is not installed, run this once from the project root:

```bash
cd acceptance-tests
npm exec -- playwright install chromium
cd ..
```

Run the backend recovery integration and flag/round regression tests:

```bash
cd backend
bash mvnw -Dtest=FlagOwnershipRecoveryIntegrationTest,SequentialFlagCaptureTest,GameRoundTest test
cd ..
```

Run the complete two-client lifecycle, including process restart:

```bash
npm --prefix acceptance-tests test -- --config=flag-recovery.config.mjs --reporter=line
```

The browser test owns a backend on port 18082 and a frontend on port 5176. Stop
manual recovery servers on those ports before running it. It creates a fresh H2
database under a unique `/tmp/wreckage-flag-recovery-*` directory, shuts down only
its own backend process group, then starts a new process against that exact same
database. It keeps both browser sessions open through the outage, verifies the
restored authenticated snapshots, reloads both pages, and attempts recapture.
The backend log is attached to the Playwright result. The fixture is paused
between explicit tick requests, so scheduling cannot race the checks.

Brief connection failures from the Vite proxy during the intentional outage are
expected; both clients recover when the backend becomes ready. The test requires
successful authenticated responses after restart, so an unchanged stale screen
alone cannot make it pass.

## Manual setup

From `backend/`, create a dedicated development database directory and start the
test application:

```bash
mkdir -p target/flag-recovery
WRECKAGE_TEST_RECOVERY_DB="$PWD/target/flag-recovery/manual-match" bash mvnw spring-boot:test-run -Dspring-boot.run.profiles=sequential-flags-e2e,flag-recovery-e2e '-Dspring-boot.run.arguments=--server.port=18082 --wreckage.games.max-concurrent=50'
```

From `frontend/`, start Vite in a second terminal:

```bash
WRECKAGE_BACKEND_URL=http://127.0.0.1:18082 npm run dev -- --host 127.0.0.1 --port 5176 --strictPort
```

Create the recovery scenario:

```bash
curl -X POST 'http://127.0.0.1:18082/games/test-fixtures/flags?scenario=RECOVERY'
```

Keep its `gameId` and two `sessions` objects: Alice first, Bob second. Open
<http://127.0.0.1:5176> in two independent browser profiles. In each developer
console, replace `SESSION_OBJECT` with that player's response object and run:

```javascript
const session = SESSION_OBJECT;
localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session));
location.assign(`/game/${session.gameId}`);
```

The board has Flag 1 at `(1,1)`, Flag 2 at `(2,1)`, a pit at `(3,1)`, and Flags
3/4 at `(6,5)`/`(7,5)`. Alice starts at `(0,1)` and Bob at `(1,1)`, facing east.

## Manual lifecycle

After each command button click, advance three ticks to pass it through queue
slots 3, 2, and 1. Replace `GAME_ID` below with the original fixture ID:

```bash
curl -X POST 'http://127.0.0.1:18082/games/test-fixtures/flags/GAME_ID/ticks?count=3'
```

1. Alice clicks **Framåt**; advance three ticks. The push awards Flag 1 to Alice
   and Flag 2 to Bob. Both clients must show one flag each, both captured markers
   absent, Flag 3 available, and Flag 4 locked.
2. Bob clicks **Framåt**; advance three ticks. Bob enters the pit and disappears
   from the board. Both strips must still name Bob as Flag 2's owner, with a
   displayed flag count of 1. Alice's ownership and global availability stay put.
3. Advance **one** tick by changing the request to `count=1`. Bob respawns at
   `(0,1)` using the existing fallback: Alice occupies Bob's assigned spawn at
   `(1,1)`. Ownership and available/locked flags must be identical before and
   after respawn.
4. Reload both browser pages. Both must reconstruct the same owners, counts,
   missing captured markers, and highlighted Flag 3.
5. Put Bob's browser offline using the developer tools Network panel. Advance
   one server tick, then return Bob online. Wait for both clients' tick numbers
   to agree; ownership and board markers must remain identical. Reload both pages
   again to check restoration from a fresh client state.
6. Stop **only the recovery backend** with Ctrl+C. Keep the frontend running.
   Restart the backend using the exact same command, working directory, and
   `WRECKAGE_TEST_RECOVERY_DB` path from setup. Reopen/reload the **original match
   URL** in both sessions. Do not create another fixture. Both original tokens
   must still work, and all ownership, counts, vehicle positions, and availability
   must match the pre-restart state.
7. Bob clicks **Framåt**; advance three ticks. Bob enters Alice's former Flag 1
   cell and pushes Alice onto Bob's former Flag 2 cell. Neither flag can be
   recaptured or transferred. Both players still own one flag, Flag 3 remains
   available, and this step emits no checkpoint capture score event.

Restarting with a different database path or the in-memory profile does not test
saved-state recovery. The retained H2 file for these manual commands is
`backend/target/flag-recovery/manual-match.mv.db`.
