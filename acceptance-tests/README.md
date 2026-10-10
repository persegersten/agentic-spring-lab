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

Represent every player with a separate Playwright `BrowserContext` and create
one page in each context. The contexts must be closed in a `finally` block. The
`withPlayerPages` helper in `player-pages.mjs` demonstrates this pattern.
Separate contexts ensure that cookies, local storage and other browser session
state are not shared between players.
