#!/usr/bin/env bash
# 用途：验证压测 override、Prometheus、Grafana，并用 fake 命令执行统一 runner 的失败恢复契约。
# 输入：无位置参数；解析 Compose 并运行隔离 fake fixture，不启动真实容器或产生真实负载。
# 输出/副作用：契约满足时返回 0；临时验证 doctor/锁/timeout/signal/cleanup/restore/resource，退出时清理。
# 依赖：bash、docker compose、Ruby、rg。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export LOAD_TEST_DISCOVERY_SETTLE_SECONDS=0
BASE_COMPOSE="$ROOT_DIR/deploy/local/compose.yml"
LOAD_COMPOSE="$ROOT_DIR/deploy/local/compose.load-test.yml"
DASHBOARD="$ROOT_DIR/deploy/local/grafana/provisioning/dashboards/im-chat-load-test.json"
RUNNER="$ROOT_DIR/scripts/load_test.sh"
RESOURCE_SAMPLER="$ROOT_DIR/scripts/load_test_resources.sh"
EVIDENCE_HELPER="$ROOT_DIR/scripts/lib/load-test-evidence.sh"

for file in "$LOAD_COMPOSE" "$DASHBOARD" "$RUNNER" "$RESOURCE_SAMPLER" "$EVIDENCE_HELPER"; do
  [[ -f "$file" ]] || {
    printf 'Missing load-test runtime file: %s\n' "$file" >&2
    exit 1
  }
done

for mode in load-test load-test-smoke; do
  rg -q "^[[:space:]]*$mode\)" "$ROOT_DIR/scripts/verify.sh" || {
    printf 'verify.sh is missing load-test mode: %s\n' "$mode" >&2
    exit 1
  }
done
rg -Fq 'test-load-test-harness.sh' "$ROOT_DIR/scripts/test-harness.sh" || {
  printf 'default Harness self-test must include the deterministic load-test fixture\n' >&2
  exit 1
}

source "$EVIDENCE_HELPER"
smoke_config_default="$(load_test_smoke_resolved_config)"
smoke_config_changed="$(LOAD_TEST_SMOKE_MESSAGE_MEASURE_SECONDS=101 load_test_smoke_resolved_config)"
[[ "$smoke_config_default" != "$smoke_config_changed" ]] || {
  printf 'current smoke environment must change the resolved config\n' >&2
  exit 1
}
ruby -rjson -e '
  config=JSON.parse(ARGV.fetch(0))
  abort "connection smoke duration changed" unless config.dig("connection", "measureSeconds") == 20.0
  abort "message smoke rate must be 1 msg/s" unless config.dig("message", "targetRate") == 1.0
  abort "message smoke must plan at least 1000 sends" unless
    config.dig("message", "targetRate") * config.dig("message", "measureSeconds") >= 1000.0
  abort "message smoke window must retain five recovery slots" unless
    config.dig("message", "measureSeconds") == 1005.0
  abort "message smoke drain must be 10 seconds" unless config.dig("message", "drainSeconds") == 10.0
  abort "smoke round timeout must cover the 1000-second message window" unless
    config.dig("timeouts", "roundSeconds") == 1500
' "$smoke_config_default"
config_bin="$(mktemp -d "${TMPDIR:-/tmp}/im-chat-load-test-config.XXXXXX")"
cat >"$config_bin/docker" <<'SH'
#!/usr/bin/env bash
printf 'fixture compose config\n'
SH
cat >"$config_bin/k6" <<'SH'
#!/usr/bin/env bash
printf 'k6 v2.2.0 (fixture)\n'
SH
chmod +x "$config_bin/docker" "$config_bin/k6"
smoke_config_digest="$(DOCKER_BIN="$config_bin/docker" K6_BIN="$config_bin/k6" \
  load_test_config_digest "$ROOT_DIR" "$smoke_config_default")"
[[ "$smoke_config_digest" =~ ^[0-9a-f]{64}$ ]] || {
  printf 'resolved smoke config digest must be a SHA-256 value\n' >&2
  exit 1
}
rm -rf "$config_bin"
scope_root="$(mktemp -d "${TMPDIR:-/tmp}/im-chat-load-test-scope.XXXXXX")"
git -C "$scope_root" init --quiet
git -C "$scope_root" config user.email harness@example.invalid
git -C "$scope_root" config user.name Harness
mkdir -p "$scope_root/docs/exec-plans/active" "$scope_root/src"
printf 'base\n' >"$scope_root/src/main.txt"
printf 'progress\n' >"$scope_root/PROGRESS.md"
printf 'plan\n' >"$scope_root/docs/exec-plans/active/current.md"
git -C "$scope_root" add .
git -C "$scope_root" commit --quiet -m base
scope_before="$(load_test_scope_fingerprint "$scope_root")"
printf 'changed state\n' >"$scope_root/PROGRESS.md"
printf 'changed plan\n' >"$scope_root/docs/exec-plans/active/current.md"
scope_after_state="$(load_test_scope_fingerprint "$scope_root")"
[[ "$scope_before" == "$scope_after_state" ]] || {
  printf 'load-test scope must ignore execution-state documents\n' >&2
  exit 1
}
printf 'changed source\n' >"$scope_root/src/main.txt"
scope_after_source="$(load_test_scope_fingerprint "$scope_root")"
[[ "$scope_before" != "$scope_after_source" ]] || {
  printf 'load-test scope must include every other Git-visible file\n' >&2
  exit 1
}
rm -rf "$scope_root"

evidence_root="$(mktemp -d "${TMPDIR:-/tmp}/im-chat-load-test-evidence.XXXXXX")"
mkdir -p "$evidence_root/scripts/lib" "$evidence_root/bin" \
  "$evidence_root/im-test/im-load-test" "$evidence_root/.harness/load-test"
