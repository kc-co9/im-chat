#!/usr/bin/env bash
# 用途：运行生产候选路由容量场景，并拒绝不完整、无有效发压、缺采样、未清理或执行期间源码漂移的证据。
# 输入：位置参数固定为 verify；场景命令读取 ROUTING_CAPACITY_CONFIG/STATE_DIR 并写 runs.jsonl、cleanup.json。
# 输出/副作用：成功写 routing-capacity detail JSON；场景产生的业务数据与资源必须由场景命令清理。
# 隔离：runner 只接受配置中的单一候选拓扑；每个五阶段、三次重复、两类 workload 组合必须恰好一条记录。
# 退出码：场景命令/超时原样传播；配置、结果、清理或 fingerprint 契约不满足返回 1；参数错误返回 2。

set -euo pipefail

ROOT_DIR="${ROUTING_CAPACITY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ACTION="${1:-}"
CONFIG="${ROUTING_CAPACITY_CONFIG:-$ROOT_DIR/im-test/im-load-test/config/routing-capacity.json}"
STATE_DIR="${ROUTING_CAPACITY_STATE_DIR:-$ROOT_DIR/.harness/routing-capacity}"
DETAIL_PATH="${ROUTING_CAPACITY_DETAIL_PATH:-$ROOT_DIR/.harness/verifications/routing-capacity-detail.json}"
SCENARIO_COMMAND="${ROUTING_CAPACITY_SCENARIO_COMMAND:-$ROOT_DIR/scripts/run-routing-capacity-scenarios.sh}"
DRIVER="${ROUTING_CAPACITY_DRIVER_COMMAND:-$ROOT_DIR/scripts/routing-capacity-driver.sh}"
TIMEOUT_SECONDS="${ROUTING_CAPACITY_TIMEOUT_SECONDS:-43200}"
source "$ROOT_DIR/scripts/lib/harness-evidence.sh"
source "$ROOT_DIR/scripts/lib/load-test-evidence.sh"

if [[ "$ACTION" != verify ]]; then
  printf 'Usage: %s verify\n' "$0" >&2
  exit 2
