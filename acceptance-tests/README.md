# End-to-end tests

The tests in this directory use Playwright to exercise user-visible Wreckage
behaviour in a real browser. API contracts and game-engine rules remain covered
by backend integration and unit tests respectively.

Install the dependencies and Chromium once:

```bash
cd acceptance-tests
npm ci
npx playwright install chromium
```

Then run the tests:

```bash
npm test
```

This runs game creation, planning, multiplayer, and lobby-bot scenarios. The bot
scenario verifies that a player can add automated opponents and complete rounds
without opening browsers for them.

Playwright starts the Spring Boot backend with the disposable `in-memory`
and `deterministic-e2e` profiles and starts the Vite development server
automatically. The deterministic board retains the standard board dimensions
and edge-wall fixture while allowing a fixed two-player program to cover a
push, checkpoint score, reload, and final standings. If compatible
servers are already running locally, Playwright reuses them. Set `BASE_URL` to
run the browser against a different frontend URL:

```bash
BASE_URL=http://localhost:4173 npm test
```

## Multiplayer scenarios

Sequential flag-capture fixtures use a separate test-only backend profile and
local ports 18080/5174. Run them from the project root with:

```bash
npm --prefix acceptance-tests test -- --config=sequential-flags.config.mjs --workers=1 --reporter=line
```

They are excluded from the default configuration. See
[manual scenario setup and expected results](../docs/dev/sequential-flags-verification.md).

That configuration also includes the final-flag completion and shared-rank
scenarios. Select just those with `final-flag`; see
[final-round manual verification](../docs/dev/final-flag-verification.md).

Select `match-limit` with that configuration to verify the existing-limit ending
with incomplete, empty, and unequal collections, plus a final capture at the
limit. See [match-limit verification](../docs/dev/match-limit-verification.md).

Ownership recovery has its own process-restart test:

```bash
npm --prefix acceptance-tests test -- --config=flag-recovery.config.mjs --reporter=line
```

It owns disposable servers on ports 18082/5176, uses a temporary file-backed H2
database, and restarts only its own backend process. Stop manual recovery servers
on those ports before running it. See
[crash, reload, reconnect, and restart verification](../docs/dev/flag-recovery-verification.md).

Represent every player with a separate Playwright `BrowserContext` and create
one page in each context. The contexts must be closed in a `finally` block. The
`withPlayerPages` helper in `player-pages.mjs` demonstrates this pattern.
Separate contexts ensure that cookies, local storage and other browser session
state are not shared between players.
## Laser theft scenarios

Run `npm --prefix acceptance-tests test -- --config=sequential-flags.config.mjs laser-theft --workers=1 --reporter=line`
from the project root for slice 1's normal, shielded, blocked, and pit-crash hits.
Includes mixed-age transfers, exact capture expiry, and three-player reset boundaries.
See [manual setup and verification](../docs/dev/laser-theft-verification.md).