cp "$ROOT_DIR/scripts/verify.sh" "$evidence_root/scripts/verify.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$evidence_root/scripts/lib/harness-evidence.sh"
cp "$EVIDENCE_HELPER" "$evidence_root/scripts/lib/load-test-evidence.sh"
touch "$evidence_root/scripts/test-load-test-harness.sh"
cat >"$evidence_root/scripts/load_test.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
  doctor) exit 0 ;;
  smoke)
    batch=smk001
    directory="$LOAD_TEST_FIXTURE_ROOT/.harness/load-test/$batch"
    mkdir -p "$directory"
    printf '{"version":1,"state":"%s","batchId":"smk001"}\n' \
      "${FAKE_SMOKE_STATE:-BASE_READY}" \
      >"$LOAD_TEST_FIXTURE_ROOT/.harness/load-test/runtime-state.jsonl"
    [[ "${FAKE_SMOKE_MUTATE_SOURCE:-0}" != 1 ]] || printf 'changed\n' \
      >>"$LOAD_TEST_FIXTURE_ROOT/source.txt"
    [[ "${FAKE_SMOKE_MUTATE_DATA:-0}" != 1 ]] || printf 'changed-data\n' \
      >"$LOAD_TEST_ORDINARY_CHECKSUM_FILE"
    printf '%s\n' '{"version":1,"boundary":"load-test-round","kind":"connection","decision":{"status":"stable","reasons":[]}}' \
      >"$directory/connection-1-20-round.json"
    printf '%s\n' "{\"version\":1,\"boundary\":\"load-test-round\",\"kind\":\"message\",\"decision\":{\"status\":\"${FAKE_SMOKE_MESSAGE_DECISION:-stable}\",\"reasons\":[]}}" \
      >"$directory/message-1-10-round.json"
    printf '%s\n' '{"batchId":"smk001","state":"LOAD_READY"}'
    [[ "${FAKE_SMOKE_MESSAGE_DECISION:-stable}" != service_failed ]] || exit 10
    ;;
  *) exit 2 ;;
esac
SH
for command in npm mvn; do
  cat >"$evidence_root/bin/$command" <<'SH'
#!/usr/bin/env bash
exit 0
SH
done
cat >"$evidence_root/bin/docker" <<'SH'
#!/usr/bin/env bash
if [[ " $* " == *" config "* ]]; then printf 'fixture compose\n'; exit 0; fi
exit 0
SH
cat >"$evidence_root/bin/k6" <<'SH'
#!/usr/bin/env bash
printf 'k6 v2.2.0 (fixture)\n'
SH
chmod +x "$evidence_root/scripts/verify.sh" "$evidence_root/scripts/load_test.sh" \
  "$evidence_root/bin/npm" "$evidence_root/bin/mvn" \
  "$evidence_root/bin/docker" "$evidence_root/bin/k6"
printf '/.harness/\n' >"$evidence_root/.gitignore"
printf 'source\n' >"$evidence_root/source.txt"
printf 'ordinary-fixture\n' >"$evidence_root/.harness/ordinary-checksum"
git -C "$evidence_root" init --quiet
git -C "$evidence_root" config user.email harness@example.invalid
git -C "$evidence_root" config user.name Harness
git -C "$evidence_root" add .
git -C "$evidence_root" commit --quiet -m fixture
LOAD_TEST_FIXTURE_ROOT="$evidence_root" \
  LOAD_TEST_ORDINARY_CHECKSUM_FILE="$evidence_root/.harness/ordinary-checksum" \
  LOAD_TEST_CONFIG_DIGEST_VALUE=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  DOCKER_BIN="$evidence_root/bin/docker" K6_BIN="$evidence_root/bin/k6" \
  PATH="$evidence_root/bin:$PATH" "$evidence_root/scripts/verify.sh" load-test-smoke >/dev/null
smoke_evidence="$evidence_root/.harness/verifications/load-test-smoke.json"
if ! ruby -rjson -e '
  evidence=JSON.parse(File.read(ARGV.fetch(0)))
  abort "batch missing" unless evidence["batchId"] == "smk001"
  abort "config digest missing" unless evidence["configDigest"]&.match?(/\A[0-9a-f]{64}\z/)
  abort "resolved config missing" unless evidence.dig("resolvedConfig", "profile") == "smoke"
  abort "scope missing" unless evidence["loadTestScopeFingerprint"]&.match?(/\A[0-9a-f]{64}\z/)
  abort "cleanup missing" unless evidence["cleanupStatus"] == "passed"
  abort "ordinary data changed" unless evidence["ordinaryDataUnchanged"] == true
  abort "round decisions missing" unless evidence.fetch("perRoundDecisions").map { |round| round.dig("decision", "status") } == %w[stable stable]
' "$smoke_evidence"; then
  printf 'load-test smoke evidence is incomplete\n' >&2
  exit 1
fi

FAKE_SMOKE_MESSAGE_DECISION=service_failed \
  LOAD_TEST_FIXTURE_ROOT="$evidence_root" \
  LOAD_TEST_ORDINARY_CHECKSUM_FILE="$evidence_root/.harness/ordinary-checksum" \
  LOAD_TEST_CONFIG_DIGEST_VALUE=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  DOCKER_BIN="$evidence_root/bin/docker" K6_BIN="$evidence_root/bin/k6" \
  PATH="$evidence_root/bin:$PATH" "$evidence_root/scripts/verify.sh" load-test-smoke >/dev/null
ruby -rjson -e '
  evidence=JSON.parse(File.read(ARGV.fetch(0)))
  abort "service failure smoke must retain passing workflow evidence" unless evidence["status"] == "passed"
  abort "message service failure was lost" unless
    evidence.fetch("perRoundDecisions").last.dig("decision", "status") == "service_failed"
' "$smoke_evidence"

set +e
LOAD_TEST_FIXTURE_ROOT="$evidence_root" \
  LOAD_TEST_ORDINARY_CHECKSUM_FILE="$evidence_root/.harness/ordinary-checksum" \
  LOAD_TEST_CONFIG_DIGEST_VALUE=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  FAKE_SMOKE_MUTATE_SOURCE=1 FAKE_SMOKE_MUTATE_DATA=1 FAKE_SMOKE_STATE=FROZEN \
  DOCKER_BIN="$evidence_root/bin/docker" K6_BIN="$evidence_root/bin/k6" \
  PATH="$evidence_root/bin:$PATH" "$evidence_root/scripts/verify.sh" load-test-smoke >/dev/null 2>&1
