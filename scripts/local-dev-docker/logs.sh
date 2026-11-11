#!/usr/bin/env bash
set -euo pipefail
if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    echo "Usage: $0 [backend|frontend ...]"
    echo "LOCAL DEVELOPMENT — Docker — Follow local logs (last 200 lines). Optional services: backend frontend. Ctrl+C stops viewing logs."
    echo "Requires Docker Engine and Compose v2. Related: start.sh, stop.sh, logs.sh, status.sh in this folder."
    exit 0
fi
for service in "$@"; do
    case "$service" in backend|frontend) ;; *) echo "Unknown service: $service" >&2; exit 2 ;; esac
done
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../lib" && pwd)/local-docker-common.sh"
local_docker_init
echo "LOCAL DEVELOPMENT — Docker — logs — http://localhost"
local_compose logs --follow --tail=200 --timestamps "$@"
