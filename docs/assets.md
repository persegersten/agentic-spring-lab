# Game presentation assets

## Shipped assets

| Asset | Source / original filename | License | Modifications |
| --- | --- | --- | --- |
| `frontend/public/audio/*.wav` | Original Wreckage synthesis; filenames below | CC0 1.0 (see bundled `audio/LICENSE.txt`) | None after generation |
| Robot artwork | Original inline SVG in `GameBoard.tsx` | Project source; no external artwork | N/A |
| HUD and command icons | Original inline SVG in `Icon.tsx` | Project source; no external artwork | N/A |
| Motion for React and its Motion dependencies | https://motion.dev, npm `motion` 14.14.0 | MIT; bundled at `public/licenses/motion-MIT.txt` | Unmodified dependencies |
| tslib (Motion dependency) | https://github.com/microsoft/tslib | 0BSD; bundled at `public/licenses/tslib-0BSD.txt` | Unmodified dependency |
| Howler.js | https://github.com/goldfire/howler.js, npm `howler` 2.2.4 | MIT; bundled at `public/licenses/howler-MIT.txt` | Unmodified dependency |

The original sound files are `submit.wav`, `tick.wav`, `move.wav`, `turn.wav`,
`impact.wav`, `blocked.wav`, `laser.wav`, `hit.wav`, `crash.wav`, `respawn.wav`,
`capture.wav`, `victory.wav`, `complete.wav`, and `shield.wav`.

Reproduce them with `python3 scripts/local-dev/generate-audio.py`. The generator
uses fixed seeds, filtered noise and inharmonic oscillators; it needs no external
samples, network access or Python packages. All files are mono 16-bit PCM WAV at
22,050 Hz. Total uncompressed audio is approximately 180 KB.

## Evaluated sources (not shipped)

- [Kenney Sci-fi Sounds](https://kenney.nl/assets/sci-fi-sounds): CC0 1.0.
  The pack download returned HTTP 403 during implementation. Original synthesis
  supplies the complete cue set without importing assets of uncertain provenance.
- [Kenney UI Pack – Sci-Fi](https://kenney.nl/assets/ui-pack-sci-fi): CC0 1.0.
  Original SVG gives the board, queue and buttons a consistent industrial style.
- [tsParticles](https://github.com/tsparticles/tsparticles): bounded SVG bursts
  cover the effects without an additional canvas lifecycle; not installed.

## Audio behavior

Pointer or keyboard interaction initializes Howler and resumes its audio context.
Cues that occur before decoding/unlock, while muted, or in a hidden tab are
discarded rather than queued. Mute and volume are persisted in `wreckage-audio`;
storage failure falls back to session preferences. Sound loading/playback failure
never prevents play. There are at most eight voices. Remote motors have the lowest
priority; impacts, commands and match completion take precedence.

The mute button and volume slider are in the game heading. Manual listening in
Firefox, Safari and on mobile hardware remains part of audio release review;
Chromium tests verify files, preferences, scheduling and the injectable voice policy.
