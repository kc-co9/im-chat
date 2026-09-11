#!/usr/bin/env bash
# 用途：自测全部 Harness checker、影响分析、验证记录和报告聚合契约。
# 输入：无位置参数；所有特殊场景在临时目录或注入变量中构造。
# 输出/副作用：执行子 Harness，短暂写入 .harness 测试证据并清理临时目录。
# 依赖：bash、git、rg、Ruby、mktemp 及全部 scripts/test-*-harness.sh。
# 退出码：所有正例、反例和 false-positive fixture 符合预期返回 0；任一断言失败返回非 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! rg -Fxq '/.harness/' "$ROOT_DIR/.gitignore"; then
  printf 'Root .gitignore must ignore the local Harness state directory.\n' >&2
  exit 1
fi

# worktree fingerprint 只描述文件树内容，不能因相同内容从 untracked 变为 staged 而变化。
fingerprint_root="$(mktemp -d)"
trap 'rm -rf "$fingerprint_root"' EXIT
git -C "$fingerprint_root" init --quiet
git -C "$fingerprint_root" config user.email harness@example.invalid
git -C "$fingerprint_root" config user.name Harness
printf 'base\n' > "$fingerprint_root/base.txt"
git -C "$fingerprint_root" add base.txt
git -C "$fingerprint_root" commit --quiet -m base
source "$ROOT_DIR/scripts/lib/harness-evidence.sh"
printf 'new\n' > "$fingerprint_root/new.txt"
fingerprint_untracked="$(harness_worktree_fingerprint "$fingerprint_root")"
git -C "$fingerprint_root" add new.txt
fingerprint_staged="$(harness_worktree_fingerprint "$fingerprint_root")"
if [[ "$fingerprint_untracked" != "$fingerprint_staged" ]]; then
  printf 'Worktree fingerprint must not change when identical content is staged.\n' >&2
  exit 1
fi
printf 'changed\n' > "$fingerprint_root/new.txt"
fingerprint_changed="$(harness_worktree_fingerprint "$fingerprint_root")"
if [[ "$fingerprint_staged" == "$fingerprint_changed" ]]; then
  printf 'Worktree fingerprint must change when file content changes.\n' >&2
  exit 1
fi
rm -rf "$fingerprint_root"
trap - EXIT

# 先验证独立 handoff 契约，再继续聚合 checker 与生命周期 fixture。
"$ROOT_DIR/scripts/test-handoff-harness.sh"
"$ROOT_DIR/scripts/test-startup-harness.sh"
"$ROOT_DIR/scripts/test-cleanup-harness.sh"
"$ROOT_DIR/scripts/test-quality-harness.sh"
cd "$ROOT_DIR"

bash "$ROOT_DIR/scripts/test-sql-harness.sh"
bash "$ROOT_DIR/scripts/test-java-style-harness.sh"
bash "$ROOT_DIR/scripts/test-management-ui-harness.sh"
bash "$ROOT_DIR/scripts/check-management-ui.sh"

space_fixture="$(mktemp -d)/repository with spaces"
ln -s "$ROOT_DIR" "$space_fixture"
trap 'rm -rf "${space_fixture%/*}"' EXIT
bash "$space_fixture/scripts/check-drift.sh" >/dev/null

# 在隔离 Git 仓库验证已跟踪 Markdown 的断链、缺失/不可读、枚举失败和未跟踪文件语义。
fixture_root="$(mktemp -d)"
trap 'rm -rf "${space_fixture%/*}" "$fixture_root"' EXIT
git -C "$fixture_root" init --quiet
mkdir -p "$fixture_root/scripts" \
  "$fixture_root/module" \
  "$fixture_root/im-gateway" \
  "$fixture_root/im-broker" \
  "$fixture_root/im-service/im-message" \
  "$fixture_root/im-test/im-architecture-test" \
  "$fixture_root/im-test/im-e2e-test" \
  "$fixture_root/docs/design-docs" \
  "$fixture_root/docs/exec-plans/active" \
  "$fixture_root/docs/exec-plans/completed" \
  "$fixture_root/docs/product-specs" \
  "$fixture_root/docs/references"
cp "$ROOT_DIR/scripts/check-drift.sh" "$fixture_root/scripts/check-drift.sh"
cat > "$fixture_root/scripts/check-java-style.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "$fixture_root/scripts/check-sql.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$fixture_root/scripts/check-drift.sh" \
  "$fixture_root/scripts/check-java-style.sh" \
  "$fixture_root/scripts/check-sql.sh"
touch "$fixture_root/README.md" \
  "$fixture_root/PROGRESS.md" \
  "$fixture_root/.java-version" \
  "$fixture_root/.nvmrc" \
  "$fixture_root/AGENTS.md" \
  "$fixture_root/ARCHITECTURE.md" \
  "$fixture_root/im-test/AGENTS.md" \
  "$fixture_root/im-test/README.md" \
  "$fixture_root/im-test/im-architecture-test/README.md" \
  "$fixture_root/im-test/im-e2e-test/README.md" \
  "$fixture_root/im-gateway/ARCHITECTURE.md" \
  "$fixture_root/im-broker/ARCHITECTURE.md" \
  "$fixture_root/im-service/im-message/ARCHITECTURE.md" \
  "$fixture_root/docs/design-docs/index.md" \
  "$fixture_root/docs/exec-plans/TEMPLATE.md" \
  "$fixture_root/docs/exec-plans/active/README.md" \
  "$fixture_root/docs/exec-plans/completed/README.md" \
  "$fixture_root/docs/exec-plans/tech-debt-tracker.md" \
  "$fixture_root/docs/product-specs/index.md" \
  "$fixture_root/docs/references/index.md" \
  "$fixture_root/docs/PLANS.md" \
  "$fixture_root/docs/RELIABILITY.md" \
  "$fixture_root/docs/SECURITY.md"
