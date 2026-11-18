#!/usr/bin/env bash
# 用途：启动真实环境路由容量 driver，复用仓库 k6 场景并调用环境指定的部署/监控 hook。
# 输入：ROUTING_CAPACITY_ENV_CONFIG 与协调器的 ROUTING_CAPACITY_*；缺环境时失败，不发压。
# 输出/副作用：driver 写原始证据和结果 JSON；失败后的环境恢复由 driver 与协调器 cleanup 共同负责。
set -euo pipefail
ROOT_DIR="${ROUTING_CAPACITY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
export ROUTING_CAPACITY_ROOT_DIR="$ROOT_DIR"
exec node "$ROOT_DIR/im-test/im-load-test/tools/routing-capacity-driver.mjs"
