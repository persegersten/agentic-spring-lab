#!/usr/bin/env bash
set -Eeuo pipefail

# shellcheck source=scripts/deployment-common.sh
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/scripts/deployment-common.sh"
deployment_init
stage=initialization
attempted_revision=
failure_reported=false

on_error() {
    local status=${1:-$?}
    trap - ERR
    failure_reported=true
    echo "Update failed during: $stage" >&2
    [[ -n ${docker_command[*]:-} ]] && compose logs --tail=150 >&2 || true
    echo "Persistent volumes and the recorded deployment reference were not changed." >&2
    [[ -n $attempted_revision ]] && echo "Attempted revision: $attempted_revision" >&2
    [[ -f $metadata_file ]] && { read_metadata; echo "Recorded healthy revision: $DEPLOYED_REVISION" >&2; } || true
    echo "After diagnosing the failure, retry ./update.sh or review the rollback procedure in docs/deployment/linux.md." >&2
    exit "$status"
}
trap on_error ERR
on_exit() {
    local status=$?
    if ((status != 0)) && ! $failure_reported; then on_error "$status"; fi
}
trap on_exit EXIT

usage() { echo "Usage: $0 [--platform|--rollback]" >&2; }
confirm() { [[ -t 0 ]] || fail "$1 requires an interactive terminal."; read -r -p "$1 [y/N] " answer; [[ $answer == y || $answer == Y ]]; }
require_clean_tree() {
    git diff --quiet && git diff --cached --quiet && [[ -z $(git ls-files --others --exclude-standard) ]] || fail "Git working tree has local changes; commit or remove them explicitly before updating."
}
lock_update() {
    mkdir -p "$state_dir"
    command -v flock >/dev/null || fail "flock is required to serialize updates."
    exec 9>"$state_dir/update.lock"
    flock -n 9 || fail "Another installation or update operation is running."
}
ensure_volumes() {
    local volume
    for volume in wreckage-postgres-data wreckage-caddy-data wreckage-caddy-config; do
        docker_cmd volume inspect "$volume" >/dev/null 2>&1 || fail "Required persistent volume $volume is missing."
    done
}
check_migrations_and_backup() {
    local old=$1 new=$2 changes backup
    changes=$(git diff --name-status "$old..$new" -- backend/src/main/resources/db/migration/)
    [[ -n $changes ]] || return 0
    if awk '$1 != "A" {exit 1}' <<<"$changes"; then :; else
        fail "Existing Flyway migrations changed or were removed. Historical migrations are immutable."
    fi
    stage="verified database backup"
    mkdir -p "$state_dir/backups"
    backup="$state_dir/backups/wreckage-${old:0:12}-$(date -u +%Y%m%dT%H%M%SZ).dump"
    note "New Flyway migrations detected; creating $backup"
    compose exec -T postgres pg_dump -U "$(env_value POSTGRES_USER)" -d "$(env_value POSTGRES_DB)" -Fc >"$backup"
    [[ -s $backup ]] || fail "Database backup is empty."
    docker_cmd run --rm -i postgres:17 pg_restore --list <"$backup" >/dev/null || fail "Database backup verification failed."
    note "Verified database backup: $backup"
}
report_status() {
    read_metadata
    printf 'Previous deployed revision: %s\nNew deployed revision: %s\nDeployment timestamp: %s\nApplication health: %s\n' \
        "${PREVIOUS_REVISION:-none}" "$DEPLOYED_REVISION" "$DEPLOYED_AT" "$HEALTH_STATUS"
    compose ps
}
application_update() {
    stage="installation validation"; validate_installation; lock_update; ensure_volumes
    [[ -d $script_dir/.git ]] || fail "The installation is not a Git checkout."
    require_clean_tree; read_metadata
    local old upstream remote target caddy_changed=false
    old=$DEPLOYED_REVISION
    git cat-file -e "$old^{commit}" 2>/dev/null || fail "Recorded revision $old is not available locally."
    upstream=$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null) || fail "The current branch has no configured upstream."
    remote=${upstream%%/*}
    stage="Git fetch"; note "Fetching $upstream"; git fetch --prune "$remote"
    target=$(git rev-parse "$upstream"); attempted_revision=$target
    git merge-base --is-ancestor "$old" "$target" || fail "Upstream is not a fast-forward from deployed revision $old."
    if [[ $old == "$target" ]]; then note "Wreckage is already up to date at $old."; report_status; return; fi
    git diff --quiet "$old..$target" -- Caddyfile compose.prod.yml || caddy_changed=true
    check_migrations_and_backup "$old" "$target"
    stage="fast-forward checkout"; git merge --ff-only "$target"
    stage="Docker image build"; note "Building $target while the current application remains running"
    WRECKAGE_IMAGE_TAG=$target compose build frontend backend
    stage="application container startup"
    WRECKAGE_IMAGE_TAG=$target compose up -d --no-deps backend
    WRECKAGE_IMAGE_TAG=$target wait_for_services backend
    WRECKAGE_IMAGE_TAG=$target compose up -d --no-deps frontend
    WRECKAGE_IMAGE_TAG=$target wait_for_services frontend
    if $caddy_changed; then
        WRECKAGE_IMAGE_TAG=$target compose up -d --no-deps caddy
        WRECKAGE_IMAGE_TAG=$target wait_for_services caddy
    fi
    stage="health verification"
    WRECKAGE_IMAGE_TAG=$target verify_internal_proxy
    WRECKAGE_IMAGE_TAG=$target wait_for_services postgres backend frontend caddy
    WRECKAGE_IMAGE_TAG=$target verify_https
    write_metadata "$target" "$old" healthy
    trap - ERR
    report_status
}

rollback() {
    stage="rollback validation"; validate_installation; lock_update; ensure_volumes; read_metadata
    [[ -n ${PREVIOUS_REVISION:-} ]] || fail "No previous deployed revision is recorded."
    docker_cmd image inspect "wreckage-backend:$PREVIOUS_REVISION" "wreckage-frontend:$PREVIOUS_REVISION" >/dev/null || fail "Previous revision images are unavailable."
    echo "WARNING: this rolls back application images only. It does not roll back Flyway migrations or PostgreSQL data."
    confirm "Deploy application images for $PREVIOUS_REVISION?" || { note "Rollback cancelled."; return; }
    local current=$DEPLOYED_REVISION
    stage="application-image rollback"
    WRECKAGE_IMAGE_TAG=$PREVIOUS_REVISION compose up -d --no-deps backend frontend
    stage="rollback health verification"
    WRECKAGE_IMAGE_TAG=$PREVIOUS_REVISION wait_for_services backend frontend
    WRECKAGE_IMAGE_TAG=$PREVIOUS_REVISION verify_internal_proxy
    WRECKAGE_IMAGE_TAG=$PREVIOUS_REVISION verify_https
    write_metadata "$PREVIOUS_REVISION" "$current" healthy
    trap - ERR; report_status
}

run_as_root() { if [[ $EUID -eq 0 ]]; then "$@"; else command -v sudo >/dev/null || fail "sudo is required for package maintenance."; sudo "$@"; fi; }
set_env_value() {
    local key=$1 value=$2 tmp
    tmp=$(mktemp "$script_dir/.env.tmp.XXXXXX")
    awk -F= -v k="$key" -v v="$value" 'BEGIN{done=0} $1==k {print k "=" v; done=1; next} {print} END{if(!done) print k "=" v}' "$env_file" >"$tmp"
    chmod 600 "$tmp"; mv "$tmp" "$env_file"
}
platform_update() {
    stage="platform inspection"; validate_installation; lock_update; ensure_volumes
    [[ -r /etc/os-release ]] || fail "Cannot identify this operating system."
    # shellcheck disable=SC1091
    . /etc/os-release
    case "${ID:-}:${VERSION_ID:-}" in ubuntu:22.04|ubuntu:24.04|ubuntu:26.04|debian:12|debian:13) ;; *) fail "Unsupported platform for automated Docker maintenance: ${PRETTY_NAME:-unknown}." ;; esac
    local docker_version compose_version current_caddy supported_caddy candidate installed proposed=false
    docker_version=$(docker_cmd version --format '{{.Server.Version}}'); compose_version=$(docker_cmd compose version --short)
    current_caddy=$(env_value CADDY_IMAGE); current_caddy=${current_caddy:-caddy:2.10.2-alpine}; supported_caddy=caddy:2.10.2-alpine
    echo "Installed Docker Engine: $docker_version"; echo "Installed Compose: $compose_version"; echo "Configured Caddy: $current_caddy"
    if [[ ! -r /etc/apt/sources.list.d/docker.sources ]] || ! grep -q 'download.docker.com' /etc/apt/sources.list.d/docker.sources; then
        echo "Docker's official apt repository is not configured; package changes are not offered. See docs/deployment/linux.md."
        candidate=
    else
        candidate=$(apt-cache policy docker-ce 2>/dev/null | awk '/Candidate:/ {print $2}')
        installed=$(apt-cache policy docker-ce 2>/dev/null | awk '/Installed:/ {print $2}')
        [[ -n $candidate && $candidate != '(none)' && $candidate != "$installed" ]] && { echo "Proposed Docker packages: $installed -> $candidate"; proposed=true; }
    fi
    local caddy_update=false
    case $current_caddy in
        "$supported_caddy") ;;
        caddy:2-alpine|caddy:2.[0-9]*-alpine)
            docker_cmd manifest inspect "$supported_caddy" >/dev/null 2>&1 \
                && { echo "Proposed Caddy image: $current_caddy -> $supported_caddy"; proposed=true; caddy_update=true; } \
                || echo "Supported Caddy image is unavailable; no Caddy change will be made."
            ;;
        *) echo "Configured Caddy image is not a recognized older supported pin; update it manually after compatibility review." ;;
    esac
    $proposed || { note "No supported platform updates are currently available."; return; }
    confirm "Apply only the platform updates shown above? Brief service interruption may occur." || { note "Platform update cancelled; nothing changed."; return; }
    if [[ -n ${candidate:-} && $candidate != '(none)' && $candidate != "${installed:-}" ]]; then
        stage="Docker package update"
        run_as_root apt-get install --yes --no-install-recommends docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
        select_docker_command
    fi
    if $caddy_update; then
        stage="Caddy image update"; docker_cmd pull "$supported_caddy"
        CADDY_IMAGE=$supported_caddy compose up -d --no-deps caddy
        CADDY_IMAGE=$supported_caddy wait_for_services caddy
        CADDY_IMAGE=$supported_caddy verify_https
        set_env_value CADDY_IMAGE "$supported_caddy"
    fi
    trap - ERR; note "Platform maintenance completed and HTTPS is healthy."
}

case $#:$* in 0:) application_update ;; 1:--platform) platform_update ;; 1:--rollback) rollback ;; *) usage; exit 2 ;; esac