printf '# 项目进度\n\n## 当前工作\n\n- 活跃计划：`none`\n' > "$fixture_root/PROGRESS.md"
cat > "$fixture_root/docs/product-specs/FEATURES.md" <<'EOF'
| ID | Owner | 可观察行为 | 验证入口 | 覆盖状态 | 证据边界 |
|---|---|---|---|---|---|
| RT-001 | Broker | route | `verify behavior` | `covered` | JVM behavior |
EOF
cat > "$fixture_root/module/README.md" <<'EOF'
[Missing target](missing-target.md)
EOF
git -C "$fixture_root" add .

if markdown_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Tracked module README with a broken link should fail.\n' >&2
  exit 1
fi
if ! rg -q 'module/README\.md' <<<"$markdown_output"; then
  printf 'Broken-link output did not name the source module README.\n' >&2
  exit 1
fi
if ! rg -q 'missing-target\.md' <<<"$markdown_output"; then
  printf 'Broken-link output did not name the missing target.\n' >&2
  exit 1
fi
for label in 'WHAT:' 'WHY:' 'FIX:'; do
  if ! rg -Fq "$label" <<<"$markdown_output"; then
    printf 'Drift diagnostic is missing %s\n%s\n' "$label" "$markdown_output" >&2
    exit 1
  fi
done

touch "$fixture_root/module/0-tracked-but-missing.md"
git -C "$fixture_root" add module/0-tracked-but-missing.md
rm "$fixture_root/module/0-tracked-but-missing.md"
if missing_markdown_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Tracked-but-missing Markdown should fail.\n' >&2
  exit 1
fi
if rg -q 'module/0-tracked-but-missing\.md: tracked Markdown file is missing or unreadable' \
  <<<"$missing_markdown_output"; then
  printf 'A Markdown source deleted in the worktree must not be parsed from the old index.\n' >&2
  exit 1
fi
if ! rg -q 'module/README\.md: missing-target\.md' <<<"$missing_markdown_output"; then
  printf 'A missing tracked file prevented a later broken link from being reported.\n' >&2
  exit 1
fi
touch "$fixture_root/module/0-tracked-but-missing.md"

rm "$fixture_root/module/0-tracked-but-missing.md"
mkdir "$fixture_root/module/0-tracked-but-missing.md"
if unreadable_markdown_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Tracked-but-unreadable Markdown should fail.\n' >&2
  exit 1
fi
if ! rg -q 'module/0-tracked-but-missing\.md: tracked Markdown file is missing or unreadable' \
  <<<"$unreadable_markdown_output"; then
  printf 'Unreadable-file output did not name the tracked Markdown file.\n' >&2
  exit 1
fi
if ! rg -q 'module/README\.md: missing-target\.md' <<<"$unreadable_markdown_output"; then
  printf 'An unreadable tracked file prevented a later broken link from being reported.\n' >&2
  exit 1
fi
rmdir "$fixture_root/module/0-tracked-but-missing.md"
touch "$fixture_root/module/0-tracked-but-missing.md"

mv "$fixture_root/.git" "$fixture_root/.git-disabled"
if enumeration_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  enumeration_status=0
else
  enumeration_status=$?
fi
mv "$fixture_root/.git-disabled" "$fixture_root/.git"
if (( enumeration_status == 0 )); then
  printf 'Markdown enumeration failure should fail.\n' >&2
  exit 1
fi
if ! rg -Fq 'git ls-files -- *.md: failed with' <<<"$enumeration_output"; then
  printf 'Enumeration-failure output did not include the Markdown checker diagnostic.\n' >&2
  exit 1
fi

cat > "$fixture_root/module/README.md" <<'EOF'
[Existing target](existing-target.md)
EOF
touch "$fixture_root/module/existing-target.md"
git -C "$fixture_root" add module/README.md module/existing-target.md
cat > "$fixture_root/module/untracked.md" <<'EOF'
[Ignored missing target](untracked-missing-target.md)
EOF
if ! markdown_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Valid tracked links and broken untracked Markdown should pass.\n%s\n' \
    "$markdown_output" >&2
  exit 1
fi

git -C "$fixture_root" rm --cached --force im-broker/ARCHITECTURE.md >/dev/null
rm "$fixture_root/im-broker/ARCHITECTURE.md"
if architecture_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Missing required local Architecture should fail.\n' >&2
  exit 1
fi
if ! rg -Fq 'im-broker/ARCHITECTURE.md' <<<"$architecture_output"; then
  printf 'Missing Architecture output did not name the required module document.\n' >&2
  exit 1
fi

touch "$fixture_root/im-broker/ARCHITECTURE.md"
git -C "$fixture_root" add im-broker/ARCHITECTURE.md
cat > "$fixture_root/docs/exec-plans/active/current.md" <<'EOF'
# Current Plan
## Sprint Contract
## 事实源
## 任务状态
## 恢复状态
## 回滚与残余风险
EOF
git -C "$fixture_root" add docs/exec-plans/active/current.md
printf '# 项目进度\n\n## 当前工作\n\n- 活跃计划：docs/exec-plans/active/current.md\n' \
  > "$fixture_root/PROGRESS.md"
