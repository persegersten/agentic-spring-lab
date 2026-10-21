#!/usr/bin/env bash
set -Eeuo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
failures=0
pass() { echo "ok - $1"; }
fail_test() { echo "not ok - $1"; failures=$((failures + 1)); }
assert() { local name=$1; shift; if "$@"; then pass "$name"; else fail_test "$name"; fi; }

assert "deployment scripts parse" bash -n "$repo/install.sh" "$repo/update.sh" "$repo/scripts/deployment-common.sh"
assert "invalid update mode is refused" bash -c '! "$1/update.sh" --automatic >/dev/null 2>&1' _ "$repo"
assert "normal updates use fast-forward only" grep -q 'git merge --ff-only' "$repo/update.sh"
assert "dirty tracked and untracked files are checked" grep -q 'git ls-files --others --exclude-standard' "$repo/update.sh"
assert "application build precedes recreation" awk '/compose build frontend backend/{b=NR} /compose up -d --no-deps backend/{u=NR} END{exit !(b && u && b<u)}' "$repo/update.sh"
assert "Compose never removes volumes" bash -c '! grep -E "down.*(-v|--volumes)|volume (rm|prune)" "$1/install.sh" "$1/update.sh"' _ "$repo"
assert "destructive Flyway operations are absent" bash -c '! grep -Ei "flyway (clean|repair|baseline)|baselineOnMigrate" "$1/install.sh" "$1/update.sh"' _ "$repo"
assert "migration backups use pg_dump" grep -q pg_dump "$repo/update.sh"
assert "migration backups are verified" grep -q 'pg_restore --list' "$repo/update.sh"
assert "platform changes require confirmation" grep -q 'confirm "Apply only the platform updates' "$repo/update.sh"
assert "platform packages are explicit" grep -q 'apt-get install.*docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin' "$repo/update.sh"

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT; mkdir -p "$tmp/.wreckage"
(
    # shellcheck source=../scripts/deployment-common.sh
    source "$repo/scripts/deployment-common.sh"
    script_dir=$tmp; state_dir="$tmp/.wreckage"; metadata_file="$state_dir/deployment.env"
    write_metadata new-revision old-revision healthy
    # shellcheck disable=SC1090
    source "$metadata_file"
    [[ $DEPLOYED_REVISION == new-revision && $PREVIOUS_REVISION == old-revision && $HEALTH_STATUS == healthy ]]
) && pass "version metadata records current and previous revisions" || fail_test "version metadata records current and previous revisions"

if command -v docker >/dev/null 2>&1; then
    assert "production Compose configuration is valid" docker compose --env-file "$repo/.env.example" -f "$repo/compose.prod.yml" config --quiet
else
    pass "production Compose validation skipped (Docker unavailable)"
fi
((failures == 0)) || { echo "$failures test(s) failed" >&2; exit 1; }
echo "All update safety tests passed."
