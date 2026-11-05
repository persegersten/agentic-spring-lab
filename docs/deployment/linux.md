# Secure Linux deployment

Wreckage production targets Debian 13 (Trixie), Docker Engine, and Docker
Compose v2. The administrator account is `admin`. The separate host account
`wreckage` is deliberately not used to run Docker or deployment scripts and
must have no sudo access, Docker-group membership, socket access, production
secrets, or write access to the checkout.

The application checkout is `/opt/wreckage`. Production configuration is
root-owned under `/etc/wreckage`; it is never copied into Git. Backend and
frontend run as non-root users inside their containers. Caddy is the only
service publishing host ports (TCP 80 and 443). PostgreSQL, backend port 8080,
and frontend port 8080 remain on Docker networks.

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

On first installation, if neither protected file exists, the installer creates
both with random database credentials:

- `/etc/wreckage/production.env`: root:root, mode 0600
- `/etc/wreckage/postgres-password`: root:root, mode 0600
- `/etc/wreckage`: root:root, mode 0700

It never overwrites either existing file. A partial configuration is treated as
an error. To provision configuration manually, create both files before
installing. `production.env` requires:

```dotenv
DOMAIN=wreckage.segersten.eu
POSTGRES_DB=wreckage
POSTGRES_USER=wreckage
POSTGRES_PASSWORD_FILE=/etc/wreckage/postgres-password
DB_URL=jdbc:postgresql://postgres:5432/wreckage
SPRING_PROFILES_ACTIVE=postgres
CADDY_IMAGE=caddy:2.10.2-alpine
WRECKAGE_LOG_MAX_SIZE=10m
WRECKAGE_LOG_MAX_FILES=5
```

Write only the password value to `postgres-password`, without putting the
password in `production.env`. Compose's `--env-file` supplies interpolation
values to the Compose model; it does not automatically inject them into
containers. `compose.prod.yml` explicitly maps the required non-secret values.
It mounts the password as a Compose secret: PostgreSQL uses
`POSTGRES_PASSWORD_FILE`, while the non-root backend reads its container-only
secret immediately before executing Java. The source file stays mode 0600 on
the host; the secret mount is readable by the intended non-root process inside
the container.

## Install

Run the script as `admin`, not with `sudo` in front of the script:

```bash
cd /opt/wreckage
./install.sh --domain wreckage.segersten.eu
```

The script uses sudo only for system packages, protected configuration, service
management, and Docker. It validates the Compose model before deployment,
creates the three named volumes idempotently, builds revision-tagged images,
starts the stack, and verifies container health, internal proxy routing, and
public HTTPS. Repeating installation preserves configuration, database data,
Caddy state, and volumes. It never runs `docker compose down --volumes` or a
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
HTTPS verification. Added Flyway migrations trigger a verified custom-format
database dump under `.wreckage/backups`. Changed or removed historical
migrations are rejected. Database and Caddy named volumes are never replaced.

Application-image rollback remains explicit and interactive:

```bash
./update.sh --rollback
```

It does not roll back PostgreSQL data or Flyway migrations. Review schema
compatibility and the verified backup first. `./update.sh --platform` remains a
separate, interactive Docker/Caddy maintenance operation; it does not perform
automatic OS or PostgreSQL major-version upgrades.

## Status and logs

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
for service in backend frontend caddy postgres; do
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
count of `2`, run `./update.sh`, and record the four container IDs and inspected
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

Finally, verify HTTPS and persistence after recreation:

```bash
curl -fsS https://wreckage.segersten.eu/games/running
$dc exec -T postgres pg_isready -U "$($dc exec -T postgres printenv POSTGRES_USER)"
$dc logs --tail=20 backend frontend caddy postgres
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

Verify persistence only in a disposable environment or an approved maintenance
window: create test database data, restart/recreate PostgreSQL without `-v`, and
confirm the data; record Caddy `/data` contents, recreate Caddy, and confirm the
same named volume remains. Never use `down --volumes` in production.

## Failure recovery

The update script leaves the previously recorded healthy metadata unchanged on
failure and prints the failed stage, attempted revision, recent logs, and last
healthy revision. Diagnose first, then rerun `./update.sh` or use the explicit
image rollback when schema-compatible. Compare:

```bash
git status --short
git rev-parse HEAD
sed -n '1,20p' .wreckage/deployment.env
sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml ps
sudo docker compose --env-file /etc/wreckage/production.env -f compose.prod.yml logs --tail=200
```

Database restore, OS upgrades, PostgreSQL major upgrades, DNS/firewall changes,
and certificate incident recovery remain manual procedures and must be rehearsed
in disposable infrastructure. Do not deploy or test failure recovery against
the live VPS without an explicit maintenance plan.

## Persistent resources

- `wreckage-postgres-data`: PostgreSQL data
- `wreckage-caddy-data`: certificates and ACME state
- `wreckage-caddy-config`: Caddy runtime configuration
- `/etc/wreckage/production.env`: non-secret production settings
- `/etc/wreckage/postgres-password`: database secret
- `/opt/wreckage/.wreckage/deployment.env`: last verified version
- `/opt/wreckage/.wreckage/backups`: pre-migration dumps
