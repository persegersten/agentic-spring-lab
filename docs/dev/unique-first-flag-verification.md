# Unique Flag Capture — Slice 1 Verification

This guide started with slice 1 and now reflects slice 2: later flags unlock
and can be captured sequentially. For current completion and shared ranks, see
[slice 4 verification](final-flag-verification.md). For the standing-robot, atomic-push, wall, Laser, and conveyor cases,
use [the slice 2 scenarios](sequential-flags-verification.md).

## Reproducible local setup

Use Java 25 and the installed frontend/acceptance-test dependencies. Install
the Chromium version required by this project's Playwright dependency once
(and again after upgrading Playwright). From the project root:

```bash
cd acceptance-tests
npm exec -- playwright install chromium
cd ..
```

Playwright normally installs browsers in `~/.cache/ms-playwright` on Linux.
If you set `PLAYWRIGHT_BROWSERS_PATH`, use the same value when installing
browsers and running tests. A browser installed in a temporary cache is not
found by a test run using the default cache.

Start a disposable backend from the project root in one terminal:

```bash
cd backend
bash mvnw spring-boot:run -Dspring-boot.run.profiles=in-memory,deterministic-e2e
```

Start the frontend from the project root in another terminal:

```bash
npm --prefix frontend run dev -- --host 127.0.0.1
```

Open <http://127.0.0.1:5173> in two separate browser profiles or one normal
window and one private window. Create a game as Alice, copy its invitation link
to the other session, and join as Bob. Start the game as Alice. These profiles
use the existing deterministic board, with Flag 1 at `(2,0)`, Flag 2 at `(4,0)`,
Flag 3 at `(4,2)`, and Flag 4 at `(2,2)`. Alice starts at `(0,0)` and Bob at
`(1,0)`, both facing south.

## Manual scenario

Queue each sequence using the named buttons. Wait until its final position is
visible before proceeding; the server executes one command every two seconds,
and newly queued commands first pass through slots 3, 2, and 1.

1. As Bob, click **Vänd**, then **Framåt**. Wait for Bob at `(1,1)` facing north.
2. As Alice, click **Vänster**, then **Framåt**. Wait at `(1,0)` facing east.
   Alice is now exactly one move from Flag 1. In both clients, verify that the
   strip shows Flag 1 as **Tillgänglig** and Flags 2–4 as **Låst**.
3. As Alice, click **Framåt**. At `(2,0)`, both clients must show Alice as the
   owner of Flag 1, remove its board marker, and highlight Flag 2. The strip
   must show Flag 2 as **Tillgänglig**, with Flags 3 and 4 still **Låst**.
4. As Alice, queue **Framåt**, **Framåt**, **Vänster**, **Framåt**, **Framåt**.
   Wait for queue space as necessary. Alice captures Flag 2, immediately unlocks Flag 3, and captures it at
   `(4,2)`; her flag count must become 3. Flag 4 becomes available.
5. As Bob, queue **Vänd**, **Framåt**, **Vänster**, **Framåt**. Wait at `(2,0)`,
   Flag 1's former cell. Both clients must still show Alice as its owner and
   Bob with zero flags; Flag 1's marker must remain absent.

The strip stays outside the board throughout. At a narrow viewport, long owner
names truncate visually, remain available through the title/accessibility text,
and must not cause horizontal scrolling.

## Automated checks

From `backend/`, run the ownership and round regression tests:

```bash
bash mvnw -Dtest=ExclusiveFirstFlagTest,GameRoundTest test
```

With the two local servers above running, run the same two-player scenario
and the narrow-screen ownership-strip check from the project root:

```bash
npm --prefix acceptance-tests test -- unique-first-flag --workers=1 --reporter=line
```

The browser test uses two independent browser contexts and real server commands
for the multiplayer scenario. It also checks both authenticated snapshots for
Alice's exclusive ownership. Its narrow-screen case uses a fixed API response
to exercise ordering, long names, and safe text rendering. No capture rules are
implemented in the browser fixtures.