failed_smoke_status=$?
set -e
((failed_smoke_status != 0)) || {
  printf 'unstable worktree/data/cleanup smoke fixture must fail\n' >&2
  exit 1
}
if ! ruby -rjson -e '
  evidence=JSON.parse(File.read(ARGV.fetch(0)))
  abort unless evidence["status"] == "failed"
  abort unless evidence["worktreeStable"] == false
  abort unless evidence["loadTestScopeStable"] == false
  abort unless evidence["cleanupStatus"] == "failed"
  abort unless evidence["ordinaryDataUnchanged"] == false
' "$smoke_evidence"; then
  printf 'failed load-test smoke evidence did not preserve failure attribution\n' >&2
  exit 1
fi
rm -rf "$evidence_root"

rg -q -- '--web.enable-remote-write-receiver' "$BASE_COMPOSE" || {
  printf 'prometheus-full must enable the Remote Write receiver\n' >&2
  exit 1
}

compose_json="$(docker compose -f "$BASE_COMPOSE" -f "$LOAD_COMPOSE" --profile full config --format json)"
ruby -rjson -e '
  config = JSON.parse(STDIN.read)
  services = config.fetch("services")
  apps = %w[im-http-gateway im-ws-gateway-server im-broker-server im-account-server
            im-social-server im-message-server im-iam-server im-audit-server im-admin im-ops]
  apps.each do |name|
    environment = services.fetch(name).fetch("environment")
    abort "#{name}: SQL logging must be disabled" unless
      environment.fetch("IM_DATASOURCE_SHARDING_SQL_SHOW") == "false"
    abort "#{name}: root logging must remain INFO" unless
      environment.fetch("LOGGING_LEVEL_ROOT") == "INFO"
    abort "#{name}: SkyWalking agent must remain configured" unless
      environment.fetch("SW_AGENT_COLLECTOR_BACKEND_SERVICES") == "skywalking-oap:11800"
    abort "#{name}: management metrics port must remain configured" unless
      environment.key?("MANAGEMENT_SERVER_PORT")
  end
  core = %w[im-http-gateway im-ws-gateway-server im-broker-server im-account-server
            im-social-server]
  core.each do |name|
    abort "#{name}: load-test memory limit must be 1 GiB" unless
      services.fetch(name).fetch("mem_limit").to_i == 1024 * 1024 * 1024
  end
  abort "im-message-server: load-test memory limit must be 2 GiB" unless
    services.fetch("im-message-server").fetch("mem_limit").to_i == 2 * 1024 * 1024 * 1024
  abort "skywalking-oap: load-test memory limit must be 1536 MiB" unless
    services.fetch("skywalking-oap").fetch("mem_limit").to_i == 1536 * 1024 * 1024
  abort "mysql: load-test memory limit must be 768 MiB" unless
    services.fetch("mysql").fetch("mem_limit").to_i == 768 * 1024 * 1024
  command = services.fetch("prometheus-full").fetch("command")
  abort "Prometheus Remote Write receiver missing" unless
    command.include?("--web.enable-remote-write-receiver")
' <<<"$compose_json"

ruby -rjson -e '
  dashboard = JSON.parse(File.read(ARGV.fetch(0)))
  abort "dashboard title missing" unless dashboard.fetch("title").include?("Load Test")
  text = JSON.generate(dashboard)
  %w[userId messageId chatId receiptId accessToken].each do |label|
    abort "high-cardinality dashboard label: #{label}" if text.include?(label)
  end
  %w[batch round phase message_type].each do |label|
    abort "dashboard missing low-cardinality label: #{label}" unless text.include?(label)
  end
' "$DASHBOARD"

for command in doctor prepare connections messages smoke cleanup all restore; do
  rg -q "$command" "$RUNNER" || {
    printf 'load-test runner is missing command: %s\n' "$command" >&2
    exit 1
  }
done
for state in BASE_READY APPLYING LOAD_READY RUNNING DRAINING FREEZING FROZEN CLEANING RESTORING; do
  rg -q "$state" "$RUNNER" || {
    printf 'load-test runner is missing state: %s\n' "$state" >&2
    exit 1
  }
done
for contract in \
  'BASE_COMPOSE=' 'LOAD_COMPOSE=' 'force-recreate' 'LOCAL_UP_BIN.*full' \
  'im-account-server im-social-server im-message-server' 'KEEP_LOAD_TEST_DATA' \
  'QUIESCED_APP_SERVICES=' 'compose_command.*stop' \
  'status.*108' 'forcedStops' \
  'K6_PROMETHEUS_RW_SERVER_URL' 'api/v1/write' 'timeout' 'lock' 'trap'; do
  rg -q "$contract" "$RUNNER" || {
    printf 'load-test runner is missing contract: %s\n' "$contract" >&2
    exit 1
  }
done

for contract in 'stats --no-stream' 'ps -o' 'hw.logicalcpu' 'availableMemoryBytes' \
  'remaining_seconds' 'sleep.*remaining_seconds'; do
  rg -q "$contract" "$RESOURCE_SAMPLER" || {
    printf 'resource sampler is missing contract: %s\n' "$contract" >&2
    exit 1
  }
done

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/im-chat-load-test-harness.XXXXXX")"
trap 'rm -rf "$temp_dir"' EXIT
mkdir -p "$temp_dir/bin" "$temp_dir/runtime"
cat >"$temp_dir/bin/docker" <<'SH'
#!/usr/bin/env bash
if [[ -n "${FAKE_DOCKER_COMMAND_LOG:-}" ]]; then
  printf '%s\n' "$*" >>"$FAKE_DOCKER_COMMAND_LOG"
fi
if [[ "${1:-}" == info ]]; then exit 0; fi
if [[ "${1:-}" == slow-info ]]; then sleep 2; exit 0; fi
if [[ "${1:-}" == stats ]]; then
  sleep "${FAKE_DOCKER_STATS_DELAY:-0}"
  printf '{"Name":"fixture","CPUPerc":"1%%","MemUsage":"1MiB / 1GiB"}\n'
  exit 0
