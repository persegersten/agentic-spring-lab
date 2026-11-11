#!/usr/bin/env bash

set -euo pipefail

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    echo "Usage: $0 [in-memory] [deterministic-e2e]"
    echo "LOCAL DEVELOPMENT — Start the backend only. Press Ctrl+C to stop. Related: start.sh."
    echo "Requires Java 25; the full application also requires npm and frontend dependencies."
    exit 0
fi
if ! command -v java >/dev/null 2>&1; then
    echo "Java 25 is required for local development." >&2
    exit 1
fi

usage() {
    echo "Usage: $0 [in-memory] [deterministic-e2e]" >&2
}

profile=${1:-in-memory}
case "$profile" in
    in-memory)
        ;;
    *)
        echo "Unknown database profile: $profile" >&2
        usage
        exit 1
        ;;
esac

profiles=$profile
if (( $# )); then shift; fi
for optional_profile in "$@"; do
    if [[ $optional_profile != "deterministic-e2e" ]]; then
        echo "Unknown optional profile: $optional_profile" >&2
        usage
        exit 1
    fi
    profiles="$profiles,$optional_profile"
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$script_dir/backend"
echo "LOCAL DEVELOPMENT — backend — http://localhost:8080 — Ctrl+C stops the service."
exec ./mvnw spring-boot:run "-Dspring-boot.run.profiles=$profiles"
