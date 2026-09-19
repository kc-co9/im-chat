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
rm "$FIXTURE_ROOT/.harness/tmp/escape"

mkdir -p "$FIXTURE_ROOT/.harness/load-test/abc123" "$FIXTURE_ROOT/.harness/load-test/runner.lock"
printf '{"accessToken":"secret"}\n' > "$FIXTURE_ROOT/.harness/load-test/abc123/credentials-round.json"
printf '{"decision":{"status":"stable"}}\n' > "$FIXTURE_ROOT/.harness/load-test/abc123/smoke-summary.json"
printf '{"version":1,"state":"DRAINING","batchId":"abc123"}\n' > "$FIXTURE_ROOT/.harness/load-test/runtime-state.jsonl"
printf '999999\n' > "$FIXTURE_ROOT/.harness/load-test/runner.lock/pid"
if load_cleanup_output="$(HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-cleanup.sh" 2>&1)"; then
  printf 'Interrupted load-test state must require explicit recovery.\n' >&2
  exit 1
fi
for expected in 'abc123' 'DRAINING' 'cleanup --resume abc123' 'runner.lock'; do
  rg -Fq "$expected" <<<"$load_cleanup_output" || {
    printf 'Load-test cleanup diagnostic is missing %s.\n%s\n' "$expected" "$load_cleanup_output" >&2
    exit 1
  }
done
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-cleanup.sh" --apply >/dev/null 2>&1; then
  printf 'Apply must not silently delete interrupted load-test state.\n' >&2
  exit 1
fi
[[ -f "$FIXTURE_ROOT/.harness/load-test/abc123/credentials-round.json" \
  && -f "$FIXTURE_ROOT/.harness/load-test/abc123/smoke-summary.json" ]] || {
  printf 'Harness cleanup deleted load-test credentials or evidence.\n' >&2
  exit 1
}
rm "$FIXTURE_ROOT/.harness/load-test/abc123/credentials-round.json"
rm -rf "$FIXTURE_ROOT/.harness/load-test/runner.lock"
printf '{"version":1,"state":"BASE_READY","batchId":null}\n' > "$FIXTURE_ROOT/.harness/load-test/runtime-state.jsonl"

printf '{"database":"secret"}\n' > "$FIXTURE_ROOT/.harness/load-test/abc123/database-targets.json"
if base_ready_sensitive_output="$(HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-cleanup.sh" 2>&1)"; then
  printf 'Sensitive load-test files must require explicit cleanup even in BASE_READY.\n' >&2
  exit 1
fi
for expected in '压测敏感或恢复文件仍存在' 'abc123' 'cleanup --resume abc123'; do
  rg -Fq "$expected" <<<"$base_ready_sensitive_output" || {
    printf 'BASE_READY sensitive-file diagnostic is missing %s.\n%s\n' "$expected" "$base_ready_sensitive_output" >&2
    exit 1
  }
done
if rg -Fq 'unbound variable' <<<"$base_ready_sensitive_output"; then
  printf 'Sensitive-file diagnostic must not fail with an unbound variable.\n%s\n' "$base_ready_sensitive_output" >&2
  exit 1
fi
rm "$FIXTURE_ROOT/.harness/load-test/abc123/database-targets.json"
HARNESS_ROOT_DIR="$FIXTURE_ROOT" "$FIXTURE_ROOT/scripts/harness-cleanup.sh" --apply >/dev/null
[[ -f "$FIXTURE_ROOT/.harness/load-test/abc123/smoke-summary.json" ]] || {
  printf 'Harness cleanup must retain completed load-test reports.\n' >&2
  exit 1
}
printf 'Cleanup Harness tests passed.\n'
