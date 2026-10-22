#!/usr/bin/env bash
# 用途：自测 routing-capacity runner 对结果完整性、有效发压、采样、清理与指纹漂移的 fail-closed 契约。
# 输入：无位置参数；在临时 Git 仓库注入 workload fixture，不启动真实 Compose 或产生业务数据。
# 输出/副作用：只写入 mktemp 目录并在退出时删除；全部正反例符合契约时返回 0。
# 依赖：bash、Git、Ruby、mktemp，以及 scripts/routing_capacity.sh。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNNER="$ROOT_DIR/scripts/routing_capacity.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

[[ -x "$RUNNER" ]] || {
  printf 'routing-capacity runner is missing or not executable: %s\n' "$RUNNER" >&2
  exit 1
}

repository="$FIXTURE_ROOT/repository"
mkdir -p "$repository/scripts/lib" "$repository/im-test/im-load-test/config"
cp "$RUNNER" "$repository/scripts/routing_capacity.sh"
cp "$ROOT_DIR/scripts/verify.sh" "$repository/scripts/verify.sh"
cp "$ROOT_DIR/scripts/run-routing-capacity-scenarios.sh" \
  "$repository/scripts/run-routing-capacity-scenarios.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$repository/scripts/lib/harness-evidence.sh"
cp "$ROOT_DIR/scripts/lib/load-test-evidence.sh" "$repository/scripts/lib/load-test-evidence.sh"
cp "$ROOT_DIR/im-test/im-load-test/config/routing-capacity.json" \
  "$repository/im-test/im-load-test/config/routing-capacity.json"
printf '/.harness/\n' >"$repository/.gitignore"
git -C "$repository" init --quiet
git -C "$repository" config user.email harness@example.invalid
git -C "$repository" config user.name Harness
git -C "$repository" add .
git -C "$repository" commit --quiet -m fixture

workload="$FIXTURE_ROOT/workload.rb"
cat >"$workload" <<'RUBY'
#!/usr/bin/env ruby
require "json"
require "fileutils"

state = ENV.fetch("ROUTING_CAPACITY_STATE_DIR")
mode = ENV.fetch("FIXTURE_MODE", "success")
config = JSON.parse(File.read(ENV.fetch("ROUTING_CAPACITY_CONFIG")))
FileUtils.mkdir_p(state)
records = []
config.fetch("phases").each do |phase|
  (1..config.fetch("repetitions")).each do |repetition|
    %w[connection message].each do |workload|
      records << {
        "runId" => "#{phase.fetch("id")}-#{repetition}-#{workload}",
        "topologyId" => "three-voter",
        "phase" => phase.fetch("id"),
        "repetition" => repetition,
        "workload" => workload,
        "level" => 50,
        "status" => "stable",
        "pressureEffective" => true,
        "metrics" => {
          "connectionSuccessRate" => 1.0, "businessSuccessRate" => 1.0,
          "ackSuccessRate" => 1.0, "receiveLatencyP95Ms" => 10.0,
          "receiveLatencyP99Ms" => 20.0, "duplicateRate" => 0.0,
          "misrouteCount" => 0, "oldEpochAdmissionCount" => 0, "lookupCount" => 0,
          "routeLookupP95Ms" => 10.0, "routeLookupP99Ms" => 20.0,
          "fullLookupRate" => 0.0, "snapshotAckRate" => 1.0,
          "snapshotAckP95Ms" => 5.0, "migrationP95Ms" => 10.0,
          "staleRouteCount" => 0, "electionRecoveryMs" => 20.0,
          "cpuMaxPercent" => 10.0, "rssLimitPercent" => 10.0,
          "rssMaxBytes" => 1024, "diskUsagePercent" => 10.0,
          "networkBytes" => 2048, "diskWriteBytes" => 4096,
          "raftFsyncP99Ms" => 2.0, "generatorSaturated" => false,
          "operationOutcome" => phase.fetch("id") == "steady-state" ? "NOT_APPLICABLE" : "SUCCEEDED"
        }
      }
    end
  end
end
case mode
when "zero" then records.clear
when "missing-stage" then records.reject! { |record| record["phase"] == config.fetch("phases").last.fetch("id") }
when "missing-repeat" then records.reject! { |record| record["repetition"] == config.fetch("repetitions") }
when "ineffective" then records.first["pressureEffective"] = false
when "missing-sampling" then records.first["metrics"].delete("cpuMaxPercent")
when "missing-report-field" then records.first.delete("status")
when "threshold-mismatch" then records.first["metrics"]["cpuMaxPercent"] = 80.0
when "uncertain-no-reason"
  records.first["metrics"]["generatorSaturated"] = true
  records.first["status"] = "uncertain"
when "valid-service-failed"
  records.first["metrics"]["cpuMaxPercent"] = 80.0
  records.first["status"] = "service_failed"
