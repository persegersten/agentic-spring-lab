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
assert "production uses only in-memory storage" bash -c 'grep -q "SPRING_PROFILES_ACTIVE: in-memory" "$1/compose.prod.yml" && ! grep -qi postgres "$1/compose.prod.yml" "$1/compose.yml" "$1/backend/pom.xml"' _ "$repo"
assert "updates remove obsolete containers without deleting volumes" grep -q 'compose up -d --remove-orphans' "$repo/update.sh"
assert "platform changes require confirmation" grep -q 'confirm "Apply only the platform updates' "$repo/update.sh"
assert "platform packages are explicit" grep -q 'apt-get install.*docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin' "$repo/update.sh"
assert "production configuration is outside repository" grep -q 'env_file="$config_dir/production.env"' "$repo/scripts/deployment-common.sh"
assert "production Docker access uses sudo" grep -q 'docker_command=(sudo docker)' "$repo/scripts/deployment-common.sh"
assert "sudo Compose preserves revision image tags" grep -q 'preserved_environment+=("WRECKAGE_IMAGE_TAG=' "$repo/scripts/deployment-common.sh"
assert "production logging uses the local driver" grep -q '^  driver: local$' "$repo/compose.prod.yml"
assert "production logging enables compression" grep -q 'compress: "true"' "$repo/compose.prod.yml"
assert "production logging defaults to 10m" grep -q 'WRECKAGE_LOG_MAX_SIZE:-10m' "$repo/compose.prod.yml"
assert "production logging defaults to five files" grep -q 'WRECKAGE_LOG_MAX_FILES:-5' "$repo/compose.prod.yml"
assert "all production services share the logging anchor" bash -c '[[ $(grep -c "logging: \*default-logging" "$1/compose.prod.yml") -eq 3 ]]' _ "$repo"
assert "local Compose logging remains unchanged" bash -c 'grep -q "driver: json-file" "$1/compose.yml" && grep -q "DOCKER_LOG_MAX_SIZE:-10m" "$1/compose.yml" && ! grep -q WRECKAGE_LOG "$1/compose.yml"' _ "$repo"
assert "backend has a dedicated non-root user" grep -q 'USER wreckage-app:wreckage-app' "$repo/Dockerfile"
assert "frontend has a non-root user" grep -q '^USER nginx:nginx$' "$repo/Dockerfile"
assert "frontend listens on an unprivileged port" grep -q 'listen 8080' "$repo/nginx.conf"
assert "Caddy routes to the unprivileged frontend port" grep -q 'reverse_proxy frontend:8080' "$repo/Caddyfile"
assert "application containers prevent privilege escalation" bash -c '[[ $(grep -c "no-new-privileges:true" "$1/compose.prod.yml") -eq 2 ]]' _ "$repo"
assert "application containers drop capabilities" bash -c '[[ $(grep -c "cap_drop: \[ALL\]" "$1/compose.prod.yml") -eq 2 ]]' _ "$repo"
assert "production Compose does not mount Docker socket" bash -c '! grep -q "/var/run/docker.sock" "$1/compose.prod.yml"' _ "$repo"
assert "production Compose publishes only Caddy ports" bash -c '[[ $(grep -c "ports:" "$1/compose.prod.yml") -eq 1 ]] && grep -q "ports: \[\"80:80\", \"443:443\"\]" "$1/compose.prod.yml"' _ "$repo"
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

validate_log_settings() {
    local env_path=$1 expected_size=$2 expected_files=$3
    bash -c '
        source "$1/scripts/deployment-common.sh"
        env_file=$2
        run_privileged() { "$@"; }
        validate_logging_configuration
        [[ $WRECKAGE_LOG_MAX_SIZE == "$3" && $WRECKAGE_LOG_MAX_FILES == "$4" ]]
    ' _ "$repo" "$env_path" "$expected_size" "$expected_files"
}
printf 'DOMAIN=example.test\n' >"$tmp/log-defaults.env"
assert "logging validation supplies defaults" validate_log_settings "$tmp/log-defaults.env" 10m 5
printf 'WRECKAGE_LOG_MAX_SIZE=25m\nWRECKAGE_LOG_MAX_FILES=8\n' >"$tmp/log-custom.env"
assert "logging validation accepts custom values" validate_log_settings "$tmp/log-custom.env" 25m 8
for invalid in '=5' '0m=5' '-1m=5' '10=5' '1.5m=5' '10t=5' '10m=0' '10m=-1' '10m=1.5' '10m=many'; do
    size=${invalid%%=*}; files=${invalid#*=}
    printf 'WRECKAGE_LOG_MAX_SIZE=%s\nWRECKAGE_LOG_MAX_FILES=%s\n' "$size" "$files" >"$tmp/log-invalid.env"
    assert "logging validation rejects size '$size' and count '$files'" bash -c '
        ! (source "$1/scripts/deployment-common.sh"; env_file=$2; run_privileged() { "$@"; }; validate_logging_configuration) >/dev/null 2>&1
    ' _ "$repo" "$tmp/log-invalid.env"
done

(
    source "$repo/scripts/deployment-common.sh"
    WRECKAGE_LOG_MAX_SIZE=10m
    WRECKAGE_LOG_MAX_FILES=5
    compose() {
        local service=$3
        case $service in backend|frontend|caddy) printf 'id-%s\n' "$service";; esac
    }
    docker_cmd() {
        local id=${@: -1}
        case $id in
            id-backend) printf 'local|10m|7|true\n';;
            *) printf 'local|10m|5|true\n';;
        esac
    }
    [[ $(logging_services_requiring_recreation) == backend ]]
) && pass "logging reconciliation selects only mismatched containers" || fail_test "logging reconciliation selects only mismatched containers"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    assert "production Compose configuration is valid" docker compose --env-file "$repo/.env.example" -f "$repo/compose.prod.yml" config --quiet
    rendered=$(docker compose --env-file "$repo/.env.example" -f "$repo/compose.prod.yml" config)
    assert "rendered production Compose applies local logging to all services" bash -c '[[ $(grep -c "driver: local" <<<"$1") -eq 4 ]]' _ "$rendered"
    assert "rendered production Compose applies compression to all services" bash -c '[[ $(grep -c "compress: \"true\"" <<<"$1") -eq 4 ]]' _ "$rendered"
    custom=$(WRECKAGE_LOG_MAX_SIZE=25m WRECKAGE_LOG_MAX_FILES=8 docker compose --env-file "$repo/.env.example" -f "$repo/compose.prod.yml" config)
    assert "rendered production Compose applies custom logging limits" bash -c '[[ $(grep -c "max-size: 25m" <<<"$1") -eq 4 && $(grep -c "max-file: \"8\"" <<<"$1") -eq 4 ]]' _ "$custom"
    assert "local Compose configuration remains valid" docker compose -f "$repo/compose.yml" config --quiet
else
    pass "production Compose validation skipped (Docker unavailable)"
fi
((failures == 0)) || { echo "$failures test(s) failed" >&2; exit 1; }
echo "All update safety tests passed."
