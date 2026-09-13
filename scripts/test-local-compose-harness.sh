#!/usr/bin/env bash
# 用途：验证本地 infra/full Compose、初始化文件和启停脚本契约。
# 输入：无位置参数；通过临时 fake docker/mvn 验证脚本调用，不启动真实容器。
# 输出/副作用：读取 deploy/local，创建并清理临时命令目录，不修改业务源码或 Docker 状态。
# 依赖：bash、docker compose、Ruby、rg、mktemp。
# 退出码：配置和脚本契约全部满足返回 0，否则返回非 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/local/compose.yml"

required_files=(
  "$COMPOSE_FILE"
  "$ROOT_DIR/deploy/local/app.Dockerfile"
  "$ROOT_DIR/deploy/local/app.Dockerfile.dockerignore"
  "$ROOT_DIR/deploy/local/README.md"
  "$ROOT_DIR/deploy/local/mysql/init/00-initialize.sql"
  "$ROOT_DIR/deploy/local/mysql/init/ddl/01-account.sql"
  "$ROOT_DIR/deploy/local/mysql/init/ddl/02-social.sql"
  "$ROOT_DIR/deploy/local/mysql/init/ddl/03-message.sql"
  "$ROOT_DIR/deploy/local/mysql/init/ddl/04-iam.sql"
  "$ROOT_DIR/deploy/local/mysql/init/ddl/05-audit.sql"
  "$ROOT_DIR/deploy/local/mysql/init/dml/06-iam-seed.sql"
  "$ROOT_DIR/im-management/im-iam/im-iam-server/sql/dml.sql"
  "$ROOT_DIR/deploy/local/prometheus/prometheus-infra.yml"
  "$ROOT_DIR/deploy/local/prometheus/prometheus-full.yml"
  "$ROOT_DIR/deploy/local/grafana/provisioning/datasources/prometheus.yml"
  "$ROOT_DIR/deploy/local/grafana/provisioning/dashboards/dashboards.yml"
  "$ROOT_DIR/deploy/local/grafana/provisioning/dashboards/im-chat-overview.json"
  "$ROOT_DIR/scripts/local_up.sh"
  "$ROOT_DIR/scripts/local_down.sh"
)
for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    printf 'Missing local runtime file: %s\n' "$file" >&2
    exit 1
  fi
done

application_poms=(
  "$ROOT_DIR/im-gateway/im-http-gateway/pom.xml"
  "$ROOT_DIR/im-gateway/im-ws-gateway/im-ws-gateway-server/pom.xml"
  "$ROOT_DIR/im-broker/im-broker-server/pom.xml"
  "$ROOT_DIR/im-service/im-account/im-account-server/pom.xml"
  "$ROOT_DIR/im-service/im-social/im-social-server/pom.xml"
  "$ROOT_DIR/im-service/im-message/im-message-server/pom.xml"
  "$ROOT_DIR/im-management/im-iam/im-iam-server/pom.xml"
  "$ROOT_DIR/im-management/im-audit/im-audit-server/pom.xml"
  "$ROOT_DIR/im-management/im-admin/pom.xml"
  "$ROOT_DIR/im-management/im-monitor/pom.xml"
)
for pom in "${application_poms[@]}"; do
  if ! rg -q '<artifactId>spring-boot-maven-plugin</artifactId>' "$pom"; then
    printf 'Application POM must build an executable Spring Boot jar: %s\n' "$pom" >&2
    exit 1
  fi
  if ! rg -q '<classifier>exec</classifier>' "$pom"; then
    printf 'Application POM must keep the thin jar and attach an exec jar: %s\n' "$pom" >&2
    exit 1
  fi
done

