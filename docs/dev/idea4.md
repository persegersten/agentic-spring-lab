# Change effect of lazer beam – Flag Capture Effect

## Objective
Make the game more competitive by making it possible to steal an opponents flag.

## New Rules
1. A bot can steal another bot's flag up to five ticks from when it was captured.
2. A bot steals a flag owned by a bot by hitting it with a lazer.
3. Ownership is transferred directly to the shooter.
4. A bot with an active shield is not affected by a lazer beam, and thus keeps his flags.
5. The five tick is excluding the tick when it was captured.

## Implementation Requirements

- It shoud be visible on the screen that the flag is transferred from the hit bot to the bot firing the lazer. Both visual and with a sound effect.
- Update backend game logic, persistence, API responses, frontend UI, and automated tests to reflect the new rules.