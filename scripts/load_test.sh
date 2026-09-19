#!/usr/bin/env bash
# 用途：统一执行本地 IM 数据准备、连接/消息压测、安全清理和 base 配置恢复。
# 输入：doctor/prepare/connections/messages/smoke/cleanup/all/restore；支持 --batch/--resume/--users/--group-size/--levels/--retest-boundary/--connections。
# 输出/副作用：状态追加并 fsync 到 .harness/load-test/runtime-state.jsonl；批次凭据保持 0600；只操作固定服务 allowlist。
# 失败传播：阶段命令原退出码优先；清理失败停在 FROZEN 且三个数据 owner 保持停止，需 cleanup --resume 重试。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER_BIN="${DOCKER_BIN:-docker}"
MAVEN_BIN="${MAVEN_BIN:-mvn}"
LOCAL_UP_BIN="${LOCAL_UP_BIN:-$ROOT_DIR/scripts/local_up.sh}"
DATA_BIN="${LOAD_TEST_DATA_BIN:-}"
RESOURCE_SAMPLER_BIN="${LOAD_TEST_RESOURCE_SAMPLER_BIN:-$ROOT_DIR/scripts/load_test_resources.sh}"
RUNTIME_ROOT="${LOAD_TEST_RUNTIME_ROOT:-$ROOT_DIR/.harness/load-test}"
BASE_COMPOSE=("$DOCKER_BIN" compose -f "$ROOT_DIR/deploy/local/compose.yml" --profile full)
LOAD_COMPOSE=("$DOCKER_BIN" compose -f "$ROOT_DIR/deploy/local/compose.yml" -f "$ROOT_DIR/deploy/local/compose.load-test.yml" --profile full)
APP_SERVICES=(im-http-gateway im-ws-gateway-server im-broker-server im-account-server im-social-server im-message-server im-iam-server im-audit-server im-admin im-monitor)
LOAD_APP_SERVICES=(im-http-gateway im-ws-gateway-server im-broker-server im-account-server im-social-server im-message-server)
BASE_CORE_APP_SERVICES=("${LOAD_APP_SERVICES[@]}" im-iam-server)
MANAGEMENT_APP_SERVICES=(im-audit-server im-admin im-monitor)
QUIESCED_APP_SERVICES=(im-iam-server "${MANAGEMENT_APP_SERVICES[@]}")
OWNER_SERVICES=(im-account-server im-social-server im-message-server)
STATE_FILE="$RUNTIME_ROOT/runtime-state.jsonl"
LOCK_DIR="$RUNTIME_ROOT/runner.lock"
LOCK_RECOVERY_DIR="$RUNTIME_ROOT/runner.lock.recovery"
K6_BIN="${K6_BIN:-$HOME/.local/bin/k6}"
K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://127.0.0.1:9090/api/v1/write}"
STAGE_TIMEOUT_SECONDS="${LOAD_TEST_STAGE_TIMEOUT_SECONDS:-7200}"
ROUND_TIMEOUT_SECONDS="${LOAD_TEST_ROUND_TIMEOUT_SECONDS:-900}"
DOCTOR_TIMEOUT_SECONDS="${LOAD_TEST_DOCTOR_TIMEOUT_SECONDS:-30}"
TOTAL_TIMEOUT_SECONDS="${LOAD_TEST_TOTAL_TIMEOUT_SECONDS:-18000}"
KEEP_LOAD_TEST_DATA="${KEEP_LOAD_TEST_DATA:-0}"
COMMAND="${1:-}"
shift || true

case "$COMMAND" in
  doctor|prepare|connections|messages|smoke|cleanup|all|restore) ;;
  *) printf 'usage: %s doctor|prepare|connections|messages|smoke|cleanup|all|restore [options]\n' "$0" >&2; exit 2 ;;
esac
[[ "$STAGE_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ && "$ROUND_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ \
  && "$DOCTOR_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ && "$TOTAL_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || {
  printf 'load-test timeout values must be positive integers\n' >&2
  exit 2
}

BATCH=""
RESUME=0
USERS=3200
GROUP_SIZE=10
LEVELS=""
RETEST_BOUNDARY=0
MESSAGE_CONNECTIONS="20"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --batch) BATCH="${2:-}"; shift 2 ;;
    --resume) BATCH="${2:-}"; RESUME=1; shift 2 ;;
    --users) USERS="${2:-}"; shift 2 ;;
    --group-size) GROUP_SIZE="${2:-}"; shift 2 ;;
    --levels) LEVELS="${2:-}"; shift 2 ;;
    --retest-boundary) RETEST_BOUNDARY=1; shift ;;
    --connections) MESSAGE_CONNECTIONS="${2:-}"; shift 2 ;;
    *) printf 'unknown load-test option: %s\n' "$1" >&2; exit 2 ;;
  esac
done
[[ "$USERS" =~ ^[1-9][0-9]*$ && "$GROUP_SIZE" =~ ^[1-9][0-9]*$ ]] || exit 2
[[ -z "$LEVELS" || "$LEVELS" =~ ^[1-9][0-9]*(,[1-9][0-9]*)*$ ]] || exit 2
[[ "$MESSAGE_CONNECTIONS" =~ ^[1-9][0-9]*$ || "$MESSAGE_CONNECTIONS" == auto:50% ]] || exit 2
[[ -z "$BATCH" || "$BATCH" =~ ^[0-9a-z]{6}$ ]] || {
  printf 'load-test batch id must contain exactly six lowercase letters or digits\n' >&2
  exit 2
}

