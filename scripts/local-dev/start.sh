#!/usr/bin/env bash

set -euo pipefail

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    echo "Usage: $0 [in-memory]"
    echo "LOCAL DEVELOPMENT — Start backend and Vite frontend. Press Ctrl+C to stop both. Related: start-backend.sh."
    echo "Requires Java 25; the full application also requires npm and frontend dependencies."
    exit 0
fi
if ! command -v java >/dev/null 2>&1; then
    echo "Java 25 is required for local development." >&2
    exit 1
fi

if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [in-memory]" >&2
    exit 1
fi

case "${1:-in-memory}" in
    in-memory) ;;
    *)
        echo "Unknown database profile: $1" >&2
        echo "Usage: $0 [in-memory]" >&2
        exit 1
        ;;
esac

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
if ! command -v npm >/dev/null 2>&1; then
    echo "npm is required to start the frontend." >&2
    exit 1
fi
if [[ ! -d "$script_dir/frontend/node_modules" ]]; then
    echo "Install frontend dependencies first: cd \"$script_dir/frontend\" && npm ci" >&2
    exit 1
fi

# Give each service its own process group so cleanup also stops its children.
set -m
service_pids=()
cleanup() {
    trap '' INT TERM
    for pid in "${service_pids[@]}"; do
        kill -TERM -- "-$pid" 2>/dev/null || true
    done
    wait || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$script_dir/scripts/local-dev/start-backend.sh" "$@" &
service_pids+=("$!")
(
    cd "$script_dir/frontend"
    exec npm run dev -- --host 0.0.0.0
) &
service_pids+=("$!")

echo "LOCAL DEVELOPMENT — backend and frontend. Open the URL printed by Vite. Press Ctrl+C to stop both."
# If either service exits, stop the other and preserve the exit status.
wait -n "${service_pids[@]}"
