#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-quick}"
cd "$ROOT_DIR"

# Persist every verification outcome so reports can expose failures even when CI continues.
record_verification() {
  local mode="$1"
  local status="$2"
  local duration="$3"
  local started_epoch="$4"
  local output_dir="$ROOT_DIR/target/harness/verifications"
  mkdir -p "$output_dir"
  cat > "$output_dir/$mode.json" <<EOF
{"mode":"$mode","status":"$status","durationSeconds":$duration,"startedAtEpoch":$started_epoch,"finishedAt":"$(date -u +%Y-%m-%dT%H:%M:%SZ)"}
EOF
}

run_drift() {
  printf '\n==> Checking repository drift\n'
  "$ROOT_DIR/scripts/check-drift.sh"
}

run_architecture() {
  printf '\n==> Running architecture tests\n'
  mvn -q -pl im-architecture -am -Dmaven.test.skip=true install
  mvn -q -pl im-architecture test
}

run_script_tests() {
  printf '\n==> Running Harness script tests\n'
  "$ROOT_DIR/scripts/test-harness.sh"
}

run_full() {
  run_drift
  run_script_tests
  printf '\n==> Running full Maven verification suite\n'
  mvn -q verify
}

run_behavior() {
  local behavior_tag="realtime-behavior"
  local broker_reports="$ROOT_DIR/im-broker/im-broker-server/target/surefire-reports"
  local gateway_reports="$ROOT_DIR/im-gateway/im-ws-gateway/im-ws-gateway-server/target/surefire-reports"

  if ! rg -q "@Tag\\(\"$behavior_tag\"\\)" \
      "$ROOT_DIR/im-broker/im-broker-server/src/test" \
      "$ROOT_DIR/im-gateway/im-ws-gateway/im-ws-gateway-server/src/test"; then
    printf 'No approved behavior tests use JUnit tag: %s\n' "$behavior_tag" >&2
    return 1
  fi

  # Remove stale reports so the execution count can only come from this behavior run.
  find "$broker_reports" "$gateway_reports" -type f -name 'TEST-*.xml' -delete 2>/dev/null || true

  printf '\n==> Running approved realtime behavior scenarios\n'
  # Build reactor dependencies first; applying groups to facade modules without a test engine makes Surefire fail.
  mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server -am \
    -DskipTests install
  mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server \
    -Dgroups="$behavior_tag" test

  local executed_tests
  executed_tests="$(ruby -r rexml/document -e '
    total = ARGV.sum do |file|
      REXML::Document.new(File.read(file)).root.attributes["tests"].to_i
    end
    puts total
  ' $(find "$broker_reports" "$gateway_reports" -type f -name 'TEST-*.xml' -print 2>/dev/null))"
  if (( executed_tests == 0 )); then
    printf 'No tests executed for JUnit tag: %s\n' "$behavior_tag" >&2
    return 1
  fi
  printf 'Executed %d approved realtime behavior tests.\n' "$executed_tests"
}

run_affected() {
  run_drift
  modules="$($ROOT_DIR/scripts/affected-modules.sh "${2:-}")"
  case "$modules" in
    none|docs) printf '\n==> No Java module tests are affected (%s)\n' "$modules" ;;
    all) printf '\n==> Shared build input changed; running full suite\n'; mvn -q test ;;
    *) printf '\n==> Running affected modules: %s\n' "$modules"; mvn -q -pl "$modules" -am test ;;
  esac
}

# Keep mode selection in one function so failure recording wraps every execution path.
dispatch() {
  case "$MODE" in
  drift)
    run_drift
    ;;
  architecture)
    run_architecture
    ;;
  quick)
    run_drift
    run_script_tests
    run_architecture
    ;;
  full)
    run_full
    ;;
  affected)
    run_affected "$@"
    ;;
  behavior)
    run_behavior
    ;;
  report)
    "$ROOT_DIR/scripts/harness-report.sh"
    ;;
  *)
    printf 'Usage: %s {drift|architecture|behavior|quick|affected|full|report}\n' "$0" >&2
    return 2
    ;;
  esac
}

started_at="$(date +%s)"
set +e
(set -e; dispatch)
exit_code=$?
set -e

status="passed"
if (( exit_code != 0 )); then
  status="failed"
fi
record_verification "$MODE" "$status" "$(( $(date +%s) - started_at ))" "$started_at"
exit "$exit_code"