mkdir -p "$(dirname "$STATE_FILE")"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  lock_pid="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
  if [[ "$lock_pid" =~ ^[1-9][0-9]*$ ]] && kill -0 "$lock_pid" 2>/dev/null; then
    printf 'load-test runner lock is held by pid %s\n' "$lock_pid" >&2
    exit 1
  fi
  if [[ ! "$lock_pid" =~ ^[1-9][0-9]*$ ]]; then
    printf 'load-test runner lock is initializing or invalid; refusing unsafe recovery\n' >&2
    exit 1
  fi
  # 陈旧锁接管必须由第二个原子目录串行化，避免两个 runner 同时删除并重建主锁。
  if ! mkdir "$LOCK_RECOVERY_DIR" 2>/dev/null; then
    printf 'load-test runner lock recovery is already in progress\n' >&2
    exit 1
  fi
  printf '%s\n' "$$" >"$LOCK_RECOVERY_DIR/pid"
  current_lock_pid="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
  if [[ "$current_lock_pid" != "$lock_pid" ]] \
      || { [[ "$current_lock_pid" =~ ^[1-9][0-9]*$ ]] && kill -0 "$current_lock_pid" 2>/dev/null; }; then
    rm -rf "$LOCK_RECOVERY_DIR"
    printf 'load-test runner lock changed during stale recovery\n' >&2
    exit 1
  fi
  rm -rf "$LOCK_DIR"
  if ! mkdir "$LOCK_DIR"; then
    rm -rf "$LOCK_RECOVERY_DIR"
    printf 'failed to recover stale load-test lock\n' >&2
    exit 1
  fi
  printf '%s\n' "$$" >"$LOCK_DIR/pid"
  rm -rf "$LOCK_RECOVERY_DIR"
else
  printf '%s\n' "$$" >"$LOCK_DIR/pid"
fi
START_SECONDS=$SECONDS
CURRENT_K6_PID=""
CURRENT_SAMPLER_PID=""
CURRENT_ROUND=""
CURRENT_CREDENTIAL_FILE=""
PORT_PROBE_BIN="${PORT_PROBE_BIN:-nc}"
HTTP_BIN="${HTTP_BIN:-curl}"

release_lock() {
  local status=$?
  set +e
  [[ -z "$CURRENT_K6_PID" ]] || terminate_pid "$CURRENT_K6_PID" 2 TERM
  [[ -z "$CURRENT_SAMPLER_PID" ]] || terminate_pid "$CURRENT_SAMPLER_PID" 2 TERM
  [[ "$(cat "$LOCK_DIR/pid" 2>/dev/null)" != "$$" ]] || rm -rf "$LOCK_DIR"
  return "$status"
}
trap release_lock EXIT
trap 'handle_signal 130' INT
trap 'handle_signal 143' TERM

current_state() {
  if [[ ! -f "$STATE_FILE" ]]; then
    printf 'BASE_READY'
    return
  fi
  node -e '
    const fs=require("fs"); const file=process.argv[1]; let text=fs.readFileSync(file,"utf8");
    if(text!=="" && !text.endsWith("\n")){
      const cut=text.lastIndexOf("\n"); const tail=text.slice(cut+1);
      try { JSON.parse(tail); text+="\n"; }
      catch { text=text.slice(0,cut+1); }
      const fd=fs.openSync(file,"r+"); fs.ftruncateSync(fd,Buffer.byteLength(text)); fs.writeFileSync(fd,text); fs.fsyncSync(fd); fs.closeSync(fd);
    }
    const lines=text.trim().split("\n").filter(Boolean); process.stdout.write(lines.length===0?"BASE_READY":JSON.parse(lines.at(-1)).state);
  ' "$STATE_FILE"
}

write_state() {
  local state=$1
  local batch=${2:-}
  STATE_PATH="$STATE_FILE" STATE_VALUE="$state" STATE_BATCH="$batch" node -e '
    const fs=require("fs"); const path=require("path");
    const target=process.env.STATE_PATH;
    const value={version:1,state:process.env.STATE_VALUE,batchId:process.env.STATE_BATCH||null,updatedAt:new Date().toISOString()};
    const fd=fs.openSync(target,"a",0o600); fs.fchmodSync(fd,0o600); fs.writeFileSync(fd,JSON.stringify(value)+"\n"); fs.fsyncSync(fd); fs.closeSync(fd);
    const dir=fs.openSync(path.dirname(target),"r"); fs.fsyncSync(dir); fs.closeSync(dir);
  '
}

transition() {
  local target=$1
  local source
  source="$(current_state)"
  case "$source:$target" in
    BASE_READY:APPLYING|LOAD_READY:APPLYING|APPLYING:LOAD_READY|APPLYING:RESTORING|LOAD_READY:RUNNING|RUNNING:DRAINING|LOAD_READY:DRAINING|DRAINING:LOAD_READY|DRAINING:FREEZING|FREEZING:FROZEN|FROZEN:CLEANING|CLEANING:FROZEN|CLEANING:RESTORING|FROZEN:RESTORING|LOAD_READY:RESTORING|RESTORING:BASE_READY) ;;
    *) printf 'invalid load-test state transition: %s -> %s\n' "$source" "$target" >&2; exit 1 ;;
  esac
  write_state "$target" "$BATCH"
}

check_total_timeout() {
  ((SECONDS - START_SECONDS < TOTAL_TIMEOUT_SECONDS)) || {
    printf 'load-test total timeout exceeded\n' >&2
    return 124
  }
}

