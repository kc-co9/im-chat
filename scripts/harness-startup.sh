#!/usr/bin/env bash
# 用途：枚举全部 Spring Boot application，并执行一一对应、隔离外部发现的 startup smoke。
# 输入：可选 --reports-since <epoch> 仅消费本轮报告；HARNESS_ROOT_DIR 可指向隔离 fixture。
# 输出/副作用：运行 Maven、刷新各应用的 Surefire 报告，并写入 .harness/startup-manifest.json。
# 依赖：Maven、Ruby JSON/REXML、rg 和 find。
# 退出码：映射/隔离契约不满足、Maven 失败、零测试或报告异常时返回非 0。

set -euo pipefail

ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
MANIFEST="$ROOT_DIR/.harness/startup-manifest.json"
mkdir -p "$(dirname "$MANIFEST")"

# 输出统一 WHAT/WHY/FIX，确保新增应用漏接 startup 门禁时可以直接修复。
startup_failure() {
  printf '\n[startup] WHAT: %s\nWHY: %s\nFIX: %s\n' "$1" "$2" "$3" >&2
}

# 映射生成与报告消费共用 reactor helper；reports-since 模式绝不执行 Maven。
HELPER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/harness-reactor-evidence.rb"
REPORTS_SINCE=""
if (( $# )); then
  if [[ $# != 2 || "$1" != "--reports-since" ]]; then
    printf 'Usage: harness-startup.sh [--reports-since EPOCH]\n' >&2
    exit 2
  fi
  REPORTS_SINCE="$2"
  if [[ -z "$REPORTS_SINCE" ]]; then
    printf 'reports-since requires a positive epoch.\n' >&2
    exit 2
  fi
fi
if ! ruby "$HELPER" startup-manifest "$ROOT_DIR" "$MANIFEST"; then
  startup_failure "Spring Boot application 与 startup-smoke test 映射不完整。" \
    "漏测、重复映射或未隔离 Nacos 会让标准启动路径出现盲区。" \
    '为 Application 增加唯一 startup-smoke context test，并显式关闭 Nacos discovery/config。'
  exit 1
fi
if [[ -n "$REPORTS_SINCE" ]]; then
  ruby "$HELPER" startup-check "$ROOT_DIR" "$REPORTS_SINCE"
  exit $?
fi

modules="$(ruby -rjson -e '
  data = JSON.parse(File.read(ARGV.fetch(0)))
  puts data.fetch("applications").map { |entry| entry.fetch("module") }.uniq.join(",")
' "$MANIFEST")"
application_count="$(ruby -rjson -e '
  puts JSON.parse(File.read(ARGV.fetch(0))).fetch("applications").length
' "$MANIFEST")"

# 删除每个映射测试的旧报告，确保后续成功只能来自本次 startup 执行。
ruby "$HELPER" startup-clear "$ROOT_DIR"

STARTED_EPOCH="$(ruby -e 'puts Time.now.to_f')"
printf '\n==> Building startup-smoke modules\n'
mvn -q -pl "$modules" -am -DskipTests install
printf '\n==> Running %d application startup smoke(s)\n' "$application_count"
mvn -q -pl "$modules" -Dgroups=startup-smoke test

# 不以 Maven 退出 0 代替逐类、逐方法实际执行证明。
if ! ruby "$HELPER" startup-check "$ROOT_DIR" "$STARTED_EPOCH"; then
  startup_failure "startup-smoke 没有为全部应用产生有效测试报告。" \
    "Maven 成功但测试未执行不能冒充启动通过。" \
    "检查 Tag、Surefire groups 和 target/surefire-reports 后重跑 startup。"
  exit 1
fi

printf 'Application startup smoke passed for %d application(s).\n' "$application_count"
