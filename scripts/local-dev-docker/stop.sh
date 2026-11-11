#!/usr/bin/env bash
set -euo pipefail
if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    echo "Usage: $0"
    echo "LOCAL DEVELOPMENT — Docker — Stop the local Docker application."
    echo "Requires Docker Engine and Compose v2. Related: start.sh, stop.sh, logs.sh, status.sh in this folder."
    exit 0
fi
[[ $# -eq 0 ]] || { echo "This command accepts no arguments; use --help." >&2; exit 2; }
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../lib" && pwd)/local-docker-common.sh"
local_docker_init
echo "LOCAL DEVELOPMENT — Docker — stop — http://localhost"
local_compose down
echo "Local Docker application stopped."