handle_signal() {
  local status=$1
  set +e
  if [[ "$(current_state)" == RUNNING ]]; then
    transition DRAINING
  fi
  [[ -z "$CURRENT_K6_PID" ]] || terminate_pid "$CURRENT_K6_PID" 5 INT
  [[ -z "$CURRENT_SAMPLER_PID" ]] || terminate_pid "$CURRENT_SAMPLER_PID" 2 TERM
  CURRENT_K6_PID=""
  CURRENT_SAMPLER_PID=""
  local logout_status=0
  logout_current_round || logout_status=$?
  ((logout_status == 0)) || exit "$status"
  if [[ -n "$BATCH" ]]; then
    if [[ "$KEEP_LOAD_TEST_DATA" == 1 ]]; then
      [[ "$(current_state)" != DRAINING ]] || transition LOAD_READY
    else
      cleanup_batch || true
    fi
  fi
  exit "$status"
}

logout_current_round() {
  [[ -n "$CURRENT_ROUND" && -n "$CURRENT_CREDENTIAL_FILE" && -f "$CURRENT_CREDENTIAL_FILE" ]] || return 0
  local output_directory
  output_directory="$(dirname "$CURRENT_CREDENTIAL_FILE")"
  local data_command=(node "$ROOT_DIR/im-test/im-load-test/tools/data.mjs")
  [[ -z "$DATA_BIN" ]] || data_command=("$DATA_BIN")
  local status=0
  run_with_timeout "$STAGE_TIMEOUT_SECONDS" env \
    IM_LOAD_TEST_HTTP_URL="${IM_LOAD_TEST_HTTP_URL:-http://127.0.0.1:18010}" \
    "${data_command[@]}" logout-round \
      --batch "$BATCH" --round "$CURRENT_ROUND" --output-dir "$output_directory" --concurrency 10 || status=$?
  if ((status == 0)); then
    rm -f "$CURRENT_CREDENTIAL_FILE"
    CURRENT_ROUND=""
    CURRENT_CREDENTIAL_FILE=""
  fi
  return "$status"
}

logout_remaining_rounds() {
  local directory="$RUNTIME_ROOT/$BATCH"
  local credential round status=0
  for credential in "$directory"/credentials-*.json; do
    [[ -f "$credential" ]] || continue
    round="${credential##*/credentials-}"
    round="${round%.json}"
    CURRENT_ROUND="$round"
    CURRENT_CREDENTIAL_FILE="$credential"
    logout_current_round || status=$?
    ((status == 0)) || return "$status"
  done
}

terminate_pid() {
  local pid=$1
  local grace=$2
  local first_signal=$3
  kill -0 "$pid" 2>/dev/null || return 0
  local child
  while read -r child; do
    [[ -z "$child" ]] || terminate_pid "$child" "$grace" "$first_signal"
  done < <(pgrep -P "$pid" 2>/dev/null || true)
  kill -"$first_signal" "$pid" 2>/dev/null || true
  local deadline=$((SECONDS + grace))
  while kill -0 "$pid" 2>/dev/null && ((SECONDS < deadline)); do sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then kill -TERM "$pid" 2>/dev/null || true; sleep 1; fi
  if kill -0 "$pid" 2>/dev/null; then kill -KILL "$pid" 2>/dev/null || true; fi
  wait "$pid" 2>/dev/null || true
}

wait_pid_with_timeout() {
  local pid=$1
  local timeout=$2
  local stage_deadline=$((SECONDS + timeout))
  local total_deadline=$((START_SECONDS + TOTAL_TIMEOUT_SECONDS))
  while kill -0 "$pid" 2>/dev/null; do
    local process_state
    process_state="$(ps -o stat= -p "$pid" 2>/dev/null | tr -d ' ')"
    [[ -n "$process_state" && "$process_state" != Z* ]] || break
    if ((SECONDS >= stage_deadline || SECONDS >= total_deadline)); then
      terminate_pid "$pid" 2 TERM
      return 124
    fi
    sleep 0.2
  done
  wait "$pid"
}

run_with_timeout() {
  local timeout=$1
  shift
  "$@" &
  local pid=$!
  wait_pid_with_timeout "$pid" "$timeout"
}

doctor() {
  [[ -x "$K6_BIN" ]] || { printf 'k6 executable missing\n' >&2; return 1; }
  run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$K6_BIN" version | grep -Eq '^k6 v2\.2\.0\b' || { printf 'k6 v2.2.0 is required\n' >&2; return 1; }
  run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$DOCKER_BIN" info >/dev/null
  run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${LOAD_COMPOSE[@]}" config >/dev/null
  [[ "$K6_PROMETHEUS_RW_SERVER_URL" == "http://127.0.0.1:9090/api/v1/write" ]] || {
    printf 'K6 Remote Write target must be the fixed local Prometheus endpoint\n' >&2
    return 1
  }
  df -Pk "$ROOT_DIR" | tail -1
  verify_base_environment
  run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$HTTP_BIN" -fsS --max-time 2 http://127.0.0.1:9090/-/ready >/dev/null
  run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$HTTP_BIN" -fsS --max-time 2 http://127.0.0.1:3000/api/health >/dev/null
  run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$HTTP_BIN" -fsS --max-time 2 http://127.0.0.1:19010/actuator/health >/dev/null
  "$PORT_PROBE_BIN" -z 127.0.0.1 18011
  local targets
  targets="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$HTTP_BIN" -fsS --max-time 2 http://127.0.0.1:9090/api/v1/targets)"
  ruby -rjson -e '
    payload=JSON.parse(STDIN.read)
    targets=payload.dig("data","activeTargets") || []
    abort "Prometheus targets are missing" if targets.empty?
    abort "Prometheus target is not healthy" unless targets.all? { |target| target["health"] == "up" }
  ' <<<"$targets"
}

