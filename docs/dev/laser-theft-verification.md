# Laser Flag Theft — Slices 1 and 2 Verification

Slice 1 demonstrates one eligible flag transferring before normal Laser
displacement and turning. Shield protection, blocked displacement, and a target
crashing into a pit have reproducible two-client scenarios. Slice 2 adds mixed-age
transfers, exact expiry boundaries, and three-player theft resets. Reload, respawn,
and backend restart verification remain in slice 3.

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

The browser cases use independent contexts for every player, actual database-backed API
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


## Slice 2: mixed ages and exact timing boundaries

Use the same servers, creation request, session setup, and one-tick advance above,
substituting a scenario below. Create a **fresh match for each row**; advancing a
successful theft changes its acquisition tick. Sessions remain ordered Alice,
Bob, then Carol when present. Use three independent profiles for chain scenarios.
Each fixture returns immediately before the shot under test, with its Laser in
locked slot 1; all other queue slots are empty (NOP). No manual positioning or
command input is required. All captured markers stay hidden and the next flag
remains available in every case.

### Mixed-age transfer

Create `THEFT_MIXED`. Both clients initially display tick 14, immediately before
the queued tick-15 shot. Alice at `(2,1)` owns Flag 1 acquired on tick 10, Flag 2
on tick 14, and Flag 3 on tick 9. Bob at `(0,1)` faces east and has Laser in slot 1;
Alice waits facing east. Flag 4 is available at `(7,5)`.

Advance once. On tick 15, both clients must highlight Flags 1 and 2 **together**,
show Bob as their owner, and change counts Alice/Bob from 3/0 to 1/2 before Alice
is displaced to `(3,1)` and turned. Flag 3 stays with Alice without a highlight.
The response must contain consecutive `FLAG_TRANSFERRED` events for CP1 and CP2,
both before displacement; Bob's acquisition ticks are now 15 for both flags,
and Alice retains CP3 at tick 9. Internal scores do not change.

### Capture window copies

In every row, Alice at `(2,1)` owns Flag 1 acquired on tick 10. Bob at `(0,1)`
faces east with Laser queued in slot 1; Alice waits facing east. Flag 2 remains
available at `(6,2)`. Advance once in each new match.

| Scenario | Initial tick | Shot tick | Expected owner and acquisition tick |
| --- | ---: | ---: | --- |
| `THEFT_CAPTURE_10` | 9 | 10 | Alice, 10; no highlight (acquisition tick excluded). |
| `THEFT_CAPTURE_11` | 10 | 11 | Bob, 11; both clients highlight Flag 1. |
| `THEFT_CAPTURE_15` | 14 | 15 | Bob, 15; both clients highlight Flag 1 (last eligible tick). |
| `THEFT_CAPTURE_16` | 15 | 16 | Alice, 10; no highlight (expired). |

Counts become Alice/Bob 0/1 only for successful theft; otherwise they remain 1/0.
The tick-10 fixture deliberately seeds ownership at tick 10 immediately before
that tick's resolution to isolate exclusion of the acquisition tick.

### Reset window copies and same-tick hit

The join order is Alice, Carol, Bob. At tick 15 the rotating initiative is Bob,
Alice, Carol. Alice starts at `(2,1)` facing east with Flag 1 acquired at tick 10.
Bob starts at `(0,1)` facing east. Carol at `(0,0)` faces north, so her Laser
always hits Bob regardless of his random facing after the hit. Flag 2 remains
available at `(6,2)`.

| Scenario | Initial tick and state | Queued shot(s) | Expected final owner and acquisition tick |
| --- | --- | --- | --- |
| `THEFT_CHAIN_15` | Tick 14; Alice owns Flag 1 at tick 10. | Bob then Carol, both slot 1 on tick 15. | Bob, 15. Carol hits Bob later that tick but cannot steal his new flag. |
| `THEFT_CHAIN_16` | Tick 15; Bob owns Flag 1 at tick 15. | Carol slot 1 on tick 16. | Carol, 16. |
| `THEFT_CHAIN_20` | Tick 19; Bob owns Flag 1 at tick 15. | Carol slot 1 on tick 20. | Carol, 20 (last eligible tick). |
| `THEFT_CHAIN_21` | Tick 20; Bob owns Flag 1 at tick 15. | Carol slot 1 on tick 21. | Bob, 15 (expired). |

For delayed Carol shots, setup actually executes Bob's tick-15 theft followed by
idle ticks, before returning the fixture. Alice therefore starts those copies
at `(3,1)` with a server-chosen facing; Bob remains `(0,1)` facing east and Carol
remains `(0,0)` facing north. Only Carol has a queued command. On her hit Bob
moves to `(0,2)` and receives the existing turn, whether or not theft succeeds.
In the same-tick copy Alice's and Bob's displacements and turns both occur after
their respective hits; check two `WEAPON_HIT` events but only one transfer.

After advancing, all three clients must agree: counts Alice/Bob/Carol are 0/1/0
for same-tick and expired cases, or 0/0/1 for successful Carol thefts. A successful
Carol theft highlights Flag 1 on every client. Acquisition timing moves to her
shot tick, so tick 16 gives a new window 17–21 and tick 20 gives 21–25. Automated
engine cases fire a third shot on that new acquisition tick, T+1, T+5, and T+6
for both successful Carol copies, verifying the reset repeats with exclusive
ownership and no additional capture score. These checks use active matches;
they do not test restoration, respawn, or backend restart.
