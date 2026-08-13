#!/usr/bin/env bash

set -euo pipefail

# HARNESS_ROOT_DIR allows the script contract to be tested with isolated fixtures.
ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
OUTPUT="${1:-$ROOT_DIR/target/harness/report.json}"
mkdir -p "$(dirname "$OUTPUT")"

started_at="$(date +%s)"
status="passed"
if ! "$ROOT_DIR/scripts/check-drift.sh"; then
  status="failed"
fi
duration=$(( $(date +%s) - started_at ))

# Sensors represent executable context and feedback capabilities, not a code-quality score.
sensor_files=(
  AGENTS.md
  im-architecture/pom.xml
  docs/product-specs/im-realtime-approved-scenarios.md
  scripts/affected-modules.sh
  .github/workflows/verify.yml
  im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/support/monitoring/BrokerMetrics.java
  docs/references/CODE_REVIEW_GUIDE.md
  docs/references/HARNESS_GUIDE.md
  docs/feedback/HARNESS_FEEDBACK.md
)
available=0
for file in "${sensor_files[@]}"; do
  [[ -f "$ROOT_DIR/$file" ]] && available=$((available + 1))
done
score=$((available * 100 / ${#sensor_files[@]}))

# Test totals are current only when generated after the latest successful full verification began.
test_status="unknown"
tests=0
failures=0
errors=0
skipped=0
full_verification="$ROOT_DIR/target/harness/verifications/full.json"
if [[ -f "$full_verification" ]] \
    && rg -q '"status":"passed"' "$full_verification" \
    && rg -q '"startedAtEpoch":[0-9]+' "$full_verification"; then
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
  read -r tests failures errors skipped <<< "$test_summary"
  test_status="current"
fi

active_plans="$(find "$ROOT_DIR/docs/exec-plans/active" -type f -name '*.md' ! -name README.md | wc -l | tr -d ' ')"
stale_plans="$(find "$ROOT_DIR/docs/exec-plans/active" -type f -name '*.md' ! -name README.md -mtime +30 | wc -l | tr -d ' ')"
open_debt="$(awk -F'|' '/^\| TD-[0-9]+ / && $7 !~ /(DONE|CLOSED)/ {count++} END {print count + 0}' "$ROOT_DIR/docs/exec-plans/tech-debt-tracker.md")"
false_positives="$(awk -F'|' '/^\| HF-[0-9]+ / && $3 ~ /FALSE_POSITIVE/ && $7 !~ /CLOSED/ {count++} END {print count + 0}' "$ROOT_DIR/docs/feedback/HARNESS_FEEDBACK.md")"
flaky_tests="$(awk -F'|' '/^\| HF-[0-9]+ / && $3 ~ /FLAKY_TEST/ && $7 !~ /CLOSED/ {count++} END {print count + 0}' "$ROOT_DIR/docs/feedback/HARNESS_FEEDBACK.md")"
performance_issues="$(awk -F'|' '/^\| HF-[0-9]+ / && $3 ~ /PERFORMANCE/ && $7 !~ /CLOSED/ {count++} END {print count + 0}' "$ROOT_DIR/docs/feedback/HARNESS_FEEDBACK.md")"

verification_files=("$ROOT_DIR"/target/harness/verifications/*.json)
verification_json=""
failed_verifications=()
if [[ -e "${verification_files[0]}" ]]; then
  for file in "${verification_files[@]}"; do
    [[ -n "$verification_json" ]] && verification_json+=","
    verification_json+="$(<"$file")"
    if rg -q '"status":"failed"' "$file"; then
      failed_verifications+=("$(sed -E 's/.*"mode":"([^"]+)".*/\1/' "$file")")
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
fi

cat > "$OUTPUT" <<EOF
{
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "$overall_status",
  "failedVerifications": [$failed_verification_json],
  "score": $score,
  "availableSensors": $available,
  "totalSensors": ${#sensor_files[@]},
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