verify_load_environment() {
  local json running
  json="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${LOAD_COMPOSE[@]}" config --format json)"
  ruby -rjson -e '
    apps=%w[im-http-gateway im-ws-gateway-server im-broker-server im-account-server im-social-server im-message-server im-iam-server im-audit-server im-admin im-monitor]
    services=JSON.parse(STDIN.read).fetch("services")
    apps.each do |name|
      env=services.fetch(name).fetch("environment")
      abort "#{name}: load environment mismatch" unless env["IM_DATASOURCE_SHARDING_SQL_SHOW"]=="false" && env["LOGGING_LEVEL_ROOT"]=="INFO"
    end
  ' <<<"$json"
  running="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${LOAD_COMPOSE[@]}" ps --services --status running)" || return $?
  [[ "$(wc -l <<<"$running" | tr -d ' ')" == 15 ]] || {
    printf 'load profile must have 15 running services\n' >&2
    return 1
  }
  local service container environment restart_count health
  for service in "${LOAD_APP_SERVICES[@]}"; do
    container="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${LOAD_COMPOSE[@]}" ps -q "$service")"
    [[ -n "$container" ]] || { printf '%s is not running\n' "$service" >&2; return 1; }
    environment="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$DOCKER_BIN" inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container")"
    grep -qx 'IM_DATASOURCE_SHARDING_SQL_SHOW=false' <<<"$environment" || return 1
    grep -qx 'LOGGING_LEVEL_ROOT=INFO' <<<"$environment" || return 1
    grep -qx 'SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800' <<<"$environment" || return 1
    restart_count="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$DOCKER_BIN" inspect --format '{{.RestartCount}}' "$container")" || return $?
    [[ "$restart_count" == 0 ]] || { printf '%s restarted unexpectedly\n' "$service" >&2; return 1; }
    health="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$DOCKER_BIN" inspect --format '{{.State.Health.Status}}' "$container")" || return $?
    [[ "$health" == healthy ]] || { printf '%s is not healthy: %s\n' "$service" "$health" >&2; return 1; }
  done
  for service in "${QUIESCED_APP_SERVICES[@]}"; do
    container="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${LOAD_COMPOSE[@]}" ps -q "$service")" || return $?
    [[ -z "$container" ]] || { printf '%s must be stopped during core load tests\n' "$service" >&2; return 1; }
  done
}

apply_load_profile() {
  local state
  state="$(current_state)"
  if [[ "$state" == LOAD_READY ]]; then
    verify_load_environment && return
    transition APPLYING
  elif [[ "$state" == BASE_READY ]]; then
    transition APPLYING
  elif [[ "$state" != APPLYING ]]; then
    printf 'load profile cannot resume from state %s\n' "$state" >&2
    return 1
  fi
  if ! verify_base_environment || ! verify_base_health; then
    run_with_timeout "$STAGE_TIMEOUT_SECONDS" "$LOCAL_UP_BIN" full || return $?
  fi
  recreate_application_profile load || return $?
  verify_load_environment || return $?
  transition LOAD_READY || return $?
}

# load 只保留被测核心链路；base 再恢复 IAM 和管理应用，避免单机资源竞争污染容量结论。
recreate_application_profile() {
  local profile=$1
  local -a compose_command
  if [[ "$profile" == load ]]; then
    compose_command=("${LOAD_COMPOSE[@]}")
    run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${compose_command[@]}" stop \
      "${QUIESCED_APP_SERVICES[@]}" || return $?
    run_with_timeout "$STAGE_TIMEOUT_SECONDS" "${compose_command[@]}" up -d \
      --force-recreate --wait --wait-timeout "$STAGE_TIMEOUT_SECONDS" \
      "${LOAD_APP_SERVICES[@]}" prometheus-full skywalking-oap mysql || return $?
  elif [[ "$profile" == base ]]; then
    compose_command=("${BASE_COMPOSE[@]}")
    run_with_timeout "$STAGE_TIMEOUT_SECONDS" "${compose_command[@]}" up -d \
      --force-recreate --wait --wait-timeout "$STAGE_TIMEOUT_SECONDS" \
      "${BASE_CORE_APP_SERVICES[@]}" prometheus-full skywalking-oap mysql || return $?
  else
    printf 'unknown application profile: %s\n' "$profile" >&2
    return 2
  fi
  wait_for_gateway_account_route || return $?
  sleep "${LOAD_TEST_DISCOVERY_SETTLE_SECONDS:-30}"
  [[ "$profile" == base ]] || return 0
  local service
  for service in "${MANAGEMENT_APP_SERVICES[@]}"; do
    run_with_timeout "$STAGE_TIMEOUT_SECONDS" "${compose_command[@]}" up -d \
      --force-recreate "$service" || return $?
    if ! wait_for_service_health "$profile" "$service" 90 true; then
      run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${compose_command[@]}" restart "$service" || return $?
      wait_for_service_health "$profile" "$service" 120 false || return $?
    fi
  done
}

# 用不存在账号执行只读认证探针；业务错误证明 Gateway 已切到当前 Account 实例。
wait_for_gateway_account_route() {
  local deadline=$((SECONDS + 90))
  local response
  while ((SECONDS < deadline)); do
    if response="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$HTTP_BIN" -fsS \
      --max-time 2 -H 'Content-Type: application/json' \
      --data '{"email":"loadtest-route-probe@invalid","password":"LoadTestRouteProbe1!"}' \
      'http://127.0.0.1:18010/account/user/signIn' 2>/dev/null)" \
        && ruby -rjson -e '
          value=JSON.parse(STDIN.read)
          exit(value.is_a?(Hash) && value["code"].is_a?(Integer) && value["code"] != 0 ? 0 : 1)
        ' <<<"$response"; then
      return 0
    fi
    sleep 2
  done
  printf 'Gateway to Account route did not converge within 90s\n' >&2
  return 124
}

