# Game-feel verification

## Completed checks

- Frontend TypeScript/Vite production build: passed.
- Frontend oxlint: passed without warnings.
- `git diff --check`: passed.
- Final isolated presentation regression suite: **75 passed**. This includes
  command queue ordering and identity, cardinal orientation and cumulative Laser
  rotation, duplicate/stale/skipped snapshots, interrupted and hidden-tab
  playback, respawns, checkpoints, sound dispatch and voice limits, persistent
  audio settings, and shield/flag behavior.
- The suite includes **49 responsive scenarios** covering square and rectangular
  boards, two/ten players, viewport resizing, retry/crash/final states, and all
  command, shield and audio controls without scrolling.
- Separate production-build performance scenario: passed with ten active robots,
  40 events per tick over three ticks, at most eight effect groups / 48 fragments,
  and complete effect cleanup.

The production sample ran in headless Chromium 153 with four logical processors,
with video and tracing disabled: median frame interval **16.7 ms**, 95th percentile
**33.4 ms**, and one **69 ms** long task over approximately 5.9 seconds. This
short desktop sample does not establish low-end mobile performance. Development
profiling with tracing/video enabled showed substantially more overhead.

The focused command, from `acceptance-tests`, is:

```sh
npm test -- command-queue continuous-laser responsive-board game-feel presentation-model shield-flags basic-game-view --grep-invert 'all players see' --workers=2
```

Local verification used a temporary Playwright server configuration because
`backend/mvnw` is not executable in this checkout; it called the installed Maven
directly. No repository deployment or server configuration was changed.

## Broader-suite limitations

The initial complete acceptance run finished with **79 passed / 24 failed**.
Failures included obsolete round/planning UI expectations, the local server's
default ten-concurrent-game limit, and an old position assertion based on
`offsetTop` rather than transformed screen coordinates. The position assertion
was updated and passes in the final focused run. Broader reruns were not clean;
the entire acceptance suite is **not** reported as passing.

Two representative legacy failures were reproduced against an unchanged HEAD
frontend and a separate disposable backend: `game-round` expects five private
program slots, and the planning scenario in `basic-game-view` expects `Round 1`.
Both UI flows were already absent from the continuous frontend. Migrating those
legacy scenarios is outside this presentation change.

## Manual review remaining

- Listen to the complete cue set on desktop and phone speakers; automated checks
  validate dispatch and audio files, not perceived balance or timbre.
- Verify audio unlocking and visual behavior in Firefox and Safari/mobile.
- Profile a representative low-end mobile device before treating the desktop
  frame timings as a wider performance guarantee.

Asset sources, licenses, reproducible sound generation and audio preferences are
documented in [assets.md](assets.md).
