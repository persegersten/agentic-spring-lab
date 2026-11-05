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
env_value() {
    run_privileged awk -F= -v k="$1" '$1==k {
        sub(/^[^=]*=/, "")
        first=substr($0, 1, 1); last=substr($0, length($0), 1); single_quote=sprintf("%c", 39)
        if ((first=="\"" && last=="\"") || (first==single_quote && last==single_quote)) print substr($0, 2, length($0)-2)
        else print
        exit
    }' "$env_file"
}
env_has_key() { run_privileged awk -F= -v k="$1" '$1==k {found=1; exit} END{exit !found}' "$env_file"; }

validate_logging_configuration() {
    local size=10m files=5
    if env_has_key WRECKAGE_LOG_MAX_SIZE; then
        size=$(env_value WRECKAGE_LOG_MAX_SIZE)
        [[ -n $size ]] || fail "WRECKAGE_LOG_MAX_SIZE must not be empty."
    fi
    if env_has_key WRECKAGE_LOG_MAX_FILES; then
        files=$(env_value WRECKAGE_LOG_MAX_FILES)
        [[ -n $files ]] || fail "WRECKAGE_LOG_MAX_FILES must not be empty."
    fi
    [[ $size =~ ^[1-9][0-9]*[kKmMgG]$ ]] \
        || fail "WRECKAGE_LOG_MAX_SIZE must be a positive Docker size using k, m, or g (for example, 10m)."
    [[ $files =~ ^[1-9][0-9]*$ ]] \
        || fail "WRECKAGE_LOG_MAX_FILES must be a positive integer."
    WRECKAGE_LOG_MAX_SIZE=$size
    WRECKAGE_LOG_MAX_FILES=$files
}

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
    [[ -z ${WRECKAGE_LOG_MAX_SIZE:-} ]] || preserved_environment+=("WRECKAGE_LOG_MAX_SIZE=$WRECKAGE_LOG_MAX_SIZE")
    [[ -z ${WRECKAGE_LOG_MAX_FILES:-} ]] || preserved_environment+=("WRECKAGE_LOG_MAX_FILES=$WRECKAGE_LOG_MAX_FILES")
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
    validate_logging_configuration
    select_docker_command
    compose config --quiet || fail "Production Compose configuration is invalid."
}

logging_services_requiring_recreation() {
    local service id actual expected
    expected="local|$WRECKAGE_LOG_MAX_SIZE|$WRECKAGE_LOG_MAX_FILES|true"
    for service in postgres backend frontend caddy; do
        id=$(compose ps -q "$service" 2>/dev/null || true)
        if [[ -z $id ]]; then
            printf '%s\n' "$service"
            continue
        fi
        actual=$(docker_cmd inspect --format '{{.HostConfig.LogConfig.Type}}|{{index .HostConfig.LogConfig.Config "max-size"}}|{{index .HostConfig.LogConfig.Config "max-file"}}|{{index .HostConfig.LogConfig.Config "compress"}}' "$id")
        [[ $actual == "$expected" ]] || printf '%s\n' "$service"
    done
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
