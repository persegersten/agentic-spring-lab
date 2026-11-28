# Enhanced visual feedback for flag events

## Goal

Make flag-related gameplay events more visually expressive and easier to follow during live gameplay.

The player should immediately understand which flag is currently available, when a flag is captured and when a flag changes ownership.

The improvement should make the game feel more dynamic and polished without changing gameplay mechanics.

## 1. Active flag pulsation

The next available flag on the game board should continuously pulse.

- Use a combination of subtle scaling, glow and brightness.
- The effect should clearly distinguish the active flag from inactive flags.
- The pulsation continues until the flag is captured or is no longer the active flag.
- Only the currently available flag should pulse.

## 2. Flag capture animation

When a flag is captured from the game board:

- Play a short, distinct flash at the captured flag's board position.
- The flash remains visible briefly even though the flag has been removed from the authoritative game state.
- After the capture flash, play a short activation flash on the next available flag, if one exists.
- The newly available flag then transitions into its continuous pulsation.

The capture flash and subsequent activation flash should be visually distinguishable from the ordinary pulsation.

## 3. Flag ownership transfer

When an existing flag changes ownership from one player to another:

- Visually highlight the affected flag in the existing ownership strip.
- Use the previous and new owner's existing player colours to communicate the ownership transition.
- After the animation, the flag displays its new owner normally.

If multiple flags change ownership during the same tick, animate all affected flags concurrently.

This requirement applies only to ownership transfers supported by the existing game mechanics. Do not introduce or restore flag-stealing mechanics.

## 4. Concurrent events

Multiple flag events may occur during the same game tick.

- Allow visual effects to overlap.
- Do not queue animations in a way that delays gameplay.
- Each capture may trigger its corresponding capture and activation effects.
- If a flag is activated and captured during the same tick, both transient effects may be displayed.
- The latest authoritative game state always determines which flag continues pulsating and who owns each flag.
- If no flags remain available, do not display an activation flash or continuous pulsation.

Visual overlap during intense gameplay is acceptable. Perfect visual separation of simultaneous events is not required.

## 5. Implementation constraints

- Reuse existing frontend components, graphical assets and animation infrastructure where practical.
- Prefer lightweight CSS animations or the existing rendering technology.
- Do not introduce new third-party dependencies.
- Animations must be non-blocking and must not affect game timing.
- Preserve existing player colours, flag graphics and ownership information.
- Transient animations must be triggered by gameplay events rather than ordinary component re-rendering.
- Loading or reconnecting to an existing game should display the current flag state without replaying historical capture animations.
- Respect reduced-motion preferences where supported.

## 6. Visual design guidance

Aim for a polished arcade-game aesthetic.

Suggested animation durations:

- Continuous pulsation: 1,200 ms per cycle.
- Capture flash: 300 ms.
- Activation flash: 300 ms.
- Ownership transfer: 600 ms.

These durations are starting values, not strict requirements. Exact colours, easing, opacity, scaling and animation parameters are delegated implementation decisions.

## 7. Out of scope

- Changes to game rules, flag mechanics or scoring.
- Backend or API redesign.
- Changes to game tick duration.
- New screens or layout redesign.
- New sound effects.
- Animation queues that synchronize or block gameplay.
- Additional particle systems or graphical frameworks.

## 8. Acceptance criteria

- The next available flag is immediately recognizable through continuous pulsation.
- Capturing a flag produces a visible flash at its former board position.
- The next available flag flashes and begins pulsating after capture.
- Existing ownership transfers produce visible feedback in the ownership strip.
- Multiple flag events within the same tick are handled without blocking gameplay.
- Continuous pulsation and displayed ownership always reflect the authoritative game state.
- Capturing the final flag does not leave a stale pulsation or activation effect.
- Normal gameplay behaviour remains unchanged.

## 9. Manual verification

Use the existing live-game presentation to demonstrate:

1. An available flag pulsating.
2. A flag being captured and the next flag becoming visually active.
3. A flag changing ownership, if supported by the current game mechanics.
4. Multiple flag events occurring during the same tick.
5. The final available flag being captured.

One focused visual tuning pass is sufficient.

## 10. Planning decisions

The complete feature consists of three independently verifiable slices, in priority order:

1. Active flag pulsation.
2. Capture flash and subsequent activation flash.
3. Ownership transfer animation.

All applicable slices are in scope.

Minor visual and technical implementation decisions are delegated to the developer. No additional product clarification is required for animation parameters, visual styling or acceptable overlap between simultaneous effects.

Proceed to planning.