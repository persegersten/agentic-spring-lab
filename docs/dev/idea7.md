# Progressive background music

## Goal

Introduce a progressive background soundtrack that increases the tension and emotional intensity of Wreckage as the match progresses.

The soundtrack consists of five original 8-bit-inspired Baroque compositions, progressing from melancholic and dystopian music to a joyful celebration.

The music should become a recognizable part of the game's identity.

## 1. Music assets

Five prerecorded audio files are supplied:

- 01-awakening.ogg
- 02-pursuit.ogg
- 03-conflict.ogg
- 04-reckoning.ogg
- 05-triumph.ogg

The supplied assets are replaceable. Music composition, editing and production are outside the implementation scope.

Do not generate additional music or search for external music libraries.

## 2. Music selection

The background track is determined by the current authoritative match progression:

| Game state | Track |
|---|---|
| Flag 1 is available | 01-awakening |
| Flag 2 is available | 02-pursuit |
| Flag 3 is available | 03-conflict |
| Flag 4 is available | 04-reckoning |
| Match has a winner | 05-triumph |

There is no background music from this feature in the lobby.

All players hear the music corresponding to the same match phase, including the final celebration. Playback does not need to be synchronized between browsers.

If the match ends without a winner, stop the background music without playing the triumph track.

## 3. Playback lifecycle

- Tracks 1–4 loop continuously while their corresponding match phase remains active.
- The triumph track plays once when a winner is declared.
- When the active flag changes, transition to the corresponding new track.
- Ownership transfers do not change or restart the background track unless authoritative flag availability changes.
- Ordinary state updates must not restart the currently playing track.
- If multiple flags are captured during the same tick, transition directly to the track corresponding to the latest authoritative state.
- Intermediate tracks may be skipped.
- If the match ends during a music transition, the final match state takes precedence.
- Leaving the game screen stops playback.
- Joining or reconnecting to an ongoing match starts the track corresponding to its current phase.
- Reconnecting to an already completed match does not replay the triumph track.

## 4. Track transitions

Use a short fade-out followed by a fade-in when switching tracks.

A total transition duration of approximately 800 ms is suggested.

Only one background music track should be audible at a time.

A newer match-phase transition replaces any pending transition. A completed fade must never activate an obsolete track.

Do not delay gameplay, event processing or graphical presentation to accommodate music transitions.

Beat matching and musically synchronized transitions are explicitly out of scope.

## 5. Volume and sound effects

Background music must remain secondary to gameplay sound effects.

- Reuse existing audio infrastructure and browser audio-unlock behaviour where practical.
- Respect existing master volume and mute controls.
- Add one independent music volume control to the existing audio settings.
- Allow music volume to be reduced to zero independently of gameplay sound effects.
- Persist the music volume preference using the existing settings mechanism.
- Default to a relatively low music volume, suggested 20%.
- Preserve the existing sound effects, cooldowns, voice limits and suppression policies.
- Do not introduce dynamic ducking or modify existing sound-effect priorities.

If necessary, use a separate playback channel for background music so that looping music does not consume existing sound-effect voices.

## 6. Technical constraints

- Reuse existing frontend audio technology wherever practical.
- Use native browser audio capabilities if the existing sound-effect infrastructure is unsuitable for looping music.
- Do not introduce new third-party dependencies.
- Keep music playback independent of game-engine timing.
- Keep the track-to-match-phase mapping centralized and easy to modify.
- Missing or failed music assets must not prevent gameplay.
- Follow existing browser autoplay restrictions.

## 7. Lifecycle invariant

The desired background track derives exclusively from current authoritative match progression.

Audio callbacks, fade completion and transient gameplay events must never independently determine the active match phase or activate an obsolete track.

## 8. Out of scope

- Game rule changes.
- Backend or API redesign.
- Sound-effect redesign.
- Music composition or asset production.
- Adaptive orchestration or individual instrumental layers.
- Beat synchronization between tracks or clients.
- External streaming services.
- New audio frameworks.

## 9. Acceptance criteria

- Entering an ongoing match starts the correct background track once browser audio playback is permitted.
- Background music loops during each of the four flag phases.
- Capturing a flag changes the background track to the correct next phase.
- Multiple captures during one tick do not cause obsolete tracks to play.
- Declaring a winner plays the triumph track once.
- Ordinary state updates and ownership transfers do not unnecessarily restart music.
- Music volume can be adjusted independently of sound effects.
- Existing master volume and mute behaviour remain functional.
- Leaving the game stops background music.
- Missing music assets do not interrupt gameplay.
- Existing gameplay and sound effects remain unchanged.

## 10. Implementation slices

All three slices are in scope, in the following priority order:

### Slice 1 — Basic background music

Play and loop the first supplied track during an active match. Support independent music volume, existing master mute and stopping playback when leaving the game.

### Slice 2 — Progressive soundtrack

Connect music selection to authoritative flag availability. Introduce track transitions, simultaneous capture handling and correct reconnect behaviour.

### Slice 3 — Match finale

Play the triumph track once when a winner is declared. Handle match termination and transitions without replaying historical victory music.

## 11. Delegated decisions

Exact gain values, fade durations, playback implementation and control placement are delegated implementation decisions.

One focused listening and integration test pass is sufficient.

All product decisions are settled. Proceed to planning.