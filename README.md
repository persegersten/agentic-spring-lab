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
