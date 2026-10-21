#!/usr/bin/env bash

set -Eeuo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
env_file="$script_dir/.env"
compose_file="$script_dir/compose.prod.yml"
docker_repo_configured=false
declare -a docker_command=()
declare -a temporary_files=()

cleanup() {
    if ((${#temporary_files[@]})); then
        rm -f "${temporary_files[@]}"
    fi
}
trap cleanup EXIT

usage() {
    echo "Usage: $0 --domain <hostname>" >&2
}

fail() {
    echo "Error: $*" >&2
    exit 1
}

note() {
    echo "==> $*"
}

run_as_root() {
    if [[ $EUID -eq 0 ]]; then
        "$@"
        return
    fi
    command -v sudo >/dev/null 2>&1 || fail "sudo is required for system package installation."
    sudo "$@"
}

parse_domain() {
    [[ $# -eq 2 && $1 == "--domain" ]] || { usage; exit 2; }
    domain=${2,,}
    [[ ${#domain} -le 253 ]] || fail "The domain is longer than 253 characters."
    [[ $domain =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]] \
        || fail "Invalid domain '$2'. Supply a hostname such as wreckage.example.com, without a scheme, port, wildcard, or path."
}

detect_os() {
    [[ -r /etc/os-release ]] || fail "Cannot identify this operating system: /etc/os-release is unavailable."
    # shellcheck disable=SC1091
    . /etc/os-release
    os_id=${ID:-}
    os_version=${VERSION_ID:-}
    os_codename=${VERSION_CODENAME:-}

    case "$os_id:$os_version" in
        ubuntu:22.04|ubuntu:24.04|ubuntu:26.04|debian:12|debian:13) ;;
        *) fail "Unsupported operating system: ${PRETTY_NAME:-$os_id $os_version}. Supported releases are Ubuntu 22.04/24.04/26.04 LTS and Debian 12/13." ;;
    esac
    [[ -n $os_codename ]] || fail "The operating-system codename is missing from /etc/os-release."
}

install_required_utilities() {
    local -a packages=()
    command -v git >/dev/null 2>&1 || packages+=(git)
    command -v curl >/dev/null 2>&1 || packages+=(curl)
    command -v gpg >/dev/null 2>&1 || packages+=(gnupg)
    command -v getent >/dev/null 2>&1 || packages+=(libc-bin)
    command -v ss >/dev/null 2>&1 || packages+=(iproute2)
    [[ -r /etc/ssl/certs/ca-certificates.crt ]] || packages+=(ca-certificates)

    if ((${#packages[@]})); then
        note "Installing missing required utilities: ${packages[*]}"
        run_as_root apt-get update
        run_as_root apt-get install --yes --no-install-recommends "${packages[@]}"
    fi
}

configure_docker_repository() {
    $docker_repo_configured && return

    local architecture key_tmp source_tmp
    architecture=$(dpkg --print-architecture)
    key_tmp=$(mktemp)
    source_tmp=$(mktemp)
    temporary_files+=("$key_tmp" "$source_tmp")

    note "Configuring Docker's official apt repository for $os_id $os_codename"
    curl --fail --silent --show-error --location \
        "https://download.docker.com/linux/$os_id/gpg" --output "$key_tmp"
    printf '%s\n' \
        'Types: deb' \
        "URIs: https://download.docker.com/linux/$os_id" \
        "Suites: $os_codename" \
        'Components: stable' \
        "Architectures: $architecture" \
        'Signed-By: /etc/apt/keyrings/docker.asc' >"$source_tmp"

    run_as_root install -m 0755 -d /etc/apt/keyrings
    run_as_root install -m 0644 "$key_tmp" /etc/apt/keyrings/docker.asc
    run_as_root install -m 0644 "$source_tmp" /etc/apt/sources.list.d/docker.sources
    run_as_root apt-get update
    rm -f "$key_tmp" "$source_tmp"
    docker_repo_configured=true
}

installed_conflicting_docker_packages() {
    local package
    for package in docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc; do
        if dpkg-query -W -f='${db:Status-Abbrev}' "$package" 2>/dev/null | grep -q '^ii'; then
            echo "$package"
        fi
    done
}

install_docker_engine() {
    local conflicts
    conflicts=$(installed_conflicting_docker_packages)
    if [[ -n $conflicts ]]; then
        fail "Docker Engine is not available, but conflicting distribution packages are installed: $(tr '\n' ' ' <<<"$conflicts"). Remove or migrate them explicitly, then rerun this installer; they will not be replaced automatically."
    fi

    configure_docker_repository
    note "Installing Docker Engine and the Docker Compose v2 plugin"
    run_as_root apt-get install --yes --no-install-recommends \
        docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    if command -v systemctl >/dev/null 2>&1; then
        run_as_root systemctl enable --now docker
    fi
}

select_docker_command() {
    if docker info >/dev/null 2>&1; then
        docker_command=(docker)
    elif [[ $EUID -eq 0 ]]; then
        fail "Docker is installed, but its daemon is not operational. Start the Docker service and rerun the installer."
    elif command -v sudo >/dev/null 2>&1 && sudo docker info >/dev/null 2>&1; then
        docker_command=(sudo docker)
        note "Using sudo for Docker commands; the installer will not add users to the docker group."
    else
        fail "Docker is installed, but the daemon is stopped or inaccessible. Start it or grant the current user explicit access, then rerun the installer."
    fi
}

docker_cmd() {
    "${docker_command[@]}" "$@"
}

ensure_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        install_docker_engine
    fi
    select_docker_command

    if ! docker_cmd compose version >/dev/null 2>&1; then
        note "Docker is operational, but the Compose v2 plugin is missing."
        configure_docker_repository
        run_as_root apt-get install --yes --no-install-recommends docker-compose-plugin
        docker_cmd compose version >/dev/null 2>&1 \
            || fail "The Docker Compose v2 plugin was installed but is not available to this Docker CLI."
    fi
}

env_value() {
    local key=$1
    awk -v wanted="$key" '
        $0 ~ "^[[:space:]]*" wanted "[[:space:]]*=" {
            sub("^[[:space:]]*" wanted "[[:space:]]*=[[:space:]]*", "")
            sub("[[:space:]]*$", "")
            if (($0 ~ /^".*"$/) || ($0 ~ /^\047.*\047$/)) {
                print substr($0, 2, length($0) - 2)
            } else {
                print
            }
            exit
        }
    ' "$env_file"
}

ensure_environment() {
    if [[ -e $env_file ]]; then
        [[ -f $env_file ]] || fail "$env_file exists but is not a regular file."
        local configured_domain configured_profile
        configured_domain=$(env_value DOMAIN)
        [[ -n $configured_domain ]] || fail "Existing .env has no DOMAIN setting; it was not modified."
        [[ ${configured_domain,,} == "$domain" ]] \
            || fail "Existing .env is configured for '$configured_domain', not '$domain'. It was not modified."
        configured_profile=$(env_value SPRING_PROFILES_ACTIVE)
        [[ $configured_profile == "postgres" ]] \
            || fail "Existing .env must set SPRING_PROFILES_ACTIVE=postgres for durable deployment; it was not modified."
        chmod 600 "$env_file" || fail "Could not restrict $env_file to mode 0600."
        note "Reusing existing .env without changing its contents."
        return
    fi

    local password temporary_env
    password=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
    temporary_env=$(mktemp "$script_dir/.env.tmp.XXXXXX")
    temporary_files+=("$temporary_env")
    umask 077
    printf '%s\n' \
        "DOMAIN=$domain" \
        'POSTGRES_DB=wreckage' \
        'POSTGRES_USER=wreckage' \
        "POSTGRES_PASSWORD=$password" \
        'DB_URL=jdbc:postgresql://postgres:5432/wreckage' \
        'SPRING_PROFILES_ACTIVE=postgres' >"$temporary_env"
    chmod 600 "$temporary_env"
    mv "$temporary_env" "$env_file"
    note "Created .env with a generated database password and mode 0600."
}

compose() {
    docker_cmd compose --env-file "$env_file" -f "$compose_file" "$@"
}

port_is_listening() {
    local port=$1
    ss -H -ltn | awk -v suffix=":$port" '$4 == "*" suffix || substr($4, length($4) - length(suffix) + 1) == suffix { found=1 } END { exit !found }'
}

port_belongs_to_caddy() {
    local port=$1 caddy_id
    caddy_id=$(compose ps -q caddy 2>/dev/null || true)
    [[ -n $caddy_id ]] || return 1
    docker_cmd port "$caddy_id" "$port/tcp" 2>/dev/null | grep -Eq "(^|:)$port$"
}

check_ports() {
    local port
    for port in 80 443; do
        if port_is_listening "$port" && ! port_belongs_to_caddy "$port"; then
            fail "TCP port $port is already in use by another service. Stop or reconfigure that service before installing Wreckage."
        fi
    done
}

check_dns() {
    if getent ahosts "$domain" >/dev/null 2>&1; then
        note "DNS resolves for $domain."
    else
        echo "Warning: $domain does not currently resolve from this server." >&2
        echo "Point its A record, and any applicable AAAA record, to this VPS before HTTPS can succeed." >&2
    fi
}

ensure_volumes() {
    local volume
    for volume in wreckage-postgres-data wreckage-caddy-data wreckage-caddy-config; do
        docker_cmd volume create "$volume" >/dev/null
        docker_cmd volume inspect "$volume" >/dev/null \
            || fail "Docker could not create or inspect persistent volume $volume."
    done
}

container_state() {
    local service=$1 id
    id=$(compose ps -q "$service" 2>/dev/null || true)
    [[ -n $id ]] || { echo missing; return; }
    docker_cmd inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id"
}

wait_for_services() {
    local deadline=$((SECONDS + 240)) service state all_healthy
    local -a services=(postgres backend frontend caddy)
    while ((SECONDS < deadline)); do
        all_healthy=true
        for service in "${services[@]}"; do
            state=$(container_state "$service")
            case "$state" in
                healthy) ;;
                unhealthy|exited|dead)
                    compose logs --tail=100 "$service" >&2 || true
                    fail "The $service container entered state '$state'."
                    ;;
                *) all_healthy=false ;;
            esac
        done
        $all_healthy && return
        sleep 3
    done
    compose ps >&2 || true
    compose logs --tail=100 >&2 || true
    fail "Timed out waiting for the production containers to become healthy."
}

verify_internal_proxy() {
    local frontend_id response
    frontend_id=$(compose ps -q frontend)
    response=$(docker_cmd exec "$frontend_id" wget --quiet --output-document=- \
        http://127.0.0.1/games/running) \
        || fail "nginx could not reach the backend through the existing /games route."
    [[ $response =~ ^[[:space:]]*\[ ]] \
        || fail "The backend health response through nginx was not the expected JSON array."
}

verify_https() {
    local attempt response
    for attempt in {1..18}; do
        if response=$(curl --fail --silent --show-error --max-time 10 \
            "https://$domain/games/running" 2>/dev/null) && [[ $response =~ ^[[:space:]]*\[ ]]; then
            note "HTTPS verification succeeded: https://$domain"
            return
        fi
        sleep 5
    done

    echo >&2
    echo "Wreckage's containers are healthy, but HTTPS verification failed for https://$domain." >&2
    echo "Confirm that DNS points to this server and that inbound TCP 80 and 443 are allowed" >&2
    echo "by both the VPS provider firewall and the host firewall. No firewall or DNS settings were changed." >&2
    echo "Inspect certificate issuance with: ${docker_command[*]} compose --env-file .env -f compose.prod.yml logs caddy" >&2
    exit 1
}

main() {
    parse_domain "$@"
    detect_os
    install_required_utilities
    ensure_environment
    ensure_docker

    cd "$script_dir"
    note "Validating production Compose configuration"
    compose config --quiet
    check_ports
    check_dns
    ensure_volumes

    note "Building and starting Wreckage"
    if ! compose up --build --detach; then
        compose logs --tail=100 >&2 || true
        fail "Docker Compose could not start the production deployment."
    fi
    wait_for_services
    verify_internal_proxy
    verify_https

    echo
    echo "Wreckage is running at https://$domain"
    echo "Status: ${docker_command[*]} compose --env-file .env -f compose.prod.yml ps"
    echo "Logs:   ${docker_command[*]} compose --env-file .env -f compose.prod.yml logs -f"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
    main "$@"
fi
