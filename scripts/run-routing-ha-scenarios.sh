#!/usr/bin/env bash
# 用途：运行 3 Broker + 2 Gateway 容器拓扑及独立三 Broker mTLS/JRaft 测试，映射 25 类场景证据。
# 证据边界：RH-05 来自隔离 Java 网络运行时；Gateway 重建以应用 ACK 输入替代，不冒充容器自动摘流。
# 输入：ROUTING_HA_STATE_DIR 由 routing_ha.sh 注入；可用 ROUTING_HA_PROJECT_NAME 覆盖 Compose project，
#      ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS 配置 Gateway/Broker 重启后的就绪等待秒数。
# 输出/副作用：写 scenarios.jsonl、topology.json 与 diagnostics/；创建的 Compose 资源全登记并在 EXIT trap 清理。
# 隔离：只操作 deploy/local/compose.routing-ha.yml 和本次唯一 project，不复用普通 local/full 环境。
# 退出码：构建、测试、拓扑、故障恢复、证据映射或清理任一步失败均非零。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
STATE_DIR="${ROUTING_HA_STATE_DIR:?ROUTING_HA_STATE_DIR is required}"
PROJECT_NAME="${ROUTING_HA_PROJECT_NAME:-im-chat-routing-ha-$$}"
COMPOSE_FILE="$ROOT_DIR/deploy/local/compose.routing-ha.yml"
MANIFEST="$ROOT_DIR/im-test/im-routing-ha/src/test/resources/routing-ha-scenarios.tsv"
RESOURCE_LEDGER="$STATE_DIR/resources.jsonl"
SCENARIO_EVIDENCE="$STATE_DIR/scenarios.jsonl"
DIAGNOSTIC_DIR="$STATE_DIR/diagnostics"
STARTED_EPOCH="$(ruby -e 'puts Time.now.to_f')"
REACTOR_EVIDENCE="${ROUTING_HA_REACTOR_EVIDENCE:-}"
EVIDENCE_HELPER="$ROOT_DIR/scripts/lib/harness-reactor-evidence.rb"
RESTART_READY_TIMEOUT_SECONDS="${ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS:-180}"
COMPOSE=(docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE")

if [[ ! "$RESTART_READY_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS must be a positive integer.\n' >&2
  exit 2
fi

mkdir -p "$STATE_DIR" "$DIAGNOSTIC_DIR"
: > "$RESOURCE_LEDGER"
: > "$SCENARIO_EVIDENCE"
PROCESS_HELPER="$ROOT_DIR/scripts/lib/routing-ha-process.rb"
RESOURCE_ATTEMPTED=0
run_step() {
  local name="$1" timeout="$2"
  shift 2
  ruby "$PROCESS_HELPER" run "$name" "$timeout" "$@"
}

cleanup() {
  local original_status=$?
  trap - EXIT
  trap '' INT TERM
  local cleanup_status=0
  if (( RESOURCE_ATTEMPTED )); then
    ROUTING_HA_STEP_OUTPUT="$DIAGNOSTIC_DIR/compose.log" run_step cleanup-logs "${ROUTING_HA_LOGS_TIMEOUT_SECONDS:-5}" "${COMPOSE[@]}" logs --no-color \
      || true
    ROUTING_HA_STEP_OUTPUT="$DIAGNOSTIC_DIR/cleanup.log" run_step cleanup-down "${ROUTING_HA_DOWN_TIMEOUT_SECONDS:-30}" "${COMPOSE[@]}" down --volumes --remove-orphans \
      || cleanup_status=$?
    if (( cleanup_status == 0 )); then
      : > "$RESOURCE_LEDGER"
    fi
  else
    printf 'No resource creation attempted; skipping Compose cleanup.\n'
  fi
  if (( original_status != 0 )); then
    exit "$original_status"
  fi
  exit "$cleanup_status"
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

# 参数：URL、最大探测次数（默认 90）；保留每次 2 秒网络上限和 1 秒重试间隔的原预算。
# 使用 Ruby 有界等待，避免 Bash sleep 轮询；HTTP 非 2xx、连接错误和耗尽次数均不算就绪。
poll_http() {
  run_step readiness "" ruby -rnet/http -ruri -e '
    uri = URI(ARGV.fetch(0))
    attempts = Integer(ARGV.fetch(1))
    attempts.times do
      begin
        Timeout.timeout(2) do
          http = Net::HTTP.new(uri.host, uri.port)
          http.open_timeout = 2
          http.read_timeout = 2
          exit 0 if http.get(uri.request_uri).is_a?(Net::HTTPSuccess)
        end
      rescue IOError, SystemCallError, Timeout::Error
      end
      IO.select(nil, nil, nil, 1)
    end
    abort "timed out waiting for #{uri}"
  ' "$1" "${2:-90}"
}

assert_cluster_state_consistent() {
  run_step cluster-consistency "" ruby -rjson -rnet/http -ruri -e '
    output = ARGV.shift
    states = ARGV.map do |url|
      response = JSON.parse(Net::HTTP.get(URI(url)))
      response.fetch("data", response)
    end
    projection = states.map { |state|
      [state.fetch("epoch"), state.fetch("revision"),
       state.fetch("algorithm"), state.fetch("brokers")]
    }
    abort "Broker cluster-state projections differ" unless projection.uniq.size == 1
    brokers = states.first.fetch("brokers")
    abort "expected three Broker nodes" unless brokers.size == 3
    abort "expected three ACTIVE voters" unless brokers.all? { |node|
      node.fetch("membershipRole") == "VOTER" && node.fetch("routingState") == "ACTIVE"
    }
    File.write(output, JSON.generate({
      "epoch" => states.first.fetch("epoch"),
      "revision" => states.first.fetch("revision"),
      "algorithm" => states.first.fetch("algorithm"),
      "brokers" => brokers.map { |node| node.fetch("brokerId") }.sort
    }) + "\n")
  ' "$STATE_DIR/topology.json" \
    http://127.0.0.1:19121/management/cluster-state \
    http://127.0.0.1:19122/management/cluster-state \
    http://127.0.0.1:19123/management/cluster-state
}

run_focused_scenarios() {
  # 独立 mTLS/JRaft 层需要 Broker 生产依赖；reactor 仅执行这两个测试类，不重复全部上游测试。
  run_step focused-routing-ha "" mvn -q -pl im-test/im-routing-ha -am \
    -Dtest=RoutingHaScenarioManifestTest,AutomaticFailureTlsHaTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  run_step focused-broker "" mvn -q -pl im-broker/im-broker-server \
    -Dtest=BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerRaftAdapterTest,RouteBrokerExecutionInterceptorTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceTest,BrokerClusterAppServiceChangeTest,LocalRouteAppServiceReceiveTest,IdentityAccessInterceptorsTest,BrokerClusterTest,BrokerClusterAppServiceFenceTest,LocalRoutingLeaseServiceTest test
  run_step focused-gateway "" mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server \
    -Dtest=FrameDeliveryDeduplicatorTest,ConnectionCloseDeduplicatorTest,ConnectionRegistryTest,GatewayRouteSnapshotCoordinatorTest,GatewayBrokerAuthorizationTest test
  run_step focused-message "" mvn -q -pl im-service/im-message/im-message-server \
    -Dtest=ImMessageNotifierInvokerTest,RedisImMessageConfirmableStoreTest test
  run_step focused-e2e "" mvn -q -pl im-test/im-e2e-test clean -Dtest=RealtimeMessagingE2ETest test
}

probe_gateway_restart() {
  run_step compose-stop "" "${COMPOSE[@]}" stop -t 10 gateway-ha-1
  poll_http http://127.0.0.1:19211/actuator/health 15
  run_step compose-start "" "${COMPOSE[@]}" start gateway-ha-1
  poll_http http://127.0.0.1:19111/actuator/health "$RESTART_READY_TIMEOUT_SECONDS"
}

probe_each_broker_restart() {
  local service
  local port
  for service in broker-ha-1 broker-ha-2 broker-ha-3; do
    run_step compose-stop "" "${COMPOSE[@]}" stop -t 10 "$service"
    for port in 19121 19122 19123; do
      case "$service:$port" in
        broker-ha-1:19121|broker-ha-2:19122|broker-ha-3:19123) continue ;;
      esac
      poll_http "http://127.0.0.1:$port/actuator/health" 30
    done
    run_step compose-start "" "${COMPOSE[@]}" start "$service"
    case "$service" in
      broker-ha-1) port=19121 ;;
      broker-ha-2) port=19122 ;;
      broker-ha-3) port=19123 ;;
    esac
    poll_http "http://127.0.0.1:$port/actuator/health" "$RESTART_READY_TIMEOUT_SECONDS"
    assert_cluster_state_consistent
  done
}

