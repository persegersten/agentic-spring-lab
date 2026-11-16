# Game-feel verification

## M3 visual effects and HUD polish — 2026-11-16

M3 adds server-driven laser flashes, impact sparks and plates, collapsing robot
silhouettes, respawn deployment brackets/scan, checkpoint brackets and standings
highlights. Gunmetal surfaces, amber command warnings and consistent SVG icons
keep the existing board and queue architecture. Checkpoint progress is now in
the standings. No dependencies, external artwork, backend contracts or gameplay
rules were added or changed.

The visual planner caps simultaneous groups at eight, including beams, and
fragments at 48. It prioritizes important/local feedback and permanently drops
excess decorative effects. Markers last 160–550 ms, bounded by the tick's existing
linger window. Overlapping local shake cues do not stack; shake is at most 2 px
for 120 ms. Live reduced-motion changes cancel decoration and retain static
markers, without replay on re-enabling motion. The countdown timer is isolated
from board renders; the scheduler stops requesting frames after settling.

Reviewed screenshots: `/tmp/wreckage-m3-capture-desktop.png`,
`/tmp/wreckage-m3-respawn-desktop.png`, `/tmp/wreckage-fit-320x568.png`,
`/tmp/wreckage-fit-568x320.png`. Effects render below robots and checkpoint
numbers and do not receive pointer input. Very small boards still compress robot
names, as before; full names remain available through accessible labels.

M3 verification:

- TypeScript/Vite production build, oxlint and `git diff --check`: passed.
- **117 unique focused regressions passed across completed runs**: 65 other
  presentation/model/audio cases, three laser cases and 49 responsive scenarios.
  The final laser/responsive run was **52 passed**. Short-lived beam/block checks
  record admission through MutationObserver rather than assuming a polling
  assertion will catch a 160–180 ms marker. The initial combined runner was
  terminated, and a reused Vite server later shut down; completed verification
  used an isolated Vite-only server because these scenarios mock backend data.
- New checks cover authoritative effect classification/anchors, capture validation,
  lifetimes, deterministic full-ID debris, preemption and no backfill, eight-group /
  48-fragment limits, non-stacking local shake, live reduced-motion cancellation
  without replay, standings feedback and a settled board with zero requested
  animation frames / DOM mutations during a 700 ms idle observation.
- The full legacy acceptance suite was not rerun for M3; its earlier limitations
  are recorded below. Backend, deployments and server timing were not changed.

Production comparison uses the same `dense multiplayer` scenario: ten robots,
40 events per tick, three ticks, Chromium 153 reporting four logical processors,
one worker, tracing/video disabled, no concurrent test run. Both builds passed
bounded-effects and cleanup checks.

| Measurement | Before M3 | After M3 |
| --- | --- | --- |
| Median frame interval | 16.7 ms | 16.7 ms |
| 95th percentile frame interval | 33.3 ms | 16.8 ms |
| Long tasks | one, 89 ms | none recorded |
| Sample duration / frames | 6.06 s / 329 | 5.68 s / 325 |
| Production JS gzip | 135.21 KB | 136.82 KB |

The shorter effects also shorten this fixture's cleanup waits. These are short
samples, not a controlled hardware benchmark or a guarantee of 60 FPS. No low-end
mobile, Firefox or Safari device profiling was performed. Dense bursts can drop
lower-priority cosmetic groups; gameplay and sound dispatch are unaffected.
Very small cells reduce decorative detail. Original SVG/CSS avoids particle
engine overhead and external asset downloads.

Raw metrics: `/tmp/wreckage-m3-baseline-performance.json` and
`/tmp/wreckage-m3-final-performance.json`. Local test logs:
`/tmp/wreckage-m3-focused.log` (67 passed before the last laser assertion fix)
and `/tmp/wreckage-m3-layout.log` (52 passed after that fix).
No deploy, commit or push was performed.

## M2 sound refinement — 2026-11-16

- Frontend TypeScript/Vite production build, oxlint and `git diff --check`: passed.
- Focused sound, M1 animation, model, responsive and shield regression suite:
  **110 passed**, including the existing 49 responsive scenarios.
- Real Chromium checks exercise Web Audio and forced HTML5 fallback, silent
  preloading, trusted-action unlocking, mute, failed decoding and continued
  command submission. A final native-audio subset was rerun after adding trusted
  click support. Automated tests do not replace listening or Safari/mobile review.
- Policy/adapter tests cover all cue mappings, eight total / two motor voice
  limits, cooldowns, per-vehicle/tick coalescing, live master-volume changes,
  asynchronous failures, pending-start expiry, cleanup and audio-context recovery.
- Integration tests cover rejected/lost submission acknowledgements, duplicate
  snapshots, scoped cancellation during an acknowledgement that advances a tick,
  respawn, checkpoint capture, victory and silence when loading a finished game.
- WAV header/duration/peak checks and SHA-256 manifest checks pass for every cue.
  Assets are locally bundled original CC0 recordings, reproducible from the
  generator. All three official Kenney archive downloads returned HTTP 403, so
  the approved original-synthesis fallback was used.

Run the focused command below with `audio-policy responsive-board shield-flags`
also included. This run used the same temporary local Maven/Playwright setup;
server rules, mechanics, API contracts and deployment configuration were unchanged.
The older full-suite limitations below were not addressed or reported as passing.
No deployment, commit or push was performed.

Manual listening, fatigue/timbre assessment, and Firefox/Safari/mobile audio
unlocking checks were **not performed**. Browser-boundary tests use Chromium,
including its HTML5 fallback; they do not establish Safari hardware behavior.

## M1 Motion refinement — 2026-11-16

- TypeScript/Vite production build, oxlint and `git diff --check`: passed.
- Focused Chromium regression suite: **83 passed**, including 49 responsive
  scenarios and new tests for Motion interpolation, slot-1 execution identity,
  collision recoil, recovery on newer/equal revisions, and snapshot deduplication.
- Additional active-animation preference test: **1 passed**. Enabling reduced
  motion while a robot and conveyor are moving immediately sets both to their
  authoritative targets. There are **84 distinct passing cases** across these runs.
- No sound features, particle libraries, backend contracts or gameplay rules
  were added or changed. The existing sound and effect regressions still pass.

The focused selection is the command below with `responsive-board shield-flags`
also included. Local execution used the same temporary Maven/Playwright setup
noted below. No deployment, commit or push was performed. The full legacy
acceptance suite was not rerun for M1; its earlier limitations remain documented.

Motion 14's reduced-motion hook captures its initial value. M1 uses a cached
media-query subscription alongside it so live preference changes cancel tweens.
CSS no longer owns transforms migrated to Motion; animation callbacks never
submit gameplay requests. The production JavaScript bundle is approximately
134 KB gzip after adding Motion (approximately 80 KB before this refinement).
The performance timings below describe the earlier implementation, not a new
performance measurement of M1.

## Earlier game-feel implementation checks

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
