# Improve action sound effects and victory feedback

## Goal

Make Wreckage feel more exciting, immersive and rewarding by increasing the audio impact of important gameplay events.

The player should clearly hear when something significant happens on the game board. Winning the game should feel particularly satisfying and dramatic.

## Requirements

Improve the sound effects for the following events:

| Event | Desired audio experience |
|---|---|
| Laser fired | Powerful, sharp and distinct |
| Robot hit by laser | Heavy, impactful and clearly distinguishable from firing |
| Flag captured | Positive, rewarding and immediately recognizable |
| Game won | Dramatic, celebratory and significantly more impactful than ordinary gameplay sounds |

## Audio rules

- Prefer reusing the existing sound library, audio assets and playback infrastructure.
- Increase the relative volume and impact of the selected sound effects without introducing audio clipping or distortion.
- Keep ordinary movement and background sounds at their current levels.
- Laser firing and laser impact sounds must be audible to all players observing the game.
- Flag capture sounds must be audible to all players.
- Play the victory celebration for the winning player only. Preserve existing audio behaviour for other players.
- Victory audio should take priority over ordinary gameplay sounds.
- Each sound should play once per corresponding gameplay event, not repeatedly during UI rendering or state updates.
- Respect existing volume and mute settings.
- When multiple events occur simultaneously, allow sounds to overlap where appropriate without excessive combined volume.

## Scope

This is an audio presentation improvement only.

Do not change:

- Game rules or mechanics.
- Game timing or animation duration.
- Backend logic or API contracts.
- Existing audio settings or controls.
- The overall audio architecture.

Only introduce new audio assets if the existing sound library cannot provide a suitable effect. Avoid introducing new dependencies.

## Acceptance criteria

- Laser firing sounds noticeably more powerful than before.
- Laser impacts are clearly distinguishable from laser firing.
- Capturing a flag provides satisfying and recognizable audio feedback.
- Winning produces a distinct and dramatic celebration.
- Simultaneous gameplay events do not produce excessive volume or distortion.
- Existing mute and volume controls continue to work.
- No duplicate sound playback is introduced.
- Existing gameplay behaviour remains unchanged.

## Implementation guidance

Inspect the existing audio implementation and available assets before proposing changes.

Exact sound selection, gain levels, layering and playback prioritization are implementation decisions delegated to the developer. Use reasonable defaults and tune them through manual playtesting.

Do not require product decisions for individual volume levels, sound asset selection or other minor audio-design details.

# Final scope decisions

This is a small audio-tuning improvement, not an audio-system redesign.

1. **Event playback:** At most once per event. Preserve all existing cooldowns, voice limits, suppression rules and fallback behaviour.

2. **Victory:** Increase the perceived impact of the existing victory sound. Do not introduce interruption, ducking, new playback priorities or suppression policies. Preserve existing recipient behaviour.

3. **Verification:** Accepted. Compare the same gameplay sequence before and after at the same master volume. Perform one focused tuning pass. Subjective audio perfection is not a requirement.

4. **Scope:** Keep all four events in scope: laser firing, laser impact, flag capture and victory. Victory is the most important improvement. Treat this as one small audio-tuning slice rather than separate capabilities.

5. **Presentation:** Use the existing live-game presentation only.

6. **Assets:** Reuse existing audio assets and infrastructure exclusively. No external sound libraries, new dependencies or multi-asset layering.

## Definition of done

- Laser firing, laser impact and flag capture are noticeably louder than before.
- Victory provides the strongest perceived audio impact.
- Movement and background sounds remain unchanged.
- No audible clipping or distortion is introduced during the verification sequence.
- Existing audio settings and playback behaviour are preserved.

Exact gain values are delegated implementation decisions.

All product decisions are now settled. Minor audio-tuning decisions do not require further clarification.

Proceed to planning.