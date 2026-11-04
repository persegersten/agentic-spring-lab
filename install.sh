#!/usr/bin/env bash
set -Eeuo pipefail

# shellcheck source=scripts/deployment-common.sh
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/scripts/deployment-common.sh"
deployment_init

run_as_root() { run_privileged "$@"; }

usage() { echo "Usage: $0 --domain <hostname>" >&2; }
parse_domain() {
    [[ $# -eq 2 && $1 == --domain ]] || { usage; exit 2; }
    domain=${2,,}
    [[ $domain =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]] || fail "Invalid domain '$2'."
}
detect_os() {
    [[ -r /etc/os-release ]] || fail "Cannot identify this operating system."
    # shellcheck disable=SC1091
    . /etc/os-release
    case "${ID:-}:${VERSION_ID:-}" in ubuntu:22.04|ubuntu:24.04|ubuntu:26.04|debian:12|debian:13) ;; *) fail "Unsupported operating system: ${PRETTY_NAME:-unknown}." ;; esac
    [[ -n ${VERSION_CODENAME:-} ]] || fail "Operating-system codename is missing."
    if [[ $ID == debian && $VERSION_ID == 13 ]]; then
        [[ $VERSION_CODENAME == trixie ]] || fail "Debian 13 must use the trixie suite."
    fi
}
configure_docker_repository() {
    local arch tmp_key tmp_source
    arch=$(dpkg --print-architecture); tmp_key=$(mktemp); tmp_source=$(mktemp)
    trap 'rm -f "${tmp_key:-}" "${tmp_source:-}"' RETURN
    curl -fsSL "https://download.docker.com/linux/$ID/gpg" -o "$tmp_key"
    printf '%s\n' 'Types: deb' "URIs: https://download.docker.com/linux/$ID" "Suites: $VERSION_CODENAME" 'Components: stable' "Architectures: $arch" 'Signed-By: /etc/apt/keyrings/docker.asc' >"$tmp_source"
    run_as_root install -d -m 0755 /etc/apt/keyrings
    run_as_root install -m 0644 "$tmp_key" /etc/apt/keyrings/docker.asc
    run_as_root install -m 0644 "$tmp_source" /etc/apt/sources.list.d/docker.sources
    run_as_root apt-get update
}
install_required_utilities() {
    local -a packages=()
    command -v git >/dev/null || packages+=(git)
    command -v curl >/dev/null || packages+=(curl)
    command -v gpg >/dev/null || packages+=(gnupg)
    command -v ss >/dev/null || packages+=(iproute2)
    [[ -r /etc/ssl/certs/ca-certificates.crt ]] || packages+=(ca-certificates)
    if ((${#packages[@]})); then run_as_root apt-get update; run_as_root apt-get install --yes --no-install-recommends "${packages[@]}"; fi
}
ensure_docker() {
    local package status
    if command -v docker >/dev/null && { [[ $EUID -eq 0 ]] || command -v sudo >/dev/null; }; then
        if { [[ $EUID -eq 0 ]] && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; } \
            || { [[ $EUID -ne 0 ]] && sudo docker info >/dev/null 2>&1 && sudo docker compose version >/dev/null 2>&1; }; then
            select_docker_command
            return
        fi
    fi
    for package in docker.io docker-compose docker-doc docker-buildx podman-docker containerd runc; do
        status=$(dpkg-query -W -f='${Status}' "$package" 2>/dev/null || true)
        [[ $status != 'install ok installed' ]] || fail "Conflicting package $package is installed. Replace it with Docker's official packages before retrying."
    done
    local needs_install=false
    for package in docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin; do
        status=$(dpkg-query -W -f='${Status}' "$package" 2>/dev/null || true)
        [[ $status == 'install ok installed' ]] || needs_install=true
    done
    if $needs_install; then
        configure_docker_repository
        run_as_root apt-get install --yes --no-install-recommends docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    fi
    command -v systemctl >/dev/null && run_as_root systemctl enable --now docker
    select_docker_command
}
check_ports() {
    local port caddy_id
    caddy_id=$(compose ps -q caddy 2>/dev/null || true)
    for port in 80 443; do
        if ss -H -ltn | awk -v p=":$port" '$4 ~ p "$" {found=1} END{exit !found}'; then
            [[ -n $caddy_id ]] && docker_cmd port "$caddy_id" "$port/tcp" 2>/dev/null | grep -Eq "(^|:)$port$" \
                || fail "TCP port $port is already used by another service."
        fi
    done
}
ensure_environment() {
    if run_as_root test -e "$env_file" || run_as_root test -e "$password_file"; then
        run_as_root test -f "$env_file" || fail "$env_file is not a regular file."
        run_as_root test -f "$password_file" || fail "$password_file is not a regular file."
        [[ $(env_value DOMAIN) == "$domain" ]] || fail "Existing production.env belongs to another domain."
        [[ $(env_value SPRING_PROFILES_ACTIVE) == postgres ]] || fail "Production must use the postgres profile."
        return
    fi
    local password env_tmp password_tmp
    password=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
    env_tmp=$(mktemp); password_tmp=$(mktemp); umask 077
    printf 'DOMAIN=%s\nPOSTGRES_DB=wreckage\nPOSTGRES_USER=wreckage\nPOSTGRES_PASSWORD_FILE=/etc/wreckage/postgres-password\nDB_URL=jdbc:postgresql://postgres:5432/wreckage\nSPRING_PROFILES_ACTIVE=postgres\nCADDY_IMAGE=caddy:2.10.2-alpine\n' "$domain" >"$env_tmp"
    printf '%s' "$password" >"$password_tmp"
    run_as_root install -d -o root -g root -m 0700 "$config_dir"
    run_as_root install -o root -g root -m 0600 "$env_tmp" "$env_file"
    run_as_root install -o root -g root -m 0600 "$password_tmp" "$password_file"
    rm -f "$env_tmp" "$password_tmp"
}
main() {
    parse_domain "$@"
    [[ $EUID -ne 0 && $(id -un) == admin ]] || fail "Run this script as the admin account without prefixing the script with sudo."
    [[ $(readlink -f "$script_dir") == /opt/wreckage ]] || fail "The production checkout must be /opt/wreckage."
    detect_os; install_required_utilities; ensure_environment; ensure_docker; cd "$script_dir"
    validate_installation
    local revision
    revision=$(git rev-parse HEAD 2>/dev/null || echo initial)
    check_ports
    getent ahosts "$domain" >/dev/null 2>&1 || echo "Warning: $domain does not currently resolve from this server." >&2
    for volume in wreckage-postgres-data wreckage-caddy-data wreckage-caddy-config; do docker_cmd volume create "$volume" >/dev/null; done
    note "Building revision $revision"
    WRECKAGE_IMAGE_TAG=$revision compose build frontend backend
    WRECKAGE_IMAGE_TAG=$revision compose up -d
    WRECKAGE_IMAGE_TAG=$revision wait_for_services postgres backend frontend caddy || fail "Containers did not become healthy; inspect Compose logs."
    WRECKAGE_IMAGE_TAG=$revision verify_internal_proxy || fail "Internal application routing failed."
    WRECKAGE_IMAGE_TAG=$revision verify_https || fail "HTTPS verification failed; check DNS, firewall, and Caddy logs."
    write_metadata "$revision" "" healthy
    echo "Wreckage is healthy at https://$domain (revision $revision)."
}
main "$@"
