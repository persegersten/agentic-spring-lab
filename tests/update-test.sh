#!/usr/bin/env bash
set -Eeuo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
failures=0
pass() { echo "ok - $1"; }
fail_test() { echo "not ok - $1"; failures=$((failures + 1)); }
assert() { local name=$1; shift; if "$@"; then pass "$name"; else fail_test "$name"; fi; }

assert "deployment scripts parse" bash -n "$repo/install.sh" "$repo/update.sh" "$repo/scripts/deployment-common.sh"
assert "invalid update mode is refused" bash -c '! "$1/update.sh" --automatic >/dev/null 2>&1' _ "$repo"
assert "normal updates use fast-forward only" grep -q 'git merge --ff-only' "$repo/update.sh"
assert "dirty tracked and untracked files are checked" grep -q 'git ls-files --others --exclude-standard' "$repo/update.sh"
assert "application build precedes recreation" awk '/compose build frontend backend/{b=NR} /compose up -d --no-deps backend/{u=NR} END{exit !(b && u && b<u)}' "$repo/update.sh"
assert "Compose never removes volumes" bash -c '! grep -E "down.*(-v|--volumes)|volume (rm|prune)" "$1/install.sh" "$1/update.sh"' _ "$repo"
assert "destructive Flyway operations are absent" bash -c '! grep -Ei "flyway (clean|repair|baseline)|baselineOnMigrate" "$1/install.sh" "$1/update.sh"' _ "$repo"
assert "migration backups use pg_dump" grep -q pg_dump "$repo/update.sh"
assert "migration backups are verified" grep -q 'pg_restore --list' "$repo/update.sh"
assert "platform changes require confirmation" grep -q 'confirm "Apply only the platform updates' "$repo/update.sh"
assert "platform packages are explicit" grep -q 'apt-get install.*docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin' "$repo/update.sh"
assert "production configuration is outside repository" grep -q 'env_file="$config_dir/production.env"' "$repo/scripts/deployment-common.sh"
assert "production Docker access uses sudo" grep -q 'docker_command=(sudo docker)' "$repo/scripts/deployment-common.sh"
assert "sudo Compose preserves revision image tags" grep -q 'preserved_environment+=("WRECKAGE_IMAGE_TAG=' "$repo/scripts/deployment-common.sh"
assert "backend has a dedicated non-root user" grep -q 'USER wreckage-app:wreckage-app' "$repo/Dockerfile"
assert "frontend has a non-root user" grep -q '^USER nginx:nginx$' "$repo/Dockerfile"
assert "frontend listens on an unprivileged port" grep -q 'listen 8080' "$repo/nginx.conf"
assert "Caddy routes to the unprivileged frontend port" grep -q 'reverse_proxy frontend:8080' "$repo/Caddyfile"
assert "database password uses a Compose secret" grep -q 'POSTGRES_PASSWORD_FILE: /run/secrets/postgres_password' "$repo/compose.prod.yml"
assert "application containers prevent privilege escalation" bash -c '[[ $(grep -c "no-new-privileges:true" "$1/compose.prod.yml") -eq 2 ]]' _ "$repo"
assert "application containers drop capabilities" bash -c '[[ $(grep -c "cap_drop: \[ALL\]" "$1/compose.prod.yml") -eq 2 ]]' _ "$repo"
assert "production Compose does not mount Docker socket" bash -c '! grep -q "/var/run/docker.sock" "$1/compose.prod.yml"' _ "$repo"
assert "production Compose publishes only Caddy ports" bash -c '[[ $(grep -c "ports:" "$1/compose.prod.yml") -eq 1 ]] && grep -q "ports: \[\"80:80\", \"443:443\"\]" "$1/compose.prod.yml"' _ "$repo"
assert "PostgreSQL uses the private application network" bash -c 'grep -A25 "^  postgres:" "$1/compose.prod.yml" | grep -q "networks: \[application\]"' _ "$repo"
assert "application network is internal" bash -c 'grep -A2 "^  application:" "$1/compose.prod.yml" | grep -q "internal: true"' _ "$repo"

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT; mkdir -p "$tmp/.wreckage"
(
    # shellcheck source=../scripts/deployment-common.sh
    source "$repo/scripts/deployment-common.sh"
    script_dir=$tmp; state_dir="$tmp/.wreckage"; metadata_file="$state_dir/deployment.env"
    write_metadata new-revision old-revision healthy
    # shellcheck disable=SC1090
    source "$metadata_file"
    [[ $DEPLOYED_REVISION == new-revision && $PREVIOUS_REVISION == old-revision && $HEALTH_STATUS == healthy ]]
) && pass "version metadata records current and previous revisions" || fail_test "version metadata records current and previous revisions"

printf 'test-password' >"$tmp/postgres-password"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    assert "production Compose configuration is valid" env POSTGRES_PASSWORD_FILE="$tmp/postgres-password" docker compose --env-file "$repo/.env.example" -f "$repo/compose.prod.yml" config --quiet
    assert "local Compose configuration remains valid" docker compose -f "$repo/compose.yml" config --quiet
else
    pass "production Compose validation skipped (Docker unavailable)"
fi
((failures == 0)) || { echo "$failures test(s) failed" >&2; exit 1; }
echo "All update safety tests passed."
