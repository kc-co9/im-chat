#!/usr/bin/env bash
# 用途：按固定五阶段、三次重复和连接/消息两类 workload 串行调度生产候选容量 driver。
# 输入：ROUTING_CAPACITY_DRIVER_COMMAND 指向环境专用 driver；ROUTING_CAPACITY_TOPOLOGY_ID 显式选择配置中的候选拓扑。
# 输出/副作用：driver 每轮向 ROUTING_CAPACITY_OUTPUT 写单条 JSON；本脚本汇总 runs.jsonl，并始终请求 driver cleanup。
# 隔离：本脚本不猜测生产部署入口，不自行创建集群；环境 driver 拥有发压、故障注入、采样、恢复和普通数据校验。
# 退出码：首个 workload 或 cleanup 失败码原样传播；缺参数/非法拓扑/无效 driver 输出返回 2。

set -euo pipefail

STATE_DIR="${ROUTING_CAPACITY_STATE_DIR:?ROUTING_CAPACITY_STATE_DIR is required}"
CONFIG="${ROUTING_CAPACITY_CONFIG:?ROUTING_CAPACITY_CONFIG is required}"
DRIVER="${ROUTING_CAPACITY_DRIVER_COMMAND:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/routing-capacity-driver.sh}"
TOPOLOGY_ID="${ROUTING_CAPACITY_TOPOLOGY_ID:-}"
RUNS="$STATE_DIR/runs.jsonl"
CLEANUP="$STATE_DIR/cleanup.json"

[[ -n "$DRIVER" && -x "$DRIVER" ]] || {
  printf 'ROUTING_CAPACITY_DRIVER_COMMAND must reference an executable production-environment driver.\n' >&2
  exit 2
}
[[ -n "$TOPOLOGY_ID" ]] || {
  printf 'ROUTING_CAPACITY_TOPOLOGY_ID must explicitly select a candidate topology.\n' >&2
  exit 2
}

mapfile_supported=true
if ! type mapfile >/dev/null 2>&1; then
  mapfile_supported=false
fi
if [[ "$mapfile_supported" == true ]]; then
  mapfile -t matrix < <(ruby -rjson -e '
    config = JSON.parse(File.read(ARGV.fetch(0)))
    topology = ARGV.fetch(1)
    abort "unknown routing-capacity topology: #{topology}" unless
      config.fetch("candidateTopologies").any? { |candidate| candidate["id"] == topology }
    config.fetch("phases").each do |phase|
      (1..config.fetch("repetitions")).each do |repetition|
        %w[connection message].each { |workload| puts [phase.fetch("id"), repetition, workload].join("\t") }
      end
    end
  ' "$CONFIG" "$TOPOLOGY_ID")
else
  matrix=()
  while IFS= read -r line; do matrix+=("$line"); done < <(ruby -rjson -e '
    config = JSON.parse(File.read(ARGV.fetch(0)))
    topology = ARGV.fetch(1)
    abort "unknown routing-capacity topology: #{topology}" unless
      config.fetch("candidateTopologies").any? { |candidate| candidate["id"] == topology }
    config.fetch("phases").each do |phase|
      (1..config.fetch("repetitions")).each do |repetition|
        %w[connection message].each { |workload| puts [phase.fetch("id"), repetition, workload].join("\t") }
      end
    end
  ' "$CONFIG" "$TOPOLOGY_ID")
fi

mkdir -p "$STATE_DIR"
: >"$RUNS"
cleanup_complete=false

request_cleanup() {
  local primary_status=$?
  if [[ "$cleanup_complete" != true ]]; then
    set +e
    ROUTING_CAPACITY_ACTION=cleanup \
      ROUTING_CAPACITY_OUTPUT="$CLEANUP" \
      ROUTING_CAPACITY_TOPOLOGY_ID="$TOPOLOGY_ID" \
      ROUTING_CAPACITY_CONFIG="$CONFIG" \
      ROUTING_CAPACITY_STATE_DIR="$STATE_DIR" \
      "$DRIVER"
    local cleanup_status=$?
    set -e
    if (( cleanup_status != 0 )) || [[ ! -f "$CLEANUP" ]]; then
      printf '{"status":"failed","ordinaryDataUnchanged":false}\n' >"$CLEANUP"
      (( primary_status != 0 )) || primary_status=$cleanup_status
      (( primary_status != 0 )) || primary_status=1
    fi
  fi
  return "$primary_status"
}
trap request_cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for entry in "${matrix[@]}"; do
  IFS=$'\t' read -r phase repetition workload <<<"$entry"
  output="$STATE_DIR/.run-${phase}-${repetition}-${workload}.json"
  rm -f "$output"
  ROUTING_CAPACITY_ACTION=run \
    ROUTING_CAPACITY_PHASE="$phase" \
    ROUTING_CAPACITY_REPETITION="$repetition" \
    ROUTING_CAPACITY_WORKLOAD="$workload" \
    ROUTING_CAPACITY_OUTPUT="$output" \
    ROUTING_CAPACITY_TOPOLOGY_ID="$TOPOLOGY_ID" \
    ROUTING_CAPACITY_CONFIG="$CONFIG" \
    ROUTING_CAPACITY_STATE_DIR="$STATE_DIR" \
    "$DRIVER"
  [[ -s "$output" ]] || { printf 'routing-capacity driver did not write %s\n' "$output" >&2; exit 2; }
  ruby -rjson -e '
    input, output = ARGV
    record = JSON.parse(File.read(input))
    File.open(output, "a", 0o600) { |file| file.puts(JSON.generate(record)); file.flush; file.fsync }
  ' "$output" "$RUNS"
  rm -f "$output"
done

ROUTING_CAPACITY_ACTION=cleanup \
  ROUTING_CAPACITY_OUTPUT="$CLEANUP" \
  ROUTING_CAPACITY_TOPOLOGY_ID="$TOPOLOGY_ID" \
  ROUTING_CAPACITY_CONFIG="$CONFIG" \
  ROUTING_CAPACITY_STATE_DIR="$STATE_DIR" \
  "$DRIVER"
cleanup_complete=true
