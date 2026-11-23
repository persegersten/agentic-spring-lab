# Change the Goal of the Game – Unique Flag Capture

## Objective

Change the current game objective from requiring every player to capture all flags in order to a competitive flag collection system.

### New Rules

1. **One flag per number:** There is exactly one flag for each flag number on the game board.
2. **Sequential unlocking:** Flags must be captured in numerical order (Flag 1 → Flag 2 → Flag 3, etc.).
3. **Exclusive ownership:** The first player to reach and capture an available flag becomes its permanent owner. The flag disappears from the board and cannot be captured by another player.
4. **Immediate unlocking:** As soon as a flag is captured, the next numbered flag becomes available for capture, even within the same round.
5. **Persistent ownership:** Once captured, a flag remains owned by that player for the rest of the match, including after crashes or respawns.
6. **No transfers:** Flags cannot be stolen, dropped, transferred, or recaptured.
7. **Hard match limit:** Keep the existing maximum number of game rounds. The match ends when either all flags have been captured or the hard match limit is reached.

## Winning Conditions

- When all flags have been captured, the match ends at the end of that round.
- If the hard match limit is reached first, the match ends at that limit.
- Players are ranked by the number of flags they own.
- The player with the most flags wins.
- Players with equal numbers of flags share the same rank.

## User Interface

- Display one persistent flag strip showing each flag number and its current owner (or *Available* / *Locked* status).
- Clearly indicate which player owns each captured flag.
- Highlight the next available flag on the game board.
- Remove a flag from the board immediately after it is captured.

## Implementation Requirements

- Preserve flag ownership across crashes and respawns.
- Ensure that only the next sequential flag can be captured.
- Preserve the existing hard match limit.
- Update backend game logic, persistence, API responses, frontend UI, and automated tests to reflect the new rules.

## Game examples

- B is already standing on Flag 2 when A captures Flag 1. B needs to leave and return to capture the flag. Capturing requires entering an available flag’s cell, through voluntary or forced movement.

- 2. One atomic push can move robots onto Flags 1 and 2 together. Flag 2 was locked before the push. Awarding it depends on whether availability is evaluated before the movement or after processing Flag 1. Robot iteration order must not choose the result. Collect new cell entries for each successful atomic movement step, then evaluate captures in flag-number order. Both flags can be awarded within that step. Robots already standing on locked flags remain ineligible.