if plan_structure_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Active plan without verification layers should fail.\n' >&2
  exit 1
fi
if ! rg -Fq 'Active execution plan structure is incomplete' <<<"$plan_structure_output"; then
  printf 'Plan-structure output did not explain the missing required section.\n%s\n' \
    "$plan_structure_output" >&2
  exit 1
fi

cat > "$fixture_root/docs/exec-plans/active/current.md" <<'EOF'
# Current Plan
## Sprint Contract
## 事实源
## 验证分层
## 任务状态
## 恢复状态
## 回滚与残余风险
EOF
if plan_state_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Active plan without a task-state table should fail.\n' >&2
  exit 1
fi
if ! rg -Fq 'Active execution plan task state is invalid' <<<"$plan_state_output"; then
  printf 'Plan-state output did not explain the missing task-state table.\n%s\n' \
    "$plan_state_output" >&2
  exit 1
fi

cat > "$fixture_root/docs/exec-plans/active/current.md" <<'EOF'
# Current Plan
## Sprint Contract
## 事实源
## 验证分层
## 任务状态
| ID | 行为目标 | 状态 |
|---|---|---|
| T1 | behavior | `done` |
## 恢复状态
## 回滚与残余风险
EOF
if plan_state_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Active plan with an unsupported task state should fail.\n' >&2
  exit 1
fi
if ! rg -Fq 'Active execution plan task state is invalid' <<<"$plan_state_output"; then
  printf 'Plan-state output did not explain the unsupported state.\n%s\n' \
    "$plan_state_output" >&2
  exit 1
fi

sed -i.bak 's/`done`/`passing`/' "$fixture_root/docs/exec-plans/active/current.md"
rm "$fixture_root/docs/exec-plans/active/current.md.bak"
printf '# 项目进度\n\n## 当前工作\n\n- 活跃计划：`none`\n' \
  > "$fixture_root/PROGRESS.md"
if progress_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'PROGRESS without the active plan link should fail.\n' >&2
  exit 1
fi
if ! rg -Fq 'PROGRESS.md does not reference active plan' <<<"$progress_output"; then
  printf 'Progress drift output did not explain the missing active plan link.\n' >&2
  exit 1
fi

printf '# 项目进度\n\n## 当前工作\n\n- 活跃计划：docs/exec-plans/active/current.md\n' \
  > "$fixture_root/PROGRESS.md"
cat > "$fixture_root/docs/product-specs/FEATURES.md" <<'EOF'
| ID | Owner | 可观察行为 | 验证入口 | 覆盖状态 | 证据边界 |
|---|---|---|---|---|---|
| RT-001 | Broker | first route |  | `done` | JVM behavior |
| RT-001 | Gateway | duplicate route | `verify e2e` | `covered` | realtime E2E |
EOF
git -C "$fixture_root" add docs/product-specs/FEATURES.md
if feature_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Invalid feature catalog should fail.\n' >&2
  exit 1
fi
if ! rg -Fq 'Feature catalog structure is invalid' <<<"$feature_output"; then
  printf 'Feature catalog output did not explain the invalid structure.\n%s\n' \
    "$feature_output" >&2
  exit 1
fi

cat > "$fixture_root/docs/product-specs/FEATURES.md" <<'EOF'
| ID | Owner | 可观察行为 | 验证入口 | 覆盖状态 | 证据边界 |
|---|---|---|---|---|---|
| RT-001 | Broker | first route | `verify behavior` | `covered` | JVM behavior |
| RT-002 | Gateway | second route | `verify e2e` | `partial` | realtime E2E |
EOF
if ! feature_output="$("$fixture_root/scripts/check-drift.sh" 2>&1)"; then
  printf 'Valid feature catalog should pass.\n%s\n' "$feature_output" >&2
  exit 1
fi
rm -rf "$fixture_root"

# 断言代表性路径映射到准确的受影响模块集合。

# 参数依次为预期模块结果和换行分隔的变更文件；不匹配时立即失败。
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
assert_impact "im-broker/im-broker-server,im-test/im-e2e-test,im-test/im-architecture-test" \
  "im-broker/im-broker-server/src/main/java/example.java"
assert_impact "im-broker/im-broker-sdk,im-broker/im-broker-server,im-test/im-e2e-test,im-test/im-architecture-test" \
  "im-broker/im-broker-sdk/src/main/java/example.java"
assert_impact "im-gateway/im-ws-gateway/im-ws-gateway-server,im-test/im-e2e-test,im-test/im-architecture-test" \
  "im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/example.java"
assert_impact "im-gateway/im-ws-gateway/im-ws-gateway-sdk,im-gateway/im-ws-gateway/im-ws-gateway-server,im-broker/im-broker-server,im-test/im-e2e-test,im-test/im-architecture-test" \
  "im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/main/java/example.java"
assert_impact "im-plugin/im-bolt,im-test/im-e2e-test,im-test/im-architecture-test" \
  "im-plugin/im-bolt/src/main/java/example.java"
assert_impact "im-plugin/im-gossip,im-test/im-architecture-test" \
  "im-plugin/im-gossip/src/main/java/example.java"
assert_impact "im-plugin/im-mq-kafka,im-test/im-architecture-test" \
  "im-plugin/im-mq/src/main/java/example.java"
assert_impact "im-management/im-monitor,im-test/im-architecture-test" \
  "im-management/im-monitor/src/main/java/example.java"
assert_impact "im-management/im-admin,im-test/im-architecture-test" \
  "im-management/im-admin/README.md"