fi
if [[ "${1:-}" == inspect ]]; then
  [[ "${FAKE_DOCKER_INSPECT_FAIL:-0}" == 1 ]] && exit 31
  if [[ " $* " == *'.RestartCount'* ]]; then
    printf '0\n'
    exit 0
  fi
  if [[ " $* " == *'.State.Health.Status'* ]]; then
    suffix=""
    [[ " $* " != *'.State.Health.FailingStreak'* ]] || suffix=":0"
    if [[ "${FAKE_DOCKER_DYNAMIC_HEALTH:-0}" == 1 ]]; then
      current_health="$(cat "$FAKE_DOCKER_HEALTH_STATE")"
      printf '%s%s\n' "$current_health" "$suffix"
      if [[ "${FAKE_DOCKER_RESTART_TRANSIENT_UNHEALTHY:-0}" == 1 \
          && "$current_health" == unhealthy \
          && -f "${FAKE_DOCKER_RESTART_MARKER:-}" ]]; then
        printf 'healthy\n' >"$FAKE_DOCKER_HEALTH_STATE"
      fi
    else
      printf '%s%s\n' "${FAKE_DOCKER_HEALTH:-healthy}" "$suffix"
    fi
    exit 0
  fi
  mode="${FAKE_DOCKER_LOAD:-0}"
  if [[ "${FAKE_DOCKER_DYNAMIC:-0}" == 1 && -f "${FAKE_DOCKER_STATE:-}" ]]; then
    mode="$(cat "$FAKE_DOCKER_STATE")"
  fi
  if [[ "$mode" == 1 || "$mode" == load ]]; then
    printf 'IM_DATASOURCE_SHARDING_SQL_SHOW=false\nLOGGING_LEVEL_ROOT=INFO\nSW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800\n'
  else
    printf 'PATH=/usr/bin\n'
  fi
  exit 0
fi
if [[ "${1:-}" == compose ]]; then
  args=" $* "
  [[ "${FAKE_DOCKER_UP_FAIL:-0}" == 1 && "$args" == *" up "* ]] && exit 33
  if [[ "${FAKE_DOCKER_DYNAMIC_HEALTH:-0}" == 1 && "$args" == *" up "* ]]; then
    if [[ "${FAKE_DOCKER_RESTART_TRANSIENT_UNHEALTHY:-0}" == 1 ]]; then
      printf 'unhealthy\n' >"$FAKE_DOCKER_HEALTH_STATE"
      rm -f "${FAKE_DOCKER_RESTART_MARKER:-}"
    else
      printf 'healthy\n' >"$FAKE_DOCKER_HEALTH_STATE"
    fi
  fi
  if [[ "${FAKE_DOCKER_DYNAMIC_HEALTH:-0}" == 1 && "$args" == *" restart "* ]]; then
    printf 'unhealthy\n' >"$FAKE_DOCKER_HEALTH_STATE"
    [[ -z "${FAKE_DOCKER_RESTART_MARKER:-}" ]] || : >"$FAKE_DOCKER_RESTART_MARKER"
  fi
  if [[ "${FAKE_DOCKER_DYNAMIC:-0}" == 1 && "$args" == *" up "* ]]; then
    if [[ "$args" == *'compose.load-test.yml'* ]]; then printf 'load' >"$FAKE_DOCKER_STATE"; else printf 'base' >"$FAKE_DOCKER_STATE"; fi
  fi
  if [[ "$args" == *" config --format json "* ]]; then
    node -e 'const apps=["im-http-gateway","im-ws-gateway-server","im-broker-server","im-account-server","im-social-server","im-message-server","im-iam-server","im-audit-server","im-admin","im-ops"]; const services=Object.fromEntries(apps.map(name=>[name,{environment:{IM_DATASOURCE_SHARDING_SQL_SHOW:"false",LOGGING_LEVEL_ROOT:"INFO",SW_AGENT_COLLECTOR_BACKEND_SERVICES:"skywalking-oap:11800",MANAGEMENT_SERVER_PORT:"1"}}])); process.stdout.write(JSON.stringify({services}));'
  elif [[ "$args" == *" ps --services --status running "* ]]; then
    printf '%s\n' mysql redis nacos kafka prometheus-full grafana skywalking-banyandb skywalking-oap skywalking-ui \
      im-http-gateway im-ws-gateway-server im-broker-server im-account-server im-social-server im-message-server
    if [[ "$args" != *'compose.load-test.yml'* ]]; then
      printf '%s\n' im-iam-server im-audit-server im-admin im-ops
    fi
  elif [[ "$args" == *" ps -q "* ]]; then
    service="${!#}"
    if [[ "$args" == *'compose.load-test.yml'* \
        && " im-iam-server im-audit-server im-admin im-ops " == *" $service "* ]]; then
      exit 0
    fi
    printf 'fixture-container\n'
  fi
  exit 0
fi
exit 1
SH
cat >"$temp_dir/bin/k6" <<'SH'
#!/usr/bin/env bash
[[ "${1:-}" == version ]] && { printf 'k6 v2.2.0 (fixture)\n'; exit 0; }
[[ -z "${FAKE_K6_LOG:-}" ]] || printf '%s\n' "$*" >>"$FAKE_K6_LOG"
[[ "${FAKE_K6_EXIT:-0}" == 1 ]] && exit 0
trap 'exit 0' INT TERM
while :; do sleep 1; done
SH
cat >"$temp_dir/bin/mvn" <<'SH'
#!/usr/bin/env bash
[[ "${FAKE_MAVEN_FAIL:-0}" == 1 ]] && exit 1
exit 0
SH
cat >"$temp_dir/bin/nc" <<'SH'
#!/usr/bin/env bash
[[ "${FAKE_NC_OPEN:-0}" == 1 ]] && exit 0
exit 1
SH
cat >"$temp_dir/bin/curl" <<'SH'
#!/usr/bin/env bash
case " $* " in
  *'/api/v1/targets'*) printf '{"data":{"activeTargets":[{"health":"up"}]}}' ;;
  *'/nacos/v1/ns/instance/list'*) printf '{"hosts":[]}' ;;
  *'/account/user/signIn'*) printf '{"code":10001,"msg":"probe","data":{}}' ;;
  *) printf '{}' ;;
