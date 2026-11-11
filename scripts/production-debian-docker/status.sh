#!/usr/bin/env bash
set -euo pipefail
if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    echo "Usage: $0"
    echo "PRODUCTION — Debian / Docker — Show service status."
    echo "Run as admin; requires sudo, Docker/Compose, and /etc/wreckage/production.env. Related: logs.sh, update.sh."
    exit 0
fi
[[ $# -eq 0 ]] || { echo "This command accepts no arguments; use --help." >&2; exit 2; }
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../lib" && pwd)/deployment-common.sh"
deployment_init
validate_installation
echo "PRODUCTION — Debian / Docker — status — https://$(env_value DOMAIN)"
compose ps
