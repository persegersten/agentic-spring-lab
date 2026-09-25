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

This runs both game creation and planning scenarios. The planning scenario checks
private five-card hands, ordering a hand, locking a program, and shared readiness.

Run the single-browser scenario separately:

```bash
npm run test:headless
```

This configuration starts the backend with both `in-memory` and
`headless-players`, then verifies that the first player can complete rounds while
all remaining players join and accept their dealt card order without browsers.

Playwright starts the Spring Boot backend with the disposable `in-memory`
profile and starts the Vite development server automatically. If compatible
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
