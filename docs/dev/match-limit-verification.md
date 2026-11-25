# Existing Match Limit — Slice 5 Verification

Incomplete collections end at the existing limit with the same flag-only
standings and shared ranks used after the final capture. Uncaptured flags remain
unowned. Zero captures means everyone shares rank 1. There is no extra round.

Register-based play retains its player-count round caps and resolves the complete
final round before ending. The current continuous client uses the existing
600000 ms match duration and 2000 ms tick interval. A tick due exactly at its
deadline now resolves fully before completion, rather than being skipped. A tick
scheduled beyond the cutoff cannot execute. A late scheduler resolves at most one
already pending permitted tick before ending; it does not extend the deadline or
replay missed ticks. No limit values, flag counts, maps, or balance rules change.

## Automated verification

From `backend/`, run:

```bash
bash mvnw -Dtest=HardMatchLimitTest,FinalFlagCompletionTest,GameRoundTest,SequentialFlagCaptureTest test
```

These tests exercise both game modes, tied and unequal counts, zero captures,
the last flag at the limit, all remaining round effects, no next round, delayed
scheduler callbacks, and deadlines that fall between ticks.

From the project root, run the four two-client limit scenarios:

```bash
npm --prefix acceptance-tests test -- --config=sequential-flags.config.mjs match-limit --workers=1 --reporter=line
```

The same configuration without the `match-limit` selector runs the capture and
final-flag regression scenarios too. It starts disposable test-profile servers on
ports 18080 and 5174. Use Java 25 and installed frontend/acceptance-test
dependencies; if necessary, install Chromium from `acceptance-tests/` with
`npm exec -- playwright install chromium`.

The browser tests use two independent sessions and real authoritative tick
resolution. Paused presentation clocks verify that later movement, conveyor
effects, and rotation appear before final standings. Both authenticated snapshots
must retain the original deadline and advance the revision only once. Repeated
tick requests and client reloads must leave the result unchanged.

## Manual setup

Start the backend and frontend using
[the sequential scenario setup](sequential-flags-verification.md):
`spring-boot:test-run` with profiles `in-memory,sequential-flags-e2e` on port
18080, and Vite on port 5174 proxying that backend.

Create a scenario, substituting a name from the table below:

```bash
curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags?scenario=LIMIT_TIE'
```

Use its `gameId` and the two `sessions` objects to open Alice and Bob in independent
browser profiles, as described in the sequential guide. The fixture positions the
continuous clock at **Tick 299**, with the last tick due exactly at the original
ten-minute deadline. It does not shorten the limit. Both robots already have
**Framåt** queued in locked slot 1, representing commands committed earlier.
No command input is needed before advancing the final tick.

Alice starts at `(0,1)` and Bob at `(0,3)`, facing east. Bob's move reaches a
conveyor at `(1,3)`, which moves him to a clockwise rotator at `(2,3)`. The fixture
gives Bob a higher internal score and a different crash history to prove these
do not decide placement.

| Scenario | Flags owned before the final tick | Expected final counts / ranks | Unclaimed flags afterward |
| --- | --- | --- | --- |
| `LIMIT_TIE` | Alice: 1; Bob: 2 | Counts 1/1; ranks 1/1; shared win | Flags 3 and 4 |
| `LIMIT_ZERO` | None | Counts 0/0; ranks 1/1; shared win | All four flags |
| `LIMIT_UNEQUAL` | Alice: 1 and 2; Bob: 3 | Counts 2/1; ranks 1/2; Alice wins | Flag 4 |
| `LIMIT_FINAL` | Alice: 1; Bob: 2 and 3 | Alice captures 4; counts 2/2; ranks 1/1 | None |

1. Confirm both clients show Tick 299 and no final standings. Unowned flags must
   still have markers; the first unowned flag is available and later flags locked.
2. Advance exactly one tick, replacing `GAME_ID` with the returned identifier:

   ```bash
   curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags/GAME_ID/ticks?count=1'
   ```

3. Observe Alice's move and Bob's move, conveyor, and southward rotation before
   final standings appear. In `LIMIT_FINAL`, Alice captures Flag 4 during this
   same final tick. The game must finish once after all those actions, at Tick 300.
4. Confirm both clients show the counts, ranks, winners, and unclaimed flags in
   the table. Uncaptured flags must not acquire owners or disappear. Controls must
   be disabled, even if a remaining flag is still highlighted.
5. Repeat the tick request with `count=2`. The round, positions, orientations,
   placements, and revision must stay unchanged; no new round may begin. Reload
   both sessions and confirm the same authoritative result is restored.

The tick response contains final `players`, `placements`, `vehicles`, and ordered
`round.playback`. Authenticated player snapshots include the retained deadline,
tick, and revision. The existing player-count caps for register-based rounds
(7/6/5) are also covered by the backend completion path; no continuous match is
reinterpreted as having only 5–7 ticks.
