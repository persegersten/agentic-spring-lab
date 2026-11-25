# Change effect of lazer beam – Flag Capture Effect

## Objective
Make the game more competitive by making it possible to steal an opponents flag.

## New Rules
1. A bot can steal another bot's flag up to five ticks from when it was captured.
2. All eligable flags is transferred by a hit.
3. Transfer ownership atomically, changing both players’ flag counts. Keep every transferred flag captured, its board marker hidden, and the next available flag unchanged.
2. A bot steals a flag owned by a bot by hitting it with a lazer.
3. Ownership is transferred directly to the shooter.
4. A bot with an active shield is not affected by a lazer beam, and thus keeps his flags.
5. The five tick is excluding the tick when it was captured.
6. Up to five ticks from when it was captured — capture or theft on tick T makes a flag stealable during T+1 through T+5. Every theft resets T.
7. The lazer ba shall still displace and randomly turn the hit bot.
8. Preserve match completion. The final flag cannot be stolen in continuous play because its capture tick is excluded and no subsequent tick begins.

## Implementation Requirements

- It shoud be visible on the screen that the flag is transferred from the hit bot to the bot firing the lazer.
- Update backend game logic, persistence, API responses, frontend UI, and automated tests to reflect the new rules.