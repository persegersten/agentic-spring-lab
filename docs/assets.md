# Game presentation assets

## Shipped assets

| Asset | Source / original filename | License | Modifications |
| --- | --- | --- | --- |
| `frontend/public/audio/*.wav` | Original Wreckage synthesis; filenames below | CC0 1.0 (see bundled `audio/LICENSE.txt`) | None after generation |
| Robot artwork | Original inline SVG in `GameBoard.tsx` | Project source; no external artwork | N/A |
| M3 beams, sparks, debris, destruction, respawn and capture effects | Original inline SVG in `BoardEffects.tsx` / `LaserEffects.tsx` and CSS in `game-polish.css` | Project source; no external artwork | N/A |
| HUD and command icons | Original inline SVG in `Icon.tsx` | Project source; no external artwork | N/A |
| Motion for React and its Motion dependencies | https://motion.dev, npm `motion` 14.14.0 | MIT; bundled at `public/licenses/motion-MIT.txt` | Unmodified dependencies |
| tslib (Motion dependency) | https://github.com/microsoft/tslib | 0BSD; bundled at `public/licenses/tslib-0BSD.txt` | Unmodified dependency |
| Howler.js | https://github.com/goldfire/howler.js, npm `howler` 2.2.4 | MIT; bundled at `public/licenses/howler-MIT.txt` | Unmodified dependency |

The original sound files are `submit.wav`, `tick.wav`, `move.wav`, `turn.wav`,
`impact.wav`, `blocked.wav`, `laser.wav`, `hit.wav`, `crash.wav`, `respawn.wav`,
`capture.wav`, `victory.wav`, `complete.wav`, and `shield.wav`.

Reproduce them with `python3 scripts/local-dev/generate-audio.py`. The generator
uses fixed seeds, filtered noise, damped metallic modes and short original melodies; it needs no external
samples, network access or Python packages. All files are mono 16-bit PCM WAV at
22,050 Hz. Each cue has a 25 ms tail fade and a maximum peak of 0.7, with per-cue playback gains.
Total uncompressed audio is approximately 166 KiB. A generated
`frontend/public/audio/manifest.json` records each source identifier, duration,
processing and SHA-256 checksum; `audioCues.ts` defines filenames, durations and gains.

## Evaluated sources (not shipped)