end
File.write(File.join(state, "runs.jsonl"), records.map { |record| JSON.generate(record) }.join("\n") + (records.empty? ? "" : "\n"))
cleanup = {"status" => mode == "cleanup-failure" ? "failed" : "passed", "ordinaryDataUnchanged" => true}
File.write(File.join(state, "cleanup.json"), JSON.generate(cleanup) + "\n")
if mode == "scope-drift"
  File.write(File.join(ENV.fetch("ROUTING_CAPACITY_ROOT_DIR"), "scope-drift.txt"), "changed\n")
end
RUBY
chmod +x "$workload"

driver="$FIXTURE_ROOT/driver.sh"
cat >"$driver" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s:%s:%s:%s\n' "$ROUTING_CAPACITY_ACTION" \
  "${ROUTING_CAPACITY_PHASE:-none}" "${ROUTING_CAPACITY_REPETITION:-none}" \
  "${ROUTING_CAPACITY_WORKLOAD:-none}" >>"$DRIVER_LOG"
if [[ "$ROUTING_CAPACITY_ACTION" == cleanup ]]; then
  printf '{"status":"passed","ordinaryDataUnchanged":true}\n' >"$ROUTING_CAPACITY_OUTPUT"
  exit 0
fi
if [[ "${FAIL_PHASE:-}" == "$ROUTING_CAPACITY_PHASE" ]]; then exit 27; fi
cat >"$ROUTING_CAPACITY_OUTPUT" <<EOF
{"runId":"$ROUTING_CAPACITY_PHASE-$ROUTING_CAPACITY_REPETITION-$ROUTING_CAPACITY_WORKLOAD","topologyId":"$ROUTING_CAPACITY_TOPOLOGY_ID","phase":"$ROUTING_CAPACITY_PHASE","repetition":$ROUTING_CAPACITY_REPETITION,"workload":"$ROUTING_CAPACITY_WORKLOAD","level":50,"status":"stable","pressureEffective":true,"metrics":{"connectionSuccessRate":1,"businessSuccessRate":1,"ackSuccessRate":1,"receiveLatencyP95Ms":1,"receiveLatencyP99Ms":2,"duplicateRate":0,"misrouteCount":0,"oldEpochAdmissionCount":0,"lookupCount":0,"routeLookupP95Ms":1,"routeLookupP99Ms":2,"fullLookupRate":0,"snapshotAckRate":1,"snapshotAckP95Ms":1,"staleRouteCount":0,"migrationP95Ms":1,"electionRecoveryMs":1,"cpuMaxPercent":1,"rssLimitPercent":1,"rssMaxBytes":1,"diskUsagePercent":1,"networkBytes":1,"diskWriteBytes":1,"raftFsyncP99Ms":1,"generatorSaturated":false,"operationOutcome":"$([[ "$ROUTING_CAPACITY_PHASE" == steady-state ]] && printf NOT_APPLICABLE || printf SUCCEEDED)"}}
EOF
SH
chmod +x "$driver"

missing_driver_log="$FIXTURE_ROOT/missing-driver.log"
set +e
ROUTING_CAPACITY_TOPOLOGY_ID=three-voter \
  ROUTING_CAPACITY_STATE_DIR="$FIXTURE_ROOT/missing-driver" \
  ROUTING_CAPACITY_CONFIG="$repository/im-test/im-load-test/config/routing-capacity.json" \
  "$repository/scripts/run-routing-capacity-scenarios.sh" 2>"$missing_driver_log"
missing_driver_status=$?
set -e
[[ "$missing_driver_status" == 2 ]] &&
  rg -q 'must reference an executable production-environment driver' "$missing_driver_log" || {
  printf 'routing-capacity coordinator must reject a missing production driver\n' >&2
  exit 1
}

driver_state="$FIXTURE_ROOT/coordinator-success"
driver_log="$FIXTURE_ROOT/coordinator-success.log"
DRIVER_LOG="$driver_log" ROUTING_CAPACITY_DRIVER_COMMAND="$driver" \
  ROUTING_CAPACITY_TOPOLOGY_ID=three-voter \
  ROUTING_CAPACITY_STATE_DIR="$driver_state" \
  ROUTING_CAPACITY_CONFIG="$repository/im-test/im-load-test/config/routing-capacity.json" \
  "$repository/scripts/run-routing-capacity-scenarios.sh"
[[ "$(wc -l <"$driver_state/runs.jsonl" | tr -d ' ')" == 30 ]] || {
  printf 'routing-capacity coordinator must execute 30 workloads\n' >&2
  exit 1
}
[[ "$(tail -1 "$driver_log")" == cleanup:none:none:none ]] || {
  printf 'routing-capacity coordinator must finish with cleanup\n' >&2
  exit 1
}

