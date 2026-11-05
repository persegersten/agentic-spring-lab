#!/usr/bin/env bash

set -euo pipefail

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

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir/backend"
exec ./mvnw spring-boot:run "-Dspring-boot.run.profiles=$profiles"