write_scenario_evidence() {
  if [[ -n "$REACTOR_EVIDENCE" ]]; then
    [[ "$(ruby -rdigest -e 'puts Digest::SHA256.file(ARGV[0]).hexdigest' "$REACTOR_EVIDENCE")" == "$expected_sha256" ]] || return 1
    run_step evidence-mapping "" ruby "$EVIDENCE_HELPER" ha-evidence "$ROOT_DIR" "$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("startedAt")' "$REACTOR_EVIDENCE")" "$SCENARIO_EVIDENCE" "$STATE_DIR/topology.json" "$REACTOR_EVIDENCE"
  else
    run_step evidence-mapping "" ruby "$EVIDENCE_HELPER" ha-standalone-evidence "$ROOT_DIR" "$STARTED_EPOCH" "$SCENARIO_EVIDENCE" "$STATE_DIR/topology.json"
  fi
}

cd "$ROOT_DIR"
# 同轮证明必须先于任何 Engine/Compose 调用；独立入口仍构建并执行聚焦测试。
if [[ -n "$REACTOR_EVIDENCE" ]]; then
  manifest_sha256="$(ruby -rdigest -e 'puts Digest::SHA256.file(ARGV[0]).hexdigest' "$REACTOR_EVIDENCE")"
  expected_sha256="${ROUTING_HA_REACTOR_SHA256:-$manifest_sha256}"
  [[ "$expected_sha256" =~ ^[0-9a-f]{64}$ && "$manifest_sha256" == "$expected_sha256" ]] || exit 1
  run_step reactor-check "" ruby "$EVIDENCE_HELPER" check "$ROOT_DIR" "$REACTOR_EVIDENCE"
fi
run_step engine-info "${ROUTING_HA_ENGINE_TIMEOUT_SECONDS:-8}" docker info
run_step compose-config "" docker compose -f "$COMPOSE_FILE" config --quiet
if [[ -z "$REACTOR_EVIDENCE" ]]; then
run_step package "" mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server \
  -am -DskipTests package
run_focused_scenarios
run_step standalone-check "" ruby "$EVIDENCE_HELPER" ha-check "$ROOT_DIR" "$STARTED_EPOCH"
fi
RESOURCE_ATTEMPTED=1
printf '{"kind":"compose-project","id":"%s"}\n' "$PROJECT_NAME" > "$RESOURCE_LEDGER"
run_step compose-up "" "${COMPOSE[@]}" up --detach --build --wait
poll_http http://127.0.0.1:19121/actuator/health
poll_http http://127.0.0.1:19122/actuator/health
poll_http http://127.0.0.1:19123/actuator/health
poll_http http://127.0.0.1:19111/actuator/health
poll_http http://127.0.0.1:19211/actuator/health
assert_cluster_state_consistent
probe_gateway_restart
probe_each_broker_restart
write_scenario_evidence

printf 'routing-ha executed 25 mapped scenarios on project %s.\n' "$PROJECT_NAME"