assert_impact "im-service/im-account/im-account-admin-facade,im-service/im-account/im-account-server,im-management/im-admin,im-test/im-architecture-test" \
  "im-service/im-account/im-account-admin-facade/src/main/java/example.java"
assert_impact "im-management/im-iam/im-iam-server,im-management/im-admin,im-management/im-monitor,im-management/im-audit/im-audit-server,im-test/im-architecture-test" \
  "im-management/pom.xml"
assert_impact all "im-common/src/main/java/example.java"

# full 必须自行包含环境、清洁状态、Harness fixture 和 Maven verify，不能依赖调用者记住前置命令。
full_root="$(mktemp -d)"
mkdir -p "$full_root/scripts/lib" "$full_root/bin" \
  "$full_root/docs/exec-plans/active" "$full_root/.harness"
cp "$ROOT_DIR/scripts/verify.sh" "$full_root/scripts/verify.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$full_root/scripts/lib/harness-evidence.sh"
cat > "$full_root/scripts/check-drift.sh" <<'EOF'
#!/usr/bin/env bash
printf 'drift\n' >> "$FULL_FIXTURE_TRACE"
EOF
cat > "$full_root/scripts/test-harness.sh" <<'EOF'
#!/usr/bin/env bash
printf 'harness-tests\n' >> "$FULL_FIXTURE_TRACE"
EOF
cat > "$full_root/scripts/harness-report.sh" <<'EOF'
#!/usr/bin/env bash
printf 'report\n' >> "$FULL_FIXTURE_TRACE"
exit "${FULL_REPORT_EXIT:-0}"
EOF
cat > "$full_root/scripts/harness-handoff.sh" <<'EOF'
#!/usr/bin/env bash
printf 'handoff\n' >> "$FULL_FIXTURE_TRACE"
exit "${FULL_HANDOFF_EXIT:-0}"
EOF
chmod +x "$full_root/scripts/check-drift.sh" "$full_root/scripts/test-harness.sh"
chmod +x "$full_root/scripts/harness-report.sh" "$full_root/scripts/harness-handoff.sh"
write_full_tool() {
  local tool="$1"
  local body="$2"
  printf '#!/usr/bin/env bash\n%s\n' "$body" > "$full_root/bin/$tool"
  chmod +x "$full_root/bin/$tool"
}
write_full_tool java 'printf '\''openjdk version "21.0.10"\n'\'''
write_full_tool mvn 'printf '\''maven %s\n'\'' "$*" >> "$FULL_FIXTURE_TRACE"; if [[ "$*" == "-q verify" ]]; then mkdir -p "$FULL_FIXTURE_ROOT/module/target/surefire-reports"; printf '\''<testsuite tests="3" failures="0" errors="0" skipped="1"/>\n'\'' > "$FULL_FIXTURE_ROOT/module/target/surefire-reports/TEST-full.xml"; fi; printf '\''Apache Maven 3.8.4\n'\'''
write_full_tool node 'printf '\''v20.19.5\n'\'''
write_full_tool npm 'printf '\''10.8.2\n'\'''
write_full_tool rg 'exec /usr/bin/grep "$@"'
write_full_tool git 'case "$1" in rev-parse) printf '\''fixture-head\n'\'';; diff|ls-files) :;; *) :;; esac'
full_trace="$full_root/.harness/full-trace.log"
if ! FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
    "$full_root/scripts/verify.sh" full >"$full_root/full.out" 2>&1; then
  printf 'Full lifecycle fixture should pass.\n' >&2
  cat "$full_root/full.out" >&2
  exit 1
fi
if ! ruby -rjson -e '
  summary = JSON.parse(File.read(ARGV.fetch(0)))["testSummary"]
  exit(summary == {"tests" => 3, "failures" => 0, "errors" => 0, "skipped" => 1} ? 0 : 1)
' "$full_root/.harness/verifications/full.json"; then
  printf 'Full verification must persist its test summary outside Maven target directories.\n' >&2
  exit 1
fi
for expected in 'readiness java=21' 'Readiness checks passed.' 'Checking clean state'; do
  if ! /usr/bin/grep -Fq "$expected" "$full_root/full.out"; then
    printf 'Full lifecycle output is missing %s\n' "$expected" >&2
    exit 1
  fi
done
if [[ "$(<"$full_trace")" != $'maven --version\ndrift\nharness-tests\nmaven -q verify\nreport\nhandoff' ]]; then
  printf 'Full lifecycle order is incorrect:\n%s\n' "$(<"$full_trace")" >&2
  exit 1
fi

# full 提前失败时仍生成派生证据，并保留最初的 Maven 退出码。
cat > "$full_root/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf 'maven %s\n' "$*" >> "$FULL_FIXTURE_TRACE"
if [[ "$*" == "--version" ]]; then
  printf 'Apache Maven 3.8.4\n'
elif [[ "$*" == "-q verify" ]]; then
  exit 7
fi
EOF
chmod +x "$full_root/bin/mvn"
: > "$full_trace"
set +e
FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
  "$full_root/scripts/verify.sh" full >/dev/null 2>&1
full_failure_status=$?
set -e
if (( full_failure_status != 7 )); then
  printf 'Full lifecycle must preserve the original gate exit code, got %d.\n' \
    "$full_failure_status" >&2
  exit 1