# 参数依次为 profile、服务名、秒级时限和是否快速失败；首次检查快速失败以触发一次 restart，
# restart 后允许短暂 unhealthy 并继续等待，避免把正常启动过渡误判为恢复失败。
wait_for_service_health() {
  local profile=$1
  local service=$2
  local timeout=$3
  local fail_fast=${4:-true}
  local -a compose_command
  if [[ "$profile" == load ]]; then
    compose_command=("${LOAD_COMPOSE[@]}")
  elif [[ "$profile" == base ]]; then
    compose_command=("${BASE_COMPOSE[@]}")
  else
    return 2
  fi
  local deadline=$((SECONDS + timeout))
  local container health failing_streak
  while ((SECONDS < deadline)); do
    container="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" \
      "${compose_command[@]}" ps -q "$service")" || return $?
    if [[ -n "$container" ]]; then
      IFS=: read -r health failing_streak <<<"$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" \
        "$DOCKER_BIN" inspect --format \
        '{{if .State.Health}}{{.State.Health.Status}}:{{.State.Health.FailingStreak}}{{else}}missing:1{{end}}' \
        "$container")" || return $?
      [[ "$health" == healthy ]] && return 0
      if [[ "$fail_fast" == true \
          && ( "$health" == unhealthy || "${failing_streak:-0}" =~ ^[1-9][0-9]*$ ) ]]; then
        return 1
      fi
    fi
    sleep 2
  done
  printf '%s did not become healthy within %ss\n' "$service" "$timeout" >&2
  return 124
}

database_targets_file() {
  local directory=$1
  local target="$directory/database-targets.json"
  mkdir -p "$directory"
  DB_TARGET_PATH="$target" DB_USER="${IM_LOCAL_MYSQL_USER:-root}" DB_PASSWORD="${IM_LOCAL_MYSQL_PASSWORD:-root}" node -e '
    const fs=require("fs"); const path=require("path"); const user=encodeURIComponent(process.env.DB_USER); const password=encodeURIComponent(process.env.DB_PASSWORD);
    const base=`mysql://${user}:${password}@127.0.0.1:3306/`;
    const target=process.env.DB_TARGET_PATH; const temp=`${target}.${process.pid}.tmp`;
    const fd=fs.openSync(temp,"wx",0o600); fs.writeFileSync(fd,JSON.stringify({account:base+"im_chat_account",social:base+"im_chat_social",message:base+"im_chat_message"})); fs.fsyncSync(fd); fs.closeSync(fd); fs.chmodSync(temp,0o600); fs.renameSync(temp,target);
    const dir=fs.openSync(path.dirname(target),"r"); fs.fsyncSync(dir); fs.closeSync(dir);
  '
  printf '%s' "$target"
}

prepare_data() {
  apply_load_profile || return $?
  [[ -n "$BATCH" ]] || BATCH="$(node -e 'const c=require("crypto"); process.stdout.write((BigInt("0x"+c.randomBytes(4).toString("hex"))%(36n**6n)).toString(36).padStart(6,"0"));')"
  local directory="$RUNTIME_ROOT/$BATCH"
  local targets
  targets="$(database_targets_file "$directory")"
  local data_command=(node "$ROOT_DIR/im-test/im-load-test/tools/data.mjs")
  [[ -z "$DATA_BIN" ]] || data_command=("$DATA_BIN")
  local prepare_status=0
  if ((RESUME)); then
    run_with_timeout "$STAGE_TIMEOUT_SECONDS" env IM_LOAD_TEST_DATABASE_TARGETS_FILE="$targets" "${data_command[@]}" prepare --resume "$BATCH" --users "$USERS" --group-size "$GROUP_SIZE" --output-dir "$directory" || prepare_status=$?
  else
    run_with_timeout "$STAGE_TIMEOUT_SECONDS" env IM_LOAD_TEST_DATABASE_TARGETS_FILE="$targets" "${data_command[@]}" prepare --batch "$BATCH" --users "$USERS" --group-size "$GROUP_SIZE" --output-dir "$directory" || prepare_status=$?
  fi
  if ((prepare_status != 0)); then
    if [[ ! -e "$directory/journal.jsonl" && ! -e "$directory/manifest.json" ]]; then
      rm -f "$targets"
    fi
    return "$prepare_status"
  fi
  printf '{"batchId":"%s","state":"LOAD_READY"}\n' "$BATCH"
}

