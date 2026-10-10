#!/usr/bin/env bash
# 用途：构建并运行 3 Broker + 2 Gateway 拓扑，再把 25 类场景绑定到真实拓扑探针或 JUnit 断言。
# 输入：ROUTING_HA_STATE_DIR 由 routing_ha.sh 注入；可用 ROUTING_HA_PROJECT_NAME 覆盖 Compose project，
#      ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS 配置 Gateway/Broker 重启后的就绪等待秒数。
# 输出/副作用：写 scenarios.jsonl、topology.json 与 diagnostics/；创建的 Compose 资源全登记并在 EXIT trap 清理。
# 隔离：只操作 deploy/local/compose.routing-ha.yml 和本次唯一 project，不复用普通 local/full 环境。
# 退出码：构建、测试、拓扑、故障恢复、证据映射或清理任一步失败均非零。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${ROUTING_HA_STATE_DIR:?ROUTING_HA_STATE_DIR is required}"
PROJECT_NAME="${ROUTING_HA_PROJECT_NAME:-im-chat-routing-ha-$$}"
COMPOSE_FILE="$ROOT_DIR/deploy/local/compose.routing-ha.yml"
MANIFEST="$ROOT_DIR/im-test/im-routing-ha/src/test/resources/routing-ha-scenarios.tsv"
RESOURCE_LEDGER="$STATE_DIR/resources.jsonl"
SCENARIO_EVIDENCE="$STATE_DIR/scenarios.jsonl"
DIAGNOSTIC_DIR="$STATE_DIR/diagnostics"
STARTED_EPOCH="$(date +%s)"
RESTART_READY_TIMEOUT_SECONDS="${ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS:-180}"
COMPOSE=(docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE")

if [[ ! "$RESTART_READY_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS must be a positive integer.\n' >&2
  exit 2
fi

mkdir -p "$STATE_DIR" "$DIAGNOSTIC_DIR"
: > "$RESOURCE_LEDGER"
: > "$SCENARIO_EVIDENCE"
printf '{"kind":"compose-project","id":"%s"}\n' "$PROJECT_NAME" > "$RESOURCE_LEDGER"

cleanup() {
  local original_status=$?
  local cleanup_status=0
  "${COMPOSE[@]}" logs --no-color > "$DIAGNOSTIC_DIR/compose.log" 2>&1 || true
  "${COMPOSE[@]}" down --volumes --remove-orphans > "$DIAGNOSTIC_DIR/cleanup.log" 2>&1 \
    || cleanup_status=$?
  if (( cleanup_status == 0 )); then
    : > "$RESOURCE_LEDGER"
  fi
  if (( original_status != 0 )); then
    exit "$original_status"
  fi
  exit "$cleanup_status"
}
trap cleanup EXIT INT TERM

poll_http() {
  local url="$1"
  local attempts="${2:-90}"
  local index
  for ((index = 1; index <= attempts; index++)); do
    if curl --fail --silent --show-error --max-time 2 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  printf 'timed out waiting for %s\n' "$url" >&2
  return 1
}

assert_cluster_state_consistent() {
  ruby -rjson -rnet/http -ruri -e '
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
  mvn -q -pl im-test/im-routing-ha test
  mvn -q -pl im-broker/im-broker-server \
    -Dtest=BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterAdapterTest,RouteBrokerExecutionInterceptorTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceTest,BrokerClusterAppServiceChangeTest,LocalRouteAppServiceReceiveTest,IdentityAccessInterceptorsTest,BrokerClusterTest,BrokerClusterAppServiceFenceTest,LocalRoutingLeaseServiceTest test
  mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server \
    -Dtest=FrameDeliveryDeduplicatorTest,ConnectionCloseDeduplicatorTest,ConnectionRegistryTest,GatewayRouteSnapshotCoordinatorTest,GatewayBrokerAuthorizationTest test
  mvn -q -pl im-service/im-message/im-message-server \
    -Dtest=ImMessageNotifierInvokerTest,RedisImMessageConfirmableStoreTest test
  mvn -q -pl im-test/im-e2e-test clean -Dtest=RealtimeMessagingE2ETest test
}

probe_gateway_restart() {
  "${COMPOSE[@]}" stop -t 10 gateway-ha-1
  poll_http http://127.0.0.1:19211/actuator/health 15
  "${COMPOSE[@]}" start gateway-ha-1
  poll_http http://127.0.0.1:19111/actuator/health "$RESTART_READY_TIMEOUT_SECONDS"
}

probe_each_broker_restart() {
  local service
  local port
  for service in broker-ha-1 broker-ha-2 broker-ha-3; do
    "${COMPOSE[@]}" stop -t 10 "$service"
    for port in 19121 19122 19123; do
      case "$service:$port" in
        broker-ha-1:19121|broker-ha-2:19122|broker-ha-3:19123) continue ;;
      esac
      poll_http "http://127.0.0.1:$port/actuator/health" 30
    done
    "${COMPOSE[@]}" start "$service"
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
  ruby -rjson -rrexml/document -rtime -e '
    manifest, output, topology, started_epoch, root = ARGV
    started_epoch = started_epoch.to_i
    testcases = []
    Dir.glob(File.join(root, "**/target/surefire-reports/TEST-*.xml")).each do |file|
      next if File.mtime(file).to_i < started_epoch
      suite = REXML::Document.new(File.read(file)).root
      suite.elements.each("testcase") do |testcase|
        failed = testcase.elements["failure"] || testcase.elements["error"]
        testcases << {
          "class" => testcase.attributes["classname"].to_s.split(".").last,
          "method" => testcase.attributes["name"].to_s,
          "durationMillis" => (testcase.attributes["time"].to_f * 1000).round,
          "passed" => failed.nil?
        }
      end
    end
    topology_evidence = {
      "cluster-state-consistent" => topology,
      "gateway-stop-start" => File.join(File.dirname(topology), "diagnostics", "compose.log")
    }
    records = File.readlines(manifest, chomp: true).each_with_object([]) do |line, result|
      next if line.empty? || line.start_with?("#")
      id, name, kind, selector, assertion = line.split("\t", -1)
      evidence_path = nil
      duration = 0
      if kind == "topology"
        evidence_path = topology_evidence.fetch(selector)
      else
        klass, method = selector.split("#", 2)
        matches = testcases.select { |testcase|
          testcase["class"] == klass && (method.nil? || testcase["method"] == method)
        }
        abort "missing fresh test evidence for #{id}: #{selector}" if matches.empty?
        abort "failed test evidence for #{id}: #{selector}" unless matches.all? { |item| item["passed"] }
        evidence_path = selector
        duration = matches.sum { |item| item["durationMillis"] }
      end
      result << {
        "id" => id,
        "name" => name,
        "status" => "passed",
        "evidenceKind" => kind,
        "evidence" => evidence_path,
        "assertion" => assertion,
        "durationMillis" => duration,
        "finishedAt" => Time.now.utc.iso8601
      }
    end
    abort "expected exactly 25 scenario records" unless records.size == 25
    File.write(output, records.map { |record| JSON.generate(record) }.join("\n") + "\n")
  ' "$MANIFEST" "$SCENARIO_EVIDENCE" "$STATE_DIR/topology.json" \
    "$STARTED_EPOCH" "$ROOT_DIR"
}

cd "$ROOT_DIR"
docker compose -f "$COMPOSE_FILE" config --quiet
mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server \
  -am -DskipTests package
run_focused_scenarios
"${COMPOSE[@]}" up --detach --build --wait
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
