#!/usr/bin/env bash

local_docker_init() {
    repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
    command -v docker >/dev/null 2>&1 || { echo "Docker is required for local Docker development." >&2; exit 1; }
    docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is required." >&2; exit 1; }
    docker info >/dev/null 2>&1 || { echo "Docker must be running and accessible." >&2; exit 1; }
}
local_compose() {
    docker compose --project-directory "$repo_dir" -f "$repo_dir/compose.yml" "$@"
}