fi
if [[ "$(<"$full_trace")" != $'maven --version\ndrift\nharness-tests\nmaven -q verify\nreport\nhandoff' ]]; then
  printf 'Failed full lifecycle did not run both evidence post-processors:\n%s\n' \
    "$(<"$full_trace")" >&2
  exit 1
fi

# full 原本通过但 report 失败时必须整体失败，并继续尝试生成 handoff。
cat > "$full_root/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf 'maven %s\n' "$*" >> "$FULL_FIXTURE_TRACE"
if [[ "$*" == "--version" ]]; then
  printf 'Apache Maven 3.8.4\n'
fi
EOF
chmod +x "$full_root/bin/mvn"
: > "$full_trace"
set +e
FULL_REPORT_EXIT=9 FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
  "$full_root/scripts/verify.sh" full >/dev/null 2>&1
postprocess_failure_status=$?
set -e
if (( postprocess_failure_status != 9 )); then
  printf 'Successful full with failed report must fail with report status, got %d.\n' \
    "$postprocess_failure_status" >&2
  exit 1
fi
if [[ "$(<"$full_trace")" != $'maven --version\ndrift\nharness-tests\nmaven -q verify\nreport\nhandoff' ]]; then
  printf 'Report failure prevented handoff post-processing:\n%s\n' "$(<"$full_trace")" >&2
  exit 1
fi
rm -rf "$full_root"

# CI 必须用仓库版本 pin，并在验证前执行 readiness 与 clean。
if (( $(rg -c --fixed-strings -- 'uses: actions/setup-node@v4' \
    "$ROOT_DIR/.github/workflows/verify.yml") != 2 )); then
  printf 'CI lifecycle must configure Node in both verification jobs.\n' >&2
  exit 1
fi
for ci_contract in 'node-version-file: .nvmrc' \
  'run: ./scripts/verify.sh readiness' 'run: ./scripts/verify.sh clean'; do
  if (( $(rg -c "^[[:space:]]+${ci_contract//./\\.}$" \
      "$ROOT_DIR/.github/workflows/verify.yml") != 2 )); then
    printf 'CI lifecycle must contain exactly two lines: %s\n' "$ci_contract" >&2
    exit 1
  fi
done
e2e_ci_count="$(rg -c '^[[:space:]]+run: \./scripts/verify\.sh e2e$' \
  "$ROOT_DIR/.github/workflows/verify.yml" || true)"
if (( ${e2e_ci_count:-0} != 1 )); then
  printf 'CI full lifecycle must run the dedicated E2E evidence entry once.\n' >&2
  exit 1
fi
if ! rg -Fq '.harness/runtime/e2e.log' "$ROOT_DIR/.github/workflows/verify.yml"; then
  printf 'CI artifacts must retain the E2E runtime log.\n' >&2
  exit 1
fi
hidden_artifact_count="$(rg -c 'include-hidden-files: true' "$ROOT_DIR/.github/workflows/verify.yml" || true)"
if (( ${hidden_artifact_count:-0} != 2 )); then
  printf 'Both CI artifact uploads must explicitly include hidden Harness files.\n' >&2
  exit 1
fi

# 用 fake PATH 隔离验证 readiness 的版本范围和缺失工具诊断。
readiness_root="$(mktemp -d)"
mkdir -p "$readiness_root/scripts/lib" "$readiness_root/bin"
cp "$ROOT_DIR/scripts/verify.sh" "$readiness_root/scripts/verify.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$readiness_root/scripts/lib/harness-evidence.sh"

# 参数为工具名和版本输出；生成只服务 readiness fixture 的可执行命令。
write_fake_tool() {
  local tool="$1"
  local output="$2"
  printf '#!/usr/bin/env bash\nprintf '\''%%s\\n'\'' '\''%s'\''\n' "$output" \
    > "$readiness_root/bin/$tool"
  chmod +x "$readiness_root/bin/$tool"
}

write_fake_tool java 'openjdk version "21.0.10"'
write_fake_tool mvn 'Apache Maven 3.8.4'
write_fake_tool node 'v20.19.5'
write_fake_tool npm '10.8.2'
write_fake_tool ruby 'ruby 3.3.0'
write_fake_tool git 'git version 2.50.0'
write_fake_tool rg 'ripgrep 14.1.0'

if ! readiness_output="$(PATH="$readiness_root/bin:/usr/bin:/bin" \
    "$readiness_root/scripts/verify.sh" readiness 2>&1)"; then
  printf 'Compatible readiness fixture should pass.\n%s\n' "$readiness_output" >&2
  exit 1
fi
if ! rg -Fq 'Readiness checks passed.' <<<"$readiness_output"; then
  printf 'Readiness success output is missing.\n' >&2
  exit 1
fi

# 参数为预期 WHAT 片段；运行 readiness 并断言失败诊断同时包含 WHAT/WHY/FIX。
expect_readiness_failure() {
  local expected="$1"
  local output
  if output="$(PATH="$readiness_root/bin:/usr/bin:/bin" \
      "$readiness_root/scripts/verify.sh" readiness 2>&1)"; then
    printf 'Readiness fixture should fail: %s\n' "$expected" >&2
    exit 1
  fi
  for label in 'WHAT:' 'WHY:' 'FIX:' "$expected"; do
    if ! rg -Fq "$label" <<<"$output"; then
      printf 'Readiness failure output is missing: %s\n%s\n' "$label" "$output" >&2
      exit 1
    fi
  done
}

write_fake_tool java 'openjdk version "17.0.12"'
expect_readiness_failure 'Java 版本不受支持: 17'
write_fake_tool java 'openjdk version "21.0.10"'

