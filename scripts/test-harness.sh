#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

bash "$ROOT_DIR/scripts/test-sql-harness.sh"
bash "$ROOT_DIR/scripts/test-java-style-harness.sh"
bash "$ROOT_DIR/scripts/test-management-ui-harness.sh"
bash "$ROOT_DIR/scripts/check-management-ui.sh"

space_fixture="$(mktemp -d)/repository with spaces"
ln -s "$ROOT_DIR" "$space_fixture"
trap 'rm -rf "${space_fixture%/*}"' EXIT
bash "$space_fixture/scripts/check-drift.sh" >/dev/null

# Verify impact analysis against representative repository paths.

assert_impact() {
  local expected="$1"
  local changed_files="$2"
  local actual
  actual="$(IM_CHANGED_FILES="$changed_files" "$ROOT_DIR/scripts/affected-modules.sh")"
  if [[ "$actual" != "$expected" ]]; then
    printf 'Expected impact "%s" for "%s", got "%s"\n' "$expected" "$changed_files" "$actual" >&2
    exit 1
  fi
}

assert_impact docs "README.md"
assert_impact "im-broker/im-broker-server,im-architecture" \
  "im-broker/im-broker-server/src/main/java/example.java"
assert_impact "im-plugin/im-gossip,im-architecture" \
  "im-plugin/im-gossip/src/main/java/example.java"
assert_impact "im-plugin/im-mq-kafka,im-architecture" \
  "im-plugin/im-mq/src/main/java/example.java"
assert_impact "im-management/im-monitor,im-architecture" \
  "im-management/im-monitor/src/main/java/example.java"
assert_impact "im-management/im-admin,im-architecture" \
  "im-management/im-admin/README.md"
assert_impact "im-service/im-account/im-account-admin-facade,im-service/im-account/im-account-server,im-management/im-admin,im-architecture" \
  "im-service/im-account/im-account-admin-facade/src/main/java/example.java"
assert_impact "im-management/im-iam/im-iam-server,im-management/im-admin,im-management/im-monitor,im-management/im-audit/im-audit-server,im-architecture" \
  "im-management/pom.xml"
assert_impact all "im-common/src/main/java/example.java"

# A failed verification must still leave machine-readable evidence.
failure_output="$ROOT_DIR/target/harness/verifications/unsupported.json"
if "$ROOT_DIR/scripts/verify.sh" unsupported >/dev/null 2>&1; then
  printf 'Unsupported verification mode should fail.\n' >&2
  exit 1
fi
if ! rg -q '"status":"failed"' "$failure_output"; then
  printf 'Failed verification was not recorded.\n' >&2
  exit 1
fi
rm -f "$failure_output"

# Exercise report aggregation with isolated, deterministic fixtures.
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT
mkdir -p "$fixture_root/scripts" \
  "$fixture_root/docs/exec-plans/active" \
  "$fixture_root/docs/feedback" \
  "$fixture_root/target/harness/verifications" \
  "$fixture_root/module/target/surefire-reports"
cp "$ROOT_DIR/scripts/harness-report.sh" "$fixture_root/scripts/harness-report.sh"
cat > "$fixture_root/scripts/check-drift.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$fixture_root/scripts/check-drift.sh"
cat > "$fixture_root/docs/exec-plans/tech-debt-tracker.md" <<'EOF'
| ID | Area | Evidence | Impact | Exit condition | Status |
|----|------|----------|--------|----------------|--------|
| TD-1 | Harness | evidence | impact | remove | OPEN |
EOF
cat > "$fixture_root/docs/feedback/HARNESS_FEEDBACK.md" <<'EOF'
| ID | Type | Evidence | Owner | Exit condition | Status |
|----|------|----------|-------|----------------|--------|
| HF-1 | FALSE_POSITIVE | evidence | harness | refine | OPEN |
| HF-2 | FLAKY_TEST | evidence | harness | stabilize | CLOSED |
EOF
full_started_at="$(( $(date +%s) - 60 ))"
cat > "$fixture_root/target/harness/verifications/full.json" <<EOF
{"mode":"full","status":"passed","durationSeconds":12,"startedAtEpoch":$full_started_at,"finishedAt":"2026-01-01T00:02:00Z"}
EOF
cat > "$fixture_root/target/harness/verifications/behavior.json" <<'EOF'
{"mode":"behavior","status":"failed","durationSeconds":3,"startedAtEpoch":100,"finishedAt":"1970-01-01T00:02:00Z"}
EOF
cat > "$fixture_root/module/target/surefire-reports/TEST-current.xml" <<'EOF'
<testsuite tests="4" failures="0" errors="0" skipped="1"/>
EOF
touch "$fixture_root/module/target/surefire-reports/TEST-current.xml"
cat > "$fixture_root/module/target/surefire-reports/TEST-stale.xml" <<'EOF'
<testsuite tests="99" failures="1" errors="0" skipped="0"/>
EOF
touch -t 202001010001 "$fixture_root/module/target/surefire-reports/TEST-stale.xml"

HARNESS_ROOT_DIR="$fixture_root" "$fixture_root/scripts/harness-report.sh" \
  "$fixture_root/target/harness/report.json" >/dev/null
report="$fixture_root/target/harness/report.json"
rg -q '"status": "failed"' "$report"
rg -q '"failedVerifications": \["behavior"\]' "$report"
rg -q '"tests": \{"status": "current", "total": 4, "failures": 0, "errors": 0, "skipped": 1\}' "$report"
rg -q '"openTechnicalDebt": 1' "$report"
rg -q '"falsePositives": 1' "$report"
rg -q '"flakyTests": 0' "$report"

printf 'Harness script tests passed.\n'