run_k6_round() {
  local kind=$1
  local scenario=$2
  local requested=${3:-20}
  local round_number=${4:-1}
  local directory="$RUNTIME_ROOT/$BATCH"
  local effective=$requested
  if [[ "$kind" == connection ]]; then
    effective=$((requested / GROUP_SIZE * GROUP_SIZE))
    ((effective >= GROUP_SIZE)) || {
      printf 'requested connection level %s has no complete cohort\n' "$requested" >&2
      return 2
    }
  fi
  local round="$kind-$round_number-$requested"
  local credential="$directory/credentials-$round.json"
  local summary="$directory/$round-summary.json"
  local resources="$directory/$round-resources.jsonl"
  local route_evidence="$directory/$round-routes.json"
  local round_report="$directory/$round-round.json"
  local data_command=(node "$ROOT_DIR/im-test/im-load-test/tools/data.mjs")
  [[ -z "$DATA_BIN" ]] || data_command=("$DATA_BIN")
  run_with_timeout "$STAGE_TIMEOUT_SECONDS" env IM_LOAD_TEST_HTTP_URL="${IM_LOAD_TEST_HTTP_URL:-http://127.0.0.1:18010}" "${data_command[@]}" login-round --batch "$BATCH" --round "$round" --output-dir "$directory" --concurrency 10 || return $?
  CURRENT_ROUND="$round"
  CURRENT_CREDENTIAL_FILE="$credential"
  transition RUNNING || return $?
  export K6_PROMETHEUS_RW_SERVER_URL
  export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,min,max'
  local k6_args=(
    run --quiet --no-usage-report -o experimental-prometheus-rw
    -e "MANIFEST_FILE=$directory/manifest.json" -e "CREDENTIAL_FILE=$credential" \
    -e "SUMMARY_PATH=$summary" -e "WEBSOCKET_URL=${IM_LOAD_TEST_WEBSOCKET_URL:-ws://127.0.0.1:18011/ws}" \
    -e "BATCH_ID=$BATCH" -e "ROUND_ID=$round" -e "ROUND_NUMBER=$round_number" \
    -e "GROUP_SIZE=$GROUP_SIZE" -e "EFFECTIVE_USERS=$effective"
  )
  if [[ "$kind" == connection ]]; then
    k6_args+=(-e "REQUESTED_USERS=$requested")
    [[ -z "${CONNECTION_MEASURE_SECONDS:-}" ]] || \
      k6_args+=(-e "MEASURE_SECONDS=$CONNECTION_MEASURE_SECONDS")
  else
    local message_users="$MESSAGE_CONNECTIONS"
    [[ "$message_users" != auto:50% ]] || message_users="$effective"
    message_users=$((message_users / GROUP_SIZE * GROUP_SIZE))
    k6_args+=(-e "EFFECTIVE_USERS=$message_users" -e "TARGET_RATE=$requested")
    [[ -z "${MESSAGE_MEASURE_SECONDS:-}" ]] || \
      k6_args+=(-e "MEASURE_SECONDS=$MESSAGE_MEASURE_SECONDS")
  fi
  k6_args+=("$scenario")
  "$K6_BIN" "${k6_args[@]}" &
  CURRENT_K6_PID=$!
  "$RESOURCE_SAMPLER_BIN" "$CURRENT_K6_PID" "$resources" &
  CURRENT_SAMPLER_PID=$!
  local status=0
  wait_pid_with_timeout "$CURRENT_K6_PID" "$ROUND_TIMEOUT_SECONDS" || status=$?
  CURRENT_K6_PID=""
  if ((status == 108)) && [[ "$kind" == message && -f "$summary" ]] && node -e '
    const value=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
    process.exit(value?.boundary === "k6-message"
      && Number.isInteger(value?.metrics?.forcedStops)
      && value.metrics.forcedStops > 0 ? 0 : 1);
  ' "$summary"; then
    status=0
  fi
  local sampler_status=0
  local sampler_process_state
  sampler_process_state="$(ps -o stat= -p "$CURRENT_SAMPLER_PID" 2>/dev/null | tr -d ' ')"
  if [[ -n "$sampler_process_state" && "$sampler_process_state" != Z* ]]; then
    terminate_pid "$CURRENT_SAMPLER_PID" 2 TERM
  else
    wait "$CURRENT_SAMPLER_PID" || sampler_status=$?
  fi
  CURRENT_SAMPLER_PID=""
  local resource_status=0
  validate_resource_evidence "$resources" || resource_status=$?
  local evidence_status=0
  if ((status == 0 && sampler_status == 0 && resource_status == 0)); then
    if [[ "$kind" == connection ]]; then
      run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" node \
        "$ROOT_DIR/im-test/im-load-test/tools/route-cleanup.mjs" \
        --credentials "$credential" \
        --user-count "$effective" \
        --url "${IM_LOAD_TEST_BROKER_MANAGEMENT_URL:-http://127.0.0.1:19020}" \
        --timeout-seconds "${LOAD_TEST_ROUTE_CLEANUP_TIMEOUT_SECONDS:-10}" \
        --output "$route_evidence" || evidence_status=$?
    fi
    if ((evidence_status == 0)); then
      local report_command=(node "$ROOT_DIR/im-test/im-load-test/tools/round-report.mjs" \
        --kind "$kind" --summary "$summary" --resources "$resources" --output "$round_report")
      [[ "$kind" != connection ]] || report_command+=(--route-evidence "$route_evidence")
      run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${report_command[@]}" || evidence_status=$?
    fi
  fi
  transition DRAINING || return $?
  local logout_status=0
  logout_current_round || logout_status=$?
  if ((logout_status == 0)); then transition LOAD_READY; fi
  ((status != 0)) && return "$status"
  ((logout_status != 0)) && return "$logout_status"
  ((sampler_status != 0)) && return "$sampler_status"
  ((resource_status != 0)) && return "$resource_status"
  ((evidence_status != 0)) && return "$evidence_status"
  local decision_status
  decision_status="$(node -e '
    const report=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
    process.stdout.write(report.decision?.status ?? "invalid");
  ' "$round_report")"
  case "$decision_status" in
    stable) return 0 ;;
    service_failed) return 10 ;;
    uncertain) return 11 ;;
    *) printf 'invalid round decision: %s\n' "$decision_status" >&2; return 1 ;;
  esac
}

validate_resource_evidence() {
  local file=$1
  node -e '
    const fs=require("fs"); const file=process.argv[1];
    if(!fs.existsSync(file)) process.exit(1);
    const lines=fs.readFileSync(file,"utf8").trim().split("\n").filter(Boolean);
    if(lines.length===0) process.exit(1);
    for(const line of lines) JSON.parse(line);
  ' "$file"
}

