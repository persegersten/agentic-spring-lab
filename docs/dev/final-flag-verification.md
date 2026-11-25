# Final Flag Completion — Slice 4 Verification

The final capture finishes the current round before ending the match. In the
continuous client, that means the current tick's remaining player commands and
board effects; queued future commands do not run. Register-based rounds finish
every remaining register and normal round-end scoring. Final placement depends
only on flag counts, with shared competition ranks (`1, 1, 3`).

This guide does not implement or verify the alternate ending where the existing
limit is reached with flags still unclaimed. That remains slice 5.

## Automated checks

From `backend/`, run:

```bash
bash mvnw -Dtest=FinalFlagCompletionTest,GameRoundTest,SequentialFlagCaptureTest,ExclusiveFirstFlagTest test
```

The completion tests cover later registers, a post-capture crash, conveyors,
rotators, round-end scoring, split ownership, shared ranks, and refusal to execute
another tick/round. They also ensure boards without flags do not finish instantly.

From the project root, run the two-client final-round scenarios:

```bash
npm --prefix acceptance-tests test -- --config=sequential-flags.config.mjs final-flag --workers=1 --reporter=line
```

The same configuration without the `final-flag` selector includes the earlier
sequential-capture scenarios. Use Java 25 and installed frontend/acceptance-test
dependencies. If needed, install the matching browser from `acceptance-tests/`
with `npm exec -- playwright install chromium`.

The browser checks use real server commands and two independent player contexts.
They control browser presentation clocks to inspect the final capture, subsequent
movement, conveyor, and rotation before advancing to final standings. They also
check the final authoritative event order, winners, counts, disabled controls,
no next tick, and immediate final standings on reload.

## Manual scenarios

Start the same disposable backend and frontend described in
[the sequential flag guide](sequential-flags-verification.md): the test application
on port 18080 with `in-memory,sequential-flags-e2e`, and Vite on port 5174 with
`WRECKAGE_BACKEND_URL=http://127.0.0.1:18080`.

Create a tied final-round fixture:

```bash
curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags?scenario=FINAL_TIE'
```

Use the returned `sessions` objects to open Alice and Bob in independent browser
profiles as described in the sequential guide. Retain the returned `gameId`.
These are paused test-only scenarios; explicit tick requests advance them.

The four flags remain numbered 1–4. Flags 1–3 start owned, with only Flag 4
available at `(1,1)`. Alice starts at `(0,1)` facing east; Bob starts at `(0,3)`
facing east. A conveyor at `(1,3)` leads east to a clockwise rotator at `(2,3)`.
The fixture deliberately gives the players different internal scores, crash
histories, and distances so those cannot explain a shared result.

1. Queue **Framåt**, then **Höger** as Alice. Queue **Framåt** as Bob. Do not
   advance time between these inputs. Alice's second command belongs to a future
   tick and must not execute after completion.
2. Advance two ticks to move the forward commands through the queue. Replace
   `GAME_ID` below with the returned ID:

   ```bash
   curl -X POST 'http://127.0.0.1:18080/games/test-fixtures/flags/GAME_ID/ticks?count=2'
   ```

   Wait until both clients show **Tick 2**. There must be no final standings yet.
3. Advance exactly one more tick using the same request with `count=1`. Alice
   captures Flag 4. Bob must still move to `(1,3)`, ride the conveyor to `(2,3)`,
   and turn south. During playback the client shows **Avslutar** and keeps final
   standings hidden. After playback, both clients show two flags each, shared
   rank 1, and **Delad vinst**. Every flag marker is gone; the strip retains the
   owners. Command buttons are disabled.
4. Request two more ticks. The round number, vehicle positions/orientations,
   and placements must stay unchanged. Alice must not execute the queued right
   turn. Reload a client: the completed standings should appear directly.
5. Create a new fixture using `scenario=FINAL_UNEQUAL` and repeat the inputs and
   tick requests. This time Alice finishes with three flags and Bob with one.
   Alice alone wins, despite Bob's higher internal score.

The final tick response lists these playback events in order:
`MOVE` (Alice), `SCORE_CHANGED` (CP4), `MOVE` (Bob), `CONVEYOR_MOVE` (Bob),
`ROTATOR_TURN` (Bob). The API reports null for the retired distance tie-break
field. No schema migration or normal deployment configuration is changed.
