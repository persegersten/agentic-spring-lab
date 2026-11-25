# Change effect of lazer beam – Flag Capture Effect

## Objective
Make the game more competitive by making it possible to steal an opponents flag.

## New Rules
1. When a bot gets ownership of a flag it is stealable for 5 ticks. This appplies both by moving into a flag or aquire flag by stealing. Acquisition by capture or theft on T permits theft during T+1 through T+5. Every theft resets T.
2. If a bot gets hit by a lazer all stealable flags are transferred to the shooter.
3. A bot with an active shield is protected from lazer, thus cannot loose stealable flags.
4. Resolve a Laser hit in this order: check the target's shield, transfer all eligible flags to the shooter, then resolve the existing Laser displacement and turning behavior. An active shield prevents the transfer and the existing Laser hit effects. A blocked displacement or subsequent crash must not undo the transfer.

## Implementation Requirements

- It shoud be visible on the screen that the flag is transferred from the hit bot to the bot firing the lazer. Highlight transferred entries in the existing ownership strip and show their new owners during playback. Exclude flying-flag animation, a new event feed, and sound.
- Update backend game logic, persistence, API responses, frontend UI, and automated tests to reflect the new rules.
