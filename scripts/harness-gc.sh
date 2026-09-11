#!/usr/bin/env bash
# 用途：执行 Harness 治理巡检，发现陈旧计划、开放技术债务和 Harness 反馈。
# 输入：无位置参数；基于当前仓库文档状态。
# 输出/副作用：生成最新 Harness report；开放债务只提示，不自动改写计划或台账。
# 依赖：find、awk、check-drift.sh、harness-report.sh。
# 退出码：drift 或超过 30 天未更新的 active plan 存在时返回非 0；开放台账本身不导致失败。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

"$ROOT_DIR/scripts/check-drift.sh"

# 治理巡检只暴露陈旧状态，不自动移动计划或关闭台账条目。
stale_plans="$(find docs/exec-plans/active -type f -name '*.md' ! -name README.md -mtime +30 -print)"
if [[ -n "$stale_plans" ]]; then
  printf 'Active plans unchanged for more than 30 days:\n%s\n' "$stale_plans" >&2
  exit 1
fi

open_debt="$(awk -F'|' '/^\| TD-[0-9]+ / && $7 !~ /(DONE|CLOSED)/ {print $0}' docs/exec-plans/tech-debt-tracker.md)"
if [[ -n "$open_debt" ]]; then
  printf 'Tracked technical debt requires review:\n%s\n' "$open_debt"
fi

open_feedback="$(awk -F'|' '/^\| HF-[0-9]+ / && $7 !~ /CLOSED/ {print $0}' docs/feedback/HARNESS_FEEDBACK.md)"
if [[ -n "$open_feedback" ]]; then
  printf 'Harness feedback requires review:\n%s\n' "$open_feedback"
fi

"$ROOT_DIR/scripts/harness-report.sh"