write_fake_tool mvn 'Apache Maven 3.8.3'
expect_readiness_failure 'Maven 版本不受支持: 3.8.3'
write_fake_tool mvn 'Apache Maven 3.8.4'

write_fake_tool node 'v20.18.1'
expect_readiness_failure 'Node.js 版本不受支持: 20.18.1'
write_fake_tool node 'v20.19.5'

rm "$readiness_root/bin/npm"
expect_readiness_failure '缺少命令: npm'
rm -rf "$readiness_root"

# 在隔离 Git 仓库验证 clean 模式不会误伤正常未跟踪源码。
clean_root="$(mktemp -d)"
mkdir -p "$clean_root/scripts/lib" "$clean_root/docs/exec-plans/active" "$clean_root/src"
cp "$ROOT_DIR/scripts/verify.sh" "$clean_root/scripts/verify.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$clean_root/scripts/lib/harness-evidence.sh"
cat > "$clean_root/scripts/check-drift.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$clean_root/scripts/verify.sh" "$clean_root/scripts/check-drift.sh"
touch "$clean_root/docs/exec-plans/active/README.md"
git -C "$clean_root" init --quiet
git -C "$clean_root" add scripts docs
cat > "$clean_root/src/NewSource.java" <<'EOF'
final class NewSource {
}
EOF
touch "$clean_root/debugging-guide.md"
if ! clean_output="$(cd "$clean_root" && scripts/verify.sh clean 2>&1)"; then
  printf 'Clean fixture with normal untracked source should pass.\n%s\n' "$clean_output" >&2
  exit 1
fi
if ! rg -Fq 'Clean-state checks passed.' <<<"$clean_output"; then
  printf 'Clean success output is missing.\n' >&2
  exit 1
fi

# 参数为预期 WHAT 片段；断言 clean 失败且输出可操作诊断。
expect_clean_failure() {
  local expected="$1"
  local output
  if output="$(cd "$clean_root" && scripts/verify.sh clean 2>&1)"; then
    printf 'Clean fixture should fail: %s\n' "$expected" >&2
    exit 1
  fi
  for label in 'WHAT:' 'WHY:' 'FIX:' "$expected"; do
    if ! rg -Fq "$label" <<<"$output"; then
      printf 'Clean failure output is missing: %s\n%s\n' "$label" "$output" >&2
      exit 1
    fi
  done
}

printf ')\n' > "$clean_root/scripts/lib/broken-helper.sh"
expect_clean_failure 'Shell 脚本语法检查失败'
rm "$clean_root/scripts/lib/broken-helper.sh"

cat > "$clean_root/docs/exec-plans/active/two-active.md" <<'EOF'
# Test Plan
| Task | Status |
|---|---|
| one | `active` |
| two | `active` |
## 恢复状态
EOF
expect_clean_failure '同时存在 2 个 active task'

cat > "$clean_root/docs/exec-plans/active/two-active.md" <<'EOF'
# Test Plan
| Task | Status |
|---|---|
| one | `active` |
EOF
expect_clean_failure '活跃计划缺少恢复状态'

cat >> "$clean_root/docs/exec-plans/active/two-active.md" <<'EOF'
## 恢复状态
EOF
touch "$clean_root/.tmp-debug.log"
expect_clean_failure '存在未分类临时或调试工件'
if [[ ! -f "$clean_root/.tmp-debug.log" ]]; then
  printf 'Clean mode must not delete temporary files automatically.\n' >&2
  exit 1
fi
rm -rf "$clean_root"

# 即使验证模式失败，也必须留下 machine-readable evidence，供报告暴露失败状态。
failure_output="$ROOT_DIR/.harness/verifications/unsupported.json"
if "$ROOT_DIR/scripts/verify.sh" unsupported >/dev/null 2>&1; then
  printf 'Unsupported verification mode should fail.\n' >&2
  exit 1
fi
if ! rg -q '"status":"failed"' "$failure_output"; then
  printf 'Failed verification was not recorded.\n' >&2
  exit 1
fi
for evidence_field in '"command":"./scripts/verify.sh unsupported"' \
  '"headRevision":' '"worktreeFingerprint":' '"worktreeStable":true'; do
  if ! rg -Fq "$evidence_field" "$failure_output"; then
    printf 'Verification evidence is missing revision-bound field: %s\n' \
      "$evidence_field" >&2
    exit 1
  fi
done
rm -f "$failure_output"

# 用 fake Maven 验证 e2e 模式保留可查询日志、测试数和固定 workload 标识。
e2e_root="$(mktemp -d)"
mkdir -p "$e2e_root/scripts/lib" "$e2e_root/bin" \
  "$e2e_root/im-test/im-e2e-test/target/surefire-reports"
cp "$ROOT_DIR/scripts/verify.sh" "$e2e_root/scripts/verify.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$e2e_root/scripts/lib/harness-evidence.sh"
cat > "$e2e_root/bin/mvn" <<'EOF'
#!/usr/bin/env bash
if [[ " $* " == *" -Dgroups=e2e "* ]]; then
  cat > "$E2E_FIXTURE_ROOT/im-test/im-e2e-test/target/surefire-reports/TEST-e2e.xml" <<'XML'
<testsuite tests="1" failures="0" errors="0" skipped="0"/>
XML
  printf 'realtime-messaging-golden-path passed\n'
