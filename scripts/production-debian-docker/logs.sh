#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 [backend|frontend|caddy ...]"
    echo "PRODUCTION — Debian / Docker. Follow production logs with timestamps, starting with the last 200 lines per service."
    echo "Run as admin; requires sudo, Docker/Compose, and /etc/wreckage/production.env. Related: status.sh, update.sh."
    echo "With no services specified, follow all services. Press Ctrl+C to stop."
}

for service in "$@"; do
    case "$service" in
        backend|frontend|caddy) ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown service: $service" >&2; usage >&2; exit 1 ;;
    esac
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
# shellcheck source=scripts/lib/deployment-common.sh
source "$script_dir/scripts/lib/deployment-common.sh"
deployment_init
validate_installation
echo "PRODUCTION — Debian / Docker — logs — https://$(env_value DOMAIN)"
compose logs --follow --tail=200 --timestamps "$@"
