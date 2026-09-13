#!/usr/bin/env bash
# 用途：启动本地 IM Chat 基础设施，或构建并启动完整应用拓扑。
# 输入：可选 `infra`/`full`，默认 `infra`；不接受其他参数。
# 输出/副作用：full 模式先清理并构建全部 Maven jar，再由 Docker Compose 创建/更新容器、网络和命名卷；等待服务健康后返回。
# 依赖：Maven、Docker Compose、Ruby、curl、nc。
# 退出码：构建、Compose 启动或状态查询任一步失败时返回非 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/local/compose.yml"
DOCKER_BIN="${DOCKER_BIN:-docker}"
MAVEN_BIN="${MAVEN_BIN:-mvn}"
PORT_PROBE_BIN="${PORT_PROBE_BIN:-nc}"
HTTP_BIN="${HTTP_BIN:-curl}"
profile="${1:-infra}"
wait_timeout="${LOCAL_WAIT_TIMEOUT_SECONDS:-600}"
switch_timeout="${LOCAL_SWITCH_TIMEOUT_SECONDS:-30}"

app_services=(
  im-http-gateway im-ws-gateway-server im-broker-server im-account-server
  im-social-server im-message-server im-iam-server im-audit-server im-admin im-monitor
)
infra_ports=(3306 6379 8848 9090 9092 9848 9849 1234 3000 11800 12800 17128 17913 18048 18050)
application_ports=(18010 18011 18012 18020 18030 18031 18032 18040 18041 18042 18043
                   19010 19011 19020 19030 19031 19032 19040 19041 19042 19043)
infra_services=(mysql redis nacos kafka prometheus-infra grafana skywalking-banyandb skywalking-oap skywalking-ui)
nacos_application_services=(
  'IM_CHAT_GROUP|im-http-gateway'
  'IM_CHAT_GROUP|im-ws-gateway'
  'IM_CHAT_GROUP|im-broker'
  'IM_CHAT_GROUP|im-account'
  'IM_CHAT_GROUP|im-social'
  'IM_CHAT_GROUP|im-message'
  'IM_CHAT_GROUP|im-iam'
  'IM_CHAT_GROUP|im-audit'
  'IM_CHAT_GROUP|im-admin'
  'IM_CHAT_GROUP|im-monitor'
  'DUBBO_GROUP|im-account'
  'DUBBO_GROUP|im-social'
  'DUBBO_GROUP|im-message'
  'DUBBO_GROUP|providers:com.co.kc.imchat.service.account.facade.AccountService:1.0.0:'
  'DUBBO_GROUP|providers:com.co.kc.imchat.service.account.admin.facade.AccountAdminService:1.0.0:'
  'DUBBO_GROUP|providers:com.co.kc.imchat.service.social.facade.SocialService:1.0.0:'
  'DUBBO_GROUP|providers:com.co.kc.imchat.service.message.facade.MessageService:1.0.0:'
  'DUBBO_GROUP|providers:com.co.kc.imchat.service.message.facade.ChatService:1.0.0:'
)

if [[ $# -gt 1 || ("$profile" != "infra" && "$profile" != "full") \
    || ! "$wait_timeout" =~ ^[1-9][0-9]*$ || ! "$switch_timeout" =~ ^[0-9]+$ ]]; then
  printf 'Usage: %s [infra|full]\n' "$0" >&2
  exit 2
fi

cd "$ROOT_DIR"

application_ports_released() {
  local port
  for port in "${application_ports[@]}"; do
    if "$PORT_PROBE_BIN" -z 127.0.0.1 "$port" >/dev/null 2>&1; then
      return 1
    fi
  done
}

