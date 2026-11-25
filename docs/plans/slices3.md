# Unique Flag Capture — Slice Plan

Input brief: [docs/dev/idea3.md](../dev/idea3.md).

Build in the order below. Each slice is a single reviewable, revertible commit with its relevant automated tests. Include the backend, persistence, API, UI, and rules-documentation changes needed for that slice's behavior; those are not separate delivery phases. Intermediate slices are development checkpoints, not releases of the complete ruleset.

Retain the existing maps, flag count, and hard match limit. The brief requests no changes to them. Manual scenarios below require reproducible local match setups with the stated positions and commands; provide those setups as part of the relevant slice so verification does not depend on chance.

## 1. Show an exclusive claim of Flag 1

- **What changes:** A player entering available Flag 1 claims it exclusively; both clients show its owner in the persistent strip, remove its board marker, and highlight Flag 2 as available while later flags remain locked.
- **How to verify manually:** Open a local two-player match in separate browser sessions with A one move from Flag 1. Move A onto it, then move B onto its former cell. Confirm both sessions show A as owner and B gains no flag. Move across a locked later flag and confirm it awards nothing.
- **What it deliberately does not do yet:** Capture later flags, restore ownership after reload, or replace match-ending behavior.

## 2. Resolve sequential captures for every movement source

- **What changes:** New cell entries from each successful atomic movement step are evaluated in flag-number order, allowing immediate sequential captures through voluntary movement, pushes, Laser displacement, and conveyors. Existing occupants do not capture newly unlocked flags.
- **How to verify manually:** Open a scenario with B already on Flag 2 and A one move from Flag 1. Capture Flag 1; confirm B receives nothing, then leave and re-enter Flag 2 to claim it. Open a push-chain scenario that moves robots onto Flags 1 and 2 together; execute the push and confirm both flags are awarded to their respective entrants. Repeat using a fixture with reversed robot storage order and confirm identical ownership. Repeat with a blocking wall and confirm no movement or captures. Use a Laser and then a conveyor to move a robot onto the next available flag and confirm each awards it. Enter an already captured flag's cell and confirm its owner never changes.
- **What it deliberately does not do yet:** Restore saved ownership or change when matches end.

## 3. Preserve ownership through crashes and saved-state recovery

- **What changes:** Captured ownership and sequential availability survive crashes, respawns, reconnects, and restoration of persisted game state; all clients reconstruct the same strip and board markers.
- **How to verify manually:** Capture a flag, drive its owner into a pit, and advance until respawn. Confirm the flag stays owned throughout. Reload both browser sessions and confirm ownership and the available flag agree. Restart the local backend using the same development database, reopen the match, and confirm the same state is restored. Have another player enter the captured flag's former cell and confirm it cannot be recaptured.
- **What it deliberately does not do yet:** Replace victory detection or final ranking.

## 4. Finish the round after the final capture

- **What changes:** Capturing the last flag allows the remaining round to resolve before the match ends; final standings rank players solely by owned-flag count and give equal counts a shared rank.
- **How to verify manually:** Open a scenario with only the final flag unclaimed and movement or board effects scheduled after its capture in the same round. Capture it and observe the remaining actions before results appear. Confirm no next round begins. Repeat with equal flag counts but different crash histories or distances to the former flags; confirm the players share rank. Repeat with unequal counts and confirm the higher flag count wins.
- **What it deliberately does not do yet:** Complete the alternate ending path where the round limit is reached with flags remaining.

## 5. Finish incomplete collections at the existing limit

- **What changes:** The existing hard match limit ends a match even with flags remaining, using the same ownership-only ranking and shared ties as final-flag completion.
- **How to verify manually:** Open a scenario at the start of the existing final permitted round with flags still unclaimed. Complete that round and confirm results appear without another round starting; uncaptured flags remain unowned. Repeat with zero captures and confirm every player shares the same rank. Repeat with unequal counts and confirm the highest count wins. Capture the last flag during the final permitted round and confirm the match completes once after that round.
- **What it deliberately does not do yet:** Scale flag counts, rebalance maps, or add early completion for mathematically unbeatable leads.

## The riskiest slice

Slice 2 is the riskiest because atomic push entries must unlock and award flags without depending on robot iteration order, while separate commands and board movements must retain their authoritative execution order. It is likely to expose capture hooks in several movement paths.

Keep it second: slice 1 supplies the smallest visible ownership path needed to inspect the more complex interactions. Do not postpone it behind persistence or final-results work.

## What is not in any slice

All explicit requirements in the brief are assigned above. Persistence, API responses, frontend presentation, automated tests, and relevant updates to docs/game-rules.md accompany the behavior they support.

The brief does not specify converting matches already underway under the previous rules. That compatibility policy remains unassigned and must be decided before releasing the change into an environment with existing matches. Flag-count scaling, map redesign, removal of unrelated internal scoring, and additional victory conditions are outside this plan because the brief does not request them.