esac
SH
cat >"$temp_dir/bin/local_up" <<'SH'
#!/usr/bin/env bash
[[ "${FAKE_LOCAL_UP_FAIL:-0}" == 1 ]] && exit 1
exit 0
SH
cat >"$temp_dir/bin/data" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$FAKE_DATA_LOG"
command=${1:-}
shift || true
[[ "${FAKE_DATA_FAIL_COMMAND:-}" == "$command" ]] && exit 23
round=""
output=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --round) round=$2; shift 2 ;;
    --output-dir) output=$2; shift 2 ;;
    *) shift ;;
  esac
done
if [[ "$command" == login-round ]]; then
  mkdir -p "$output"
  printf '{"version":1,"batchId":"abc123","credentials":[]}' >"$output/credentials-$round.json"
  chmod 0600 "$output/credentials-$round.json"
fi
SH
cat >"$temp_dir/bin/resources" <<'SH'
#!/usr/bin/env bash
[[ "${FAKE_RESOURCE_FAIL:-0}" == 1 ]] && exit 1
if [[ "${FAKE_RESOURCE_PARTIAL_FAIL:-0}" == 1 ]]; then
  printf '{"capturedAt":"fixture","status":"ok"}\n' >"$2"
  exit 32
fi
trap 'exit 0' INT TERM
while :; do sleep 1; done
SH
chmod +x "$temp_dir/bin/docker" "$temp_dir/bin/k6" "$temp_dir/bin/mvn" \
  "$temp_dir/bin/nc" "$temp_dir/bin/curl" "$temp_dir/bin/local_up" \
  "$temp_dir/bin/data" "$temp_dir/bin/resources"

FAKE_NC_OPEN=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  K6_BIN="$temp_dir/bin/k6" HTTP_BIN="$temp_dir/bin/curl" PORT_PROBE_BIN="$temp_dir/bin/nc" \
  "$RUNNER" doctor >/dev/null
mkdir "$temp_dir/runtime/runner.lock"
printf '%s\n' "$$" >"$temp_dir/runtime/runner.lock/pid"
if FAKE_NC_OPEN=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
    K6_BIN="$temp_dir/bin/k6" HTTP_BIN="$temp_dir/bin/curl" PORT_PROBE_BIN="$temp_dir/bin/nc" \
    "$RUNNER" doctor >/dev/null 2>&1; then
  printf 'runner must reject an existing lock\n' >&2
  exit 1
fi
rm -rf "$temp_dir/runtime/runner.lock"
mkdir "$temp_dir/runtime/runner.lock"
printf '999999\n' >"$temp_dir/runtime/runner.lock/pid"
set +e
for contender in 1 2; do
  FAKE_NC_OPEN=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
    K6_BIN="$temp_dir/bin/k6" HTTP_BIN="$temp_dir/bin/curl" PORT_PROBE_BIN="$temp_dir/bin/nc" \
    "$RUNNER" doctor >"$temp_dir/stale-lock-$contender.stdout" \
      2>"$temp_dir/stale-lock-$contender.stderr" &
  stale_lock_pids[$contender]=$!
done
stale_lock_successes=0
for contender in 1 2; do
  wait "${stale_lock_pids[$contender]}"
  [[ $? -eq 0 ]] && stale_lock_successes=$((stale_lock_successes + 1))
done
set -e
[[ "$stale_lock_successes" == 1 ]] || {
  cat "$temp_dir/stale-lock-1.stderr" "$temp_dir/stale-lock-2.stderr" >&2
  printf 'exactly one concurrent stale-lock contender must enter, got %s\n' \
    "$stale_lock_successes" >&2
  exit 1
}

cat >"$temp_dir/bin/slow-docker" <<SH
#!/usr/bin/env bash
if [[ "\${1:-}" == info ]]; then sleep 2; exit 0; fi
exec "$temp_dir/bin/docker" "\$@"
SH
chmod +x "$temp_dir/bin/slow-docker"
if FAKE_NC_OPEN=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/slow-docker" \
    K6_BIN="$temp_dir/bin/k6" HTTP_BIN="$temp_dir/bin/curl" PORT_PROBE_BIN="$temp_dir/bin/nc" \
    LOAD_TEST_DOCTOR_TIMEOUT_SECONDS=1 \
    "$RUNNER" doctor >/dev/null 2>&1; then
  printf 'doctor timeout must propagate\n' >&2
  exit 1
fi
[[ ! -d "$temp_dir/runtime/runner.lock" ]] || {
  printf 'timeout must release runner lock\n' >&2
  exit 1
}

sleep 5.5 &
sample_pid=$!
sample_file="$temp_dir/resources.jsonl"
FAKE_DOCKER_STATS_DELAY=0.1 LOAD_TEST_RESOURCE_INTERVAL_SECONDS=1 \
  DOCKER_BIN="$temp_dir/bin/docker" \
  "$RESOURCE_SAMPLER" "$sample_pid" "$sample_file"
wait "$sample_pid" 2>/dev/null || true
ruby -rjson -e '
  path=ARGV.fetch(0)
  abort "resource sample mode must be 0600" unless (File.stat(path).mode & 0777) == 0600
  lines=File.readlines(path)
  abort "resource sampler did not emit enough data" if lines.length < 4
  samples=lines.map { |line| JSON.parse(line) }
  intervals=samples.each_cons(2).map { |left,right| right.fetch("timestamp")-left.fetch("timestamp") }
  sorted=intervals.sort
  middle=sorted.length / 2
  median=sorted.length.odd? ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0
  valid_intervals=intervals.all? { |interval| interval > 0.5 && interval <= 1.5 }
  valid_median=median > 0.75 && median <= 1.25
  abort "resource sampler cadence includes collection time: #{intervals.inspect}" unless
    valid_intervals && valid_median
' "$sample_file"

