#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
K6_BIN="${K6_BIN:-$HOME/.local/bin/k6}"
GROUP_SIZE=10
MOCK_PID=""
ACTIVE_PID=""
TEMP_DIR=""

# 保留原始退出码；任何阶段失败时终止当前子进程、mock runtime 并删除私有 fixture 数据。
cleanup() {
  local status=$?
  set +e
  [[ -z "$ACTIVE_PID" ]] || terminate_process "$ACTIVE_PID" 2
  [[ -z "$MOCK_PID" ]] || terminate_process "$MOCK_PID" 2
  if [[ -n "$TEMP_DIR" ]]; then
    rm -rf "$TEMP_DIR"
  fi
  return "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

MODE="${1:-}"
[[ "$MODE" == "connection" || "$MODE" == "message" ]] || {
  printf 'usage: %s connection|message\n' "$0" >&2
  exit 2
}
[[ -x "$K6_BIN" ]] || {
  printf 'k6 executable not found: %s\n' "$K6_BIN" >&2
  exit 2
}
"$K6_BIN" version | grep -Eq '^k6 v2\.2\.0\b' || {
  printf 'k6 v2.2.0 is required\n' >&2
  exit 2
}

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/im-chat-k6-connection.XXXXXX")"

# 为没有内建 timeout 的 macOS 提供外层 deadline，并把被测命令的退出码原样返回。
run_with_deadline() {
  local timeout_seconds=$1
  shift
  "$@" &
  ACTIVE_PID=$!
  local deadline=$((SECONDS + timeout_seconds))
  while kill -0 "$ACTIVE_PID" 2>/dev/null; do
    if ((SECONDS >= deadline)); then
      terminate_process "$ACTIVE_PID" 1
      ACTIVE_PID=""
      return 124
    fi
    sleep 0.1
  done
  local status=0
  wait "$ACTIVE_PID" || status=$?
  ACTIVE_PID=""
  return "$status"
}

terminate_process() {
  local pid=$1
  local grace_seconds=$2
  kill -0 "$pid" 2>/dev/null || return 0
  kill -TERM "$pid" 2>/dev/null || true
  local deadline=$((SECONDS + grace_seconds))
  while kill -0 "$pid" 2>/dev/null && ((SECONDS < deadline)); do
    sleep 0.05
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL "$pid" 2>/dev/null || true
  fi
  wait "$pid" 2>/dev/null || true
}

wait_for_file() {
  local file=$1
  local timeout_seconds=$2
  local deadline=$((SECONDS + timeout_seconds))
  until [[ -s "$file" ]]; do
    ((SECONDS < deadline)) || return 1
    sleep 0.05
  done
}

json_field() {
  local file=$1
  local field=$2
  node -e 'const fs=require("fs"); const value=JSON.parse(fs.readFileSync(process.argv[1], "utf8")); process.stdout.write(String(value[process.argv[2]]));' "$file" "$field"
}

stop_mock() {
  if [[ -n "$MOCK_PID" ]]; then
    terminate_process "$MOCK_PID" 2
    MOCK_PID=""
  fi
}

run_connection_fixture() {
  local requested=$1
  local expected=$2
  local mode=$3
  local round_id="connection-$requested-$mode"
  local round_dir="$TEMP_DIR/$round_id"
  local seed_dir="$round_dir/seed"
  local ready_file="$round_dir/ready.json"
  local summary_file="$round_dir/k6-summary.json"
  local evidence_file="$round_dir/mock-evidence.json"
  mkdir -p "$round_dir"

  local mock_args=(
    "$ROOT_DIR/im-test/im-load-test/tools/mock-runtime.mjs"
    --ready-file "$ready_file"
    --users "$requested"
    --group-size "$GROUP_SIZE"
    --seed-directory "$seed_dir"
  )
  case "$mode" in
    disconnect)
      mock_args+=(--unexpected-disconnect-ordinal 0 --unexpected-disconnect-after-ms 1500)
      ;;
    route-timeout)
      mock_args+=(--route-cleanup-timeout-ordinal 0 --route-cleanup-timeout-ms 100)
      ;;
  esac
  node "${mock_args[@]}" \
    >"$round_dir/mock.stdout" 2>"$round_dir/mock.stderr" &
  MOCK_PID=$!
  wait_for_file "$ready_file" 10 || {
    cat "$round_dir/mock.stderr" >&2
    exit 1
  }

  local http_url
  local websocket_url
  http_url="$(json_field "$ready_file" httpUrl)"
  websocket_url="$(json_field "$ready_file" websocketUrl)"
  IM_LOAD_TEST_HTTP_URL="$http_url" node "$ROOT_DIR/im-test/im-load-test/tools/data.mjs" \
    login-round \
    --batch mock \
    --round "$round_id" \
    --output-dir "$seed_dir" \
    --concurrency 10
  local login_summary="$seed_dir/login-$round_id.json"
  node -e 'const fs=require("fs"); const value=JSON.parse(fs.readFileSync(process.argv[1], "utf8")); if(value.successCount!==Number(process.argv[2]) || value.failureCount!==0) process.exit(1);' \
    "$login_summary" "$requested"

  local credential_file="$seed_dir/credentials-$round_id.json"
  if [[ "$mode" == "handshake" ]]; then
    node -e 'const fs=require("fs"); const path=process.argv[1]; const value=JSON.parse(fs.readFileSync(path, "utf8")); value.credentials[0].accessToken="invalid-fixture-token"; fs.writeFileSync(path, JSON.stringify(value));' \
      "$credential_file"
  fi
  local round_start_ms
  if [[ "$mode" == "late" ]]; then
    round_start_ms="$(node -p 'Date.now() - 2000')"
  else
    round_start_ms="$(node -p 'Date.now() + 1000')"
  fi
  run_with_deadline 20 "$K6_BIN" run --quiet --no-usage-report \
    -e "CREDENTIAL_FILE=$credential_file" \
    -e "SUMMARY_PATH=$summary_file" \
    -e "WEBSOCKET_URL=$websocket_url" \
    -e "GROUP_SIZE=$GROUP_SIZE" \
    -e "REQUESTED_USERS=$requested" \
    -e "EFFECTIVE_USERS=$requested" \
    -e BATCH_ID=mock \
    -e "ROUND_ID=$round_id" \
    -e "ROUND_START_MS=$round_start_ms" \
    -e CONNECTION_RATE=20 \
    -e LATE_SLOT_MS=200 \
    -e STABLE_SECONDS=0 \
    -e MEASURE_SECONDS=1 \
    -e DISCONNECT_SECONDS=1 \
    -e ROUTE_CLEANUP_SECONDS=1 \
    "$ROOT_DIR/im-test/im-load-test/scenarios/connection-capacity.js"

  route_deadline=$((SECONDS + 5))
  while :; do
    curl --fail --silent --show-error "$http_url/mock/evidence" --output "$evidence_file"
    if node -e 'const fs=require("fs"); const value=JSON.parse(fs.readFileSync(process.argv[1], "utf8")).data; const mode=process.argv[2]; const ready=mode==="route-timeout" ? value.activeRoutes===1 && value.routeCleanups.some(({outcome})=>outcome==="timed_out") : value.activeRoutes===0; process.exit(ready ? 0 : 1);' \
      "$evidence_file" "$mode"; then
      break
    fi
    ((SECONDS < route_deadline)) || {
      printf 'activeRoutes did not reach the expected %s state for %s users\n' "$mode" "$requested" >&2
      exit 1
    }
    sleep 0.05
  done

  node "$ROOT_DIR/im-test/im-load-test/tools/connection-fixture-check.mjs" \
    --summary "$summary_file" \
    --evidence "$evidence_file" \
    --requested "$requested" \
    --effective "$requested" \
    --group-size "$GROUP_SIZE" \
    --expected "$expected"
  stop_mock
}