fi
if [[ ! "$TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'ROUTING_CAPACITY_TIMEOUT_SECONDS must be a positive integer.\n' >&2
  exit 2
fi
[[ -f "$CONFIG" ]] || { printf 'routing-capacity config is missing: %s\n' "$CONFIG" >&2; exit 2; }
[[ -x "$SCENARIO_COMMAND" ]] || {
  printf 'routing-capacity scenario command is not executable: %s\n' "$SCENARIO_COMMAND" >&2
  exit 2
}

ruby -rjson -e '
  config = JSON.parse(File.read(ARGV.fetch(0)))
  expected_levels = [50, 100, 200, 400, 800, 1600, 3200]
  abort "routing-capacity connection levels changed" unless config.dig("levels", "connection") == expected_levels
  abort "routing-capacity message levels changed" unless config.dig("levels", "message") == expected_levels
  abort "routing-capacity message mix changed" unless config["messageMix"] == {"private" => 70, "group" => 30}
  abort "routing-capacity payload changed" unless config["messageBytes"] == 256 && config["groupSize"] == 10
  abort "routing-capacity repetition count changed" unless config["repetitions"] == 3
  abort "routing-capacity windows changed" unless config["windows"] == {
    "warmupSeconds" => 60, "measureSeconds" => 300, "drainSeconds" => 60
  }
  expected_phases = %w[steady-state gateway-switch broker-drain leader-route-broker-failure minority-jvm-pause]
  abort "routing-capacity phases changed" unless config.fetch("phases").map { |phase| phase["id"] } == expected_phases
  topologies = config.fetch("candidateTopologies").to_h { |topology| [topology["id"], topology] }
  abort "routing-capacity three-voter topology missing" unless topologies.dig("three-voter", "voters") == 3
  abort "routing-capacity five-voter topology missing" unless topologies.dig("five-voter", "voters") == 5
  abort "routing-capacity headroom changed" unless config["resourceHeadroomPercent"] == 30
  required_thresholds = %w[connectionSuccessRateMin businessSuccessRateMin ackSuccessRateMin
    receiveLatencyP95MsMax receiveLatencyP99MsMax duplicateRateMax misrouteCountMax
    oldEpochAdmissionCountMax routeLookupP95MsMax routeLookupP99MsMax fullLookupRateMax
    snapshotAckRateMin staleRouteCountMax cpuLimitPercentMax rssLimitPercentMax diskUsagePercentMax]
  abort "routing-capacity thresholds are incomplete" unless required_thresholds.all? {
    |name| config.fetch("thresholds")[name].is_a?(Numeric)
  }
' "$CONFIG"

mkdir -p "$STATE_DIR" "$(dirname "$DETAIL_PATH")"
rm -f "$STATE_DIR/runs.jsonl" "$STATE_DIR/cleanup.json" "$DETAIL_PATH"
started_head="$(harness_head_revision "$ROOT_DIR")"
started_fingerprint="$(harness_worktree_fingerprint "$ROOT_DIR")"
started_scope="$(load_test_scope_fingerprint "$ROOT_DIR")"
config_digest="$(routing_capacity_config_digest "$ROOT_DIR" "$CONFIG")"

set +e
ROUTING_CAPACITY_STATE_DIR="$STATE_DIR" ROUTING_CAPACITY_CONFIG="$CONFIG" \
  ROUTING_CAPACITY_ROOT_DIR="$ROOT_DIR" ruby -e '
    command = ARGV.fetch(0)
    timeout = Integer(ARGV.fetch(1))
    environment = {
      "ROUTING_CAPACITY_STATE_DIR" => ENV.fetch("ROUTING_CAPACITY_STATE_DIR"),
      "ROUTING_CAPACITY_CONFIG" => ENV.fetch("ROUTING_CAPACITY_CONFIG"),
      "ROUTING_CAPACITY_ROOT_DIR" => ENV.fetch("ROUTING_CAPACITY_ROOT_DIR")
    }
    ENV.each { |key, value| environment[key] = value if key.start_with?("FIXTURE_") }
    pid = Process.spawn(environment, command, pgroup: true)
    deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + timeout
    loop do
      waited = Process.waitpid(pid, Process::WNOHANG)
      exit($?.exitstatus || 1) if waited
      if Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline
        Process.kill("TERM", -pid) rescue nil
        sleep 2
        Process.kill("KILL", -pid) rescue nil
        Process.waitpid(pid) rescue nil
        exit 124
      end
      sleep 0.05
    end
  ' "$SCENARIO_COMMAND" "$TIMEOUT_SECONDS"
scenario_status=$?
set -e
if (( scenario_status != 0 )); then
  printf 'routing-capacity scenario command failed with exit code %d.\n' "$scenario_status" >&2
  # 场景进程组可能已被外层超时终止；父进程独立启动 cleanup，避免依赖被杀进程的 EXIT trap。
  if [[ "$SCENARIO_COMMAND" != "$ROOT_DIR/scripts/run-routing-capacity-scenarios.sh" || ! -x "$DRIVER" ]]; then
    exit "$scenario_status"
  fi
  set +e
  ROUTING_CAPACITY_ACTION=cleanup \
    ROUTING_CAPACITY_OUTPUT="$STATE_DIR/cleanup.json" \
    ROUTING_CAPACITY_TOPOLOGY_ID="${ROUTING_CAPACITY_TOPOLOGY_ID:-}" \
    ROUTING_CAPACITY_CONFIG="$CONFIG" \
    ROUTING_CAPACITY_STATE_DIR="$STATE_DIR" \
    ROUTING_CAPACITY_ROOT_DIR="$ROOT_DIR" \
    ROUTING_CAPACITY_ENV_CONFIG="${ROUTING_CAPACITY_ENV_CONFIG:-}" \
    ruby -e '
      command = ARGV.fetch(0); timeout = Integer(ENV.fetch("ROUTING_CAPACITY_CLEANUP_TIMEOUT_SECONDS", "600"))
      pid = Process.spawn(command, pgroup: true)
      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + timeout
      loop do
        waited = Process.waitpid(pid, Process::WNOHANG)
        exit($?.exitstatus || 1) if waited
        if Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline
          Process.kill("TERM", -pid) rescue nil
          sleep 1
          Process.kill("KILL", -pid) rescue nil
          Process.waitpid(pid) rescue nil
          exit 124
        end
        sleep 0.1
      end
    ' "$DRIVER"
  cleanup_status=$?
  set -e
  (( cleanup_status == 0 )) || printf 'routing-capacity independent cleanup failed with exit code %d.\n' "$cleanup_status" >&2
  exit "$scenario_status"
fi

finished_head="$(harness_head_revision "$ROOT_DIR")"
finished_fingerprint="$(harness_worktree_fingerprint "$ROOT_DIR")"
finished_scope="$(load_test_scope_fingerprint "$ROOT_DIR")"
if [[ "$started_head" != "$finished_head" || "$started_fingerprint" != "$finished_fingerprint" \
    || "$started_scope" != "$finished_scope" ]]; then
  printf 'routing-capacity repository scope changed during execution.\n' >&2
  exit 1
fi

runs="$STATE_DIR/runs.jsonl"
cleanup="$STATE_DIR/cleanup.json"
[[ -s "$runs" ]] || { printf 'routing-capacity produced zero workload records.\n' >&2; exit 1; }
[[ -f "$cleanup" ]] || { printf 'routing-capacity cleanup evidence is missing.\n' >&2; exit 1; }

summary="$(ruby -rjson -e '
  config_path, runs_path, cleanup_path = ARGV
  config = JSON.parse(File.read(config_path))
  records = File.readlines(runs_path, chomp: true).reject(&:empty?).map { |line| JSON.parse(line) }
  cleanup = JSON.parse(File.read(cleanup_path))
  abort "routing-capacity cleanup failed" unless cleanup["status"] == "passed"
  abort "routing-capacity changed ordinary data" unless cleanup["ordinaryDataUnchanged"] == true
  phases = config.fetch("phases").map { |phase| phase.fetch("id") }
  repetitions = config.fetch("repetitions")
  expected = phases.product((1..repetitions).to_a, %w[connection message])
  actual = records.map { |record| [record["phase"], record["repetition"], record["workload"]] }
  abort "routing-capacity workload matrix is incomplete or duplicated" unless actual.sort == expected.sort
  topology_ids = config.fetch("candidateTopologies").map { |topology| topology.fetch("id") }
  selected = records.map { |record| record["topologyId"] }.uniq
  abort "routing-capacity must measure exactly one candidate topology" unless selected.size == 1 && topology_ids.include?(selected.first)
  allowed_levels = config.fetch("levels")
  required_metrics = %w[connectionSuccessRate businessSuccessRate ackSuccessRate receiveLatencyP95Ms
    receiveLatencyP99Ms duplicateRate misrouteCount oldEpochAdmissionCount lookupCount
    routeLookupP95Ms routeLookupP99Ms fullLookupRate snapshotAckRate snapshotAckP95Ms
    staleRouteCount migrationP95Ms electionRecoveryMs cpuMaxPercent rssLimitPercent
    rssMaxBytes diskUsagePercent networkBytes diskWriteBytes raftFsyncP99Ms]
  thresholds = config.fetch("thresholds")
  records.each do |record|
    abort "routing-capacity report field missing" unless record["runId"].is_a?(String) && !record["runId"].empty?
    abort "routing-capacity report status missing" unless %w[stable service_failed uncertain].include?(record["status"])
    abort "routing-capacity pressure was ineffective" unless record["pressureEffective"] == true
    abort "routing-capacity level is invalid" unless allowed_levels.fetch(record.fetch("workload")).include?(record["level"])
    metrics = record["metrics"]
    abort "routing-capacity resource sampling is incomplete" unless metrics.is_a?(Hash) &&
      required_metrics.all? { |name| metrics[name].is_a?(Numeric) && metrics[name].finite? && metrics[name] >= 0 }
    abort "routing-capacity generator saturation field missing" unless [true, false].include?(metrics["generatorSaturated"])
    abort "routing-capacity operation outcome missing" unless metrics["operationOutcome"].is_a?(String)
    phase_requires_operation = record["phase"] != "steady-state"
    checks = [
      metrics["duplicateRate"] <= thresholds["duplicateRateMax"],
      metrics["misrouteCount"] <= thresholds["misrouteCountMax"],
      metrics["oldEpochAdmissionCount"] <= thresholds["oldEpochAdmissionCountMax"],
      metrics["routeLookupP95Ms"] <= thresholds["routeLookupP95MsMax"],
      metrics["routeLookupP99Ms"] <= thresholds["routeLookupP99MsMax"],
      metrics["fullLookupRate"] <= thresholds["fullLookupRateMax"],
      metrics["snapshotAckRate"] >= thresholds["snapshotAckRateMin"],
      metrics["staleRouteCount"] <= thresholds["staleRouteCountMax"],
      metrics["cpuMaxPercent"] <= thresholds["cpuLimitPercentMax"],
      metrics["rssLimitPercent"] <= thresholds["rssLimitPercentMax"],
      metrics["diskUsagePercent"] <= thresholds["diskUsagePercentMax"],
      !phase_requires_operation || metrics["operationOutcome"] == "SUCCEEDED"
    ]
    if record["workload"] == "connection"
      checks << (metrics["connectionSuccessRate"] >= thresholds["connectionSuccessRateMin"])
    else
      checks.concat([
        metrics["businessSuccessRate"] >= thresholds["businessSuccessRateMin"],
        metrics["ackSuccessRate"] >= thresholds["ackSuccessRateMin"],
        metrics["receiveLatencyP95Ms"] <= thresholds["receiveLatencyP95MsMax"],
        metrics["receiveLatencyP99Ms"] <= thresholds["receiveLatencyP99MsMax"]
      ])
    end
    expected = if metrics["generatorSaturated"]
      "uncertain"
    elsif checks.all?
      "stable"
    else
      "service_failed"
    end
    abort "routing-capacity decision does not match metrics" unless record["status"] == expected
    if expected == "uncertain"
      abort "routing-capacity uncertainty reasons missing" unless
        record["uncertaintyReasons"].is_a?(Array) && !record["uncertaintyReasons"].empty?
    end
  end
  status_counts = %w[stable service_failed uncertain].to_h { |status|
    [status, records.count { |record| record["status"] == status }]
  }
  qualification_status = if status_counts["uncertain"] > 0
    "uncertain"
  elsif status_counts["service_failed"] > 0
    "failed"
  else
    "passed"
  end
  print JSON.generate({"topologyId" => selected.first, "workloadCount" => records.size,
    "phaseCount" => phases.size, "repetitionCount" => repetitions,
    "cleanupStatus" => cleanup["status"], "ordinaryDataUnchanged" => cleanup["ordinaryDataUnchanged"],
    "statusCounts" => status_counts, "qualificationStatus" => qualification_status})
' "$CONFIG" "$runs" "$cleanup")" || exit 1

topology_id="$(ruby -rjson -e 'print JSON.parse(ARGV.fetch(0)).fetch("topologyId")' "$summary")"
archive_dir="$STATE_DIR/evidence/$topology_id"
mkdir -p "$archive_dir"
cp "$runs" "$archive_dir/runs.jsonl"
archived_runs="$archive_dir/runs.jsonl"

ruby -rjson -rtime -e '
  output, summary_json, config_digest, head, fingerprint, scope, runs, root = ARGV
  summary = JSON.parse(summary_json)
  results_path = runs.start_with?(root + "/") ? runs.delete_prefix(root + "/") : runs
  evidence = summary.merge({
    "version" => 1, "evidenceKind" => "routing-capacity",
    "configDigest" => config_digest, "headRevision" => head,
    "worktreeFingerprint" => fingerprint, "worktreeStable" => true,
    "loadTestScopeFingerprint" => scope, "loadTestScopeStable" => true,
    "resultsPath" => results_path, "finishedAt" => Time.now.utc.iso8601
  })
  File.write(output, JSON.generate(evidence) + "\n")
' "$DETAIL_PATH" "$summary" "$config_digest" "$finished_head" "$finished_fingerprint" \
  "$finished_scope" "$archived_runs" "$ROOT_DIR"
cp "$DETAIL_PATH" "$archive_dir/evidence.json"

printf 'routing-capacity accepted %s complete workload records.\n' \
  "$(ruby -rjson -e 'print JSON.parse(File.read(ARGV.fetch(0))).fetch("workloadCount")' "$DETAIL_PATH")"