fi
EOF
chmod +x "$e2e_root/bin/mvn"
if ! E2E_FIXTURE_ROOT="$e2e_root" PATH="$e2e_root/bin:$PATH" \
    "$e2e_root/scripts/verify.sh" e2e >/dev/null 2>&1; then
  printf 'E2E verification fixture should pass with one generated test report.\n' >&2
  exit 1
fi
e2e_evidence="$e2e_root/.harness/verifications/e2e.json"
for expected in '"status":"passed"' '"logPath":".harness/runtime/e2e.log"' \
  '"testCount":1' '"workload":"realtime-messaging-golden-path"'; do
  if ! rg -Fq "$expected" "$e2e_evidence"; then
    printf 'E2E evidence is missing %s\n' "$expected" >&2
    exit 1
  fi
done
if ! rg -Fq 'realtime-messaging-golden-path passed' \
    "$e2e_root/.harness/runtime/e2e.log"; then
  printf 'E2E runtime log did not preserve Maven output.\n' >&2
  exit 1
fi
rm -rf "$e2e_root"

# 用隔离且确定性的 verification/Surefire/台账 fixture 验证报告聚合。
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT
mkdir -p "$fixture_root/scripts/lib" \
  "$fixture_root/docs/exec-plans/active" \
  "$fixture_root/docs/exec-plans/completed" \
  "$fixture_root/docs/feedback" \
  "$fixture_root/docs/references" \
  "$fixture_root/im-test/im-architecture-test" \
  "$fixture_root/.harness/verifications" \
  "$fixture_root/.harness/runtime" \
  "$fixture_root/module/target/surefire-reports"
cp "$ROOT_DIR/scripts/harness-report.sh" "$fixture_root/scripts/harness-report.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$fixture_root/scripts/lib/harness-evidence.sh"
touch "$fixture_root/AGENTS.md" \
  "$fixture_root/ARCHITECTURE.md" \
  "$fixture_root/README.md" \
  "$fixture_root/PROGRESS.md" \
  "$fixture_root/.java-version" \
  "$fixture_root/.nvmrc" \
  "$fixture_root/pom.xml" \
  "$fixture_root/scripts/verify.sh" \
  "$fixture_root/im-test/im-architecture-test/pom.xml" \
  "$fixture_root/docs/PLANS.md" \
  "$fixture_root/docs/exec-plans/active/README.md" \
  "$fixture_root/docs/exec-plans/completed/README.md" \
  "$fixture_root/docs/references/HARNESS_GUIDE.md"
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
cat > "$fixture_root/.harness/verifications/full.json" <<EOF
{"mode":"full","command":"./scripts/verify.sh full","status":"passed","durationSeconds":12,"startedAtEpoch":$full_started_at,"finishedAt":"2026-01-01T00:02:00Z","headRevision":"fixture-head","worktreeFingerprint":"fixture-fingerprint","worktreeStable":true}
EOF
cat > "$fixture_root/.harness/verifications/behavior.json" <<'EOF'
{"mode":"behavior","command":"./scripts/verify.sh behavior","status":"failed","durationSeconds":3,"startedAtEpoch":100,"finishedAt":"1970-01-01T00:02:00Z","headRevision":"fixture-head","worktreeFingerprint":"fixture-fingerprint","worktreeStable":true}
EOF
for mode in readiness clean quick; do
  cat > "$fixture_root/.harness/verifications/$mode.json" <<EOF
{"mode":"$mode","command":"./scripts/verify.sh $mode","status":"passed","durationSeconds":1,"startedAtEpoch":100,"finishedAt":"1970-01-01T00:02:00Z","headRevision":"fixture-head","worktreeFingerprint":"fixture-fingerprint","worktreeStable":true}
EOF
done
cat > "$fixture_root/.harness/verifications/e2e.json" <<'EOF'
{"mode":"e2e","command":"./scripts/verify.sh e2e","status":"passed","durationSeconds":8,"startedAtEpoch":100,"finishedAt":"1970-01-01T00:02:00Z","headRevision":"fixture-head","worktreeFingerprint":"fixture-fingerprint","worktreeStable":true,"logPath":".harness/runtime/e2e.log","testCount":1,"workload":"realtime-messaging-golden-path"}
EOF
printf 'e2e fixture log\n' > "$fixture_root/.harness/runtime/e2e.log"
cat > "$fixture_root/module/target/surefire-reports/TEST-current.xml" <<'EOF'
<testsuite tests="4" failures="0" errors="0" skipped="1"/>
EOF
touch "$fixture_root/module/target/surefire-reports/TEST-current.xml"
cat > "$fixture_root/module/target/surefire-reports/TEST-stale.xml" <<'EOF'
<testsuite tests="99" failures="1" errors="0" skipped="0"/>
EOF
touch -t 202001010001 "$fixture_root/module/target/surefire-reports/TEST-stale.xml"

HARNESS_ROOT_DIR="$fixture_root" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="fixture-fingerprint" \
  "$fixture_root/scripts/harness-report.sh" \
  "$fixture_root/.harness/report.json" >/dev/null
report="$fixture_root/.harness/report.json"
rg -q '"status": "failed"' "$report"
rg -q '"failedVerifications": \["behavior"\]' "$report"
rg -q '"tests": \{"status": "current", "total": 4, "failures": 0, "errors": 0, "skipped": 1\}' "$report"
rg -q '"openTechnicalDebt": 1' "$report"
rg -q '"falsePositives": 1' "$report"
rg -q '"flakyTests": 0' "$report"
if ! ruby -rjson -e '
  report = JSON.parse(File.read(ARGV.fetch(0)))
  exit(report.key?("sensorCoverage") && report["sensorCoverage"] == report["score"] ? 0 : 1)