- [Kenney Sci-fi Sounds](https://kenney.nl/assets/sci-fi-sounds): CC0 1.0.
  The official archive returned HTTP 403 again during M2 implementation.
- [Kenney Impact Sounds](https://kenney.nl/assets/impact-sounds): CC0 1.0.
  The official archive returned HTTP 403 during M2 implementation.
- [Kenney Interface Sounds](https://kenney.nl/assets/interface-sounds): CC0 1.0.
  The official archive returned HTTP 403 during M2 implementation.
  The approved fallback is the original reproducible CC0 palette; no Kenney
  recordings are bundled and no unknown mirror sources were used.
- [Kenney UI Pack – Sci-Fi](https://kenney.nl/assets/ui-pack-sci-fi): CC0 1.0.
  Original SVG gives the board, queue and buttons a consistent industrial style.
- [tsParticles](https://github.com/tsparticles/tsparticles): bounded SVG bursts
  cover the effects without an additional canvas lifecycle; not installed.

## M2 cue palette

Every file below is original Wreckage synthesis, dedicated to CC0 1.0 under
`frontend/public/audio/LICENSE.txt`. Its source filename is
`original-synthesis:<cue>` in `scripts/local-dev/generate-audio.py`. There are no
third-party samples. The WAVs are written directly by the generator; edits and
processing are reproducible from that source.

| Cue / filename | Character | Duration | Gain before master volume |
| --- | --- | --- | --- |
| `submit.wav` | Double mechanical latch | 120 ms | 0.50 |
| `tick.wav` | Quiet low execution clunk | 100 ms | 0.16 |
| `move.wav` | Low motor burst | 180 ms | 0.24 |
| `turn.wav` | Short servo | 160 ms | 0.24 |
| `impact.wav` | Metallic body impact | 240 ms | 0.65 |
| `blocked.wav` | Restrained stop clack | 100 ms | 0.38 |
| `laser.wav` | Short descending discharge | 180 ms | 0.65 |
| `hit.wav` | Bright metallic impact | 200 ms | 0.75 |
| `crash.wav` | Rumble and three debris clicks | 480 ms | 0.65 |
| `respawn.wav` | Motor restart and rising notes | 380 ms | 0.46 |
| `capture.wav` | Three-note checkpoint confirmation | 360 ms | 0.70 |
| `victory.wav` | Four-note finish flourish | 650 ms | 1.00 |
| `complete.wav` | Neutral match-complete confirmation | 400 ms | 0.42 |
| `shield.wav` | Brief protection hum | 280 ms | 0.40 |

### Action-cue gain tuning verification

Slice 1 changes only the laser, hit, capture and victory gains. Relative to the
previous gains, these increase by 2.63, 2.23, 2.92 and 4.44 dB respectively.
Victory has the largest gain and the highest gain-adjusted RMS of the four
existing recordings. All source assets, durations and other cue gains are unchanged.

Verification used the live React presentation and real Howler/Web Audio in
headless Chromium, with deterministic API response fixtures rather than a running
backend. Original gains were served through a temporary browser route for the
baseline. Both versions received the same ordered movement/turn, firing/hit,
checkpoint and winner-finish sequence, plus a dense 20-event tick with overlapping
firing, hits, movement, turns and captures. Browser polling and rendering introduce
small timing differences between captures. The same browser and master volumes
were used; a temporary tap recorded the mixed floating-point signal before output.

| Master volume | Original mixed peak | Tuned mixed peak | Samples exceeding full scale |
| --- | --- | --- | --- |
| 50% | 0.2203 | 0.3498 | 0 in both captures |
| 100% | 0.4661 | 0.6996 | 0 in both captures |

The frontend production build and 29 existing audio-policy/browser checks passed,
covering settings, deduplication, cooldowns, voice limits, readiness/failures,
winner dispatch and real HTML5 fallback. Four temporary live-capture checks and
one finish-recipient check in isolated winner/non-winner browser contexts also
passed. Capture WAVs are available for this workspace session as
`/tmp/wreckage-audio-live-{before,after}-{0.5,1}.wav`; temporary verification scripts
and configuration were not added to the project.

These measurements establish headroom for the tested live sequence, not every
possible mix. A separate synthetic stress mix that starts duplicate firing and
hit waveforms at exactly the same sample exceeded full scale with both the old
and new gains. No global mixing or suppression policy was changed to address that
pre-existing limitation. Human listening, perceived victory impact, audible
distortion and real-device speaker quality remain unverified in this environment.

## Audio behavior

The game preloads local assets without playing them. Trusted pointer, touch-end, click
or keyboard interaction requests audio unlocking. Web Audio readiness follows
the actual context state, and HTML5 fallback elements are primed silently inside
the gesture. Fallback playback reuses one primed element per cue rather than
allocating overlapping, potentially locked elements. Howler's standard
touch/unlock support remains enabled. Sounds do
not wait in a playback backlog: locked, unloaded, failed, hidden or muted cues
are consumed and discarded. Pending starts expire after 150 ms. A failed cue
can be reloaded on a future user gesture, without replaying its old events.
Leaving the game unloads its Howls and clears playback listeners and watchdogs.

Game cues use the M1 event schedule and game/tick/stream/sequence identity.
Submission sounds require an acknowledged request ID. Initial loads, recovery,
skipped ticks, hidden-tab return and loading a finished match remain silent.
A tick delayed more than 150 ms is silent, matching late-event suppression.
Only checkpoint score events produce capture sounds; authoritative winner
placements select victory rather than the neutral completion cue.

There are at most eight audible voices and at most two motor voices. Motors are
coalesced per vehicle, cue and tick. Remote motor/impact cues have an 80 ms per-cue
cooldown and 0.55 gain relative to local cues. Local commands, crashes, capture and
victory take precedence over remote sounds. No loops, music or continuous engines
are introduced.

The mute button and master-volume slider remain in the game heading. Preferences
are stored in `wreckage-audio` with session-only fallback when storage is blocked.
Default master volume is 50%. Volume changes update active voices immediately;
mute and zero volume stop them. Unmuting never releases earlier sounds.

Automated checks validate cue dispatch, voice policy, readiness/failure handling,
asset integrity and real Chromium Web Audio starts. Manual listening in Firefox,
Safari and on mobile hardware remains part of audio release review; those checks
are needed to assess timbre, fatigue, speaker balance and device-specific unlocking.
