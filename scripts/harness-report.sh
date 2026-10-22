#!/usr/bin/env bash
# 用途：聚合验证记录、测试统计、计划与 Harness 反馈，生成机器可读报告。
# 输入：可选输出 JSON 路径；测试可通过 HARNESS_ROOT_DIR 指向隔离 fixture。
# 输出/副作用：默认写入 .harness/report.json，并打印报告路径。
# 依赖：check-drift.sh、harness-evidence.sh、Ruby JSON/REXML、rg、find、awk 和既有 Surefire/verification 产物。
# 退出码：drift 通过返回 0；drift 失败时仍写报告并返回非 0。

set -euo pipefail

# 允许 Harness 自测在隔离目录验证报告契约，而不读取真实工作树状态。
ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
OUTPUT="${1:-$ROOT_DIR/.harness/report.json}"
if ! command -v rg >/dev/null 2>&1; then
  printf '[report] WHAT: 缺少命令: rg\nWHY: drift 与 Harness 报告依赖 ripgrep，不能在缺失时判断计划状态。\nFIX: 安装 ripgrep 后重新运行 ./scripts/verify.sh report。\n' >&2
  exit 2
fi
source "$ROOT_DIR/scripts/lib/harness-evidence.sh"
source "$ROOT_DIR/scripts/lib/load-test-evidence.sh"
mkdir -p "$(dirname "$OUTPUT")"

current_head="${HARNESS_CURRENT_HEAD:-$(harness_head_revision "$ROOT_DIR")}"
current_fingerprint="${HARNESS_CURRENT_WORKTREE_FINGERPRINT:-$(harness_worktree_fingerprint "$ROOT_DIR")}"
current_load_test_scope="${HARNESS_CURRENT_LOAD_TEST_SCOPE_FINGERPRINT:-$(load_test_scope_fingerprint "$ROOT_DIR" 2>/dev/null || printf unavailable)}"
current_routing_capacity_config_digest="${HARNESS_CURRENT_ROUTING_CAPACITY_CONFIG_DIGEST:-$(routing_capacity_config_digest "$ROOT_DIR" 2>/dev/null || printf unavailable)}"

# 根据验证记录与当前仓库状态返回 fresh、stale 或 unavailable。
verification_freshness() {
  local file="$1"
  ruby -rjson -e '
    record = JSON.parse(File.read(ARGV.fetch(0)))
    current_head = ARGV.fetch(1)
    current_fingerprint = ARGV.fetch(2)
    recorded_head = record["headRevision"]
    recorded_fingerprint = record["worktreeFingerprint"]
    stable = record["worktreeStable"]
    if [current_head, current_fingerprint, recorded_head, recorded_fingerprint].any? { |value| value.nil? || value == "unavailable" }
      puts "unavailable"
    elsif stable == true && recorded_head == current_head && recorded_fingerprint == current_fingerprint
      puts "fresh"
    else
      puts "stale"
    end
  ' "$file" "$current_head" "$current_fingerprint"
}

started_at="$(date +%s)"
status="passed"
if ! "$ROOT_DIR/scripts/check-drift.sh"; then
  status="failed"
fi
duration=$(( $(date +%s) - started_at ))

# Sensor 可用率只表示反馈能力是否存在，不是源码质量评分。
sensor_files=(
  AGENTS.md
  im-test/im-architecture-test/pom.xml
  im-test/im-e2e-test/pom.xml
  docs/product-specs/FEATURES.md
  docs/product-specs/im-realtime-approved-scenarios.md
  scripts/affected-modules.sh
  .github/workflows/verify.yml
  im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/infrastructure/monitoring/BrokerMetrics.java
  docs/references/CODE_REVIEW_GUIDE.md
  docs/references/HARNESS_GUIDE.md
  docs/references/HARNESS_RULE_MATRIX.md
  docs/feedback/HARNESS_FEEDBACK.md
)
available=0
for file in "${sensor_files[@]}"; do
  [[ -f "$ROOT_DIR/$file" ]] && available=$((available + 1))