run_levels() {
  local kind=$1
  local scenario=$2
  local defaults=$3
  local selected="${LEVELS:-$defaults}"
  local values=()
  IFS=',' read -r -a values <<<"$selected"
  local index=0 level
  for level in "${values[@]}"; do
    index=$((index + 1))
    run_k6_round "$kind" "$scenario" "$level" "$index" || return $?
  done
  if ((RETEST_BOUNDARY)); then
    local start=0
    ((${#values[@]} > 1)) && start=$((${#values[@]} - 2))
    for ((i=start; i<${#values[@]}; i++)); do
      index=$((index + 1))
      run_k6_round "$kind" "$scenario" "${values[$i]}" "$index" || return $?
    done
  fi
}

freeze_owners() {
  transition FREEZING || return $?
  run_with_timeout "$STAGE_TIMEOUT_SECONDS" "${LOAD_COMPOSE[@]}" stop im-account-server im-social-server im-message-server || return $?
  wait_for_owner_release || return $?
  transition FROZEN || return $?
}

owners_released() {
  local port descriptor group service response count
  for port in 18030 18031 18032 19030 19031 19032; do
    "$PORT_PROBE_BIN" -z 127.0.0.1 "$port" >/dev/null 2>&1 && return 1
  done
  for descriptor in \
    'IM_CHAT_GROUP|im-account' 'IM_CHAT_GROUP|im-social' 'IM_CHAT_GROUP|im-message' \
    'DUBBO_GROUP|im-account' 'DUBBO_GROUP|im-social' 'DUBBO_GROUP|im-message' \
    'DUBBO_GROUP|providers:com.co.kc.imchat.service.account.facade.AccountService:1.0.0:' \
    'DUBBO_GROUP|providers:com.co.kc.imchat.service.account.admin.facade.AccountAdminService:1.0.0:' \
    'DUBBO_GROUP|providers:com.co.kc.imchat.service.social.facade.SocialService:1.0.0:' \
    'DUBBO_GROUP|providers:com.co.kc.imchat.service.message.facade.MessageService:1.0.0:' \
    'DUBBO_GROUP|providers:com.co.kc.imchat.service.message.facade.ChatService:1.0.0:'; do
    group="${descriptor%%|*}"
    service="${descriptor#*|}"
    response="$("$HTTP_BIN" -fsS --get --connect-timeout 1 --max-time 1 \
      'http://127.0.0.1:8848/nacos/v1/ns/instance/list' \
      --data-urlencode 'namespaceId=public' --data-urlencode "groupName=$group" \
      --data-urlencode "serviceName=$service")" || return 1
    count="$(ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("hosts", []).count { |host| host.fetch("enabled", true) }' <<<"$response")" || return 1
    ((count == 0)) || return 1
  done
}

wait_for_owner_release() {
  local deadline=$((SECONDS + STAGE_TIMEOUT_SECONDS))
  until owners_released; do
    ((SECONDS < deadline && SECONDS - START_SECONDS < TOTAL_TIMEOUT_SECONDS)) || {
      printf 'owner port or Nacos/Dubbo unregister timeout\n' >&2
      return 124
    }
    sleep 1
  done
}

restore_base() {
  local state
  state="$(current_state)"
  [[ "$state" == BASE_READY ]] && return
  if [[ "$state" == RESTORING ]] && verify_base_environment && verify_base_health; then
    transition BASE_READY
    return
  fi
  [[ "$state" == RESTORING ]] || transition RESTORING || return $?
  recreate_application_profile base || return $?
  verify_base_environment || return $?
  verify_base_health || return $?
  transition BASE_READY || return $?
}

verify_base_environment() {
  local running service container environment
  running="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${BASE_COMPOSE[@]}" ps --services --status running)" || return $?
  [[ "$(wc -l <<<"$running" | tr -d ' ')" == 19 ]] || { printf 'base full must have 19 running services\n' >&2; return 1; }
  for service in "${APP_SERVICES[@]}"; do
    container="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "${BASE_COMPOSE[@]}" ps -q "$service")" || return $?
    [[ -n "$container" ]] || { printf '%s is not running\n' "$service" >&2; return 1; }
    environment="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$DOCKER_BIN" inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container")" || return $?
    if grep -q '^IM_DATASOURCE_SHARDING_SQL_SHOW=' <<<"$environment"; then
      printf '%s retained load-test SQL override\n' "$service" >&2
      return 1
    fi
  done
}

verify_base_health() {
  local service container health
  local services=(mysql redis nacos kafka prometheus-full grafana skywalking-banyandb
    skywalking-oap skywalking-ui "${APP_SERVICES[@]}")
  for service in "${services[@]}"; do
    container="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" \
      "${BASE_COMPOSE[@]}" ps -q "$service")" || return $?
    [[ -n "$container" ]] || { printf '%s is not running\n' "$service" >&2; return 1; }
    health="$(run_with_timeout "$DOCTOR_TIMEOUT_SECONDS" "$DOCKER_BIN" inspect \
      --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      "$container")" || return $?
    [[ "$health" == healthy ]] || {
      printf '%s is not healthy: %s\n' "$service" "$health" >&2
      return 1
    }
  done
}

finalize_completed_cleanup() {
  local directory=$1
  local manifest="$directory/manifest.json"
  local checkpoints="$manifest.cleanup-checkpoints"
  ruby -rdigest -e '
    manifest, checkpoints, batch = ARGV
    abort "cleanup manifest must be a regular file" unless File.lstat(manifest).file?
    abort "cleanup checkpoints must be a regular file" unless File.lstat(checkpoints).file?
    lines = File.readlines(checkpoints, chomp: true)
    expected = %w[account cache-account cache-message cache-social message receipts social]
    abort "cleanup checkpoint header mismatch" unless lines.shift == "batch=#{batch}"
    abort "cleanup manifest digest mismatch" unless
      lines.shift == "manifest=#{Digest::SHA256.file(manifest).hexdigest}"
    abort "cleanup checkpoints are incomplete" unless lines.sort == expected
  ' "$manifest" "$checkpoints" "$BATCH" || {
    printf 'cannot finalize cleanup for batch %s without complete digest-bound checkpoints\n' "$BATCH" >&2
    return 1
  }
  rm -f "$directory/credentials.json" "$directory"/credentials-*.json "$directory/database-targets.json"
}

cleanup_batch() {
  [[ -n "$BATCH" ]] || { printf '--batch or --resume is required\n' >&2; return 2; }
  local directory="$RUNTIME_ROOT/$BATCH"
  case "$(current_state)" in
    BASE_READY) finalize_completed_cleanup "$directory"; return $? ;;
    RUNNING|LOAD_READY) transition DRAINING || return $?; logout_remaining_rounds || return $?; freeze_owners || return $? ;;
    DRAINING) logout_remaining_rounds || return $?; freeze_owners || return $? ;;
    FREEZING) wait_for_owner_release || return $?; transition FROZEN || return $? ;;
    CLEANING) transition FROZEN || return $? ;;
    FROZEN) ;;
    *) printf 'cleanup cannot resume from state %s\n' "$(current_state)" >&2; return 1 ;;
  esac
  transition CLEANING || return $?
  if ! run_with_timeout "$STAGE_TIMEOUT_SECONDS" "$MAVEN_BIN" -q \
      -pl im-test/im-load-test -am -DskipTests install; then
    transition FROZEN || return $?
    return 1
  fi
  if ! LOAD_TEST_CLEANUP_ACCOUNT_URL=jdbc:mysql://127.0.0.1:3306/im_chat_account \
    LOAD_TEST_CLEANUP_ACCOUNT_USER="${IM_LOCAL_MYSQL_USER:-root}" LOAD_TEST_CLEANUP_ACCOUNT_PASSWORD="${IM_LOCAL_MYSQL_PASSWORD:-root}" \
    LOAD_TEST_CLEANUP_SOCIAL_URL=jdbc:mysql://127.0.0.1:3306/im_chat_social \
    LOAD_TEST_CLEANUP_SOCIAL_USER="${IM_LOCAL_MYSQL_USER:-root}" LOAD_TEST_CLEANUP_SOCIAL_PASSWORD="${IM_LOCAL_MYSQL_PASSWORD:-root}" \
    LOAD_TEST_CLEANUP_MESSAGE_URL=jdbc:mysql://127.0.0.1:3306/im_chat_message \
    LOAD_TEST_CLEANUP_MESSAGE_USER="${IM_LOCAL_MYSQL_USER:-root}" LOAD_TEST_CLEANUP_MESSAGE_PASSWORD="${IM_LOCAL_MYSQL_PASSWORD:-root}" \
    LOAD_TEST_CLEANUP_REDIS_URL=redis://127.0.0.1:6379 LOAD_TEST_CLEANUP_REDIS_PASSWORD="${IM_LOCAL_REDIS_PASSWORD:-}" \
    run_with_timeout "$STAGE_TIMEOUT_SECONDS" "$MAVEN_BIN" -q \
      -f "$ROOT_DIR/im-test/im-load-test/pom.xml" -DskipTests test-compile exec:java \
      -Dexec.classpathScope=test -Dexec.mainClass=com.co.kc.imchat.loadtest.cleanup.LoadTestCleanupMain \
      -Dexec.args="execute --manifest $directory/manifest.json --confirm-batch $BATCH --journal $directory/journal.jsonl"; then
    transition FROZEN || return $?
    return 1
  fi
  restore_base || return $?
  rm -f "$directory/credentials.json" "$directory"/credentials-*.json "$directory/database-targets.json"
}

