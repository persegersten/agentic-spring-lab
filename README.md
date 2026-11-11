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

## Choose an environment

| Environment | Start | Lifecycle |
|---|---|---|
| 1. Local development | `./scripts/local-dev/start.sh` | Java 25 and npm on the host; Ctrl+C stops both services |
| 2. Local development in Docker | `./scripts/local-dev-docker/start.sh` | Built containers at http://localhost; runs in the background |
| 3. Production Debian in Docker | `./scripts/production-debian-docker/install.sh --domain <hostname>` | Prepared Debian server, Docker and Caddy HTTPS; automatic container restart |

Run these commands from the repository root. Scripts also work by absolute path
from other directories. Each entry point supports `--help`. Shared helpers live
in `scripts/lib/` and are not entry points.

## 1. Local development

Install frontend dependencies once:

```bash
cd frontend
npm ci
cd ..
```

Start both services with the in-memory database:

```bash
./scripts/local-dev/start.sh
```

For manual testing from a single browser, join the created lobby and use the
`Add Bot` button. Each bot occupies a normal player slot and submits its program
automatically throughout the match.

Open the frontend URL printed by Vite (normally <http://localhost:5173>).
The frontend listens on all network interfaces so players on the same network
can use your computer's IP address. Press Ctrl+C to stop both services; if
either service exits, the script stops the other as well.

### Backend only

Start the backend with its default in-memory database:

```bash
./scripts/local-dev/start-backend.sh
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

## 2. Local development in Docker

Build and start the complete application in the background:

```bash
./scripts/local-dev-docker/start.sh
```

Requires a running, accessible Docker Engine and the Compose v2 plugin.
This mode builds source into images; there is no source hot reload. After code
changes, rerun the start command to rebuild and recreate changed services.

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
./scripts/local-dev-docker/status.sh

# Logs
./scripts/local-dev-docker/logs.sh

# Stop
./scripts/local-dev-docker/stop.sh

# Rebuild and restart
./scripts/local-dev-docker/stop.sh
./scripts/local-dev-docker/start.sh
```

Container logs rotate at 10 MB per file, keeping at most seven files per container
(approximately 70 MB). Set `DOCKER_LOG_MAX_SIZE` and `DOCKER_LOG_MAX_FILES` in `.env`
to override these defaults. Oldest files are deleted when the limit is reached;
retention depends on log volume, not days. Changes apply when containers are recreated.

### Troubleshooting: missing Rolldown native binding

If the frontend build fails with `Cannot find native binding` and a missing
`@rolldown/binding-linux-x64-gnu` module, rebuild the images without Docker's
build cache to reinstall dependencies, then start the application:

```bash
docker compose -f compose.yml build --no-cache frontend backend
./scripts/local-dev-docker/start.sh
```

Run these commands from the repository root. The Docker build explicitly includes
optional dependencies because Rolldown distributes its platform bindings that way.
Keep the existing lockfile and use normal cached builds for everyday development.
If the error persists, inspect the npm installation output for skipped or failed
optional dependencies.

## 3. Production Debian in Docker

Production runs on a dedicated Debian Linux server using Docker Compose and
Caddy for HTTPS. Follow [the Linux deployment guide](docs/deployment/linux.md)
to prepare the server, run `scripts/production-debian-docker/install.sh`, and deploy updates with `scripts/production-debian-docker/update.sh`.

The backend listens on container port 8080. Only Caddy publishes host ports
80 and 443. Game data is stored in memory and is lost whenever the backend
restarts or is replaced during an update.
