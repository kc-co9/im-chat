#!/usr/bin/env bash
# 用途：校验四个管理 UI 的依赖、质量脚本、设计 token、控制台导航和本地端口约定。
# 输入：无位置参数；测试可通过 MANAGEMENT_UI_ROOT_DIR 指向隔离 fixture。
# 输出/副作用：失败时输出具体文件和缺失约定；只读检查 UI 源码。
# 依赖：node、rg，以及各 UI 的 package.json、Vite 和样式配置。
# 退出码：全部 UI 满足约定返回 0；首个确定性违规返回 1。

set -euo pipefail

ROOT_DIR="${MANAGEMENT_UI_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
UI_ROOTS=(
  "im-management/im-admin/ui"
  "im-management/im-audit/im-audit-server/ui"
  "im-management/im-iam/im-iam-server/ui"
  "im-management/im-monitor/ui"
)
EXPECTED_PORTS=(18093 18091 18090 18092)
TOKENS=(
  "--im-console-bg: #f3f6fa;"
  "--im-console-surface: #ffffff;"
  "--im-console-sidebar: #172334;"
  "--im-console-sidebar-active: #2a425d;"
  "--im-console-primary: #247662;"
  "--im-console-link: #286493;"
  "--im-console-warning: #a36019;"
  "--im-console-danger: #b33d3d;"
  "--im-console-text: #1f2b3d;"
  "--im-console-muted: #718096;"
  "--im-console-border: #dfe5ec;"
)

# 逐个 UI 校验其自有依赖、质量脚本、共享视觉契约、导航配置和本地代理端口。
for index in "${!UI_ROOTS[@]}"; do
  relative_ui_root="${UI_ROOTS[$index]}"
  expected_port="${EXPECTED_PORTS[$index]}"
  ui_root="$ROOT_DIR/$relative_ui_root"
  package_file="$ui_root/package.json"
  token_file="$ui_root/src/styles/tokens.css"
  console_file="$ui_root/src/styles/console.css"
  console_links_file="$ui_root/src/config/consoleLinks.ts"
  vite_file="$ui_root/vite.config.ts"

  node - "$package_file" <<'NODE'
const fs = require('node:fs')
const file = process.argv[2]
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'))
const requiredDependencies = ['vue', 'element-plus', '@element-plus/icons-vue']
const requiredScripts = ['build', 'lint', 'format:check', 'typecheck', 'test:unit']
for (const dependency of requiredDependencies) {
  if (!manifest.dependencies?.[dependency]) {
    throw new Error(`${file}: missing dependency ${dependency}`)
  }
}
for (const script of requiredScripts) {
  if (!manifest.scripts?.[script]) {
    throw new Error(`${file}: missing script ${script}`)
  }
}
NODE

  if [[ ! -f "$token_file" || ! -f "$console_file" || ! -f "$console_links_file" ]]; then
    printf '%s must own src/styles/tokens.css and src/styles/console.css\n' "$relative_ui_root" >&2
    exit 1
  fi
  for console_location in \
      "VITE_IAM_CONSOLE_URL|http://localhost:18090" \
      "VITE_AUDIT_CONSOLE_URL|http://localhost:18091" \
      "VITE_MONITOR_CONSOLE_URL|http://localhost:18092" \
      "VITE_ADMIN_CONSOLE_URL|http://localhost:18093"; do
    environment_name="${console_location%%|*}"
    local_default="${console_location#*|}"
    if ! rg -q --fixed-strings "$environment_name" "$console_links_file" \
        || ! rg -q --fixed-strings "$local_default" "$console_links_file"; then
      printf '%s is missing %s with local default %s\n' \
        "$console_links_file" "$environment_name" "$local_default" >&2
      exit 1
    fi
  done
  if ! rg -q --fixed-strings "http://127.0.0.1:$expected_port" "$vite_file"; then
    printf '%s does not proxy to approved local port %s\n' "$vite_file" "$expected_port" >&2
    exit 1
  fi
  for token in "${TOKENS[@]}"; do
    if ! rg -q --fixed-strings -- "$token" "$token_file"; then
      printf '%s is missing approved token: %s\n' "$token_file" "$token" >&2
      exit 1
    fi
  done

  if rg -n 'window\.(confirm|alert)[[:space:]]*\(' "$ui_root/src"; then
    printf '%s uses a browser-native confirmation API\n' "$relative_ui_root" >&2
    exit 1
  fi
  if rg -n "(from|@import).*im-(admin|audit|iam|monitor)" \
      "$ui_root/src"; then
    printf '%s imports UI source owned by another management application\n' "$relative_ui_root" >&2
    exit 1
  fi
done

if rg -n 'ConsoleNavigationController|/api/console/navigation' \
    "$ROOT_DIR/im-management" --glob '*.java' --glob '*.{ts,vue}'; then
  printf 'Management console navigation must remain frontend-owned\n' >&2
  exit 1
fi

printf 'Management UI Harness passed.\n'
