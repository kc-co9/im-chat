#!/usr/bin/env bash
# 用途：自测 routing-ha runner 对成功、零场景、子进程失败、超时和残留资源的传播契约。
# 输入：无位置参数；通过临时 fixture 注入场景命令和状态目录。
# 输出/副作用：只写入 mktemp 目录并在退出时删除，不启动仓库 Compose。
# 依赖：bash、mktemp、Ruby，以及 scripts/routing_ha.sh。
# 退出码：全部正反例符合 fail-closed 契约时返回 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNNER="$ROOT_DIR/scripts/routing_ha.sh"
SCENARIO_RUNNER="$ROOT_DIR/scripts/run-routing-ha-scenarios.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

restart_timeout_default="$(ruby -e '
  source = File.read(ARGV.fetch(0))
  match = source.match(/RESTART_READY_TIMEOUT_SECONDS="\$\{ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS:-(\d+)\}"/)
  abort "routing-ha restart readiness timeout must be configurable" unless match
  puts match[1]
' "$SCENARIO_RUNNER")"
if (( restart_timeout_default < 120 )); then
  printf 'routing-ha restart readiness timeout must cover the observed slow startup budget.\n' >&2
  exit 1
fi
restart_timeout_uses="$(rg -c 'poll_http .* "\$RESTART_READY_TIMEOUT_SECONDS"' "$SCENARIO_RUNNER")"
if (( restart_timeout_uses != 2 )); then
  printf 'Gateway and Broker restart probes must use the configured readiness timeout.\n' >&2
  exit 1
fi

ruby -ryaml -e '
  compose = YAML.load_file(ARGV.fetch(0))
  health = compose.fetch("services").fetch("routing-ha-nacos").fetch("healthcheck").fetch("test").last
  abort "routing-ha Nacos readiness must include naming gRPC" unless health.include?("9848")
' "$ROOT_DIR/deploy/local/compose.routing-ha.yml"

run_fixture() {
  local name="$1"
  local command="$2"
  local timeout_seconds="${3:-15}"
  local state_dir="$FIXTURE_ROOT/$name/state"
  local detail="$FIXTURE_ROOT/$name/detail.json"
  mkdir -p "$state_dir"
  ROUTING_HA_SCENARIO_COMMAND="$command" \
    ROUTING_HA_STATE_DIR="$state_dir" \
    ROUTING_HA_DETAIL_PATH="$detail" \
    ROUTING_HA_TIMEOUT_SECONDS="$timeout_seconds" \
    "$RUNNER" verify
}

success_command="$FIXTURE_ROOT/success.sh"
cat > "$success_command" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
for index in $(seq 1 25); do
  printf '{"id":"RH-%02d","name":"fixture","status":"passed","evidenceKind":"fixture","evidence":"fixture","assertion":"fixture passed","durationMillis":1,"finishedAt":"2026-09-21T00:00:00Z"}\n' "$index"
done > "$ROUTING_HA_STATE_DIR/scenarios.jsonl"
EOF
chmod +x "$success_command"
run_fixture success "$success_command"

zero_command="$FIXTURE_ROOT/zero.sh"
cat > "$zero_command" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
: > "$ROUTING_HA_STATE_DIR/scenarios.jsonl"
EOF
chmod +x "$zero_command"
if run_fixture zero "$zero_command"; then
  printf 'routing-ha must reject zero executed scenarios.\n' >&2
  exit 1
fi

failure_command="$FIXTURE_ROOT/failure.sh"
cat > "$failure_command" <<'EOF'
#!/usr/bin/env bash
exit 23
EOF
chmod +x "$failure_command"
if run_fixture failure "$failure_command"; then
  printf 'routing-ha must propagate a scenario command failure.\n' >&2
  exit 1
fi

timeout_command="$FIXTURE_ROOT/timeout.sh"
cat > "$timeout_command" <<'EOF'
#!/usr/bin/env bash
exec sleep 30
EOF
chmod +x "$timeout_command"
set +e
run_fixture timeout "$timeout_command" 1
timeout_status=$?
set -e
if (( timeout_status != 124 )); then
  printf 'routing-ha must fail when the scenario command times out.\n' >&2
  exit 1
fi

leftover_command="$FIXTURE_ROOT/leftover.sh"
cat > "$leftover_command" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
for index in $(seq 1 25); do
  printf '{"id":"RH-%02d","name":"fixture","status":"passed","evidenceKind":"fixture","evidence":"fixture","assertion":"fixture passed","durationMillis":1,"finishedAt":"2026-09-21T00:00:00Z"}\n' "$index"
done > "$ROUTING_HA_STATE_DIR/scenarios.jsonl"
printf '{"kind":"container","id":"broker-orphan"}\n' \
  > "$ROUTING_HA_STATE_DIR/resources.jsonl"
EOF
chmod +x "$leftover_command"
if run_fixture leftover "$leftover_command"; then
  printf 'routing-ha must reject a non-empty owned-resource ledger.\n' >&2
  exit 1
fi

printf 'routing-ha Harness self-test passed.\n'
