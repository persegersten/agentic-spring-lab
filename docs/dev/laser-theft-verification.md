# Laser Flag Theft — Slice 1 Verification

This slice demonstrates one eligible flag transferring before normal Laser
displacement and turning. Shield protection, blocked displacement, and a target
crashing into a pit have reproducible two-client scenarios. Mixed-age transfers,
repeated theft chains, and restart recovery remain later slices.

## Automated checks

From `backend/`:

```bash
bash mvnw -Dtest=LaserFlagTheftTest,LaserRulesTest,SequentialFlagCaptureTest,FinalFlagCompletionTest,HardMatchLimitTest test
```

From the project root:

```bash
npm --prefix frontend run build
npm --prefix frontend run lint
npm --prefix acceptance-tests test -- --config=sequential-flags.config.mjs laser-theft --workers=1 --reporter=line
```

Omit the `laser-theft` selector to include the existing capture, final-flag, and
hard-limit browser regressions. The configuration starts disposable local servers
on ports 18080 and 5174. Use Java 25 and installed frontend/acceptance-test
dependencies. If Chromium is missing, run
`npm --prefix acceptance-tests exec -- playwright install chromium`.

The four browser cases use two independent contexts, actual database-backed API
responses, and paused presentation clocks. They check that the old owner remains
visible before the transfer event, the highlighted new owner appears before
displacement, and final counts and markers agree on both clients. Backend tests
verify event order, shield prevention, existing turns, crash scoring, and that a
real cell-entry capture records its tick.

## Start local scenario servers

In `backend/`:

```bash
bash mvnw spring-boot:test-run -Dspring-boot.run.profiles=in-memory,sequential-flags-e2e '-Dspring-boot.run.arguments=--server.port=18080 --wreckage.games.max-concurrent=50'
```

In `frontend/`, in a second terminal:

```bash
WRECKAGE_BACKEND_URL=http://127.0.0.1:18080 npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

These fixtures exist only on the test classpath under the explicit fixture
profile. Normal scheduling cannot advance their clock; use the tick endpoint.
The in-memory database is disposable.

## Open Alice and Bob

Create one scenario, using a name from the table below:

```bash
curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags?scenario=THEFT'
```

The response contains `gameId` and two `sessions`, ordered Alice then Bob. Open
`http://127.0.0.1:5174` in two independent browser profiles. In each profile's
developer console, assign the corresponding session object from the response:

```js
const session = /* paste Alice's or Bob's complete session object here */;
localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session));
location.assign(`/game/${session.gameId}`);
```

Both clients start at tick 10. Alice at `(2,1)` owns Flag 1 acquired on tick 10;
Bob at `(0,1)` faces east with Laser already queued in locked slot 1. Alice
waits. Flag 2 is available and Flag 1's board marker is hidden. No command input
is needed.

Advance the actual next tick, substituting the returned game ID:

```bash
curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags/GAME_ID/ticks?count=1'
```

| Scenario | Expected tick 11 result |
| --- | --- |
| `THEFT` | Both strips highlight Flag 1 changing Alice → Bob before Alice moves to `(3,1)` and receives the existing random turn. |
| `THEFT_SHIELD` | Alice's shield starts before the shot. She keeps Flag 1, stays at `(2,1)` facing east, and no transfer highlight appears. |
| `THEFT_BLOCKED` | A wall blocks Alice's displacement from `(2,1)`. Bob still receives Flag 1 and Alice still receives the existing random turn. |
| `THEFT_PIT` | Bob receives Flag 1 before Alice is displaced into the pit at `(3,1)`. Alice crashes and does not turn; the crash cannot undo the transfer. |

For every successful theft, check both clients show owned counts Alice 0 / Bob 1;
the shield case remains Alice 1 / Bob 0. Flag 1's marker must stay hidden and
Flag 2 must remain available. The game stays running. A random turn may select
the original direction; inspect the authoritative `TURN` event rather than
requiring a different facing. Existing Laser audio is retained; theft adds no
sound, flying-flag animation, or event feed.

The tick response records `WEAPON_HIT`, then `FLAG_TRANSFERRED`, then the normal
displacement/crash and turn events. A transfer's `playerId` identifies Alice
(previous owner), `sourcePlayerId` identifies Bob (new owner), and
`checkpointId` is `CP1`. Authenticated snapshots expose acquisition tick 11 for
Bob's transferred flag. No capture score is awarded for theft.