check_total_timeout
run_all_or_smoke() {
  local primary_status=0
  local smoke_message_rate="${SMOKE_MESSAGE_RATE:-10}"
  [[ "$smoke_message_rate" =~ ^[1-9][0-9]*$ ]] || {
    printf 'smoke message rate must be a positive integer\n' >&2
    return 2
  }
  prepare_data || primary_status=$?
  if ((primary_status == 0)); then
    run_k6_round connection "$ROOT_DIR/im-test/im-load-test/scenarios/connection-capacity.js" 20 1 || primary_status=$?
  fi
  if ((primary_status == 0)); then
    run_k6_round message "$ROOT_DIR/im-test/im-load-test/scenarios/message-throughput.js" \
      "$smoke_message_rate" 1 || primary_status=$?
  fi
  if ((primary_status == 0)); then
    verify_load_environment || primary_status=$?
  fi
  if [[ "$KEEP_LOAD_TEST_DATA" != 1 && -n "$BATCH" && -f "$RUNTIME_ROOT/$BATCH/manifest.json" ]]; then
    local cleanup_status=0
    cleanup_batch || cleanup_status=$?
    ((primary_status != 0)) && return "$primary_status"
    return "$cleanup_status"
  fi
  if ((primary_status != 0)) && [[ "$(current_state)" != BASE_READY ]]; then
    local restore_status=0
    restore_base || restore_status=$?
    ((primary_status != 0)) && return "$primary_status"
    return "$restore_status"
  fi
  return "$primary_status"
}

case "$COMMAND" in
  doctor) doctor ;;
  prepare) prepare_data ;;
  connections)
    [[ -n "$BATCH" ]] || { printf -- '--batch is required\n' >&2; exit 2; }
    apply_load_profile
    run_levels connection "$ROOT_DIR/im-test/im-load-test/scenarios/connection-capacity.js" '50,100,200,400,800,1600,3200'
    ;;
  messages)
    [[ -n "$BATCH" ]] || { printf -- '--batch is required\n' >&2; exit 2; }
    apply_load_profile
    run_levels message "$ROOT_DIR/im-test/im-load-test/scenarios/message-throughput.js" '50,100,200,400,800,1600,3200'
    ;;
  cleanup) cleanup_batch ;;
  restore) restore_base ;;
  smoke|all) run_all_or_smoke ;;
esac
