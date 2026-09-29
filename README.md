Wreckage

Web-based turn-based multiplayer vehicle combat game.

Current scope:
- Create game
- Add players
- Read game state
- Program three private movement cards per player
- Resolve simultaneous movement and replay each round in the browser

There is no account system; private player views use the secret token returned
when that player joins a game.

## Run backend and frontend

Install frontend dependencies once:

```bash
cd frontend
npm ci
cd ..
```

Start both services with one database profile argument:

```bash
./start.sh in-memory
```

For manual testing from a single browser, add the `headless-players` Spring
profile:

```bash
./start.sh in-memory headless-players
```

The first player joining a game is controlled by the browser. The profile
immediately fills every remaining slot up to `maxPlayers` with headless players,
starts the game, and locks each headless player's cards in the order they were
dealt. The first player remains the only participant requiring input. Omit the
optional profile for normal multiplayer games.

For PostgreSQL, start the database first:

```bash
docker compose up -d postgres
./start.sh postgres
```

Open the frontend URL printed by Vite (normally <http://localhost:5173>).
The frontend listens on all network interfaces so players on the same network
can use your computer's IP address. Press Ctrl+C to stop both services; if
either service exits, the script stops the other as well. PostgreSQL remains
running independently.

## Heroku

Heroku builds the root `Dockerfile` using `heroku.yml`. The build installs
Node.js 24, npm and frontend dependencies (including Vite), builds the frontend,
and packages it inside the Spring Boot JAR using Java 25 and the Maven wrapper.
The runtime image contains Java 25 and the JAR; Spring Boot serves both the
frontend and API on Heroku's `PORT` with `in-memory,headless-players` enabled.
No Vite development server or Maven runs in production.

Set the existing Heroku app to the container stack once before deploying:

```bash
heroku stack:set container --app YOUR_APP_NAME
```

Then deploy this repository using your normal Heroku Git/GitHub workflow.
Heroku uses the Dockerfile's `CMD`; no buildpacks or `Procfile` are required.
See [Heroku's container build documentation](https://devcenter.heroku.com/articles/build-docker-images-heroku-yml).

To build and run the same image locally:

```bash
docker build --platform linux/amd64 -t wreckage .
docker run --rm -p 8080:8080 -e PORT=8080 wreckage
```

Open <http://localhost:8080>. In-memory game data is lost whenever the container
or dyno restarts. Local development still uses `./start.sh in-memory headless-players`
with Vite and the backend as separate processes.

## Run the backend only

The startup script requires a database profile. To run with PostgreSQL, first
start the database and then the backend:

```bash
docker compose up -d postgres
./start-server.sh postgres
```

The PostgreSQL connection can be configured with the `DB_URL`, `DB_USERNAME`,
and `DB_PASSWORD` environment variables. To run without an external database,
use the `in-memory` profile:

```bash
./start-server.sh in-memory
```

The backend-only equivalent for single-browser testing is:

```bash
./start-server.sh in-memory headless-players
```

The `in-memory` profile uses an H2 database that is discarded when the backend
process exits. Calling the script without either `postgres` or `in-memory`
results in an error.

With the backend running, interactive API documentation is available in
Swagger UI at <http://localhost:8080/swagger-ui.html>. The generated OpenAPI
document is available as JSON at <http://localhost:8080/v3/api-docs>.

Playwright end-to-end tests live in `acceptance-tests/` and exercise
user-visible behaviour through Chromium. Their configuration starts both the
backend and frontend automatically. See `acceptance-tests/README.md` for setup
and usage.
