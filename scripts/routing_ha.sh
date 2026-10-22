#!/usr/bin/env bash
# 用途：运行 3 Broker + 2 Gateway routing-ha 场景，并校验场景数量、结果与 Harness 自有资源清理。
# 输入：位置参数固定为 verify；ROUTING_HA_SCENARIO_COMMAND 指定实际场景入口。
# 输出/副作用：场景在 ROUTING_HA_STATE_DIR 写 scenarios.jsonl、resources.jsonl 与诊断；成功写 detail JSON。
# 隔离：实际场景必须只创建登记在 resources.jsonl 的专用进程、容器、网络和 volume，并在返回前清空 ledger。
# 退出码：场景命令退出码原样传播；超时返回 124；场景/清理证据不完整返回 1；参数错误返回 2。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ACTION="${1:-}"
STATE_DIR="${ROUTING_HA_STATE_DIR:-$ROOT_DIR/.harness/routing-ha}"
DETAIL_PATH="${ROUTING_HA_DETAIL_PATH:-$ROOT_DIR/.harness/verifications/routing-ha-detail.json}"
SCENARIO_COMMAND="${ROUTING_HA_SCENARIO_COMMAND:-$ROOT_DIR/scripts/run-routing-ha-scenarios.sh}"
TIMEOUT_SECONDS="${ROUTING_HA_TIMEOUT_SECONDS:-900}"

if [[ "$ACTION" != "verify" ]]; then
  printf 'Usage: %s verify\n' "$0" >&2
  exit 2
fi
if [[ ! "$TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'ROUTING_HA_TIMEOUT_SECONDS must be a positive integer.\n' >&2
  exit 2
fi
if [[ ! -x "$SCENARIO_COMMAND" ]]; then
  printf 'routing-ha scenario command is not executable: %s\n' "$SCENARIO_COMMAND" >&2
  exit 2
fi

started_head="$(git -C "$ROOT_DIR" rev-parse HEAD)"
source "$ROOT_DIR/scripts/lib/harness-evidence.sh"
started_fingerprint="$(harness_worktree_fingerprint "$ROOT_DIR")"
[[ "$started_fingerprint" != unavailable ]] || exit 1
if [[ -n "${ROUTING_HA_REACTOR_EVIDENCE:-}" ]]; then
  manifest_sha256="$(ruby -rdigest -e 'puts Digest::SHA256.file(ARGV[0]).hexdigest' "$ROUTING_HA_REACTOR_EVIDENCE")"
  expected_sha256="${ROUTING_HA_REACTOR_SHA256:-$manifest_sha256}"
  [[ "$expected_sha256" =~ ^[0-9a-f]{64}$ && "$manifest_sha256" == "$expected_sha256" ]] || exit 1
  ruby "$ROOT_DIR/scripts/lib/harness-reactor-evidence.rb" check "$ROOT_DIR" "$ROUTING_HA_REACTOR_EVIDENCE"
fi
mkdir -p "$STATE_DIR" "$(dirname "$DETAIL_PATH")"
rm -f "$STATE_DIR/scenarios.jsonl" "$STATE_DIR/resources.jsonl" "$DETAIL_PATH"

set +e
# shell 只转发首次取消并继续 wait；Ruby 持有场景组及整个清理宽限。
forwarded=0
supervisor_pid=''
forward_signal() {
  if (( forwarded == 0 )) && [[ -n "$supervisor_pid" ]]; then
    forwarded=1
    kill -s "$1" "$supervisor_pid" 2>/dev/null || true
  fi
}
trap 'forward_signal TERM' TERM
trap 'forward_signal INT' INT
ROUTING_HA_STATE_DIR="$STATE_DIR" ruby "$ROOT_DIR/scripts/lib/routing-ha-process.rb" \
  supervise "$TIMEOUT_SECONDS" "$SCENARIO_COMMAND" &
supervisor_pid=$!
while true; do
  wait "$supervisor_pid"
  scenario_status=$?
  if ! kill -0 "$supervisor_pid" 2>/dev/null; then
    break
  fi
done
trap - INT TERM
set -e
if (( scenario_status != 0 )); then
  printf 'routing-ha scenario command failed with exit code %d.\n' "$scenario_status" >&2
  exit "$scenario_status"
fi

scenario_file="$STATE_DIR/scenarios.jsonl"
resource_file="$STATE_DIR/resources.jsonl"
if [[ ! -f "$scenario_file" ]]; then
  printf 'routing-ha did not produce scenarios.jsonl.\n' >&2
  exit 1
fi

scenario_count="$(ruby -rjson -e '
  path = ARGV.fetch(0)
  records = File.readlines(path, chomp: true).reject(&:empty?).map { |line| JSON.parse(line) }
  abort "routing-ha must execute exactly 25 scenarios" unless records.size == 25
  ids = records.map { |record| record.fetch("id") }
  abort "routing-ha scenario ids must be unique" unless ids.uniq.size == ids.size
  abort "routing-ha scenario failed" unless records.all? { |record| record["status"] == "passed" }
  required = %w[name evidenceKind evidence assertion durationMillis finishedAt]
  records.each do |record|
    missing = required.reject { |key| record.key?(key) && !record[key].nil? && record[key] != "" }
    abort "routing-ha scenario evidence is incomplete: #{record.fetch("id")}" unless missing.empty?
  end
  puts records.size
' "$scenario_file")" || exit 1

# 缺失 ledger 不是清空证明；只接受当前场景生成的普通文件。
if [[ ! -f "$resource_file" || -L "$resource_file" ]]; then
  printf 'routing-ha did not produce a regular resources.jsonl cleanup ledger.\n' >&2
  exit 1
fi
if [[ -s "$resource_file" ]]; then
  printf 'routing-ha left Harness-owned resources in %s.\n' "$resource_file" >&2
  exit 1
fi
if find "$STATE_DIR" -maxdepth 1 -type f -name '*.pid' -size +0c -print -quit | rg -q .; then
  printf 'routing-ha left a non-empty PID file in %s.\n' "$STATE_DIR" >&2
  exit 1
fi

[[ "$(harness_head_revision "$ROOT_DIR")" == "$started_head" && "$(harness_worktree_fingerprint "$ROOT_DIR")" == "$started_fingerprint" ]] || exit 1
if [[ -n "${ROUTING_HA_REACTOR_EVIDENCE:-}" ]]; then
  [[ "$(ruby -rdigest -e 'puts Digest::SHA256.file(ARGV[0]).hexdigest' "$ROUTING_HA_REACTOR_EVIDENCE")" == "$expected_sha256" ]] || exit 1
  ruby "$ROOT_DIR/scripts/lib/harness-reactor-evidence.rb" check "$ROOT_DIR" "$ROUTING_HA_REACTOR_EVIDENCE"
fi
ruby -rjson -rtime -e '
  output, scenarios, state_dir = ARGV
  File.write(output, JSON.generate({
    "version" => 1,
    "topology" => {"brokers" => 3, "gateways" => 2},
    "scenarioCount" => scenarios.to_i,
    "scenarioEvidencePath" => File.join(state_dir, "scenarios.jsonl"),
    "cleanupStatus" => "passed",
    "finishedAt" => Time.now.utc.iso8601
  }) + "\n")
' "$DETAIL_PATH" "$scenario_count" "$STATE_DIR"

printf 'routing-ha passed %d scenarios with clean owned resources.\n' "$scenario_count"
