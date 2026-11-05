# Secure Linux deployment

Wreckage production targets Debian 13 (Trixie), Docker Engine, and Docker
Compose v2. The administrator account is `admin`. The separate host account
`wreckage` is deliberately not used to run Docker or deployment scripts and
must have no sudo access, Docker-group membership, socket access, production
secrets, or write access to the checkout.

The application checkout is `/opt/wreckage`. Production configuration is
root-owned under `/etc/wreckage`; it is never copied into Git. Backend and
frontend run as non-root users inside their containers. Caddy is the only
service publishing host ports (TCP 80 and 443). Backend port 8080 and frontend port 8080 remain on Docker networks.
The only database is H2 in memory. All game data is lost when the backend
restarts, is updated, or is rolled back.

## Manual server preparation

Perform these steps while logged in as `admin` with sudo. They are separate
from application installation:

```bash
sudo install -d -o admin -g admin -m 0755 /opt/wreckage
git clone <repository-url> /opt/wreckage
sudo chown -R admin:admin /opt/wreckage
sudo install -d -o root -g root -m 0700 /etc/wreckage

# Optional restricted account; do not add it to sudo or docker.
id wreckage >/dev/null 2>&1 || sudo useradd --system --create-home wreckage
```

Point the A/AAAA records for `wreckage.segersten.eu` at the server and allow
inbound TCP 80 and 443. Do not add `wreckage` to the `docker` group. The
checkout must be writable by `admin` for safe fast-forward updates but not by
`wreckage`.

The installer reuses a working Docker Engine and Compose v2 installation. If
they are absent, it configures Docker's official Debian repository and installs
the explicit Docker CE packages. It does not install host Java or Node.

## Protected configuration

On first installation, the installer creates `/etc/wreckage/production.env`
(root:root, mode 0600) inside `/etc/wreckage` (root:root, mode 0700).
Existing configuration is reused. To provision it manually, use:

```dotenv
DOMAIN=wreckage.segersten.eu
CADDY_IMAGE=caddy:2.10.2-alpine
WRECKAGE_LOG_MAX_SIZE=10m
WRECKAGE_LOG_MAX_FILES=5
```

Production always selects `in-memory`. No database credentials or database
server are required. Existing database-related environment keys are ignored.

## Install

Run the script as `admin`, not with `sudo` in front of the script:

```bash
cd /opt/wreckage
./install.sh --domain wreckage.segersten.eu
```

The script uses sudo only for system packages, protected configuration, service
management, and Docker. It validates the Compose model before deployment,
creates the two Caddy named volumes idempotently, builds revision-tagged images,
starts the stack, and verifies container health, internal proxy routing, and
public HTTPS. Repeating installation preserves configuration and Caddy state.
Backend recreation discards game data. It never runs `docker compose down --volumes` or a
destructive Flyway operation.

## Update and rollback

Run application updates as `admin`:

```bash
cd /opt/wreckage
./update.sh
```

Updates refuse tracked, staged, or untracked local changes; fetch the configured
upstream; require a fast-forward from the recorded healthy revision; build
before replacing containers; and report the deployed revision after health and
HTTPS verification. Every backend replacement starts an empty database.
Caddy named volumes are never replaced.

Application-image rollback remains explicit and interactive:

```bash
./update.sh --rollback
```

Rollback starts an empty in-memory database. Only roll back to revisions
that support in-memory production deployment. `./update.sh --platform` remains a
separate, interactive Docker/Caddy maintenance operation; it does not perform
automatic OS upgrades.

## Status and logs

As `admin`, follow production logs in real time:

```bash
cd /opt/wreckage
./logs.sh                  # All services
./logs.sh backend          # Backend only
./logs.sh frontend caddy   # Selected services
```

The script uses sudo for production configuration and Docker access. It shows
the last 200 lines per service with timestamps, then follows new output.
Press Ctrl+C to stop viewing logs; the services keep running.
Run `./logs.sh --help` for usage. The script also works from other directories
when invoked using its absolute path.

Every production service uses Docker's `local` logging driver. Docker rotates a
container's logs when the active file reaches 10 MB by default, keeps at most
five files, compresses rotated files, and automatically removes the oldest file
after that limit is exceeded. This retention policy is based on size, not time;
there is no daily rotation or fixed number of retention days.

To customize the limits, edit the protected configuration as `admin`:

```bash
sudoedit /etc/wreckage/production.env
```

Set a positive Docker size with a `k`, `m`, or `g` suffix and a positive integer
file count, for example:

```dotenv
WRECKAGE_LOG_MAX_SIZE=25m
WRECKAGE_LOG_MAX_FILES=8
```