done
score=$((available * 100 / ${#sensor_files[@]}))

# 参数为验证模式和必要文件；按文件存在性、验证结果及 fingerprint 返回能力状态。
subsystem_status() {
  local verification_mode="$1"
  shift
  local file
  for file in "$@"; do
    [[ -e "$ROOT_DIR/$file" ]] || { printf 'missing\n'; return 0; }
  done
  local verification_file="$ROOT_DIR/.harness/verifications/$verification_mode.json"
  if [[ ! -f "$verification_file" ]]; then
    printf 'present\n'
    return 0
  fi
  local freshness
  freshness="$(verification_freshness "$verification_file")"
  if [[ "$freshness" == "stale" ]]; then
    printf 'stale\n'
  elif [[ "$freshness" == "unavailable" ]]; then
    printf 'present\n'
  elif rg -q '"status":"passed"' "$verification_file"; then
    printf 'verified\n'
  else
    printf 'degraded\n'
  fi
}

instruction_status="$(subsystem_status clean \
  AGENTS.md ARCHITECTURE.md docs/references/HARNESS_GUIDE.md \
  docs/references/HARNESS_RULE_MATRIX.md)"
tools_status="$(subsystem_status quick \
  scripts/verify.sh scripts/check-drift.sh im-test/im-architecture-test/pom.xml)"
environment_status="$(subsystem_status readiness pom.xml README.md .java-version .nvmrc)"
state_status="$(subsystem_status clean \
  PROGRESS.md docs/PLANS.md docs/exec-plans/active/README.md docs/exec-plans/completed/README.md)"
feedback_status="$(subsystem_status full \
  scripts/verify.sh scripts/harness-report.sh docs/feedback/HARNESS_FEEDBACK.md)"

# 只统计最近一次成功 full 开始之后生成的 Surefire 报告，避免陈旧测试结果冒充当前证据。
test_status="unknown"
tests=0
failures=0
errors=0
skipped=0
full_verification="$ROOT_DIR/.harness/verifications/full.json"
full_freshness="missing"
if [[ -f "$full_verification" ]]; then
  full_freshness="$(verification_freshness "$full_verification")"
fi
if [[ -f "$full_verification" ]] \
    && rg -q '"status":"passed"' "$full_verification" \
    && rg -q '"startedAtEpoch":[0-9]+' "$full_verification" \
    && [[ "$full_freshness" == "fresh" ]]; then
  test_summary="$(ruby -rjson -e '
    record = JSON.parse(File.read(ARGV.fetch(0)))
    summary = record["testSummary"]
    puts %w[tests failures errors skipped].map { |name| summary[name].to_i }.join(" ") if summary
  ' "$full_verification")"
  if [[ -z "$test_summary" ]]; then
    full_started_at="$(sed -E 's/.*"startedAtEpoch":([0-9]+).*/\1/' "$full_verification")"
    test_summary="$(ruby -r rexml/document -e '
      threshold = ARGV.shift.to_i
      totals = Hash.new(0)
      ARGV.each do |file|
        next if File.mtime(file).to_i < threshold
        root = REXML::Document.new(File.read(file)).root
        %w[tests failures errors skipped].each { |name| totals[name] += root.attributes[name].to_i }
      end
      puts %w[tests failures errors skipped].map { |name| totals[name] }.join(" ")
    ' "$full_started_at" $(find "$ROOT_DIR" -path '*/target/surefire-reports/TEST-*.xml' -type f -print))"
  fi
  read -r tests failures errors skipped <<< "$test_summary"
  test_status="current"
elif [[ -f "$full_verification" ]] && rg -q '"status":"passed"' "$full_verification"; then
  test_status="stale"
fi

active_plans="$(find "$ROOT_DIR/docs/exec-plans/active" -type f -name '*.md' ! -name README.md | wc -l | tr -d ' ')"
stale_plans="$(find "$ROOT_DIR/docs/exec-plans/active" -type f -name '*.md' ! -name README.md -mtime +30 | wc -l | tr -d ' ')"
open_debt="$(awk -F'|' '/^\| TD-[0-9]+ / && $7 !~ /(DONE|CLOSED)/ {count++} END {print count + 0}' "$ROOT_DIR/docs/exec-plans/tech-debt-tracker.md")"
false_positives="$(awk -F'|' '/^\| HF-[0-9]+ / && $3 ~ /FALSE_POSITIVE/ && $7 !~ /CLOSED/ {count++} END {print count + 0}' "$ROOT_DIR/docs/feedback/HARNESS_FEEDBACK.md")"
flaky_tests="$(awk -F'|' '/^\| HF-[0-9]+ / && $3 ~ /FLAKY_TEST/ && $7 !~ /CLOSED/ {count++} END {print count + 0}' "$ROOT_DIR/docs/feedback/HARNESS_FEEDBACK.md")"
performance_issues="$(awk -F'|' '/^\| HF-[0-9]+ / && $3 ~ /PERFORMANCE/ && $7 !~ /CLOSED/ {count++} END {print count + 0}' "$ROOT_DIR/docs/feedback/HARNESS_FEEDBACK.md")"

verification_files=("$ROOT_DIR"/.harness/verifications/*.json)
verification_json=""
failed_verifications=()
incomplete_verifications=false
if [[ -e "${verification_files[0]}" ]]; then
  for file in "${verification_files[@]}"; do
    freshness="$(verification_freshness "$file")"
    enriched_verification="$(ruby -rjson -e '
      record = JSON.parse(File.read(ARGV.fetch(0)))
      record["freshness"] = ARGV.fetch(1)
      print JSON.generate(record)
    ' "$file" "$freshness")"
    [[ -n "$verification_json" ]] && verification_json+=","
    verification_json+="$enriched_verification"
    if [[ "$freshness" == "fresh" ]]; then
      verification_status="$(ruby -rjson -e 'print JSON.parse(File.read(ARGV.fetch(0)))["status"]' "$file")"
      case "$verification_status" in
        failed) failed_verifications+=("$(sed -E 's/.*"mode":"([^"]+)".*/\1/' "$file")") ;;
        passed) ;;
        *) incomplete_verifications=true ;;
      esac
    fi
  done
fi

overall_status="passed"
failed_verification_json=""
if (( ${#failed_verifications[@]} > 0 )); then
  overall_status="failed"
  for mode in "${failed_verifications[@]}"; do
    [[ -n "$failed_verification_json" ]] && failed_verification_json+=","
    failed_verification_json+="\"$mode\""
  done
fi
if [[ "$status" == "failed" ]]; then
  overall_status="failed"
elif [[ "$overall_status" == "passed" && "$full_freshness" == "missing" ]]; then
  overall_status="incomplete"
elif [[ "$overall_status" == "passed" \
    && "$full_freshness" != "fresh" \
    ]]; then
  overall_status="stale"
fi

# fresh 未完成、partial 或未知状态不能因其他证据有效而发布绿色结果。
if [[ "$overall_status" == passed && "$incomplete_verifications" == true ]]; then
  overall_status=incomplete
fi

e2e_verification="$ROOT_DIR/.harness/verifications/e2e.json"
if [[ -f "$e2e_verification" ]]; then
  e2e_freshness="$(verification_freshness "$e2e_verification")"
  e2e_runtime_json="$(ruby -rjson -rpathname -e '
    record = JSON.parse(File.read(ARGV.fetch(0)))
    freshness = ARGV.fetch(1)
    root = ARGV.fetch(2)
    log_path = record["logPath"]
    full_log_path = log_path && (Pathname.new(log_path).absolute? ? log_path : File.join(root, log_path))
    log_available = full_log_path && File.file?(full_log_path)
    status = if freshness == "fresh" && record["status"] == "passed" && log_available
      "verified"
    elsif freshness == "fresh" && record["status"] == "passed"
      "degraded"
    elsif freshness == "stale"
      "stale"
    elsif freshness == "fresh"
      "degraded"
    else
      "present"
    end
    print JSON.generate({
      "status" => status,
      "freshness" => freshness,
      "logPath" => log_path,
      "logAvailable" => !!log_available,
      "testCount" => record["testCount"].to_i,
      "workload" => record["workload"]
    })
  ' "$e2e_verification" "$e2e_freshness" "$ROOT_DIR")"
else
  e2e_runtime_json='{"status":"missing","freshness":"missing","logPath":null,"logAvailable":false,"testCount":0,"workload":null}'
fi

routing_ha_verification="$ROOT_DIR/.harness/verifications/routing-ha.json"
if [[ -f "$routing_ha_verification" ]]; then
  routing_ha_freshness="$(verification_freshness "$routing_ha_verification")"
  routing_ha_runtime_json="$(ruby -rjson -e '
    record = JSON.parse(File.read(ARGV.fetch(0)))
    freshness = ARGV.fetch(1)
    status = if freshness == "fresh" && record["status"] == "passed" &&
        record["scenarioCount"].to_i == 25 && record["cleanupStatus"] == "passed"
      "verified"
    elsif freshness == "stale"
      "stale"
    elsif freshness == "fresh"
      "degraded"
    else
      "present"
    end
    print JSON.generate({
      "status" => status,
      "freshness" => freshness,
      "scenarioCount" => record["scenarioCount"].to_i,
      "cleanupStatus" => record["cleanupStatus"],
      "topology" => record["topology"],
      "scenarioEvidencePath" => record["scenarioEvidencePath"]
    })
  ' "$routing_ha_verification" "$routing_ha_freshness")"
else
  routing_ha_runtime_json='{"status":"missing","freshness":"missing","scenarioCount":0,"cleanupStatus":null,"topology":null,"scenarioEvidencePath":null}'
fi

# 压测证据按专用 scope 判断 freshness，使计划状态更新不使已完成的真实运行失效。
load_test_runtime_evidence() {
  local kind="$1"
  local file="$ROOT_DIR/.harness/verifications/load-test-$kind.json"
  if [[ ! -f "$file" ]]; then
    printf '{"status":"missing","freshness":"missing","batchId":null,"configDigest":null,"worktreeStable":null,"cleanupStatus":null,"ordinaryDataUnchanged":null,"perRoundDecisions":[]}'
    return
  fi
  local resolved_config_json
  local current_config_digest
  if [[ "$kind" == smoke ]]; then
    resolved_config_json="$(load_test_smoke_resolved_config 2>/dev/null || printf '{}')"
  else
    resolved_config_json="$(ruby -rjson -e '
      value = JSON.parse(File.read(ARGV.fetch(0)))["resolvedConfig"]
      print JSON.generate(value || {})
    ' "$file")"
  fi
  current_config_digest="${HARNESS_CURRENT_LOAD_TEST_CONFIG_DIGEST:-$(load_test_config_digest "$ROOT_DIR" "$resolved_config_json" 2>/dev/null || printf unavailable)}"
  ruby -rjson -e '
    evidence = JSON.parse(File.read(ARGV.fetch(0)))
    current_scope = ARGV.fetch(1)
    kind = ARGV.fetch(2)
    current_config_digest = ARGV.fetch(3)
    recorded_scope = evidence["loadTestScopeFingerprint"]
    config_fresh = current_config_digest != "unavailable" &&
      evidence["configDigest"] == current_config_digest
    freshness = if current_scope == "unavailable" || recorded_scope.nil? || current_config_digest == "unavailable"
      "unavailable"
    elsif evidence["loadTestScopeStable"] == true && current_scope == recorded_scope && config_fresh
      "fresh"
    else
      "stale"
    end
    rounds = Array(evidence["perRoundDecisions"])
    decisions_valid = !rounds.empty? && rounds.all? do |round|
      %w[stable service_failed uncertain skipped].include?(round.dig("decision", "status"))
    end
    smoke_stable = kind != "smoke" || (
      rounds.map { |round| round["kind"] } == %w[connection message] &&
      rounds.fetch(0).dig("decision", "status") == "stable" &&
      %w[stable service_failed].include?(rounds.fetch(1).dig("decision", "status"))
    )
    capacity_complete = kind != "capacity" || (
      rounds.count { |round| round["kind"] == "connection" } >= 1 &&
      rounds.select { |round| round["kind"] == "connection" }
        .all? { |round| round.dig("decision", "status") == "stable" } &&
      rounds.count { |round| round["kind"] == "message" } == 1 &&
      %w[stable service_failed skipped].include?(
        rounds.find { |round| round["kind"] == "message" }.dig("decision", "status")
      )
    )
    verified = freshness == "fresh" && evidence["status"] == "passed" &&
      evidence["cleanupStatus"] == "passed" && evidence["ordinaryDataUnchanged"] == true &&
      evidence["worktreeStable"] == true && decisions_valid && smoke_stable && capacity_complete
    status = if verified
      "verified"
    elsif freshness == "stale"
      "stale"
    elsif freshness == "unavailable"
      "present"
    else
      "degraded"
    end
    print JSON.generate({
      "status" => status,
      "freshness" => freshness,
      "batchId" => evidence["batchId"],
      "configDigest" => evidence["configDigest"],
      "configFresh" => config_fresh,
      "worktreeStable" => evidence["worktreeStable"],
      "cleanupStatus" => evidence["cleanupStatus"],
      "ordinaryDataUnchanged" => evidence["ordinaryDataUnchanged"],
      "perRoundDecisions" => rounds
    })
  ' "$file" "$current_load_test_scope" "$kind" "$current_config_digest"
}

load_test_smoke_runtime_json="$(load_test_runtime_evidence smoke)"
load_test_capacity_runtime_json="$(load_test_runtime_evidence capacity)"
load_test_runtime_statuses="$(ruby -rjson -e '
  puts [JSON.parse(ARGV.fetch(0))["status"], JSON.parse(ARGV.fetch(1))["status"]].join(" ")
' "$load_test_smoke_runtime_json" "$load_test_capacity_runtime_json")"
for load_test_runtime_status in $load_test_runtime_statuses; do
  case "$load_test_runtime_status" in
    degraded) overall_status=failed ;;
    stale) [[ "$overall_status" == failed ]] || overall_status=stale ;;
    missing|present)
      [[ "$overall_status" == failed || "$overall_status" == stale ]] || overall_status=incomplete
      ;;
  esac
done

routing_capacity_verification="$ROOT_DIR/.harness/verifications/routing-capacity.json"
if [[ -f "$routing_capacity_verification" ]]; then
  routing_capacity_runtime_json="$(ruby -rjson -e '
    evidence = JSON.parse(File.read(ARGV.fetch(0)))
    current_scope, current_config = ARGV.fetch(1), ARGV.fetch(2)
    scope_fresh = current_scope != "unavailable" &&
      evidence["loadTestScopeFingerprint"] == current_scope && evidence["loadTestScopeStable"] == true
    config_fresh = current_config != "unavailable" && evidence["configDigest"] == current_config
    freshness = scope_fresh && config_fresh ? "fresh" :
      (current_scope == "unavailable" || current_config == "unavailable" ? "unavailable" : "stale")
    verified = freshness == "fresh" && evidence["status"] == "passed" &&
      evidence["worktreeStable"] == true && evidence["cleanupStatus"] == "passed" &&
      evidence["ordinaryDataUnchanged"] == true && evidence["phaseCount"] == 5 &&
      evidence["repetitionCount"] == 3 && evidence["workloadCount"] == 30
    status = verified ? "verified" :
      (freshness == "stale" ? "stale" : (freshness == "unavailable" ? "present" : "degraded"))
    print JSON.generate({
      "status" => status, "freshness" => freshness, "topologyId" => evidence["topologyId"],
      "configDigest" => evidence["configDigest"], "configFresh" => config_fresh,
      "loadTestScopeFingerprint" => evidence["loadTestScopeFingerprint"],
      "loadTestScopeFresh" => scope_fresh, "workloadCount" => evidence["workloadCount"],
      "phaseCount" => evidence["phaseCount"], "repetitionCount" => evidence["repetitionCount"],
      "qualificationStatus" => evidence["qualificationStatus"],
      "statusCounts" => evidence["statusCounts"],
      "cleanupStatus" => evidence["cleanupStatus"],
      "ordinaryDataUnchanged" => evidence["ordinaryDataUnchanged"],
      "resultsPath" => evidence["resultsPath"]
    })
  ' "$routing_capacity_verification" "$current_load_test_scope" \
    "$current_routing_capacity_config_digest")"
else
  routing_capacity_runtime_json='{"status":"missing","freshness":"missing","topologyId":null,"workloadCount":0,"phaseCount":0,"repetitionCount":0,"qualificationStatus":null,"statusCounts":null,"cleanupStatus":null,"ordinaryDataUnchanged":null,"resultsPath":null}'
fi
routing_capacity_candidates_json="$(ruby -rjson -e '
  directory, current_scope, current_config = ARGV
  candidates = Dir.glob(File.join(directory, "*", "evidence.json")).sort.map do |path|
    evidence = JSON.parse(File.read(path))
    scope_fresh = current_scope != "unavailable" &&
      evidence["loadTestScopeFingerprint"] == current_scope && evidence["loadTestScopeStable"] == true
    config_fresh = current_config != "unavailable" && evidence["configDigest"] == current_config
    freshness = scope_fresh && config_fresh ? "fresh" :
      (current_scope == "unavailable" || current_config == "unavailable" ? "unavailable" : "stale")
    valid = evidence["worktreeStable"] == true && evidence["cleanupStatus"] == "passed" &&
      evidence["ordinaryDataUnchanged"] == true && evidence["phaseCount"] == 5 &&
      evidence["repetitionCount"] == 3 && evidence["workloadCount"] == 30
    {
      "topologyId" => evidence["topologyId"],
      "status" => freshness == "fresh" && valid ? "verified" :
        (freshness == "stale" ? "stale" : (freshness == "unavailable" ? "present" : "degraded")),
      "freshness" => freshness,
      "qualificationStatus" => evidence["qualificationStatus"],
      "statusCounts" => evidence["statusCounts"],
      "resultsPath" => evidence["resultsPath"]
    }
  end
  print JSON.generate(candidates)
' "$ROOT_DIR/.harness/routing-capacity/evidence" "$current_load_test_scope" \
  "$current_routing_capacity_config_digest")"
routing_capacity_runtime_json="$(ruby -rjson -e '
  current = JSON.parse(ARGV.fetch(0))
  current["candidates"] = JSON.parse(ARGV.fetch(1))
  print JSON.generate(current)
' "$routing_capacity_runtime_json" "$routing_capacity_candidates_json")"
routing_capacity_runtime_status="$(ruby -rjson -e 'print JSON.parse(ARGV.fetch(0))["status"]' "$routing_capacity_runtime_json")"
case "$routing_capacity_runtime_status" in
  degraded) overall_status=failed ;;
  stale) [[ "$overall_status" == failed ]] || overall_status=stale ;;
  missing|present) [[ "$overall_status" == failed || "$overall_status" == stale ]] || overall_status=incomplete ;;
