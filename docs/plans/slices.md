# Flag visual feedback — slice plan

Source: [Enhanced visual feedback for flag events](../dev/idea6.md).

Build the three slices in the brief's priority order. Each is an independently
observable, verifiable change small enough to revert as one commit. This plan
does not authorize creating commits.

## Shared constraints

Use the existing live-game board, ownership strip, flag graphics, player colours
and frontend animation infrastructure. Inspect current components, event payloads
and relevant flag rules before implementation. No new dependencies are needed.
Animations never delay commands or alter game timing.

Transient effects describe gameplay events; continuous pulsation derives only
from current authoritative availability. Animation completion cannot reactivate
a captured flag. Existing ownership presentation settles to authoritative state.
Trigger transients using stable gameplay-event identities, never ordinary renders;
loading, reconnecting and duplicate snapshots must not replay historical effects.

Respect reduced motion using a static active-flag highlight and restrained,
non-scaling transient highlights. The suggested 1,200 ms pulse, 300 ms capture,
300 ms activation and 600 ms transfer durations are starting values. Exact styling
is delegated to implementation. One focused visual tuning pass is sufficient.

## Ordered slices

1. **Active flag pulsation**
   - **What changes:** Only the currently available board flag pulses with subtle scaling, glow and brightness; reduced motion uses a static highlight.
   - **How to verify manually:** Open a running local game in two separate browser sessions. Identify the pulsing flag, program movement to capture it, and confirm only the newly available flag pulses. Reload both sessions and confirm current availability appears immediately. Capture the final flag and confirm no pulse remains; also inspect a loaded all-captured state. Enable the browser's reduced-motion emulation and repeat the availability check, confirming a static highlight replaces pulsation. Confirm flag graphics, player colours and board layout remain intact. Run relevant frontend checks and the production build, recording actual results.
   - **What it deliberately does not do yet:** No new capture, activation or ownership-transfer animations; existing feedback remains in place.

2. **Capture flash followed by activation feedback**
   - **What changes:** A capture leaves a distinct flash at its former board position, followed by a successor activation flash when applicable, while current authoritative availability determines persistent pulsation.
   - **How to verify manually:** Capture a flag while watching both browser sessions. Confirm its flash survives removal of the board flag, then observe the successor's distinct activation flash. Exercise multiple captures within one tick, including a successor activated and captured in that tick; confirm transient effects can overlap without delaying commands or leaving a captured flag pulsing. Capture the final flag and confirm no activation or pulsation remains when no flags are available. Reload or reconnect after a capture and confirm old flashes do not replay; ordinary state refreshes must not restart them. Repeat with reduced motion. Run relevant presentation and browser checks and the frontend build, recording actual results and any scenarios that could not be exercised.
   - **What it deliberately does not do yet:** No enhancement of ownership-transfer feedback or changes to gameplay/event contracts.

   Extend the existing capture presentation rather than adding duplicate effects.
   Retain the event's board position for its transient lifetime independently of
   current flag rendering. Preserve the brief's permission for overlapping
   historical activation/capture flashes, subject to its rule that no activation
   flash is displayed when no flags remain available. Capture-to-activation
   sequencing is local to that effect pair, never a gameplay-blocking queue.

3. **Ownership transfer in the existing strip**
   - **What changes:** Existing supported ownership transfers highlight affected strip entries using the previous and new owners' player colours; different flags animate concurrently and settle to authoritative ownership.
   - **How to verify manually:** Use an ownership transfer supported by the current mechanics, watching both browser sessions. Confirm the affected strip entry transitions between the previous and new owners' colours and then displays the new owner normally. Transfer multiple flags in one tick and confirm concurrent feedback. Exercise repeated transfers of one flag and confirm no obsolete owner transition persists. Reload or reconnect and confirm current ownership appears without replaying old highlights. Repeat with reduced motion. Run relevant ownership/presentation and browser checks and the frontend build, recording actual results and any unavailable scenarios.
   - **What it deliberately does not do yet:** No new or restored transfer mechanics, ownership-strip layout changes, or unrelated visual effects.

   Inspect and reuse existing FLAG_TRANSFERRED presentation and strip markers
   where supported. If the current mechanics do not support transfers, document
   this slice as inapplicable rather than implementing mechanics or inferring
   transfers from ordinary snapshot differences. For repeated transfers of one
   flag, let the latest presented transfer replace an older transition; distinct
   flags remain concurrent.

## The riskiest slice

Slice 2 is most likely to overrun: removed flags, successive captures in one tick,
activation flashes, existing effect lifetimes and reconnect suppression all meet
at the same lifecycle boundary. Test those cases early within slice 2.

Keep it second rather than moving it ahead of slice 1. The brief explicitly sets
the priority order, and slice 1 provides immediate feedback while establishing
the authoritative availability handling that slice 2 must preserve.

## What is not in any slice

No applicable brief requirement is deferred. Ownership-transfer feedback is
conditional on mechanics already supporting it, exactly as specified in the brief.

Excluded: changes to rules, scoring, flag mechanics, backend/API contracts, game
tick or animation-driven gameplay timing; new screens or layout redesign; new
sound effects; dependencies, particle systems or graphics frameworks; blocking
animation queues; perfect separation of simultaneous effects; additional visual
tuning passes. No deployment, implementation or commits are part of this planning
task.
