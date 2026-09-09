#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 <postgres|in-memory>" >&2
}

if [[ $# -ne 1 ]]; then
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

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir/backend"
exec ./mvnw spring-boot:run "-Dspring-boot.run.profiles=$profile"