Omitted settings use `10m` and `5`. Empty, zero, negative, fractional, or
unsupported values cause installation or update to stop with a clear error.
Changing these values does not alter existing containers. Run `./update.sh` to
inspect the effective settings and recreate only containers whose logging
configuration differs, even when the application revision is already current.
Persistent named volumes are retained.

Use the same privileged Compose form used by the scripts:

```bash
cd /opt/wreckage
sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml ps
sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml logs --tail=200
sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml logs -f backend
sed -n '1,20p' .wreckage/deployment.env
```

`docker compose logs` and `docker compose logs -f` continue to read and follow
logs through Docker; applications continue writing to stdout and stderr and do
not write files under `/var/log/wreckage`.

Inspect the effective logging configuration for every production container:

```bash
cd /opt/wreckage
dc='sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml'
for service in backend frontend caddy; do
  id=$($dc ps -q "$service")
  printf '%s: ' "$service"
  sudo docker inspect --format '{{json .HostConfig.LogConfig}}' "$id"
done
```

Each result must show type `local` with `max-size`, `max-file`, and
`compress:true` matching `production.env` or the defaults.

### Disposable rotation verification

Do not perform a forced rotation test on the live VPS. On a disposable copy of
the production deployment, set a small valid limit such as `10k` and a file
count of `2`, run `./update.sh`, and record the three container IDs and inspected
logging configurations. Follow the logs in one terminal:

```bash
$dc logs -f frontend
```

In another terminal, generate enough normal HTTP requests to make the frontend
access log exceed the configured total, then confirm `$dc logs frontend`
continues to work and its oldest output is discarded as new output arrives.
Restart the service and repeat the log commands, then change the limits and run
`./update.sh` again. Containers with changed logging settings must receive new
IDs; a subsequent update with unchanged settings must preserve their IDs.
Never inspect or modify files inside Docker's internal log storage.

Finally, verify HTTPS after recreation:

```bash
curl -fsS https://wreckage.segersten.eu/games/running
$dc logs --tail=20 backend frontend caddy
```

## Security verification

Run these commands manually on the prepared server:

```bash
cd /opt/wreckage
dc='sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml'

$dc exec -T backend id
$dc exec -T frontend id
$dc ps
sudo ss -ltnp | grep -E ':(80|443|5432|8080)\b'
sudo docker inspect "$($dc ps -q backend)" --format '{{json .HostConfig.SecurityOpt}} {{json .HostConfig.CapDrop}}'
sudo docker inspect "$($dc ps -q frontend)" --format '{{json .HostConfig.SecurityOpt}} {{json .HostConfig.CapDrop}}'
$dc exec -T frontend wget -qO- http://127.0.0.1:8080/games/running
curl -fsS https://wreckage.segersten.eu/games/running

id wreckage
getent group docker
sudo -u wreckage test ! -r /etc/wreckage/production.env
sudo -u wreckage test ! -w /opt/wreckage
sudo -u wreckage docker info   # must fail
```

Backend UID 10001 and the frontend `nginx` UID must be non-zero. Only Caddy
should show host mappings for 80 and 443. No service mounts the Docker socket.

In a disposable environment, create a game and restart the backend. Verify
that the game no longer exists. Caddy state remains in its named volumes.
Never use `down --volumes` in production.

## Failure recovery

The update script leaves the previously recorded healthy metadata unchanged on
failure and prints the failed stage, attempted revision, recent logs, and last
healthy revision. Diagnose first, then rerun `./update.sh` or use the explicit
image rollback to an in-memory-compatible revision. Compare:

```bash
git status --short
git rev-parse HEAD
sed -n '1,20p' .wreckage/deployment.env
sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml ps
sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml logs --tail=200
```

OS upgrades, DNS/firewall changes,
and certificate incident recovery remain manual procedures and must be rehearsed
in disposable infrastructure. Do not deploy or test failure recovery against
the live VPS without an explicit maintenance plan.

## Persistent resources

- `wreckage-caddy-data`: certificates and ACME state
- `wreckage-caddy-config`: Caddy runtime configuration
- `/etc/wreckage/production.env`: non-secret production settings
- `/opt/wreckage/.wreckage/deployment.env`: last verified version

## Transition from the former PostgreSQL deployment

For this transition, load the new deployment scripts before running the update
(the former updater still waits for the PostgreSQL service):

```bash
cd /opt/wreckage
git pull --ff-only
./update.sh
```

The first update to this version starts an empty H2 database. No existing game
data is imported. Once the new services are started, Compose removes the old
PostgreSQL container with `--remove-orphans`. Its old `wreckage-postgres-data`
volume, password file, and any backup dumps are left untouched for manual
retention or removal. They are no longer used by the application.
