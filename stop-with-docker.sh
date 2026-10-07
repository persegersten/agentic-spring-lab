#!/usr/bin/env bash

set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir"

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to stop Wreckage." >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "The Docker Compose plugin is required to stop Wreckage." >&2
    exit 1
fi

docker compose down

echo "Wreckage stopped."
