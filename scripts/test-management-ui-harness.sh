#!/usr/bin/env bash
# 用途：用隔离 UI fixture 验证管理端依赖、脚本、样式 token、端口和导航规则。
# 输入：无位置参数；fixture 在临时目录内生成。
# 输出/副作用：创建并清理临时 UI 目录，不修改真实管理端源码。
# 依赖：bash、node、rg、mktemp 和 check-management-ui.sh。
# 退出码：正例通过且每个反例产生预期诊断时返回 0，否则返回非 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKER="$ROOT_DIR/scripts/check-management-ui.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

# 参数为 UI 根目录和预期端口；生成一套满足当前约定的最小基线。
create_ui() {
  local ui_root="$1"
  local port="$2"
  mkdir -p "$ui_root/src/styles" "$ui_root/src/config"
  cat > "$ui_root/package.json" <<'EOF'
{
  "scripts": {
    "build": "vite build",
    "lint": "eslint .",
    "format:check": "prettier --check .",
    "typecheck": "vue-tsc --noEmit",
    "test:unit": "vitest run"
  },
  "dependencies": {
    "@element-plus/icons-vue": "1.0.0",
    "element-plus": "1.0.0",
    "vue": "1.0.0"
  }
}
EOF
  cat > "$ui_root/src/styles/tokens.css" <<'EOF'
:root {
  --im-console-bg: #f3f6fa;
  --im-console-surface: #ffffff;
  --im-console-sidebar: #172334;
  --im-console-sidebar-active: #2a425d;
  --im-console-primary: #247662;
  --im-console-link: #286493;
  --im-console-warning: #a36019;
  --im-console-danger: #b33d3d;
  --im-console-text: #1f2b3d;
  --im-console-muted: #718096;
  --im-console-border: #dfe5ec;
}
EOF
  printf '.console-shell { min-height: 100vh; }\n' > "$ui_root/src/styles/console.css"
  printf '<script setup lang="ts"></script>\n' > "$ui_root/src/App.vue"
  cat > "$ui_root/src/config/consoleLinks.ts" <<'EOF'
const locations = {
  iam: import.meta.env.VITE_IAM_CONSOLE_URL || 'http://localhost:18090',
  audit: import.meta.env.VITE_AUDIT_CONSOLE_URL || 'http://localhost:18091',
  monitor: import.meta.env.VITE_MONITOR_CONSOLE_URL || 'http://localhost:18092',
  admin: import.meta.env.VITE_ADMIN_CONSOLE_URL || 'http://localhost:18093',
}
EOF
  printf "export default { server: { proxy: 'http://127.0.0.1:%s' } }\n" "$port" \
    > "$ui_root/vite.config.ts"
}

# 重建四个管理端基线，确保每个反例都从干净状态开始。
create_all() {
  create_ui "$FIXTURE_ROOT/im-management/im-admin/ui" 18093
  create_ui "$FIXTURE_ROOT/im-management/im-audit/im-audit-server/ui" 18091
  create_ui "$FIXTURE_ROOT/im-management/im-iam/im-iam-server/ui" 18090
  create_ui "$FIXTURE_ROOT/im-management/im-monitor/ui" 18092
}

# 参数为预期诊断片段；执行 checker 并断言失败输出包含该片段。
expect_failure() {
  local message="$1"
  if MANAGEMENT_UI_ROOT_DIR="$FIXTURE_ROOT" bash "$CHECKER" >/dev/null 2>&1; then
    printf 'Expected management UI Harness failure: %s\n' "$message" >&2
    exit 1
  fi
}

create_all
MANAGEMENT_UI_ROOT_DIR="$FIXTURE_ROOT" bash "$CHECKER" >/dev/null

sed -i.bak '/"element-plus"/d' "$FIXTURE_ROOT/im-management/im-iam/im-iam-server/ui/package.json"
expect_failure 'missing Element Plus dependency'
rm -f "$FIXTURE_ROOT/im-management/im-iam/im-iam-server/ui/package.json.bak"

rm -rf "$FIXTURE_ROOT"
FIXTURE_ROOT="$(mktemp -d)"
create_all
sed -i.bak '/"lint"/d' "$FIXTURE_ROOT/im-management/im-monitor/ui/package.json"
expect_failure 'missing quality script'
rm -f "$FIXTURE_ROOT/im-management/im-monitor/ui/package.json.bak"

rm -rf "$FIXTURE_ROOT"
FIXTURE_ROOT="$(mktemp -d)"
create_all
sed -i.bak 's/#247662/#000000/' \
  "$FIXTURE_ROOT/im-management/im-admin/ui/src/styles/tokens.css"
expect_failure 'token drift'
rm -f "$FIXTURE_ROOT/im-management/im-admin/ui/src/styles/tokens.css.bak"

rm -rf "$FIXTURE_ROOT"
FIXTURE_ROOT="$(mktemp -d)"
create_all
printf '<script setup lang="ts">window.confirm("discard?")</script>\n' \
  > "$FIXTURE_ROOT/im-management/im-audit/im-audit-server/ui/src/App.vue"
expect_failure 'browser-native confirmation API'

rm -rf "$FIXTURE_ROOT"
FIXTURE_ROOT="$(mktemp -d)"
create_all
printf "@import '../../../../im-admin/ui/src/styles/console.css';\n" \
  > "$FIXTURE_ROOT/im-management/im-iam/im-iam-server/ui/src/styles/console.css"
expect_failure 'cross-application UI source import'

rm -rf "$FIXTURE_ROOT"
FIXTURE_ROOT="$(mktemp -d)"
create_all
sed -i.bak 's/18092/18090/' "$FIXTURE_ROOT/im-management/im-monitor/ui/vite.config.ts"
expect_failure 'stale Vite proxy port'

rm -rf "$FIXTURE_ROOT"
FIXTURE_ROOT="$(mktemp -d)"
create_all
sed -i.bak '/VITE_ADMIN_CONSOLE_URL/d' \
  "$FIXTURE_ROOT/im-management/im-admin/ui/src/config/consoleLinks.ts"
expect_failure 'missing frontend console environment override'

printf 'Management UI Harness fixture tests passed.\n'
