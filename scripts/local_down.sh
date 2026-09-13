#!/usr/bin/env bash
# 用途：停止本地 IM Chat Compose 拓扑，默认保留持久化命名卷。
# 输入：可选 `--volumes`；只有显式传入时删除 Compose 命名卷。
# 输出/副作用：停止并移除容器和网络；`--volumes` 额外删除 MySQL、Redis、Nacos、Kafka、Prometheus、Grafana、BanyanDB、Horizon 数据。
# 依赖：Docker Compose。
# 退出码：参数非法返回 2，Compose 停止失败返回非 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/local/compose.yml"
DOCKER_BIN="${DOCKER_BIN:-docker}"

if [[ $# -gt 1 || (${1:-} != "" && ${1:-} != "--volumes") ]]; then
  printf 'Usage: %s [--volumes]\n' "$0" >&2
  exit 2
fi

args=(compose -f "$COMPOSE_FILE" --profile infra --profile full down --remove-orphans)
if [[ ${1:-} == "--volumes" ]]; then
  args+=(--volumes)
fi
"$DOCKER_BIN" "${args[@]}"
