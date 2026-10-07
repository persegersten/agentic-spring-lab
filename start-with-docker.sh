#!/usr/bin/env bash

set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir"

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to start Wreckage." >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "The Docker Compose plugin is required to start Wreckage." >&2
    exit 1
fi

docker compose up --build -d

echo
echo "Wreckage started."
echo "Local: http://localhost"
echo
echo "For external alpha testing, configure your router:"
echo "TCP 80 -> this laptop's TCP 80"
echo