ddl_snapshots=(
  "$ROOT_DIR/im-service/im-account/im-account-server/sql/ddl.sql|$ROOT_DIR/deploy/local/mysql/init/ddl/01-account.sql"
  "$ROOT_DIR/im-service/im-social/im-social-server/sql/ddl.sql|$ROOT_DIR/deploy/local/mysql/init/ddl/02-social.sql"
  "$ROOT_DIR/im-service/im-message/im-message-server/sql/ddl.sql|$ROOT_DIR/deploy/local/mysql/init/ddl/03-message.sql"
  "$ROOT_DIR/im-management/im-iam/im-iam-server/sql/ddl.sql|$ROOT_DIR/deploy/local/mysql/init/ddl/04-iam.sql"
  "$ROOT_DIR/im-management/im-audit/im-audit-server/sql/ddl.sql|$ROOT_DIR/deploy/local/mysql/init/ddl/05-audit.sql"
  "$ROOT_DIR/im-management/im-iam/im-iam-server/sql/dml.sql|$ROOT_DIR/deploy/local/mysql/init/dml/06-iam-seed.sql"
)
for pair in "${ddl_snapshots[@]}"; do
  source_ddl="${pair%%|*}"
  snapshot="${pair#*|}"
  if ! cmp -s "$source_ddl" "$snapshot"; then
    printf 'MySQL init snapshot differs from owning DDL: %s\n' "$snapshot" >&2
    exit 1
  fi
done

for ddl in \
  "$ROOT_DIR/im-service/im-account/im-account-server/sql/ddl.sql" \
  "$ROOT_DIR/im-service/im-social/im-social-server/sql/ddl.sql" \
  "$ROOT_DIR/im-service/im-message/im-message-server/sql/ddl.sql" \
  "$ROOT_DIR/im-management/im-iam/im-iam-server/sql/ddl.sql" \
  "$ROOT_DIR/im-management/im-audit/im-audit-server/sql/ddl.sql"; do
  if rg -qi '\bDROP\s+TABLE\b' "$ddl"; then
    printf 'Owned DDL must not drop existing tables: %s\n' "$ddl" >&2
    exit 1
  fi
done

initializer="$ROOT_DIR/deploy/local/mysql/init/00-initialize.sql"
ruby -e '
  expected = %w[
    ddl/01-account.sql ddl/02-social.sql ddl/03-message.sql
    ddl/04-iam.sql ddl/05-audit.sql dml/06-iam-seed.sql
  ].map { |path| "SOURCE /docker-entrypoint-initdb.d/#{path};" }
  actual = File.readlines(ARGV.fetch(0), chomp: true).grep(/^SOURCE /)
  abort "MySQL init dispatcher must execute all DDL before DML" unless actual == expected
' "$initializer"

iam_seed="$ROOT_DIR/im-management/im-iam/im-iam-server/sql/dml.sql"
for seed_value in imIam imAdmin imAudit imMonitor \
  im-admin-client im-audit-client im-monitor-client \
  im-admin-catalog im-audit-catalog im-monitor-catalog \
  localhost:18041 localhost:18042 localhost:18043; do
  if ! rg -q "$seed_value" "$iam_seed"; then
    printf 'IAM local seed is missing required value: %s\n' "$seed_value" >&2
    exit 1
  fi
done