batch=abc123
mkdir -p "$temp_dir/runtime/$batch"
printf '{"version":1,"state":"APPLYING","batchId":null}\n' \
  >"$temp_dir/runtime/runtime-state.jsonl"
if FAKE_DOCKER_UP_FAIL=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" \
    DOCKER_BIN="$temp_dir/bin/docker" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
    HTTP_BIN="$temp_dir/bin/curl" LOAD_TEST_DISCOVERY_SETTLE_SECONDS=0 \
    K6_BIN="$temp_dir/bin/k6" "$RUNNER" restore >/dev/null 2>&1; then
  printf 'restore failure must propagate\n' >&2
  exit 1
fi
restore_failed_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$restore_failed_state" == RESTORING ]] || {
  printf 'restore failure must retain RESTORING\n' >&2
  exit 1
}
LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  LOCAL_UP_BIN="$temp_dir/bin/local_up" HTTP_BIN="$temp_dir/bin/curl" \
  LOAD_TEST_DISCOVERY_SETTLE_SECONDS=0 K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" restore >/dev/null

printf '{"version":1,"state":"LOAD_READY","batchId":"%s"}\n' "$batch" \
  >"$temp_dir/runtime/runtime-state.jsonl"
if FAKE_MAVEN_FAIL=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" \
    DOCKER_BIN="$temp_dir/bin/docker" MAVEN_BIN="$temp_dir/bin/mvn" \
    LOCAL_UP_BIN="$temp_dir/bin/local_up" PORT_PROBE_BIN="$temp_dir/bin/nc" \
    HTTP_BIN="$temp_dir/bin/curl" K6_BIN="$temp_dir/bin/k6" \
    "$RUNNER" cleanup --batch "$batch" >/dev/null 2>&1; then
  printf 'cleaner failure must propagate\n' >&2
  exit 1
fi
failed_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$failed_state" == FROZEN ]] || {
  printf 'cleaner failure must retain FROZEN, got %s\n' "$failed_state" >&2
  exit 1
}

LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  MAVEN_BIN="$temp_dir/bin/mvn" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
  PORT_PROBE_BIN="$temp_dir/bin/nc" HTTP_BIN="$temp_dir/bin/curl" K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" cleanup --resume "$batch" >/dev/null
restored_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$restored_state" == BASE_READY ]] || {
  printf 'cleanup resume must restore BASE_READY, got %s\n' "$restored_state" >&2
  exit 1
}

# restore may complete before the final sensitive-file unlink. A resume from BASE_READY
# may only finish that unlink when the digest-bound cleanup checkpoints are complete.
printf '{"version":1,"batchId":"%s"}\n' "$batch" >"$temp_dir/runtime/$batch/manifest.json"
manifest_digest="$(shasum -a 256 "$temp_dir/runtime/$batch/manifest.json" | awk '{print $1}')"
printf 'batch=%s\nmanifest=%s\naccount\ncache-account\ncache-message\ncache-social\nmessage\nreceipts\nsocial\n' \
  "$batch" "$manifest_digest" >"$temp_dir/runtime/$batch/manifest.json.cleanup-checkpoints"
printf '{"accessToken":"secret"}\n' >"$temp_dir/runtime/$batch/credentials.json"
printf '{"account":"secret"}\n' >"$temp_dir/runtime/$batch/database-targets.json"
FAKE_MAVEN_FAIL=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" \
  DOCKER_BIN="$temp_dir/bin/docker" MAVEN_BIN="$temp_dir/bin/mvn" \
  LOCAL_UP_BIN="$temp_dir/bin/local_up" PORT_PROBE_BIN="$temp_dir/bin/nc" \
  HTTP_BIN="$temp_dir/bin/curl" K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" cleanup --resume "$batch" >/dev/null
[[ ! -e "$temp_dir/runtime/$batch/credentials.json" \
  && ! -e "$temp_dir/runtime/$batch/database-targets.json" ]] || {
  printf 'BASE_READY cleanup resume must remove only verified sensitive remnants\n' >&2
  exit 1
}

printf '{"accessToken":"secret"}\n' >"$temp_dir/runtime/$batch/credentials.json"
printf 'batch=%s\nmanifest=%064d\naccount\ncache-account\ncache-message\ncache-social\nmessage\nreceipts\nsocial\n' \
  "$batch" 0 >"$temp_dir/runtime/$batch/manifest.json.cleanup-checkpoints"
if LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
    MAVEN_BIN="$temp_dir/bin/mvn" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
    PORT_PROBE_BIN="$temp_dir/bin/nc" HTTP_BIN="$temp_dir/bin/curl" K6_BIN="$temp_dir/bin/k6" \
    "$RUNNER" cleanup --resume "$batch" >/dev/null 2>&1; then
  printf 'BASE_READY cleanup resume must reject mismatched cleanup checkpoints\n' >&2
  exit 1
fi
[[ -f "$temp_dir/runtime/$batch/credentials.json" ]] || {
  printf 'Rejected BASE_READY cleanup resume deleted sensitive evidence\n' >&2
  exit 1
}
rm -f "$temp_dir/runtime/$batch/credentials.json"
rm -f "$temp_dir/runtime/$batch/manifest.json" \
  "$temp_dir/runtime/$batch/manifest.json.cleanup-checkpoints"

if LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
    K6_BIN="$temp_dir/bin/k6" "$RUNNER" cleanup --resume '../escape' >/dev/null 2>&1; then
  printf 'load-test runner must reject batch path traversal\n' >&2
  exit 1
fi

printf '{"version":1,"state":"LOAD_READY","batchId":"%s"}\n' "$batch" \
  >"$temp_dir/runtime/runtime-state.jsonl"
fake_data_log="$temp_dir/data.log"
: >"$fake_data_log"
FAKE_DOCKER_LOAD=1 FAKE_DATA_LOG="$fake_data_log" KEEP_LOAD_TEST_DATA=1 \
  LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  LOAD_TEST_DATA_BIN="$temp_dir/bin/data" LOAD_TEST_RESOURCE_SAMPLER_BIN="$temp_dir/bin/resources" \
  LOCAL_UP_BIN="$temp_dir/bin/local_up" K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" connections --batch "$batch" --levels 20 >"$temp_dir/signal.stdout" 2>"$temp_dir/signal.stderr" &
