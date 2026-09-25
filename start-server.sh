#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 <postgres|in-memory> [headless-players]" >&2
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
    usage
    exit 1
fi

profile=$1
case "$profile" in
    postgres|in-memory)
        ;;
    *)
        echo "Unknown database profile: $profile" >&2
        usage
        exit 1
        ;;
esac

profiles=$profile
if [[ $# -eq 2 ]]; then
    if [[ $2 != "headless-players" ]]; then
        echo "Unknown optional profile: $2" >&2
        usage
        exit 1
    fi
    profiles="$profiles,$2"
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir/backend"
exec ./mvnw spring-boot:run "-Dspring-boot.run.profiles=$profiles"