compose_json="$(docker compose -f "$COMPOSE_FILE" --profile infra --profile full config --format json)"
ruby -rjson -e '
  config = JSON.parse(STDIN.read)
  services = config.fetch("services")
  shared_infra_images = {
    "mysql" => "mysql:8.0.39",
    "redis" => "redis:7.2.0",
    "nacos" => "nacos/nacos-server:v3.0.2",
    "kafka" => "apache/kafka:4.3.1",
    "skywalking-banyandb" => "apache/skywalking-banyandb:0.11.0",
    "grafana" => "grafana/grafana:12.1.1",
    "skywalking-oap" => "apache/skywalking-oap-server:11.0.0",
    "skywalking-ui" => "ghcr.io/apache/skywalking-horizon-ui:1.0.0"
  }
  apps = %w[im-http-gateway im-ws-gateway-server im-broker-server im-account-server
            im-social-server im-message-server im-iam-server im-audit-server im-admin im-monitor]
  management_ports = {
    "im-http-gateway" => "19010", "im-ws-gateway-server" => "19011",
    "im-broker-server" => "19020", "im-account-server" => "19030",
    "im-social-server" => "19031", "im-message-server" => "19032",
    "im-iam-server" => "19040", "im-audit-server" => "19041",
    "im-admin" => "19042", "im-monitor" => "19043"
  }
  application_ports = {
    "im-http-gateway" => [18010, 19010],
    "im-ws-gateway-server" => [18011, 18012, 19011],
    "im-broker-server" => [18020, 19020],
    "im-account-server" => [18030, 19030],
    "im-social-server" => [18031, 19031],
    "im-message-server" => [18032, 19032],
    "im-iam-server" => [18040, 19040],
    "im-audit-server" => [18041, 19041],
    "im-admin" => [18042, 19042],
    "im-monitor" => [18043, 19043]
  }
  shared_infra_images.each do |name, image|
    abort "#{name}: missing infra service" unless services.key?(name)
    service = services.fetch(name)
    profiles = service.fetch("profiles", [])
    abort "#{name}: must belong to infra and full" unless %w[infra full].all? { |profile| profiles.include?(profile) }
    abort "#{name}: expected image #{image}" unless service.fetch("image") == image
  end
  {"prometheus-infra" => "infra", "prometheus-full" => "full"}.each do |name, profile|
    service = services.fetch(name)
    abort "#{name}: expected pinned Prometheus image" unless
      service.fetch("image") == "prom/prometheus:v3.5.0"
    abort "#{name}: must belong only to #{profile}" unless
      service.fetch("profiles") == [profile]
    abort "#{name}: must use bridge networking" if service.key?("network_mode")
    abort "#{name}: must expose the prometheus network alias" unless
      service.dig("networks", "im-chat-local", "aliases").include?("prometheus")
    abort "#{name}: host port 9090 must bind to loopback" unless
      service.fetch("ports").any? do |port|
        port.fetch("published").to_s == "9090" && port.fetch("host_ip") == "127.0.0.1"
      end
  end
  apps.each do |name|
    abort "#{name}: missing full service" unless services.key?(name)
    service = services.fetch(name)
    profiles = service.fetch("profiles", [])
    abort "#{name}: must belong only to full" unless profiles == ["full"]
    expected = "im-chat/#{name}:local"
    abort "#{name}: expected image #{expected}" unless service.fetch("image") == expected
    abort "#{name}: must use bridge networking" if service.key?("network_mode")
    abort "#{name}: missing local network" unless
      service.fetch("networks").key?("im-chat-local")
    abort "#{name}: SW_AGENT_NAME must equal service name" unless service.fetch("environment").fetch("SW_AGENT_NAME") == name
    abort "#{name}: missing local JVM memory budget" unless
      service.fetch("environment").fetch("JAVA_TOOL_OPTIONS") ==
        "-Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:-UseContainerSupport"
    abort "#{name}: missing container memory limit" unless
      service.fetch("mem_limit").to_i == 768 * 1024 * 1024
    abort "#{name}: local Redis SSL must be disabled" unless
      service.fetch("environment").fetch("SPRING_DATA_REDIS_SSL_ENABLED") == "false"
    abort "#{name}: local Redisson must use the Redis service" unless
      service.fetch("environment").fetch("IM_REDIS_REDISSON_ADDRESS") == "redis://redis:6379"
    abort "#{name}: Redis host must use the Compose service" unless
      service.fetch("environment").fetch("SPRING_DATA_REDIS_HOST") == "redis"
    abort "#{name}: Nacos must use the Compose service" unless
      service.fetch("environment").fetch("IM_NACOS_SERVER_ADDR") == "nacos:8848"
    abort "#{name}: Kafka must use the internal listener" unless
      service.fetch("environment").fetch("SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS") == "kafka:29092"
    abort "#{name}: SkyWalking must use the OAP service" unless
      service.fetch("environment").fetch("SW_AGENT_COLLECTOR_BACKEND_SERVICES") == "skywalking-oap:11800"
    %w[SERVER_ADDRESS IM_BOLT_SERVER_HOST IM_GATEWAY_BIND_HOST].each do |key|
      abort "#{name}: #{key} must bind inside the container" unless
        service.fetch("environment").fetch(key) == "0.0.0.0"
    end
    abort "#{name}: must not configure a redundant management bind address" if
      service.fetch("environment").key?("MANAGEMENT_SERVER_ADDRESS")
    abort "#{name}: must let Nacos auto-detect its container IP" if
      service.fetch("environment").key?("SPRING_CLOUD_NACOS_DISCOVERY_IP")
    abort "#{name}: must let Dubbo auto-detect its container IP" if
      service.fetch("environment").key?("IM_DUBBO_PROTOCOL_HOST")
    abort "#{name}: wrong management health port" unless
      service.fetch("environment").fetch("MANAGEMENT_SERVER_PORT").to_s == management_ports.fetch(name)
    health_command = "wget -q -O - http://127.0.0.1:$${MANAGEMENT_SERVER_PORT}/actuator/health >/dev/null 2>&1"
    abort "#{name}: missing management healthcheck" unless
      service.dig("healthcheck", "test") == ["CMD-SHELL", health_command]
    published_ports = service.fetch("ports").map { |port| port.fetch("published").to_i }.sort
    abort "#{name}: wrong published ports #{published_ports}" unless
      published_ports == application_ports.fetch(name).sort
    abort "#{name}: application ports must bind to loopback" unless
      service.fetch("ports").all? { |port| port.fetch("host_ip") == "127.0.0.1" }
    jar_file = service.fetch("build").fetch("args").fetch("JAR_FILE")
    abort "#{name}: Docker build must consume a version-independent exec jar" unless jar_file.end_with?("target/*-exec.jar")
  end
  abort "ws gateway: wrong advertised host" unless
    services.fetch("im-ws-gateway-server").dig("environment", "IM_GATEWAY_HOST") ==
      "im-ws-gateway-server"
  broker_environment = services.fetch("im-broker-server").fetch("environment")
  abort "broker: wrong advertised Bolt host" unless
    broker_environment.fetch("IM_BROKER_HOST") == "im-broker-server"
  abort "broker: management bind/advertised hosts must differ" unless
    broker_environment.fetch("IM_BROKER_MANAGEMENT_BIND_HOST") == "0.0.0.0" &&
      broker_environment.fetch("IM_BROKER_MANAGEMENT_HOST") == "im-broker-server"
  database_schemas = {
    "im-account-server" => "im_chat_account",
    "im-social-server" => "im_chat_social",
    "im-message-server" => "im_chat_message",
    "im-iam-server" => "im_chat_iam",
    "im-audit-server" => "im_chat_audit"
  }
  database_schemas.each do |name, schema|
    jdbc_url = services.fetch(name).dig("environment", "IM_DATASOURCE_SHARDING_JDBC_URL")
    abort "#{name}: JDBC URL must use MySQL service and #{schema}" unless
      jdbc_url.start_with?("jdbc:mysql://mysql:3306/#{schema}?")
  end
  %w[im-audit-server im-admin im-monitor].each do |name|
    service = services.fetch(name)
    abort "#{name}: wrong internal IAM URI" unless
      service.dig("environment", "IM_IAM_SERVICE_URI") == "http://im-iam-server:18040"
    abort "#{name}: local IAM read timeout must tolerate full startup pressure" unless
      service.dig("environment", "IM_IAM_HTTP_READ_TIMEOUT") == "10s"
    abort "#{name}: must wait for IAM" unless
      service.dig("depends_on", "im-iam-server", "condition") == "service_healthy"
  end
  %w[im-ws-gateway-server im-account-server im-message-server].each do |name|
    abort "#{name}: must wait for Broker registration" unless
      services.fetch(name).dig("depends_on", "im-broker-server", "condition") ==
        "service_healthy"
  end
  expected_volumes = %w[mysql-data redis-data nacos-data nacos-logs kafka-data
                        prometheus-data grafana-data banyan-data horizon-data]
  missing = expected_volumes - config.fetch("volumes", {}).keys
  abort "missing named volumes: #{missing.join(", ")}" unless missing.empty?

  services.each do |name, service|
    service.fetch("ports", []).each do |port|
      abort "#{name}: published port must bind to loopback" unless port.fetch("host_ip") == "127.0.0.1"
    end
  end

  oap = services.fetch("skywalking-oap")
  abort "skywalking-oap: must use BanyanDB storage" unless
    oap.dig("environment", "SW_STORAGE") == "banyandb" &&
      oap.dig("environment", "SW_STORAGE_BANYANDB_TARGETS") == "skywalking-banyandb:17912"
  abort "skywalking-oap: must wait for BanyanDB" unless
    oap.dig("depends_on", "skywalking-banyandb", "condition") == "service_healthy"
  abort "skywalking-oap: healthcheck must probe the query port" unless
    oap.dig("healthcheck", "test") == ["CMD", "bash", "-c", "cat < /dev/null > /dev/tcp/127.0.0.1/12800"]
  abort "skywalking-oap: telemetry port must bind to loopback" unless
    oap.fetch("ports").any? { |port| port.fetch("published").to_s == "1234" && port.fetch("host_ip") == "127.0.0.1" }
  abort "skywalking-oap: missing local JVM memory budget" unless
    oap.dig("environment", "JAVA_OPTS") == "-Xms256m -Xmx512m" &&
      oap.fetch("mem_limit").to_i == 1024 * 1024 * 1024
  nacos = services.fetch("nacos")
  abort "nacos: missing local JVM memory budget" unless
    nacos.dig("environment", "JVM_XMS") == "256m" &&
      nacos.dig("environment", "JVM_XMX") == "512m" &&
      nacos.dig("environment", "JVM_XMN") == "128m" &&
      nacos.fetch("mem_limit").to_i == 768 * 1024 * 1024
  kafka = services.fetch("kafka")
  abort "kafka: missing local JVM memory budget" unless
    kafka.dig("environment", "KAFKA_HEAP_OPTS") == "-Xms256m -Xmx384m" &&
      kafka.fetch("mem_limit").to_i == 640 * 1024 * 1024
  banyan = services.fetch("skywalking-banyandb")
  abort "skywalking-banyandb: /data must use a named volume" unless
    banyan.fetch("volumes", []).any? { |volume| volume.fetch("type") == "volume" && volume.fetch("target") == "/data" }
  abort "skywalking-banyandb: missing gRPC healthcheck" unless
    banyan.dig("healthcheck", "test") == ["CMD", "sh", "-c", "nc -nz 127.0.0.1 17912"]
  abort "skywalking-banyandb: must use a stable loopback node address" unless
    banyan.fetch("command").each_cons(2).include?(["--node-host-provider", "flag"]) &&
      banyan.fetch("command").each_cons(2).include?(["--node-host", "127.0.0.1"])
  %w[/data/stream /data/measure /data/trace /data/property /data/schema].each do |path|
    abort "skywalking-banyandb: command must persist #{path}" unless banyan.fetch("command").include?(path)
  end
  prometheus_infra = services.fetch("prometheus-infra")
  prometheus_full = services.fetch("prometheus-full")
  grafana = services.fetch("grafana")
  [prometheus_infra, prometheus_full].each do |prometheus|
    abort "prometheus: must listen on the container interface" unless
      prometheus.fetch("command").include?("--web.listen-address=0.0.0.0:9090")
  end
  abort "prometheus-infra: missing host gateway" unless
    prometheus_infra.fetch("extra_hosts").include?("host.docker.internal=host-gateway")
  abort "grafana: must use bridge networking" if grafana.key?("network_mode")
  abort "grafana: must not depend on profile-specific Prometheus" if grafana.key?("depends_on")
  abort "grafana: must listen on the container interface" unless
    grafana.dig("environment", "GF_SERVER_HTTP_ADDR") == "0.0.0.0"
  abort "grafana: host port 3000 must bind to loopback" unless
    grafana.fetch("ports").any? do |port|
      port.fetch("published").to_s == "3000" && port.fetch("host_ip") == "127.0.0.1"
    end
  horizon = services.fetch("skywalking-ui")
  horizon_env = horizon.fetch("environment")
  abort "skywalking-ui: wrong OAP query URL" unless
    horizon_env.fetch("HORIZON_OAP_QUERY_URL") == "http://skywalking-oap:12800"
  abort "skywalking-ui: wrong OAP admin URL" unless
    horizon_env.fetch("HORIZON_OAP_ADMIN_URL") == "http://skywalking-oap:17128"
  abort "skywalking-ui: local admin is missing" unless
    horizon_env.fetch("HORIZON_AUTH_LOCAL_USERS").include?(%q{"username":"admin"})
  abort "skywalking-ui: healthcheck must use Horizon API" unless
    horizon.dig("healthcheck", "test") == ["CMD", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8081/api/health"]
  abort "skywalking-ui: host port must map to Horizon 8081" unless
    horizon.fetch("ports").any? { |port| port.fetch("published").to_s == "18050" && port.fetch("target") == 8081 }
  abort "skywalking-ui: /data must use a named volume" unless
    horizon.fetch("volumes", []).any? { |volume| volume.fetch("type") == "volume" && volume.fetch("target") == "/data" }
' <<<"$compose_json"

for target in 19010 19011 19020 19030 19031 19032 19040 19041 19042 19043; do
  if ! rg -q "host.docker.internal:$target" \
      "$ROOT_DIR/deploy/local/prometheus/prometheus-infra.yml"; then
    printf 'Infra Prometheus config is missing IDE management target %s\n' "$target" >&2
    exit 1
  fi
done
for target in \
  im-http-gateway:19010 im-ws-gateway-server:19011 im-broker-server:19020 \
  im-account-server:19030 im-social-server:19031 im-message-server:19032 \
  im-iam-server:19040 im-audit-server:19041 im-admin:19042 im-monitor:19043; do
  if ! rg -q "targets: \[$target\]" "$ROOT_DIR/deploy/local/prometheus/prometheus-full.yml"; then
    printf 'Full Prometheus config is missing container management target %s\n' "$target" >&2
    exit 1
  fi
done
rg -q 'targets: \[skywalking-oap:1234\]' \
  "$ROOT_DIR/deploy/local/prometheus/prometheus-infra.yml"
rg -q 'targets: \[skywalking-oap:1234\]' \
  "$ROOT_DIR/deploy/local/prometheus/prometheus-full.yml"
rg -q 'url: http://prometheus:9090' \
  "$ROOT_DIR/deploy/local/grafana/provisioning/datasources/prometheus.yml"

if ! rg -q 'apache/skywalking-java-agent:9\.7\.0-java21' "$ROOT_DIR/deploy/local/app.Dockerfile" \
    || ! rg -q -- '-javaagent:/skywalking/agent/skywalking-agent.jar' "$ROOT_DIR/deploy/local/app.Dockerfile"; then
  printf 'Application Dockerfile must embed SkyWalking Java Agent 9.7.0.\n' >&2
  exit 1
fi
if ! rg -q '^FROM eclipse-temurin:21\.0\.12_8-jre-jammy$' "$ROOT_DIR/deploy/local/app.Dockerfile"; then
  printf 'Application Dockerfile must pin the Java runtime image to an exact release.\n' >&2
  exit 1
fi
if [[ $(sed -n '1p' "$ROOT_DIR/deploy/local/app.Dockerfile.dockerignore") != '**' ]]; then
  printf 'Docker ignore file must start from a deny-all baseline.\n' >&2
  exit 1
fi
for jar_pattern in \
  'im-gateway/im-http-gateway/target/\*-exec\.jar' \
  'im-gateway/im-ws-gateway/im-ws-gateway-server/target/\*-exec\.jar' \
  'im-broker/im-broker-server/target/\*-exec\.jar' \
  'im-service/im-account/im-account-server/target/\*-exec\.jar' \
  'im-service/im-social/im-social-server/target/\*-exec\.jar' \
  'im-service/im-message/im-message-server/target/\*-exec\.jar' \
  'im-management/im-iam/im-iam-server/target/\*-exec\.jar' \
  'im-management/im-audit/im-audit-server/target/\*-exec\.jar' \
  'im-management/im-admin/target/\*-exec\.jar' \
  'im-management/im-monitor/target/\*-exec\.jar'; do
  if ! rg -q "^!${jar_pattern}$" "$ROOT_DIR/deploy/local/app.Dockerfile.dockerignore"; then
    printf 'Docker ignore file must allow only required exec jar path: %s\n' "$jar_pattern" >&2
    exit 1
  fi
done

fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT
cat > "$fixture_root/fake-command" <<'EOF'
#!/usr/bin/env bash
printf '%s %s\n' "$(basename "$0")" "$*" >> "$LOCAL_COMMAND_LOG"
if [[ $(basename "$0") == docker && "$*" == *'ps --format json'* ]]; then
  printf '%s\n' "${LOCAL_COMPOSE_PS_JSON:-[]}"
elif [[ $(basename "$0") == docker && "$*" == *'ps --services --status running'* ]]; then
  printf '%s\n' "${LOCAL_RUNNING_SERVICES:-}"
elif [[ $(basename "$0") == curl ]]; then
  if [[ ${LOCAL_CURL_FAILURE:-false} == true ]]; then
    exit 43
  fi
  if [[ -n ${LOCAL_NACOS_INSTANCE_JSON:-} ]]; then
    printf '%s\n' "$LOCAL_NACOS_INSTANCE_JSON"
  else
    printf '%s\n' '{"hosts":[]}'
  fi
fi
if [[ -n ${LOCAL_FAIL_MATCH:-} && "$*" == *"$LOCAL_FAIL_MATCH"* ]]; then
  exit 42
fi
EOF
cat > "$fixture_root/fake-port-probe" <<'EOF'
#!/usr/bin/env bash
printf 'probe %s\n' "$*" >> "$LOCAL_COMMAND_LOG"
if [[ -n ${LOCAL_BUSY_PORT:-} && ${*: -1} == "$LOCAL_BUSY_PORT" ]]; then
  exit 0
fi
exit 1
EOF
chmod +x "$fixture_root/fake-command"
chmod +x "$fixture_root/fake-port-probe"
ln -s "$fixture_root/fake-command" "$fixture_root/docker"
ln -s "$fixture_root/fake-command" "$fixture_root/mvn"
ln -s "$fixture_root/fake-command" "$fixture_root/curl"
command_log="$fixture_root/calls.log"
infra_services='grafana
kafka
mysql
nacos
prometheus-infra
redis
skywalking-banyandb
skywalking-oap
skywalking-ui'
full_services="${infra_services/prometheus-infra/prometheus-full}
im-account-server
im-admin
im-audit-server
im-broker-server
im-http-gateway
im-iam-server
im-message-server
im-monitor
im-social-server
im-ws-gateway-server"

LOCAL_COMMAND_LOG="$command_log" DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
  PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
  LOCAL_RUNNING_SERVICES="$infra_services" \
  "$ROOT_DIR/scripts/local_up.sh"
rg -q -- 'rm -s -f prometheus-full im-http-gateway im-ws-gateway-server im-broker-server im-account-server im-social-server im-message-server im-iam-server im-audit-server im-admin im-monitor' "$command_log"
rg -q -- '--profile infra up -d --remove-orphans' "$command_log"
rg -q -- '--wait --wait-timeout 600' "$command_log"
rg -q -- 'probe -z 127.0.0.1 18010' "$command_log"
rg -q '^curl .*groupName=IM_CHAT_GROUP' "$command_log"
rg -q '^curl .*groupName=DUBBO_GROUP' "$command_log"
rg -q '^curl .*--connect-timeout 1 --max-time 1' "$command_log"
ruby -e '
  lines = File.readlines(ARGV.fetch(0))
  removal = lines.index { |line| line.include?("rm -s -f prometheus-full") }
  port_probe = lines.index { |line| line.include?("probe -z 127.0.0.1 18010") }
  registry_probe = lines.index { |line| line.start_with?("curl ") }
  startup = lines.index { |line| line.include?("--profile infra up -d") }
  abort "infra switch checks must run after removal and before startup" unless
    [removal, port_probe, registry_probe, startup].none?(&:nil?) &&
      removal < port_probe && removal < registry_probe &&
      port_probe < startup && registry_probe < startup
' "$command_log"
if rg -q '^mvn ' "$command_log"; then
  printf 'Default infra startup must not build application jars.\n' >&2
  exit 1
fi

: > "$command_log"
LOCAL_COMMAND_LOG="$command_log" DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
  PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
  LOCAL_RUNNING_SERVICES="$full_services" \
  "$ROOT_DIR/scripts/local_up.sh" full
rg -q -- 'rm -s -f prometheus-infra' "$command_log"
rg -q '^mvn .* -DskipTests clean package$' "$command_log"
rg -q -- '--profile full up -d --build --remove-orphans' "$command_log"
rg -q -- '--wait --wait-timeout 600' "$command_log"

if LOCAL_COMMAND_LOG="$command_log" LOCAL_FAIL_MATCH='--wait' DOCKER_BIN="$fixture_root/docker" \
    PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
    "$ROOT_DIR/scripts/local_up.sh" infra >/dev/null 2>&1; then
  printf 'Local startup must propagate a Compose wait failure.\n' >&2
  exit 1
else
  status=$?
  if [[ $status -ne 42 ]]; then
    printf 'Local startup returned %s instead of the Compose failure status.\n' "$status" >&2
    exit 1
  fi
fi

: > "$command_log"
if LOCAL_COMMAND_LOG="$command_log" LOCAL_BUSY_PORT=18010 \
    DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
    PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
    "$ROOT_DIR/scripts/local_up.sh" full >/dev/null 2>&1; then
  printf 'Full startup must reject an externally occupied application port.\n' >&2
  exit 1
fi
if rg -q '^mvn ' "$command_log"; then
  printf 'Port preflight must run before the full Maven build.\n' >&2
  exit 1
fi

: > "$command_log"
owned_port='[{"Publishers":[{"PublishedPort":18010}]}]'
LOCAL_COMMAND_LOG="$command_log" LOCAL_BUSY_PORT=18010 LOCAL_COMPOSE_PS_JSON="$owned_port" \
  DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
  PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
  LOCAL_RUNNING_SERVICES="$full_services" \
  "$ROOT_DIR/scripts/local_up.sh" full
rg -q '^mvn .* -DskipTests clean package$' "$command_log"

: > "$command_log"
if LOCAL_COMMAND_LOG="$command_log" LOCAL_NACOS_INSTANCE_JSON='{"hosts":[{}]}' \
    LOCAL_SWITCH_TIMEOUT_SECONDS=0 LOCAL_RUNNING_SERVICES="$infra_services" \
    DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
    PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
    "$ROOT_DIR/scripts/local_up.sh" infra >/dev/null 2>&1; then
  printf 'Infra switch must reject application registrations that did not disappear.\n' >&2
  exit 1
fi
if rg -q -- '--profile infra up -d' "$command_log"; then
  printf 'Infra startup must wait for application registration cleanup before Compose up.\n' >&2
  exit 1
fi

: > "$command_log"
if LOCAL_COMMAND_LOG="$command_log" LOCAL_CURL_FAILURE=true \
    LOCAL_SWITCH_TIMEOUT_SECONDS=0 LOCAL_RUNNING_SERVICES="$infra_services" \
    DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
    PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
    "$ROOT_DIR/scripts/local_up.sh" infra >/dev/null 2>&1; then
  printf 'Infra switch must not treat a running Nacos query failure as zero registrations.\n' >&2
  exit 1
fi

: > "$command_log"
if LOCAL_COMMAND_LOG="$command_log" LOCAL_NACOS_INSTANCE_JSON='not-json' \
    LOCAL_SWITCH_TIMEOUT_SECONDS=0 LOCAL_RUNNING_SERVICES="$infra_services" \
    DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
    PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
    "$ROOT_DIR/scripts/local_up.sh" infra >/dev/null 2>&1; then
  printf 'Infra switch must not treat an invalid Nacos response as zero registrations.\n' >&2
  exit 1
fi

: > "$command_log"
if LOCAL_COMMAND_LOG="$command_log" LOCAL_RUNNING_SERVICES='mysql' \
    DOCKER_BIN="$fixture_root/docker" MAVEN_BIN="$fixture_root/mvn" \
    PORT_PROBE_BIN="$fixture_root/fake-port-probe" HTTP_BIN="$fixture_root/curl" \
    "$ROOT_DIR/scripts/local_up.sh" infra >/dev/null 2>&1; then
  printf 'Local startup must reject an incomplete final service set.\n' >&2
  exit 1
fi

: > "$command_log"
LOCAL_COMMAND_LOG="$command_log" DOCKER_BIN="$fixture_root/docker" \
  "$ROOT_DIR/scripts/local_down.sh"
if rg -q -- '--volumes' "$command_log"; then
  printf 'Default local down must preserve named volumes.\n' >&2
  exit 1
fi

: > "$command_log"
LOCAL_COMMAND_LOG="$command_log" DOCKER_BIN="$fixture_root/docker" \
  "$ROOT_DIR/scripts/local_down.sh" --volumes
rg -q -- 'down --remove-orphans --volumes' "$command_log"

printf 'Local Compose Harness tests passed.\n'
