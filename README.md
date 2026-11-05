## Wreckage ##

Web-based turn-based multiplayer programming and checkpoint game.

See [docs/deployment/linux.md](docs/deployment/linux.md) for production Linux installation and safe updates.

Current scope:
- Create game
- Add players
- Read game state
- Build a private ordered program from the complete v2 command set
- Resolve simultaneous movement and replay each round in the browser
- Use the LASER programming card to displace vehicles and a shield once per match

The current game has no selectable loadouts, rockets, ammunition, weapon damage,
or scheduled combat actions. Legacy database columns remain until a separate
physical-schema cleanup.

There is no account system; private player views use the secret token returned
when that player joins a game.

## Local Docker alpha deployment

Build and start the complete application in the background:

```bash
./start-with-docker.sh
```

Open <http://localhost>. Host port 80 maps to the non-root nginx container on
its unprivileged port 8080, which forwards the existing `/games` API requests
to an internal backend container. The backend is not published on a host port,
and game data uses the existing in-memory database and is lost when the backend
is replaced.

For temporary external alpha testing, manually forward router TCP port 80 to
this laptop's TCP port 80, then open `http://<PUBLIC-IP>`. This setup uses plain
HTTP and is not intended for production hosting.

Useful commands:

```bash
# Status
docker compose ps

# Logs
docker compose logs -f

# Stop
./stop-with-docker.sh

# Rebuild and restart
./stop-with-docker.sh
./start-with-docker.sh
```

Container logs rotate at 10 MB per file, keeping at most seven files per container
(approximately 70 MB). Set `DOCKER_LOG_MAX_SIZE` and `DOCKER_LOG_MAX_FILES` in `.env`
to override these defaults. Oldest files are deleted when the limit is reached;
retention depends on log volume, not days. Changes apply when containers are recreated.

## Run backend and frontend

Install frontend dependencies once:

```bash
cd frontend
npm ci
cd ..
```

Start both services with the in-memory database:

```bash
./start.sh
```

For manual testing from a single browser, join the created lobby and use the
`Add Bot` button. Each bot occupies a normal player slot and submits its program
automatically throughout the match.

Open the frontend URL printed by Vite (normally <http://localhost:5173>).
The frontend listens on all network interfaces so players on the same network
can use your computer's IP address. Press Ctrl+C to stop both services; if
either service exits, the script stops the other as well.

## Dedicated Debian server

Production runs on a dedicated Debian Linux server using Docker Compose and
Caddy for HTTPS. Follow [the Linux deployment guide](docs/deployment/linux.md)
to prepare the server, run `install.sh`, and deploy updates with `update.sh`.

The backend listens on container port 8080. Only Caddy publishes host ports
80 and 443. Game data is stored in memory and is lost whenever the backend
restarts or is replaced during an update.

## Run the backend only

Start the backend with its default in-memory database:

```bash
./start-server.sh
```

Each backend process accepts at most 10 waiting or running games by default.
Override the runtime limit with the Spring property
`wreckage.games.max-concurrent` (for example,
`--wreckage.games.max-concurrent=20`). Finished games do not count toward the
limit.

The `in-memory` profile uses an H2 database that is discarded when the backend
process exits. It is the only supported database; no external database is required.

With the backend running, interactive API documentation is available in
Swagger UI at <http://localhost:8080/swagger-ui.html>. The generated OpenAPI
document is available as JSON at <http://localhost:8080/v3/api-docs>.

Playwright end-to-end tests live in `acceptance-tests/` and exercise
user-visible behaviour through Chromium. Their configuration starts both the
backend and frontend automatically. See `acceptance-tests/README.md` for setup
and usage.
