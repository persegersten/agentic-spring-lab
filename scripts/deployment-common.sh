#!/usr/bin/env bash

deployment_init() {
    script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[1]}")" && pwd)
    config_dir=/etc/wreckage
    env_file="$config_dir/production.env"
    password_file="$config_dir/postgres-password"
    compose_file="$script_dir/compose.prod.yml"
    state_dir="$script_dir/.wreckage"
    metadata_file="$state_dir/deployment.env"
    docker_command=()
}

fail() { echo "Error: $*" >&2; exit 1; }
note() { echo "==> $*"; }
run_privileged() {
    if [[ $EUID -eq 0 ]]; then "$@"
    else command -v sudo >/dev/null || fail "sudo is required for production deployment."; sudo "$@"
    fi
}
env_value() { run_privileged awk -F= -v k="$1" '$1==k {sub(/^[^=]*=/,""); gsub(/^['\''\"]|['\''\"]$/,""); print; exit}' "$env_file"; }

select_docker_command() {
    if [[ $EUID -eq 0 ]] && docker info >/dev/null 2>&1; then docker_command=(docker)
    elif [[ $EUID -ne 0 ]] && command -v sudo >/dev/null && sudo docker info >/dev/null 2>&1; then docker_command=(sudo docker)
    else fail "Docker is unavailable, stopped, or inaccessible."; fi
    docker_cmd compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required."
}
docker_cmd() { "${docker_command[@]}" "$@"; }
compose() {
    local -a preserved_environment=()
    [[ -z ${WRECKAGE_IMAGE_TAG:-} ]] || preserved_environment+=("WRECKAGE_IMAGE_TAG=$WRECKAGE_IMAGE_TAG")
    [[ -z ${CADDY_IMAGE:-} ]] || preserved_environment+=("CADDY_IMAGE=$CADDY_IMAGE")
    if [[ ${docker_command[0]} == sudo ]]; then
        sudo env "${preserved_environment[@]}" docker compose --env-file "$env_file" -f "$compose_file" "$@"
    else
        env "${preserved_environment[@]}" docker compose --env-file "$env_file" -f "$compose_file" "$@"
    fi
}

validate_installation() {
    [[ -f $compose_file && -f $script_dir/Caddyfile ]] || fail "This is not a complete Wreckage installation."
    run_privileged test -f "$env_file" || fail "$env_file is missing."
    run_privileged test -f "$password_file" || fail "$password_file is missing."
    local permissions owner protected_file
    permissions=$(run_privileged stat -c '%a' "$config_dir") || fail "Cannot inspect $config_dir permissions."
    [[ $permissions == 700 ]] || fail "$config_dir must have permissions 0700."
    owner=$(run_privileged stat -c '%U:%G' "$config_dir")
    [[ $owner == root:root ]] || fail "$config_dir must be owned by root:root."
    for protected_file in "$env_file" "$password_file"; do
        permissions=$(run_privileged stat -c '%a' "$protected_file")
        [[ $permissions == 600 ]] || fail "$protected_file must have permissions 0600."
        owner=$(run_privileged stat -c '%U:%G' "$protected_file")
        [[ $owner == root:root ]] || fail "$protected_file must be owned by root:root."
    done
    local key
    for key in DOMAIN POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD_FILE DB_URL SPRING_PROFILES_ACTIVE; do
        [[ -n $(env_value "$key") ]] || fail "$env_file is missing $key."
    done
    [[ $(env_value POSTGRES_PASSWORD_FILE) == "$password_file" ]] || fail "POSTGRES_PASSWORD_FILE must be $password_file."
    [[ $(env_value SPRING_PROFILES_ACTIVE) == postgres ]] || fail "SPRING_PROFILES_ACTIVE must be postgres."
    select_docker_command
    compose config --quiet || fail "Production Compose configuration is invalid."
}

container_state() {
    local id
    id=$(compose ps -q "$1" 2>/dev/null || true)
    [[ -n $id ]] || { echo missing; return; }
    docker_cmd inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id"
}

wait_for_services() {
    local deadline=$((SECONDS + ${HEALTH_TIMEOUT:-240})) service state ok
    local -a services=("$@")
    while ((SECONDS < deadline)); do
        ok=true
        for service in "${services[@]}"; do
            state=$(container_state "$service")
            case $state in healthy) ;; unhealthy|exited|dead) compose logs --tail=100 "$service" >&2 || true; return 1 ;; *) ok=false ;; esac
        done
        $ok && return 0
        sleep 3
    done
    compose ps >&2 || true; compose logs --tail=100 "${services[@]}" >&2 || true; return 1
}

verify_internal_proxy() {
    local id response
    id=$(compose ps -q frontend)
    response=$(docker_cmd exec "$id" wget -qO- http://127.0.0.1:8080/games/running) || return 1
    [[ $response =~ ^[[:space:]]*\[ ]]
}

verify_https() {
    local domain attempt response
    domain=$(env_value DOMAIN)
    for attempt in {1..18}; do
        response=$(curl -fsS --max-time 10 "https://$domain/games/running" 2>/dev/null) && [[ $response =~ ^[[:space:]]*\[ ]] && return 0
        sleep 5
    done
    return 1
}

write_metadata() {
    local current=$1 previous=$2 health=$3 tmp
    mkdir -p "$state_dir"
    tmp=$(mktemp "$state_dir/deployment.env.tmp.XXXXXX")
    printf 'DEPLOYED_REVISION=%q\nPREVIOUS_REVISION=%q\nDEPLOYED_AT=%q\nHEALTH_STATUS=%q\nFRONTEND_IMAGE=%q\nBACKEND_IMAGE=%q\n' \
        "$current" "$previous" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$health" \
        "wreckage-frontend:$current" "wreckage-backend:$current" >"$tmp"
    chmod 600 "$tmp"; mv "$tmp" "$metadata_file"
}

read_metadata() {
    [[ -f $metadata_file ]] || fail "Deployment metadata is missing; rerun install.sh to verify and initialize this installation."
    # shellcheck disable=SC1090
    source "$metadata_file"
    [[ -n ${DEPLOYED_REVISION:-} ]] || fail "Deployment metadata is invalid."
}
