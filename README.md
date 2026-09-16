Wreckage

Web-based turn-based multiplayer vehicle combat game.

Current scope:
- Create game
- Add players
- Read game state

No combat.
No movement.
No authentication.

## Run the backend

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
