#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 <postgres|in-memory> [deterministic-e2e]" >&2
}

if [[ $# -lt 1 ]]; then
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
shift
for optional_profile in "$@"; do
    if [[ $optional_profile != "deterministic-e2e" ]]; then
        echo "Unknown optional profile: $optional_profile" >&2
        usage
        exit 1
    fi
    profiles="$profiles,$optional_profile"
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir/backend"
exec ./mvnw spring-boot:run "-Dspring-boot.run.profiles=$profiles"