runner_pid=$!
signal_deadline=$((SECONDS + 15))
until [[ -f "$temp_dir/runtime/runtime-state.jsonl" ]] && \
    [[ "$(node -e 'const fs=require("fs"); const lines=fs.readFileSync(process.argv[1],"utf8").trim().split("\n"); process.stdout.write(JSON.parse(lines.at(-1)).state);' "$temp_dir/runtime/runtime-state.jsonl")" == RUNNING ]]; do
  ((SECONDS < signal_deadline)) || {
    cat "$temp_dir/signal.stderr" >&2
    cat "$temp_dir/signal.stdout" >&2
    cat "$fake_data_log" >&2
    printf 'runner did not enter RUNNING\n' >&2
    exit 1
  }
  sleep 0.05
done
kill -TERM "$runner_pid"
set +e
wait "$runner_pid"
signal_status=$?
set -e
[[ "$signal_status" == 143 ]] || { printf 'signal exit status must be 143, got %s\n' "$signal_status" >&2; exit 1; }
signal_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$signal_state" == LOAD_READY ]] || { printf 'KEEP signal must retain LOAD_READY\n' >&2; exit 1; }
rg -q '^logout-round ' "$fake_data_log" || { printf 'signal drain must execute logout-round\n' >&2; exit 1; }

printf '{"version":1,"state":"LOAD_READY","batchId":"%s"}\n' "$batch" \
  >"$temp_dir/runtime/runtime-state.jsonl"
set +e
FAKE_DOCKER_LOAD=1 FAKE_K6_EXIT=1 FAKE_K6_LOG="$temp_dir/k6.log" \
  FAKE_RESOURCE_FAIL=1 FAKE_DATA_LOG="$fake_data_log" \
  LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  LOAD_TEST_DATA_BIN="$temp_dir/bin/data" LOAD_TEST_RESOURCE_SAMPLER_BIN="$temp_dir/bin/resources" \
  LOCAL_UP_BIN="$temp_dir/bin/local_up" K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" messages --batch "$batch" --connections 20 --levels 5 \
  >/dev/null 2>"$temp_dir/message-rate.stderr"
message_rate_status=$?
set -e
((message_rate_status != 0)) || { printf 'missing message resource evidence must fail the round\n' >&2; exit 1; }
rg -q -- '-e TARGET_RATE=5' "$temp_dir/k6.log" || {
  printf 'message rate must not be rounded as a connection cohort\n' >&2
  exit 1
}
rg -q -- '-e EFFECTIVE_USERS=20' "$temp_dir/k6.log" || {
  printf 'message connection count must remain cohort aligned\n' >&2
  exit 1
}

printf '{"version":1,"state":"LOAD_READY","batchId":"%s"}\n' "$batch" \
  >"$temp_dir/runtime/runtime-state.jsonl"
set +e
FAKE_DOCKER_LOAD=1 FAKE_K6_EXIT=1 FAKE_RESOURCE_FAIL=1 FAKE_DATA_LOG="$fake_data_log" \
  LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  LOAD_TEST_DATA_BIN="$temp_dir/bin/data" LOAD_TEST_RESOURCE_SAMPLER_BIN="$temp_dir/bin/resources" \
  LOCAL_UP_BIN="$temp_dir/bin/local_up" K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" connections --batch "$batch" --levels 20 >/dev/null 2>"$temp_dir/resource-failure.stderr"
resource_failure_status=$?
set -e
((resource_failure_status != 0)) || { printf 'missing resource evidence must fail the round\n' >&2; exit 1; }

printf '{"version":1,"state":"LOAD_READY","batchId":"%s"}\n' "$batch" \
  >"$temp_dir/runtime/runtime-state.jsonl"
set +e
FAKE_DOCKER_LOAD=1 FAKE_K6_EXIT=1 FAKE_RESOURCE_PARTIAL_FAIL=1 FAKE_DATA_LOG="$fake_data_log" \
  LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  LOAD_TEST_DATA_BIN="$temp_dir/bin/data" LOAD_TEST_RESOURCE_SAMPLER_BIN="$temp_dir/bin/resources" \
  LOCAL_UP_BIN="$temp_dir/bin/local_up" K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" connections --batch "$batch" --levels 20 >/dev/null 2>"$temp_dir/resource-partial-failure.stderr"
resource_partial_failure_status=$?
set -e
((resource_partial_failure_status != 0)) || {
  printf 'resource sampler early exit must fail even when one valid sample exists\n' >&2
  exit 1
}

printf '{"version":1,"state":"LOAD_READY","batchId":"%s"}\n' "$batch" \
  >"$temp_dir/runtime/runtime-state.jsonl"
: >"$fake_data_log"
FAKE_DOCKER_LOAD=1 FAKE_MAVEN_FAIL=1 FAKE_DATA_LOG="$fake_data_log" \
  LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  MAVEN_BIN="$temp_dir/bin/mvn" LOAD_TEST_DATA_BIN="$temp_dir/bin/data" \
  LOAD_TEST_RESOURCE_SAMPLER_BIN="$temp_dir/bin/resources" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
  PORT_PROBE_BIN="$temp_dir/bin/nc" HTTP_BIN="$temp_dir/bin/curl" K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" connections --batch "$batch" --levels 20 >"$temp_dir/default-signal.stdout" 2>"$temp_dir/default-signal.stderr" &
runner_pid=$!
signal_deadline=$((SECONDS + 15))
until [[ "$(node -e 'const fs=require("fs"); const lines=fs.readFileSync(process.argv[1],"utf8").trim().split("\n"); process.stdout.write(JSON.parse(lines.at(-1)).state);' "$temp_dir/runtime/runtime-state.jsonl")" == RUNNING ]]; do
  ((SECONDS < signal_deadline)) || { cat "$temp_dir/default-signal.stderr" >&2; printf 'default signal runner did not enter RUNNING\n' >&2; exit 1; }
  sleep 0.05
