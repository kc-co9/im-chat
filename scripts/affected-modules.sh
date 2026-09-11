#!/usr/bin/env bash
# 用途：根据 Git 变更路径计算需要验证的 Maven 模块，并保守追加 Architecture Test。
# 输入：可选 baseline revision；测试可通过 IM_CHANGED_FILES 注入确定性文件列表。
# 输出/副作用：stdout 输出 all、docs、none 或逗号分隔模块列表；不修改工作树。
# 依赖：git、rg、cut 以及当前 Maven 目录结构。
# 退出码：成功返回 0；无法识别有效 baseline 时输出 all 并返回 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_REF="${1:-}"
cd "$ROOT_DIR"

if [[ -n "${IM_CHANGED_FILES:-}" ]]; then
  changed_files="$IM_CHANGED_FILES"
elif [[ -n "$BASE_REF" ]] && git rev-parse --verify --quiet "$BASE_REF^{commit}" >/dev/null; then
  changed_files="$(git diff --name-only "$BASE_REF"...HEAD)"
elif [[ -n "$BASE_REF" ]]; then
  printf 'all\n'
  exit 0
else
  changed_files="$(git diff --name-only HEAD; git ls-files --others --exclude-standard)"
fi

if [[ -z "$changed_files" ]]; then
  printf 'none\n'
  exit 0
fi

# 根 POM、公共模块、验证脚本和 CI 属于共享输入，变化时保守升级为全量范围。
if printf '%s\n' "$changed_files" | rg -q '^(pom\.xml|im-common/|scripts/|\.github/)'; then
  printf 'all\n'
  exit 0
fi

modules=()
# 将模块加入结果集合；参数为模块相对路径，重复模块只保留一次。
add_module() {
  local module="$1"
  [[ " ${modules[*]-} " == *" $module "* ]] || modules+=("$module")
}

while IFS= read -r file; do
  case "$file" in
    im-plugin/im-mq/*) add_module "im-plugin/im-mq-kafka" ;;
    im-plugin/im-bolt/*)
      add_module "im-plugin/im-bolt"
      add_module "im-test/im-e2e-test"
      ;;
    im-plugin/*/*) add_module "$(cut -d/ -f1-2 <<< "$file")" ;;
    im-broker/im-broker-sdk/*)
      add_module "im-broker/im-broker-sdk"
      add_module "im-broker/im-broker-server"
      add_module "im-test/im-e2e-test"
      ;;
    im-broker/im-broker-server/*)
      add_module "im-broker/im-broker-server"
      add_module "im-test/im-e2e-test"
      ;;
    im-gateway/im-http-gateway/*) add_module "im-gateway/im-http-gateway" ;;
    im-gateway/im-ws-gateway/im-ws-gateway-sdk/*)
      add_module "im-gateway/im-ws-gateway/im-ws-gateway-sdk"
      add_module "im-gateway/im-ws-gateway/im-ws-gateway-server"
      add_module "im-broker/im-broker-server"
      add_module "im-test/im-e2e-test"
      ;;
    im-gateway/im-ws-gateway/im-ws-gateway-server/*)
      add_module "im-gateway/im-ws-gateway/im-ws-gateway-server"
      add_module "im-test/im-e2e-test"
      ;;
    im-management/im-admin/*) add_module "im-management/im-admin" ;;
    im-management/im-monitor/*) add_module "im-management/im-monitor" ;;
    im-management/im-iam/im-iam-sdk/*)
      add_module "im-management/im-iam/im-iam-sdk"
      add_module "im-management/im-iam/im-iam-server"
      add_module "im-management/im-admin"
      add_module "im-management/im-monitor"
      add_module "im-management/im-audit/im-audit-server"
      ;;
    im-management/im-iam/im-iam-server/*) add_module "im-management/im-iam/im-iam-server" ;;
    im-management/im-audit/im-audit-sdk/*)
      add_module "im-management/im-audit/im-audit-sdk"
      add_module "im-management/im-audit/im-audit-server"
      add_module "im-management/im-admin"
      add_module "im-management/im-iam/im-iam-server"
      ;;
    im-management/im-audit/im-audit-server/*)
      add_module "im-management/im-audit/im-audit-server"
      ;;
    im-management/*)
      add_module "im-management/im-iam/im-iam-server"
      add_module "im-management/im-admin"
      add_module "im-management/im-monitor"
      add_module "im-management/im-audit/im-audit-server"
      ;;
    im-service/im-account/im-account-admin-facade/*)
      add_module "im-service/im-account/im-account-admin-facade"
      add_module "im-service/im-account/im-account-server"
      add_module "im-management/im-admin"
      ;;
    im-service/*/*-facade/*)
      service="$(cut -d/ -f1-2 <<< "$file")"
      add_module "$service/$(basename "$service")-facade"
      add_module "$service/$(basename "$service")-server"
      ;;
    im-service/*/*-server/*) add_module "$(cut -d/ -f1-3 <<< "$file")" ;;
    im-test/im-architecture-test/*) add_module "im-test/im-architecture-test" ;;
    im-test/im-e2e-test/*) add_module "im-test/im-e2e-test" ;;
  esac
done <<< "$changed_files"

if (( ${#modules[@]} == 0 )); then
  printf 'docs\n'
else
  (IFS=,; printf '%s\n' "${modules[*]},im-test/im-architecture-test")
fi