esac

cat > "$OUTPUT" <<EOF
{
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "$overall_status",
  "failedVerifications": [$failed_verification_json],
  "repository": {"headRevision": "$current_head", "worktreeFingerprint": "$current_fingerprint"},
  "score": $score,
  "sensorCoverage": $score,
  "availableSensors": $available,
  "totalSensors": ${#sensor_files[@]},
  "subsystems": {
    "instruction": {"status": "$instruction_status", "evidence": ["AGENTS.md", "ARCHITECTURE.md", "docs/references/HARNESS_GUIDE.md", "docs/references/HARNESS_RULE_MATRIX.md"]},
    "tools": {"status": "$tools_status", "evidence": ["scripts/verify.sh", "scripts/check-drift.sh", "im-test/im-architecture-test/pom.xml"]},
    "environment": {"status": "$environment_status", "evidence": ["pom.xml", ".java-version", ".nvmrc", "README.md", "verify.sh readiness"]},
    "state": {"status": "$state_status", "evidence": ["PROGRESS.md", "docs/PLANS.md", "docs/exec-plans/active", "docs/exec-plans/completed"]},
    "feedback": {"status": "$feedback_status", "evidence": ["verification JSON", "Harness report", "Harness Feedback"]}
  },
  "externalHarnessAdaptation": {
    "status": "adapted",
    "capabilities": [
      {"status": "implemented", "capability": "repository instructions", "evidence": "AGENTS.md and local documentation routing"},
      {"status": "equivalent", "capability": "task runner", "evidence": "scripts/verify.sh replaces Makefile targets"},
      {"status": "implemented", "capability": "root PROGRESS.md", "evidence": "bounded current-state index linked to active execution plans"},
      {"status": "equivalent", "capability": "cross-session state", "evidence": "PROGRESS index plus active execution plan recovery state"},
      {"status": "equivalent", "capability": "dependency reproducibility", "evidence": "Maven dependency management plus nested UI package-lock files"},
      {"status": "equivalent", "capability": "feature list", "evidence": "docs/product-specs/FEATURES.md owns behavior coverage while PROGRESS and execution plans own work state"},
      {"status": "deferred", "capability": "automatic agent loop", "reason": "requires a machine task queue and explicit user authorization"}
    ]
  },
  "runtimeEvidence": {
    "e2e": $e2e_runtime_json,
    "routingHa": $routing_ha_runtime_json,
    "loadTest": {"smoke": $load_test_smoke_runtime_json, "capacity": $load_test_capacity_runtime_json},
    "routingCapacity": $routing_capacity_runtime_json
  },
  "drift": {"status": "$status", "durationSeconds": $duration},
  "tests": {"status": "$test_status", "total": $tests, "failures": $failures, "errors": $errors, "skipped": $skipped},
  "governance": {"activePlans": $active_plans, "stalePlans": $stale_plans, "openTechnicalDebt": $open_debt},
  "feedback": {"falsePositives": $false_positives, "flakyTests": $flaky_tests, "performanceIssues": $performance_issues},
  "verifications": [$verification_json],
  "note": "The score measures Harness sensor availability. Test totals come from available Surefire reports; feedback counts require explicit tracker entries. These signals do not measure source-code quality."
}
EOF

printf 'Harness report: %s\n' "$OUTPUT"
[[ "$status" == "passed" ]]