failure_state="$FIXTURE_ROOT/coordinator-failure"
failure_log="$FIXTURE_ROOT/coordinator-failure.log"
set +e
FAIL_PHASE=broker-drain DRIVER_LOG="$failure_log" \
  ROUTING_CAPACITY_DRIVER_COMMAND="$driver" ROUTING_CAPACITY_TOPOLOGY_ID=three-voter \
  ROUTING_CAPACITY_STATE_DIR="$failure_state" \
  ROUTING_CAPACITY_CONFIG="$repository/im-test/im-load-test/config/routing-capacity.json" \
  "$repository/scripts/run-routing-capacity-scenarios.sh"
coordinator_status=$?
set -e
[[ "$coordinator_status" == 27 && "$(tail -1 "$failure_log")" == cleanup:none:none:none ]] || {
  printf 'routing-capacity coordinator must preserve failure 27 and still cleanup\n' >&2
  exit 1
}

run_fixture() {
  local name="$1"
  local mode="$2"
  local expected_status="$3"
  local state="$FIXTURE_ROOT/$name/state"
  local detail="$FIXTURE_ROOT/$name/detail.json"
  set +e
  FIXTURE_MODE="$mode" \
    ROUTING_CAPACITY_ROOT_DIR="$repository" \
    ROUTING_CAPACITY_SCENARIO_COMMAND="$workload" \
    ROUTING_CAPACITY_STATE_DIR="$state" \
    ROUTING_CAPACITY_DETAIL_PATH="$detail" \
    ROUTING_CAPACITY_CONFIG_DIGEST_VALUE="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
    "$repository/scripts/routing_capacity.sh" verify
  local status=$?
  set -e
  if (( status != expected_status )); then
    printf '%s: expected status %d, got %d\n' "$name" "$expected_status" "$status" >&2
    exit 1
  fi
}

run_fixture success success 0
ruby -rjson -e '
  detail = JSON.parse(File.read(ARGV.fetch(0)))
  abort unless detail["workloadCount"] == 30 && detail["phaseCount"] == 5
  abort unless detail["repetitionCount"] == 3 && detail["cleanupStatus"] == "passed"
  abort unless detail["loadTestScopeStable"] == true && detail["worktreeStable"] == true
  abort unless detail["qualificationStatus"] == "passed"
  abort unless detail["statusCounts"] == {"stable" => 30, "service_failed" => 0, "uncertain" => 0}
  abort unless detail["resultsPath"].end_with?("evidence/three-voter/runs.jsonl")
' "$FIXTURE_ROOT/success/detail.json"
[[ -s "$FIXTURE_ROOT/success/state/evidence/three-voter/runs.jsonl" &&
  -s "$FIXTURE_ROOT/success/state/evidence/three-voter/evidence.json" ]] || {
  printf 'routing-capacity must retain topology-specific evidence\n' >&2
  exit 1
}

FIXTURE_MODE=success \
  ROUTING_CAPACITY_ROOT_DIR="$repository" \
  ROUTING_CAPACITY_SCENARIO_COMMAND="$workload" \
  ROUTING_CAPACITY_STATE_DIR="$FIXTURE_ROOT/verify/state" \
  ROUTING_CAPACITY_DETAIL_PATH="$repository/.harness/verifications/routing-capacity-detail.json" \
  ROUTING_CAPACITY_CONFIG_DIGEST_VALUE="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
  "$repository/scripts/verify.sh" routing-capacity >/dev/null
ruby -rjson -e '
  evidence = JSON.parse(File.read(ARGV.fetch(0)))
  abort unless evidence["mode"] == "routing-capacity" && evidence["status"] == "passed"
  abort unless evidence["workloadCount"] == 30 && evidence["evidenceKind"] == "routing-capacity"
  detail = JSON.parse(File.read(ARGV.fetch(1)))
  %w[configDigest loadTestScopeFingerprint loadTestScopeStable ordinaryDataUnchanged phaseCount repetitionCount qualificationStatus statusCounts resultsPath].each do |key|
    abort "lost business field #{key}" unless evidence[key] == detail.fetch(key)
  end
' "$repository/.harness/verifications/routing-capacity.json" "$FIXTURE_ROOT/verify/state/evidence/three-voter/evidence.json"

run_fixture zero zero 1
run_fixture missing-stage missing-stage 1
run_fixture missing-repeat missing-repeat 1
run_fixture ineffective ineffective 1
run_fixture missing-sampling missing-sampling 1
run_fixture cleanup-failure cleanup-failure 1
run_fixture missing-report-field missing-report-field 1
run_fixture threshold-mismatch threshold-mismatch 1
run_fixture uncertain-no-reason uncertain-no-reason 1
run_fixture valid-service-failed valid-service-failed 0
ruby -rjson -e '
  detail = JSON.parse(File.read(ARGV.fetch(0)))
  abort unless detail["qualificationStatus"] == "failed"
  abort unless detail.dig("statusCounts", "service_failed") == 1
' "$FIXTURE_ROOT/valid-service-failed/detail.json"
run_fixture scope-drift scope-drift 1

printf 'routing-capacity Harness self-test passed.\n'