run_message_fixture() {
  local mode=$1
  local requested=20
  local round_id="message-$mode"
  local round_dir="$TEMP_DIR/$round_id"
  local seed_dir="$round_dir/seed"
  local ready_file="$round_dir/ready.json"
  local summary_file="$round_dir/k6-summary.json"
  local evidence_file="$round_dir/mock-evidence.json"
  mkdir -p "$round_dir"

  local mock_args=(
    "$ROOT_DIR/im-test/im-load-test/tools/mock-runtime.mjs"
    --ready-file "$ready_file" \
    --users "$requested" \
    --group-size "$GROUP_SIZE" \
    --seed-directory "$seed_dir"
  )
  if [[ "$mode" != "normal" ]]; then
    mock_args+=(--message-fault "$mode")
  fi
  node "${mock_args[@]}" \
    >"$round_dir/mock.stdout" 2>"$round_dir/mock.stderr" &
  MOCK_PID=$!
  wait_for_file "$ready_file" 10 || {
    cat "$round_dir/mock.stderr" >&2
    exit 1
  }

  local http_url
  local websocket_url
  http_url="$(json_field "$ready_file" httpUrl)"
  websocket_url="$(json_field "$ready_file" websocketUrl)"
  IM_LOAD_TEST_HTTP_URL="$http_url" node "$ROOT_DIR/im-test/im-load-test/tools/data.mjs" \
    login-round \
    --batch mock \
    --round "$round_id" \
    --output-dir "$seed_dir" \
    --concurrency 10
  local credential_file="$seed_dir/credentials-$round_id.json"
  local round_start_ms
  round_start_ms="$(node -p 'Date.now() + 1000')"
  run_with_deadline 20 "$K6_BIN" run --quiet --no-usage-report \
    -e "MANIFEST_FILE=$seed_dir/manifest.json" \
    -e "CREDENTIAL_FILE=$credential_file" \
    -e "SUMMARY_PATH=$summary_file" \
    -e "WEBSOCKET_URL=$websocket_url" \
    -e BATCH_ID=mock \
    -e "ROUND_ID=$round_id" \
    -e ROUND_NUMBER=1 \
    -e "GROUP_SIZE=$GROUP_SIZE" \
    -e "EFFECTIVE_USERS=$requested" \
    -e TARGET_RATE=20 \
    -e MESSAGE_BYTES=256 \
    -e LATE_SLOT_MS=200 \
    -e WARMUP_SECONDS=0 \
    -e MEASURE_SECONDS=1 \
    -e DRAIN_SECONDS=1 \
    -e BACKLOG_SAMPLE_SECONDS=0.25 \
    -e "ROUND_START_MS=$round_start_ms" \
    "$ROOT_DIR/im-test/im-load-test/scenarios/message-throughput.js"

  curl --fail --silent --show-error "$http_url/mock/evidence" --output "$evidence_file"
  local expected_decision
  case "$mode" in
    normal|response-after-push) expected_decision=stable ;;
    duplicate|unknown) expected_decision=service_failed ;;
    missing|ack-failure|send-timeout|send-busy|send-repeat) expected_decision=service_failed ;;
  esac
  node "$ROOT_DIR/im-test/im-load-test/tools/message-fixture-check.mjs" \
    --summary "$summary_file" \
    --evidence "$evidence_file" \
    --mode "$mode" \
    --generator healthy \
    --expected "$expected_decision"
  if [[ "$mode" == "normal" ]]; then
    node "$ROOT_DIR/im-test/im-load-test/tools/message-fixture-check.mjs" \
      --summary "$summary_file" \
      --evidence "$evidence_file" \
      --mode normal \
      --generator saturated \
      --expected uncertain
  fi
  stop_mock
}

if [[ "$MODE" == "connection" ]]; then
  for requested in 20 50; do
    run_connection_fixture "$requested" stable stable
  done
  run_connection_fixture 20 service_failed handshake
  run_connection_fixture 20 service_failed disconnect
  run_connection_fixture 20 service_failed route-timeout
  run_connection_fixture 20 uncertain late
else
  for message_mode in normal duplicate missing unknown ack-failure send-timeout send-busy send-repeat response-after-push; do
    run_message_fixture "$message_mode"
  done
fi
