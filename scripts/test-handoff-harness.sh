#!/usr/bin/env bash
# 用途：验证 session handoff 只从当前仓库事实源派生，并准确暴露新鲜、陈旧和失败证据。
# 输入：无；测试在临时 Git 仓库中构造确定性 fixture。
# 输出/副作用：仅创建并删除临时目录，成功时输出通过信息，不修改真实工作树或 Git index。
# 依赖：待测 harness-handoff.sh、harness-evidence.sh、Ruby JSON、Git 和 rg。
# 退出码：任一契约不满足时返回非 0，全部 fixture 通过时返回 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

mkdir -p "$FIXTURE_ROOT/scripts/lib" \
  "$FIXTURE_ROOT/docs/exec-plans/active" \
  "$FIXTURE_ROOT/docs/product-specs" \
  "$FIXTURE_ROOT/.harness/verifications"
cp "$ROOT_DIR/scripts/harness-handoff.sh" "$FIXTURE_ROOT/scripts/harness-handoff.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$FIXTURE_ROOT/scripts/lib/harness-evidence.sh"
chmod +x "$FIXTURE_ROOT/scripts/harness-handoff.sh"

cat > "$FIXTURE_ROOT/PROGRESS.md" <<'EOF'
# 项目进度

## 当前状态（Current State）

- 活跃计划：[Current](docs/exec-plans/active/current.md)
- 当前任务：T2，自动生成交接报告。
- 状态：`active`

## 阻塞项

- `none`

## 下一步（Next Steps）

实现 handoff 生成器并通过 fixture。
EOF
cat > "$FIXTURE_ROOT/docs/exec-plans/active/current.md" <<'EOF'
# Current Plan

## 恢复状态

- 当前任务：T2，自动生成交接报告。
- 已完成：T1 设计已经通过。
- 阻塞项：`none`。
- 下一步：实现 handoff 生成器并通过 fixture。
- 不要修改：业务代码和真实 Git index。
EOF
cat > "$FIXTURE_ROOT/docs/product-specs/FEATURES.md" <<'EOF'
| ID | Owner | 可观察行为 | 验证入口 | 覆盖状态 | 证据边界 |
|---|---|---|---|---|---|
| RT-001 | Broker | route | `verify behavior` | `covered` | JVM behavior |
EOF
git -C "$FIXTURE_ROOT" init --quiet
git -C "$FIXTURE_ROOT" add PROGRESS.md docs scripts

cat > "$FIXTURE_ROOT/.harness/report.json" <<'EOF'
{
  "generatedAt": "2026-09-10T00:00:00Z",
  "status": "passed",
  "repository": {"headRevision": "fixture-head", "worktreeFingerprint": "fixture-fingerprint"},
  "tests": {"status": "current", "total": 12, "failures": 0, "errors": 0, "skipped": 0},
  "verifications": [
    {"mode": "full", "status": "passed", "freshness": "fresh", "command": "./scripts/verify.sh full"},
    {"mode": "e2e", "status": "passed", "freshness": "fresh", "command": "./scripts/verify.sh e2e"}
  ]
}
EOF

HARNESS_ROOT_DIR="$FIXTURE_ROOT" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="fixture-fingerprint" \
  "$FIXTURE_ROOT/scripts/harness-handoff.sh" >/dev/null

handoff_json="$FIXTURE_ROOT/.harness/session-handoff.json"
handoff_md="$FIXTURE_ROOT/.harness/session-handoff.md"
if ! ruby -rjson -e '
  report = JSON.parse(File.read(ARGV.fetch(0)))
  valid = report["evidenceFreshness"] == "fresh" &&
    report.dig("currentlyVerified", "status") == "passed" &&
    report.dig("currentlyVerified", "testTotal") == 12 &&
    report.dig("workState", "activePlan") == "docs/exec-plans/active/current.md" &&
    report.dig("nextAction", "priority").include?("实现 handoff") &&
    report.dig("nextAction", "doNotTouch").include?("业务代码")
  exit(valid ? 0 : 1)
' "$handoff_json"; then
  printf 'Fresh handoff did not preserve current evidence and recovery state.\n' >&2
  exit 1
fi
for heading in '## 当前已验证' '## 本轮改动' '## 仍损坏或未验证' \
  '## 下一步最佳动作' '## 命令'; do
  if ! rg -Fq "$heading" "$handoff_md"; then
    printf 'Handoff Markdown is missing heading: %s\n' "$heading" >&2
    exit 1
  fi
done

# 当前 worktree 与报告不一致时，handoff 必须标记 stale，不能复述为已验证。
HARNESS_ROOT_DIR="$FIXTURE_ROOT" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="changed-fingerprint" \
  "$FIXTURE_ROOT/scripts/harness-handoff.sh" >/dev/null
if ! ruby -rjson -e '
  report = JSON.parse(File.read(ARGV.fetch(0)))
  exit(report["evidenceFreshness"] == "stale" &&
    report.dig("currentlyVerified", "status") == "stale" ? 0 : 1)
' "$handoff_json"; then
  printf 'Changed worktree must make handoff evidence stale.\n' >&2
  exit 1
fi

# 当前报告失败时，handoff 必须列出失败模式供下一会话直接定位。
ruby -rjson -e '
  path = ARGV.fetch(0)
  report = JSON.parse(File.read(path))
  report["status"] = "failed"
  report["failedVerifications"] = ["full"]
  File.write(path, JSON.pretty_generate(report) + "\n")
' "$FIXTURE_ROOT/.harness/report.json"
HARNESS_ROOT_DIR="$FIXTURE_ROOT" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="fixture-fingerprint" \
  "$FIXTURE_ROOT/scripts/harness-handoff.sh" >/dev/null
if ! ruby -rjson -e '
  report = JSON.parse(File.read(ARGV.fetch(0)))
  exit(report.dig("currentlyVerified", "status") == "failed" &&
    report.dig("unverified", "failedVerifications") == ["full"] ? 0 : 1)
' "$handoff_json"; then
  printf 'Failed handoff did not expose failed verification modes.\n' >&2
  exit 1
fi

# 没有 active plan 时必须输出 null，并从 PROGRESS 提供下一步，而不是发明任务。
rm "$FIXTURE_ROOT/docs/exec-plans/active/current.md"
cat > "$FIXTURE_ROOT/PROGRESS.md" <<'EOF'
# 项目进度

## 当前状态（Current State）

- 活跃计划：`none`
- 当前任务：`none`
- 状态：`idle`

## 阻塞项

- `none`

## 下一步（Next Steps）

等待下一个已批准任务。
EOF
HARNESS_ROOT_DIR="$FIXTURE_ROOT" \
  HARNESS_CURRENT_HEAD="fixture-head" \
  HARNESS_CURRENT_WORKTREE_FINGERPRINT="fixture-fingerprint" \
  "$FIXTURE_ROOT/scripts/harness-handoff.sh" >/dev/null
if ! ruby -rjson -e '
  report = JSON.parse(File.read(ARGV.fetch(0)))
  exit(report.dig("workState", "activePlan").nil? &&
    report.dig("nextAction", "priority").include?("等待下一个") ? 0 : 1)
' "$handoff_json"; then
  printf 'No-plan handoff did not preserve the explicit idle state.\n' >&2
  exit 1
fi

printf 'Handoff Harness tests passed.\n'
