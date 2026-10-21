# Linux deployment

Wreckage can install itself on a fresh Ubuntu 22.04, 24.04, or 26.04 LTS VPS,
or Debian 12 or 13 VPS. The production deployment uses Docker Compose, Caddy,
nginx, Spring Boot, and PostgreSQL. Java, Maven, Node.js, and npm are needed only
inside the image build and are not installed on the host.

## Before installing

Create a DNS A record for the deployment hostname pointing to the VPS's public
IPv4 address. If the server has working public IPv6, also point the hostname's
AAAA record to it; otherwise remove an inapplicable AAAA record. DNS changes can
take time to propagate.

Allow incoming TCP ports 80 and 443 in both the VPS provider firewall and the
host firewall. The installer reports conflicts but does not edit DNS or firewall
rules. Caddy needs both ports to obtain a public certificate and redirect HTTP
to HTTPS.

## Install

Clone the repository and run:

```bash
git clone <repository-url>
cd <repository-directory>
./install.sh --domain wreckage.example.com
```

The argument must be a hostname without `https://`, a port, wildcard, or path.
The installer detects working Docker installations and reuses them. If Docker is
absent, it installs Docker Engine and the Compose v2 plugin from Docker's official
apt repository. It may request `sudo` for package, service, or Docker access; it
does not add the current user to the `docker` group.

Installation creates `.env` with mode 0600 and a random PostgreSQL password.
An existing `.env` is never overwritten. A repeated run with the same domain
reuses its configuration, containers, and volumes. If the requested domain does
not match the existing file, update the file deliberately or use the configured
domain before rerunning.

The deployment is successful only when the installer can verify
`https://<domain>/games/running` with a trusted certificate. If containers are
healthy but that check fails, correct DNS or network access and rerun the same
command. Caddy retries certificate issuance automatically.

## Configuration

`.env.example` documents every production value:

- `DOMAIN` is the public hostname used by Caddy.
- `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` configure PostgreSQL.
- `DB_URL` connects Spring Boot to the internal `postgres` service.
- `SPRING_PROFILES_ACTIVE` must remain `postgres` for durable storage.

Do not commit `.env`. If database settings must change after data already exists,
make and test a database migration or restore plan first; changing the environment
does not rewrite credentials inside an initialized PostgreSQL volume.

## Operations

Run these commands from the repository directory. Prefix `docker` with `sudo`
when the installer reported that it was using privileged Docker access.

```bash
# Status
docker compose --env-file .env -f compose.prod.yml ps

# Follow all logs, or append a service name such as caddy or backend
docker compose --env-file .env -f compose.prod.yml logs -f

# Restart without deleting data
docker compose --env-file .env -f compose.prod.yml restart

# Stop safely; named volumes remain
docker compose --env-file .env -f compose.prod.yml down

# Start again
docker compose --env-file .env -f compose.prod.yml up -d
```

Do not add `--volumes` to `down`. The installer never runs `down -v`.

## Persistent storage

PostgreSQL data and Caddy's certificates and state live in named Docker volumes:

- `wreckage-postgres-data`
- `wreckage-caddy-data`
- `wreckage-caddy-config`

They survive container rebuilds, restarts, and ordinary `docker compose down`.
Flyway applies the repository's normal migrations when the backend starts; the
deployment does not run Flyway clean, repair, or destructive database setup.
Docker volumes are persistence, not backups—include the PostgreSQL volume in the
server's tested backup procedure.

## Troubleshooting

- **Unsupported operating system:** use one of the releases listed above. The
  installer intentionally does not guess apt repository settings.
- **Docker is inaccessible:** start the Docker service or run with an account
  permitted to use it. The installer can use `sudo docker` but does not change
  group membership.
- **Port 80 or 443 is occupied:** stop or reconfigure the conflicting web server.
  Only the Wreckage Caddy container may own these host ports.
- **DNS warning or HTTPS failure:** check `A` and `AAAA` answers from another
  network, verify both provider and host firewalls, then inspect Caddy with
  `docker compose --env-file .env -f compose.prod.yml logs caddy`.
- **A service is unhealthy:** inspect `docker compose --env-file .env -f
  compose.prod.yml ps` and that service's logs. PostgreSQL starts first, followed
  by the backend, frontend, and Caddy.
- **Configuration validation fails:** compare `.env` with `.env.example`. The
  installer leaves an existing file untouched so operator settings are not lost.

The local `compose.yml`, `start-with-docker.sh`, and `start.sh` workflows are
separate from this production stack and continue to use their existing defaults.
