#!/usr/bin/env bash
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
for script in "$repo"/scripts/{local-dev,local-dev-docker,production-debian-docker}/*.sh; do
    bash -n "$script"
    (cd /tmp && "$script" --help >/dev/null)
done
bash -n "$repo/scripts/lib/"*.sh

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
cat >"$tmp/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$SCRIPT_TEST_DOCKER_LOG"
DOCKER
chmod +x "$tmp/bin/docker"
export SCRIPT_TEST_DOCKER_LOG="$tmp/docker.log"
export PATH="$tmp/bin:$PATH"
for operation in start stop logs status; do
    (cd "$tmp" && "$repo/scripts/local-dev-docker/$operation.sh" >/dev/null)
done
for expected in 'up --build -d' down 'logs --follow --tail=200 --timestamps' ps; do
    grep -Fx -- "compose --project-directory $repo -f $repo/compose.yml $expected" "$SCRIPT_TEST_DOCKER_LOG" >/dev/null
done
(cd "$tmp" && "$repo/scripts/local-dev-docker/logs.sh" backend >/dev/null)
grep -Fx -- "compose --project-directory $repo -f $repo/compose.yml logs --follow --tail=200 --timestamps backend" "$SCRIPT_TEST_DOCKER_LOG" >/dev/null
before=$(wc -l <"$SCRIPT_TEST_DOCKER_LOG")
if "$repo/scripts/local-dev-docker/logs.sh" caddy >/dev/null 2>&1; then
    echo 'Local logs unexpectedly accepted a production-only service.' >&2; exit 1
fi
[[ $(wc -l <"$SCRIPT_TEST_DOCKER_LOG") -eq $before ]]
# Shared production paths must resolve from the helper regardless of caller cwd.
(cd "$tmp" && bash -c 'source "$1/scripts/lib/deployment-common.sh"; deployment_init; [[ $script_dir == "$1" && $compose_file == "$1/compose.prod.yml" ]]' _ "$repo")
printf 'All script syntax, help, and local Docker routing checks passed.\n'
