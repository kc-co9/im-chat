#!/usr/bin/env bash
# 用途：验证 im-monitor 到 im-ops 的一次性运行时重命名已经完整完成。
# 输入：无位置参数；检查当前工作树中的模块、源码、配置、seed、部署、脚本和当前事实文档。
# 输出/副作用：按残留类别输出精确路径和行号；只读，不修改文件或 Git index。
# 依赖：bash、rg。
# 退出码：不存在旧运行时名称返回 0；任一类别仍有残留返回 1。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

report_matches() {
  local title="$1"
  local matches="$2"
  if [[ -n "$matches" ]]; then
    printf '\n[im-ops-rename] WHAT: %s\n' "$title"
    printf 'WHY: 未上线系统采用一次性重命名，旧运行时名称会形成双入口或配置漂移。\n'
    printf 'FIX: 将下列当前运行事实改为 im-ops；不要增加兼容别名。\n%s\n' "$matches"
    failures=$((failures + 1))
  fi
}

legacy_module_path=""
if [[ -e im-management/im-monitor ]]; then
  legacy_module_path="im-management/im-monitor"
fi
report_matches "Legacy im-monitor module path still exists" "$legacy_module_path"

pom_matches="$(rg -n --glob 'pom.xml' 'im-monitor|ImMonitor|im\.monitor|monitor:' . || true)"
report_matches "Maven module or artifact still uses the legacy name" "$pom_matches"

management_source_matches="$(rg -n \
  --glob 'im-management/**/src/**' \
  --glob 'im-management/**/ui/**' \
  --glob '!**/target/**' \
  --glob '!**/node_modules/**' \
  'im-monitor|ImMonitor|im\.monitor|management\.monitor|VITE_MONITOR|Monitor|monitor' \
  . || true)"
report_matches "Management source, test, UI, or configuration still uses the legacy name" \
  "$management_source_matches"

runtime_matches="$(rg -n \
  --glob 'deploy/**' \
  --glob 'scripts/**' \
  --glob '!scripts/test-im-ops-rename.sh' \
  --glob '!**/target/**' \
  'im-monitor|ImMonitor|im\.monitor|IM_MONITOR|VITE_MONITOR|\bMonitor\b|\bmonitor\b|monitor:' \
  . || true)"
report_matches "Deployment or script runtime references still use the legacy name" "$runtime_matches"

seed_matches="$(rg -n \
  --glob 'im-management/im-iam/**/sql/**' \
  --glob 'im-management/im-audit/**/src/main/resources/**' \
  --glob 'deploy/local/mysql/init/**' \
  'im-monitor|ImMonitor|im\.monitor|IM_MONITOR|VITE_MONITOR|\bMonitor\b|\bmonitor\b|monitor:' \
  . || true)"
report_matches "IAM or Audit seed/config still uses the legacy name" "$seed_matches"

current_fact_matches="$(rg -n \
  'im-monitor|ImMonitor|im\.monitor|IM_MONITOR|VITE_MONITOR|\bMonitor\b|\bmonitor\b|monitor:' \
  AGENTS.md ARCHITECTURE.md README.md \
  im-management/AGENTS.md im-management/README.md \
  im-management/im-iam/README.md \
  im-management/im-audit/im-audit-server/README.md \
  docs/RELIABILITY.md docs/SECURITY.md docs/references/HARNESS_GUIDE.md \
  2>/dev/null || true)"
report_matches "Current developer facts still describe the legacy runtime" "$current_fact_matches"

if (( failures > 0 )); then
  printf '\nim-ops rename verification failed with %d violation group(s).\n' "$failures"
  exit 1
fi

printf 'im-ops rename verification passed.\n'