' "$report"; then
  printf 'Harness report must expose sensorCoverage without changing the compatibility score.\n' >&2
  exit 1
fi
rg -Fq '"subsystems": {' "$report"
rg -Fq '"instruction": {"status": "verified"' "$report"
rg -Fq '"tools": {"status": "verified"' "$report"
rg -Fq '"environment": {"status": "verified"' "$report"
rg -Fq '"state": {"status": "verified"' "$report"
rg -Fq '"feedback": {"status": "verified"' "$report"
rg -Fq '"externalHarnessAdaptation": {' "$report"
rg -Fq '"status": "equivalent", "capability": "task runner"' "$report"
rg -Fq '"status": "implemented", "capability": "root PROGRESS.md"' "$report"
rg -Fq '"status": "deferred", "capability": "automatic agent loop"' "$report"
if ! ruby -rjson -e '
  e2e = JSON.parse(File.read(ARGV.fetch(0))).dig("runtimeEvidence", "e2e")
  expected = {"status" => "verified", "freshness" => "fresh",
    "logPath" => ".harness/runtime/e2e.log", "logAvailable" => true, "testCount" => 1,
    "workload" => "realtime-messaging-golden-path"}
  exit(e2e == expected ? 0 : 1)
' "$report"; then
  printf 'Harness report must expose fresh E2E runtime evidence.\n' >&2
  exit 1
fi

# verification JSON 新鲜但原始运行日志丢失时，report 必须降级运行证据。
rm "$fixture_root/.harness/runtime/e2e.log"
HARNESS_ROOT_DIR="$fixture_root" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="fixture-fingerprint" \
  "$fixture_root/scripts/harness-report.sh" \
  "$fixture_root/.harness/missing-log-report.json" >/dev/null
if ! ruby -rjson -e '
  e2e = JSON.parse(File.read(ARGV.fetch(0))).dig("runtimeEvidence", "e2e")
  exit(e2e["status"] == "degraded" && e2e["logAvailable"] == false ? 0 : 1)
' "$fixture_root/.harness/missing-log-report.json"; then
  printf 'Missing E2E log must degrade otherwise-fresh runtime evidence.\n' >&2
  exit 1
fi
printf 'e2e fixture log\n' > "$fixture_root/.harness/runtime/e2e.log"

# 当前 worktree 与成功 full 证据不匹配时，报告必须拒绝历史结果冒充 current。
cat > "$fixture_root/.harness/verifications/behavior.json" <<'EOF'
{"mode":"behavior","command":"./scripts/verify.sh behavior","status":"passed","durationSeconds":3,"startedAtEpoch":100,"finishedAt":"1970-01-01T00:02:00Z","headRevision":"fixture-head","worktreeFingerprint":"fixture-fingerprint","worktreeStable":true}
EOF
HARNESS_ROOT_DIR="$fixture_root" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="changed-fingerprint" \
  "$fixture_root/scripts/harness-report.sh" \
  "$fixture_root/.harness/stale-report.json" >/dev/null
if ! ruby -rjson -e '
  exit(JSON.parse(File.read(ARGV.fetch(0)))["status"] == "stale" ? 0 : 1)
' "$fixture_root/.harness/stale-report.json"; then
  printf 'Harness report must become stale when the worktree fingerprint changes.\n' >&2
  exit 1
fi
if ! rg -Fq '"tests": {"status": "stale"' "$fixture_root/.harness/stale-report.json"; then
  printf 'Surefire totals must not remain current after the worktree changes.\n' >&2
  exit 1
fi
if ! rg -Fq '"environment": {"status": "stale"' "$fixture_root/.harness/stale-report.json"; then
  printf 'Subsystem evidence must become stale after the worktree changes.\n' >&2
  exit 1
fi

# 升级前没有 fingerprint 的旧证据同样不能支撑当前 worktree 为 passed。
cat > "$fixture_root/.harness/verifications/full.json" <<EOF
{"mode":"full","status":"passed","durationSeconds":12,"startedAtEpoch":$full_started_at,"finishedAt":"2026-01-01T00:02:00Z"}
EOF
HARNESS_ROOT_DIR="$fixture_root" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="fixture-fingerprint" \
  "$fixture_root/scripts/harness-report.sh" \
  "$fixture_root/.harness/unavailable-report.json" >/dev/null
if ! ruby -rjson -e '
  exit(JSON.parse(File.read(ARGV.fetch(0)))["status"] == "stale" ? 0 : 1)
' "$fixture_root/.harness/unavailable-report.json"; then
  printf 'Harness report must be stale when full evidence has no fingerprint.\n' >&2
  exit 1
fi

# 从未运行 full 的工作树没有完成证据，不能仅因 drift 通过就报告 passed。
rm "$fixture_root/.harness/verifications/full.json"
HARNESS_ROOT_DIR="$fixture_root" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="fixture-fingerprint" \
  "$fixture_root/scripts/harness-report.sh" \
  "$fixture_root/.harness/incomplete-report.json" >/dev/null
if ! ruby -rjson -e '
  report = JSON.parse(File.read(ARGV.fetch(0)))
  exit(report["status"] == "incomplete" && report.dig("tests", "status") == "unknown" ? 0 : 1)
' "$fixture_root/.harness/incomplete-report.json"; then
  printf 'Harness report without full evidence must be incomplete with unknown tests.\n' >&2
  exit 1
fi

printf 'Harness script tests passed.\n'
