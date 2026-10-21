# Linux deployment

Wreckage's production stack uses Docker Compose, Caddy, nginx, Spring Boot, and PostgreSQL on Ubuntu 22.04/24.04/26.04 or Debian 12/13. Java and Node versions are controlled by the Dockerfile, not host packages.

## Install

Point the domain's A/AAAA records at the server, allow inbound TCP 80 and 443, then run:

```bash
git clone <repository-url>
cd <repository-directory>
./install.sh --domain wreckage.example.com
```

The installer creates a mode-0600 `.env`, installs Docker from Docker's official apt repository when needed, creates named volumes, builds revision-tagged images, and succeeds only after container, proxy, and public HTTPS checks pass. Re-running it preserves `.env` and volumes.

## Application updates

Run `./update.sh`. It refuses local tracked or untracked changes, fetches the configured upstream, requires a fast-forward, builds images before replacing containers, recreates only backend/frontend, and verifies HTTPS. With no new commit it changes nothing.

Successful state is stored in `.wreckage/deployment.env` only after verification:

```bash
sed -n '1,20p' .wreckage/deployment.env
docker compose --env-file .env -f compose.prod.yml ps
```

The metadata reports current/previous revisions, UTC deployment time, health, and images. On failure the previous healthy record remains authoritative; compare `git rev-parse HEAD` with `DEPLOYED_REVISION` before retrying.

New Flyway migrations require a verified custom-format dump under `.wreckage/backups` before the new backend starts. Changed, removed, or renamed historical migrations are rejected. Flyway clean, repair, baseline, schema downgrade, and automatic database restore are never run.

## Logs and recovery

```bash
docker compose --env-file .env -f compose.prod.yml logs --tail=200
docker compose --env-file .env -f compose.prod.yml logs -f backend
```

Correct the failed stage and rerun `./update.sh`. Existing containers, `.env`, PostgreSQL data, Caddy certificates, previous images, and metadata remain. Never use `docker compose down --volumes`.

When prior tagged images remain, deliberate application-image rollback is available:

```bash
./update.sh --rollback
```

It requires confirmation and replaces only backend/frontend. It is not database rollback. An older application may be incompatible after schema migration; review migrations and the verified backup first. Database recovery is manual and should be rehearsed on disposable PostgreSQL.

## Manual database backup

```bash
mkdir -p .wreckage/backups
docker compose --env-file .env -f compose.prod.yml exec -T postgres \
  pg_dump -U wreckage -d wreckage -Fc > .wreckage/backups/wreckage.dump
test -s .wreckage/backups/wreckage.dump
docker run --rm -i postgres:17 pg_restore --list < .wreckage/backups/wreckage.dump >/dev/null
```

Every command must succeed. Adjust names to `.env` and copy verified backups off-host; volumes are not backups.

## Platform maintenance

Application and platform updates are separate. `./update.sh --platform` reports Docker Engine, Compose, configured Caddy, and supported apt candidates. It changes only listed Docker packages from Docker's official repository and/or the supported pinned Caddy image, after interactive confirmation. It never performs a full OS, PostgreSQL major, or host Java/Node upgrade. Unavailable repositories or images produce no corresponding change.

Caddy recreation may briefly interrupt connections, but `wreckage-caddy-data` and `wreckage-caddy-config` preserve certificates, ACME state, renewal, and configuration. Its `.env` value changes only after health and HTTPS pass. Docker package maintenance may restart the daemon and briefly interrupt all containers.

Major PostgreSQL/OS upgrades, Java/Node major changes, restores, schema rollback, firewall/DNS changes, and image pruning are intentionally manual. Dockerfile/Compose changes deploy through normal `./update.sh`.

## Persistent resources

- `wreckage-postgres-data`: database
- `wreckage-caddy-data`: certificates and ACME state
- `wreckage-caddy-config`: Caddy runtime configuration
- `.env`: domain and secrets
- `.wreckage/deployment.env`: last verified version
- `.wreckage/backups`: pre-migration dumps

Normal install, update, rollback, restart, and platform maintenance never delete these resources.