done
kill -TERM "$runner_pid"
set +e
wait "$runner_pid"
default_signal_status=$?
set -e
[[ "$default_signal_status" == 143 ]] || { printf 'default signal must preserve 143\n' >&2; exit 1; }
default_signal_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$default_signal_state" == FROZEN ]] || { printf 'cleaner failure after signal must retain FROZEN\n' >&2; exit 1; }

printf '{"version":1,"state":"LOAD_READY"' >>"$temp_dir/runtime/runtime-state.jsonl"
LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  LOCAL_UP_BIN="$temp_dir/bin/local_up" HTTP_BIN="$temp_dir/bin/curl" \
  LOAD_TEST_DISCOVERY_SETTLE_SECONDS=0 K6_BIN="$temp_dir/bin/k6" \
  "$RUNNER" restore >/dev/null
tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e '
  abort "torn journal recovery must end BASE_READY" unless JSON.parse(STDIN.read).fetch("state") == "BASE_READY"
'

printf '{"version":1,"state":"RESTORING","batchId":null}\n' \
  >"$temp_dir/runtime/runtime-state.jsonl"
if FAKE_DOCKER_INSPECT_FAIL=1 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" \
    DOCKER_BIN="$temp_dir/bin/docker" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
    HTTP_BIN="$temp_dir/bin/curl" LOAD_TEST_DISCOVERY_SETTLE_SECONDS=0 \
    K6_BIN="$temp_dir/bin/k6" "$RUNNER" restore >/dev/null 2>&1; then
  printf 'base restore must fail when docker inspect fails\n' >&2
  exit 1
fi
inspect_failed_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$inspect_failed_state" == RESTORING ]] || {
  printf 'inspect failure must retain RESTORING, got %s\n' "$inspect_failed_state" >&2
  exit 1
}

printf '{"version":1,"state":"RESTORING","batchId":null}\n' \
  >"$temp_dir/runtime/runtime-state.jsonl"
printf 'unhealthy\n' >"$temp_dir/docker-health-state"
FAKE_DOCKER_DYNAMIC_HEALTH=1 FAKE_DOCKER_HEALTH_STATE="$temp_dir/docker-health-state" \
  LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" \
  DOCKER_BIN="$temp_dir/bin/docker" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
  HTTP_BIN="$temp_dir/bin/curl" LOAD_TEST_DISCOVERY_SETTLE_SECONDS=0 \
  K6_BIN="$temp_dir/bin/k6" "$RUNNER" restore >/dev/null
health_recovered_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$health_recovered_state" == BASE_READY ]] || {
  printf 'unhealthy fast-path fixture must use full restore and end BASE_READY\n' >&2
  exit 1
}

printf '{"version":1,"state":"RESTORING","batchId":null}\n' \
  >"$temp_dir/runtime/runtime-state.jsonl"
printf 'unhealthy\n' >"$temp_dir/docker-health-state"
rm -f "$temp_dir/docker-restart-marker"
FAKE_DOCKER_DYNAMIC_HEALTH=1 FAKE_DOCKER_HEALTH_STATE="$temp_dir/docker-health-state" \
  FAKE_DOCKER_RESTART_TRANSIENT_UNHEALTHY=1 \
  FAKE_DOCKER_RESTART_MARKER="$temp_dir/docker-restart-marker" \
  LOAD_TEST_MANAGEMENT_HEALTH_TIMEOUT_SECONDS=2 \
  LOAD_TEST_MANAGEMENT_RESTART_HEALTH_TIMEOUT_SECONDS=5 \
  LOAD_TEST_DISCOVERY_SETTLE_SECONDS=0 LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" \
  DOCKER_BIN="$temp_dir/bin/docker" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
  HTTP_BIN="$temp_dir/bin/curl" K6_BIN="$temp_dir/bin/k6" "$RUNNER" restore >/dev/null
transient_health_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$transient_health_state" == BASE_READY ]] || {
  printf 'post-restart transient unhealthy fixture must wait until BASE_READY\n' >&2
  exit 1
}

printf '{"version":1,"state":"BASE_READY","batchId":null}\n' \
  >"$temp_dir/runtime/runtime-state.jsonl"
printf 'base' >"$temp_dir/docker-state"
: >"$temp_dir/docker-command.log"
set +e
FAKE_DOCKER_DYNAMIC=1 FAKE_DOCKER_STATE="$temp_dir/docker-state" \
  FAKE_DOCKER_COMMAND_LOG="$temp_dir/docker-command.log" \
  FAKE_DATA_FAIL_COMMAND=prepare FAKE_DATA_LOG="$fake_data_log" \
  LOAD_TEST_RUNTIME_ROOT="$temp_dir/runtime" DOCKER_BIN="$temp_dir/bin/docker" \
  LOAD_TEST_DATA_BIN="$temp_dir/bin/data" LOCAL_UP_BIN="$temp_dir/bin/local_up" \
  HTTP_BIN="$temp_dir/bin/curl" PORT_PROBE_BIN="$temp_dir/bin/nc" \
  K6_BIN="$temp_dir/bin/k6" "$RUNNER" all --batch "$batch" --users 20 --group-size 10 \
  >/dev/null 2>"$temp_dir/all-failure.stderr"
all_status=$?
set -e
[[ "$all_status" == 23 ]] || { printf 'all must preserve prepare failure 23, got %s\n' "$all_status" >&2; exit 1; }
rg -q 'compose.load-test.yml.* stop im-iam-server im-audit-server im-admin im-ops' \
  "$temp_dir/docker-command.log" || {
  printf 'load profile must stop non-core management services\n' >&2
  exit 1
}
all_state="$(tail -1 "$temp_dir/runtime/runtime-state.jsonl" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("state")')"
[[ "$all_state" == BASE_READY ]] || { printf 'all prepare failure must restore BASE_READY\n' >&2; exit 1; }
[[ ! -f "$temp_dir/runtime/$batch/database-targets.json" ]] || {
  printf 'preflight prepare failure must remove the unused database target file\n' >&2
  exit 1
}
