# Laser Flag Theft — Slice Plan

Input brief: [docs/dev/idea4.md](../dev/idea4.md).

Build in the order below. Each slice is one independently reviewable, revertible change with relevant automated tests and rules documentation. Include the backend, persistence, API, and frontend work needed for that slice's observable behavior together. Do not split these into technical-layer phases.

Acquisition by capture or theft on tick T permits theft only during T+1 through T+5. Every theft resets T. Resolve each Laser hit as: shield check, transfer eligible flags, existing displacement and turning. Blocked displacement or a subsequent crash cannot undo a transfer. Preserve existing behavior outside these explicit changes, including targeting, sequential flag availability, and match completion.

Each slice must supply reproducible local scenarios and instructions for opening their matches in independent browser sessions, with stated ownership, acquisition ticks, positions, and queued commands. Manual verification must not depend on random maps or chance shots. Reuse existing local test-fixture infrastructure where suitable; no production scenario endpoints are requested.

## 1. Watch one eligible flag change owners on a Laser hit

- **What changes:** An unshielded Laser hit transfers an eligible flag before its existing displacement and turning effects; both clients highlight the changed ownership-strip entry and show its new owner during playback.
- **How to verify manually:** Open a supplied two-player local scenario in separate browser sessions, with Alice owning Flag 1 acquired on tick 10 and Bob's Laser due to hit her on tick 11. Advance the tick. Confirm both clients highlight Flag 1 changing from Alice to Bob, and owned counts change from 1/0 to 0/1. Confirm the captured board marker stays hidden and Flag 2 remains the next available flag. Observe the existing displacement and turn after the transfer. Repeat the scenario with Alice shielded: ownership and Laser hit effects must remain unchanged. Repeat with displacement blocked by a wall, then with displacement sending Alice into a pit: Bob must retain the transferred flag in both cases, and existing crash/turn rules still apply.
- **What it deliberately does not do yet:** Demonstrate mixed-age multi-flag transfers, repeated theft chains, or recovery across reloads and backend restarts; those follow in slices 2 and 3.

## 2. Transfer all eligible flags with exact expiry and reset boundaries

- **What changes:** A hit atomically transfers every eligible flag while leaving ineligible flags with the target; each transferred flag receives a fresh five-tick window excluding its acquisition tick.
- **How to verify manually:** Open a supplied scenario at tick 15 with the target owning Flags 1 and 2 acquired on ticks 10 and 14, plus Flag 3 acquired on tick 9. Fire the queued Laser: Flags 1 and 2 must transfer together while Flag 3 stays with the target, with both changed strip entries highlighted and counts updated on both clients. In separate copies of a flag acquired on tick 10, fire on ticks 10, 11, 15, and 16: only ticks 11 and 15 may steal it. Open a three-player scenario in which Bob steals Alice's flag on tick 15 and Carol hits Bob later in that same tick: Carol must not steal the newly acquired flag. Repeat Carol's hit on tick 16, tick 20, and tick 21 in separate copies: the first two must transfer it and the last must not. After each successful theft, confirm the same reset rule applies, ownership remains exclusive, and no captured board marker returns or flag becomes relocked.
- **What it deliberately does not do yet:** Verify saved-state restoration across reloads, crashes followed by respawn, or backend restart.

## 3. Preserve transferred ownership and vulnerability through recovery

- **What changes:** Reconnecting clients and a restarted backend restore the same owners and acquisition ticks, while crashes and respawns leave the theft window running on match ticks.
- **How to verify manually:** Use a supplied local scenario with a flag stolen on tick 15. Reload both browser sessions and confirm the same owner and counts, without replaying an old transfer highlight as a new theft. Restart the local backend using the same retained development database and reopen both sessions. In separate restored copies, hit the owner on tick 16, tick 20, and tick 21: theft must succeed only on 16 and 20. Repeat after the owner's crash and respawn, confirming neither resets nor pauses the window. Reload a finished match containing a prior transfer and confirm final ownership and standings remain unchanged. Use reproducible saved-state copies so checking one boundary does not reset the clock for another.
- **What it deliberately does not do yet:** Define conversion rules for matches saved before acquisition timing existed, or alter existing match-ending behavior.

## The riskiest slice

Slice 1 is the riskiest and belongs first. It crosses authoritative hit resolution, ownership mutation, event ordering, and client playback. A target crashing after transfer or a client showing final snapshot ownership too early can make a correct-looking ordinary shot hide an incorrect rule. The shield, blocked-displacement, and pit cases expose those failures before building the broader timing and recovery scenarios.

## What is not in any slice

All explicit requirements in the brief are assigned above. Relevant automated tests accompany each slice; persistence and API support are included wherever needed for the demonstrated behavior, with restart verification in slice 3.

Flying-flag animation, a new event feed, and sound are explicitly excluded by the brief. New vulnerability countdown UI, targeting controls, bot strategy changes, map balancing, and extended match endings are not requested. Retain the existing Laser displacement and turning rules after theft; do not introduce new weapon effects or scoring rules.

The brief does not specify how to assign acquisition timing to flags in matches saved before this feature existed. That compatibility policy remains outside these slices and must be decided before releasing into an environment containing such matches; do not invent capture times during implementation. The recovery scenarios above begin with matches created under the new rules.
