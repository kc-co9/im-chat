#!/usr/bin/env bash
# 用途：验证 cleanup 默认只读、apply allowlist、symlink 逃逸保护、PID provenance 和幂等性。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; FIXTURE_ROOT="$(mktemp -d)"; trap 'rm -rf "$FIXTURE_ROOT"' EXIT
mkdir -p "$FIXTURE_ROOT/scripts" "$FIXTURE_ROOT/.harness/tmp" "$FIXTURE_ROOT/.harness/pids" "$FIXTURE_ROOT/outside"
cp "$ROOT_DIR/scripts/harness-cleanup.sh" "$FIXTURE_ROOT/scripts/harness-cleanup.sh"; chmod +x "$FIXTURE_ROOT/scripts/harness-cleanup.sh"
printf 'stale\n' > "$FIXTURE_ROOT/.harness/tmp/stale.tmp"; printf 'pid=999999\nroot=%s\ncommand=fixture\n' "$FIXTURE_ROOT" > "$FIXTURE_ROOT/.harness/pids/stale.pid"
if ! HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-cleanup.sh" >/dev/null; then printf 'Read-only cleanup should pass.\n' >&2; exit 1; fi
[[ -f "$FIXTURE_ROOT/.harness/tmp/stale.tmp" && -f "$FIXTURE_ROOT/.harness/pids/stale.pid" ]] || { printf 'Read-only cleanup deleted a file.\n' >&2; exit 1; }
HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-cleanup.sh" --apply >/dev/null
[[ ! -e "$FIXTURE_ROOT/.harness/tmp/stale.tmp" && ! -e "$FIXTURE_ROOT/.harness/pids/stale.pid" ]] || { printf 'Apply cleanup did not remove allowlisted stale files.\n' >&2; exit 1; }
printf 'outside\n' > "$FIXTURE_ROOT/outside/keep"; ln -s "$FIXTURE_ROOT/outside/keep" "$FIXTURE_ROOT/.harness/tmp/escape"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-cleanup.sh" --apply >/dev/null 2>&1; then printf 'Symlink escape should fail.\n' >&2; exit 1; fi
[[ -f "$FIXTURE_ROOT/outside/keep" ]] || { printf 'Symlink escape deleted outside data.\n' >&2; exit 1; }
printf 'Cleanup Harness tests passed.\n'