nacos_application_instances_released() {
  local deadline="$1"
  local running_services descriptor group service response instance_count
  if ! running_services="$("$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile infra --profile full \
      ps --services --status running)"; then
    return 1
  fi
  if ! printf '%s\n' "$running_services" | grep -qx nacos; then
    return 0
  fi
  for descriptor in "${nacos_application_services[@]}"; do
    if (( SECONDS > deadline )); then
      return 1
    fi
    group="${descriptor%%|*}"
    service="${descriptor#*|}"
    if ! response="$("$HTTP_BIN" -fsS --get \
        --connect-timeout 1 --max-time 1 \
        'http://127.0.0.1:8848/nacos/v1/ns/instance/list' \
        --data-urlencode 'namespaceId=public' \
        --data-urlencode "groupName=$group" \
        --data-urlencode "serviceName=$service")"; then
      return 1
    fi
    if ! instance_count="$(printf '%s' "$response" | ruby -rjson -e '
        payload = JSON.parse(STDIN.read)
        puts payload.fetch("hosts", []).count { |host| host.fetch("enabled", true) }
      ')"; then
      return 1
    fi
    if (( instance_count > 0 )); then
      return 1
    fi
  done
}

wait_for_application_shutdown() {
  local deadline=$((SECONDS + switch_timeout))
  while true; do
    if application_ports_released && nacos_application_instances_released "$deadline"; then
      return
    fi
    if (( SECONDS >= deadline )); then
      printf 'Infra switch timed out after %ss waiting for application ports and Nacos/Dubbo instances to disappear.\n' \
        "$switch_timeout" >&2
      return 1
    fi
    sleep 1
  done
}

assert_running_service_set() {
  local expected_services=("${infra_services[@]}")
  if [[ "$profile" == "full" ]]; then
    expected_services=(
      mysql redis nacos kafka prometheus-full grafana
      skywalking-banyandb skywalking-oap skywalking-ui
      "${app_services[@]}"
    )
  fi
  "$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile "$profile" \
    ps --services --status running | ruby -e '
      actual = STDIN.each_line.map(&:strip).reject(&:empty?).sort
      expected = ARGV.sort
      abort "Unexpected running service set: expected=#{expected.join(",")}, actual=#{actual.join(",")}" unless
        actual == expected
    ' "${expected_services[@]}"
}

if [[ "$profile" == "infra" ]]; then
  "$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile infra --profile full \
    rm -s -f prometheus-full "${app_services[@]}"
  wait_for_application_shutdown
else
  "$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile infra --profile full \
    rm -s -f prometheus-infra
fi

owned_ports="$({
  "$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile infra --profile full \
    ps --format json
} | ruby -rjson -e '
  raw = STDIN.read.strip
  records = if raw.empty?
              []
            elsif raw.start_with?("[")
              JSON.parse(raw)
            else
              raw.lines.map { |line| JSON.parse(line) }
            end
  records.flat_map { |record| record.fetch("Publishers", []) }
         .map { |publisher| publisher.fetch("PublishedPort", 0).to_i }
         .select(&:positive?)
         .uniq
         .sort
         .each { |port| puts port }
')"

ports=("${infra_ports[@]}")
if [[ "$profile" == "full" ]]; then
  ports+=("${application_ports[@]}")
fi
for port in "${ports[@]}"; do
  if printf '%s\n' "$owned_ports" | grep -qx "$port"; then
    continue
  fi
  if "$PORT_PROBE_BIN" -z 127.0.0.1 "$port" >/dev/null 2>&1; then
    printf 'Local %s startup cannot bind 127.0.0.1:%s; the port is owned outside this Compose project.\n' \
      "$profile" "$port" >&2
    exit 1
  fi
done

if [[ "$profile" == "full" ]]; then
  "$MAVEN_BIN" -q -DskipTests clean package
  "$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile full up -d --build --remove-orphans \
    --wait --wait-timeout "$wait_timeout"
else
  "$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile infra up -d --remove-orphans \
    --wait --wait-timeout "$wait_timeout"
fi

assert_running_service_set
"$DOCKER_BIN" compose -f "$COMPOSE_FILE" --profile "$profile" ps
