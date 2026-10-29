#!/usr/bin/env bash
# 用途：专用合成数据 bolt-dev 拓扑先 create，再核对实际隔离，最后才 start；禁止与 broadinfra Compose 合并。
# 输入：可选 --check-only（只创建/检查后删除）；BOLT_DEV_PROJECT 可指定独占项目名；镜像由 Compose 的 BOLT_DEV_*_IMAGE 指定。
# 输出/副作用：按实际 Docker endpoint+project 原子 mkdir 独占，持锁至检查、创建、启动和清理结束；失败只删除本次创建的项目。
# 锁位于固定 /tmp/im-chat-bolt-dev-$UID，不随调用者 TMPDIR 改变；陈旧锁不自动抢占，须人工核对 owner PID 与 Docker 状态后处理。不承诺防恶意本地用户/privileged Docker 操作者。
# 成功启动后容器归调用方，使用输出的精确 down 命令清理；--check-only 和所有失败由本脚本清理。
# 依赖：Docker Compose、Ruby JSON。单个 Docker 操作最多 120 秒；超时杀掉该命令进程组并非 0。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project="${BOLT_DEV_PROJECT:-im-chat-bolt-dev}"
check_only=false
if [[ $# == 1 && "$1" == --check-only ]]; then check_only=true
elif [[ $# != 0 ]]; then printf 'Usage: %s [--check-only]\n' "$0" >&2; exit 2; fi
[[ "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || exit 2
DOCKER_BIN="${DOCKER_BIN:-docker}"
# 每条 Docker 操作由可中断的 Ruby supervisor 拥有一个独立进程组。
# Bash wait 可被 TERM/INT 打断；先通知 supervisor，待其杀掉并回收本次 Docker 子进程后才清理项目。
operation_pid=""
pending_signal=0
# trap只记录取消事实，不在PID发布之前退出；每次操作在发布PID后和wait后统一收口。
trap 'pending_signal=130' INT
trap 'pending_signal=143' TERM
stop_operation() {
  if [[ -n "$operation_pid" ]]; then
    kill -TERM "$operation_pid" 2>/dev/null || true
    wait "$operation_pid" || true
    operation_pid=""
  fi
}
docker_bounded() {
  [[ "$pending_signal" == 0 ]] || return "$pending_signal"
  ruby -rtimeout -e '
    cancelled = false
    waiting = false
    pid = nil
    # 整个spawn/handoff/wait都在ensure保护内；尚未发布PID时信号只记录，绝不逃离所有权建立。
    handler = proc { cancelled = true; raise Interrupt if waiting }
    begin
      trap("TERM", &handler)
      trap("INT", &handler)
      pid = Process.spawn(*ARGV, pgroup: true)
      waiting = true
      raise Interrupt if cancelled
      Timeout.timeout(120) { Process.wait(pid) }
      status = $?.exitstatus || 128 + $?.termsig
      pid = nil
      exit status
    rescue Timeout::Error, Interrupt
      warn "bolt-dev Docker operation cancelled or timed out"
      exit 124
    ensure
      waiting = false
      trap("TERM", "IGNORE")
      trap("INT", "IGNORE")
      if pid
        Process.kill("KILL", -pid) rescue nil
        Process.wait(pid) rescue nil
      end
    end
  ' "$DOCKER_BIN" "$@" &
  operation_pid=$!
  local status=0
  if [[ "$pending_signal" != 0 ]]; then
    stop_operation
    return "$pending_signal"
  fi
  wait "$operation_pid" || status=$?
  if [[ "$pending_signal" != 0 ]]; then
    stop_operation
    return "$pending_signal"
  fi
  operation_pid=""
  return "$status"
}
compose() { docker_bounded compose -p "$project" -f "$ROOT_DIR/deploy/local/compose.bolt-dev.yml" "$@"; }
# DOCKER_CONTEXT 的非空值优先于 DOCKER_HOST；解析失败不以空串选择其他 daemon。
if [[ -n "${DOCKER_CONTEXT:-}" ]]; then
  endpoint="$(docker_bounded context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}')" || exit 1
elif [[ -n "${DOCKER_HOST:-}" ]]; then
  endpoint="$DOCKER_HOST"
else
  context="$(docker_bounded context show)" || exit 1
  [[ -n "$context" ]] || exit 1
  endpoint="$(docker_bounded context inspect "$context" --format '{{.Endpoints.docker.Host}}')" || exit 1
fi
[[ -n "$endpoint" && "$endpoint" != '<no value>' ]] || { echo 'cannot resolve Docker endpoint' >&2; exit 1; }
lock_key="$(printf '%s\n%s' "$endpoint" "$project" | shasum -a 256 | cut -d ' ' -f 1)"
# 固定每用户路径不使用调用者 TMPDIR，避免相同 daemon/project 通过临时目录分叉锁。
lock_root="/tmp/im-chat-bolt-dev-$(id -u)"
if [[ -L "$lock_root" ]]; then echo 'bolt-dev lock root must not be symlink' >&2; exit 1; fi
umask 077
mkdir -p "$lock_root"
[[ -O "$lock_root" && -d "$lock_root" ]] || exit 1
chmod 700 "$lock_root"
lock="$lock_root/${lock_key}.lock"
umask 077
if ! mkdir "$lock" 2>/dev/null; then
  printf 'bolt-dev lock exists: %s; refuse concurrent/stale lock, review owner and Docker state manually.\n' "$lock" >&2
  exit 1
fi
printf '%s\n' "$$" > "$lock/owner-pid"
tmp=""
created=false
keep=false
cleanup() {
  result=$?
  trap - EXIT
  trap '' INT TERM
  stop_operation
  pending_signal=0
  if $created && ! $keep; then compose down --volumes --remove-orphans || result=1; fi
  if [[ -n "$tmp" ]]; then rm -rf "$tmp"; fi
  # 不递归删除锁；只删除本进程记录后 rmdir，意外内容保留供人工检查。
  if [[ "$(cat "$lock/owner-pid")" == "$$" ]]; then
    rm "$lock/owner-pid"
    rmdir "$lock" || result=1
  else result=1; fi
  exit "$result"
}
trap cleanup EXIT
# cleanup继承pending-only trap；其入口先收口operation，再清零取消事实以允许本次down。
# 两个 probe 必须分别检查命令状态，不能用 [[ -z $(...) ]] 丢失 Docker 失败。
containers="$(docker_bounded ps -aq --filter "label=com.docker.compose.project=$project")" || exit 1
[[ -z "$containers" ]] || { echo 'bolt-dev project already has containers' >&2; exit 1; }
networks="$(docker_bounded network ls -q --filter "label=com.docker.compose.project=$project")" || exit 1
[[ -z "$networks" ]] || { echo 'bolt-dev project already has networks' >&2; exit 1; }
tmp="$(mktemp -d)"
compose config --format json > "$tmp/compose.json"
ruby "$ROOT_DIR/scripts/lib/bolt-dev-network.rb" config "$tmp/compose.json"
# empty probes与独占锁已确立本次责任；launch前登记，信号落在create期间也必须清理部分资源。
created=true
create_status=0
compose create --no-build --pull never || create_status=$?
[[ "$create_status" == 0 ]] || exit "$create_status"
network="$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("networks").fetch("bolt-dev").fetch("name")' "$tmp/compose.json")"
docker_bounded network inspect "$network" > "$tmp/network.json"
ids="$(compose ps -aq)"
[[ -n "$ids" ]] || { echo 'bolt-dev create produced no containers' >&2; exit 1; }
# Docker IDs 只含十六进制，按行展开为 inspect 参数。
docker_bounded inspect $ids > "$tmp/containers.json"
ruby "$ROOT_DIR/scripts/lib/bolt-dev-network.rb" actual "$tmp/compose.json" "$tmp/network.json" "$tmp/containers.json"
if ! $check_only; then
  compose start
  keep=true
  printf 'Started synthetic bolt-dev. Cleanup: docker compose -p %s -f %s down --volumes --remove-orphans\n' "$project" "$ROOT_DIR/deploy/local/compose.bolt-dev.yml"
else
  echo 'Actual bolt-dev topology preflight passed; removing created resources without starting applications.'
fi
