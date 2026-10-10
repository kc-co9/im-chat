#!/usr/bin/env bash
# 用途：计算压测专用源码指纹、非敏感配置摘要和三业务库普通数据校验和。
# 输入：各函数首参为仓库根目录；测试可注入 LOAD_TEST_ORDINARY_CHECKSUM_VALUE。
# 输出/副作用：指纹函数只输出 SHA-256；数据校验默认只读 Compose MySQL，不写数据库。
# 依赖：Git、Ruby、Docker Compose 以及 mysql:8.0.39 镜像内的 mysqldump。
# 失败语义：任一 Git 枚举、Compose 解析或数据库导出失败均返回非零，不输出可用证据。

# 指纹覆盖全部 Git-visible 文件，但排除只记录执行状态的进度与 active/completed plan。
load_test_scope_fingerprint() {
  local root_dir="$1"
  ruby -rdigest -ropen3 -e '
    root = File.expand_path(ARGV.fetch(0))
    output, error, status = Open3.capture3(
      "git", "-C", root, "ls-files", "--cached", "--others", "--exclude-standard", "-z",
      binmode: true
    )
    abort error unless status.success?
    paths = output.split("\0").reject(&:empty?).uniq.sort.reject do |path|
      path == "PROGRESS.md" || path.match?(%r{\Adocs/exec-plans/(active|completed)/})
    end
    digest = Digest::SHA256.new
    digest.update("LOAD_TEST_SCOPE\0")
    paths.each do |relative_path|
      full_path = File.join(root, relative_path)
      if File.symlink?(full_path)
        digest.update("\0SYMLINK\0#{relative_path}\0#{File.readlink(full_path)}")
      elsif File.file?(full_path)
        executable = File.stat(full_path).mode & 0o111 == 0 ? "regular" : "executable"
        digest.update("\0#{executable}\0#{relative_path}\0")
        digest.update(File.binread(full_path))
      end
    end
    puts digest.hexdigest
  ' "$root_dir"
}

# 配置摘要包含 canonical defaults、两个场景、合并后 Compose 输出和实际 k6 版本，不保存配置正文。
load_test_smoke_resolved_config() {
  ruby -rjson -e '
    number = ->(name, fallback) { Float(ENV.fetch(name, fallback.to_s)) }
    print JSON.generate({
      "profile" => "smoke", "users" => 20, "groupSize" => 10,
      "preparation" => {"pollAttempts" => 20, "pollIntervalMs" => 500,
        "mutationAttempts" => 10, "mutationRetryMs" => 2000,
        "gatewayRouteTimeoutSeconds" => 90,
        "discoverySettleSeconds" => Integer(ENV.fetch("LOAD_TEST_DISCOVERY_SETTLE_SECONDS", "30")),
        "websocketOpenAttempts" => 6,
        "websocketOpenRetryMs" => 2000},
      "connection" => {"requestedUsers" => 20,
        "stableSeconds" => number.call("LOAD_TEST_SMOKE_CONNECTION_STABLE_SECONDS", 1),
        "measureSeconds" => number.call("LOAD_TEST_SMOKE_CONNECTION_MEASURE_SECONDS", 20),
        "disconnectSeconds" => number.call("LOAD_TEST_SMOKE_DISCONNECT_SECONDS", 2),
        "routeCleanupSeconds" => number.call("LOAD_TEST_SMOKE_ROUTE_CLEANUP_SECONDS", 10)},
      "message" => {"connections" => 20,
        "targetRate" => number.call("LOAD_TEST_SMOKE_MESSAGE_RATE", 1),
        "warmupSeconds" => number.call("LOAD_TEST_SMOKE_MESSAGE_WARMUP_SECONDS", 0),
        "measureSeconds" => number.call("LOAD_TEST_SMOKE_MESSAGE_MEASURE_SECONDS", 1005),
        "drainSeconds" => number.call("LOAD_TEST_SMOKE_MESSAGE_DRAIN_SECONDS", 10),
        "backlogSampleSeconds" => number.call("LOAD_TEST_SMOKE_BACKLOG_SAMPLE_SECONDS", 5)},
      "targets" => {"http" => ENV.fetch("IM_LOAD_TEST_HTTP_URL", "http://127.0.0.1:18010"),
        "websocket" => ENV.fetch("IM_LOAD_TEST_WEBSOCKET_URL", "ws://127.0.0.1:18011/ws"),
        "brokerManagement" => ENV.fetch("IM_LOAD_TEST_BROKER_MANAGEMENT_URL", "http://127.0.0.1:19020"),
        "prometheusWrite" => ENV.fetch("K6_PROMETHEUS_RW_SERVER_URL", "http://127.0.0.1:9090/api/v1/write")},
      "timeouts" => {"doctorSeconds" => Integer(ENV.fetch("LOAD_TEST_DOCTOR_TIMEOUT_SECONDS", "30")),
        "roundSeconds" => Integer(ENV.fetch("LOAD_TEST_SMOKE_ROUND_TIMEOUT_SECONDS", "1500")),
        "stageSeconds" => Integer(ENV.fetch("LOAD_TEST_STAGE_TIMEOUT_SECONDS", "7200")),
        "totalSeconds" => Integer(ENV.fetch("LOAD_TEST_TOTAL_TIMEOUT_SECONDS", "18000"))}
    })
  '
}

load_test_config_digest() {
  local root_dir="$1"
  local resolved_config="${2:-}"
  [[ -n "$resolved_config" ]] || resolved_config='{}'
  if [[ -n "${LOAD_TEST_CONFIG_DIGEST_VALUE:-}" ]]; then
    printf '%s\n' "$LOAD_TEST_CONFIG_DIGEST_VALUE"
    return 0
  fi
  local docker_bin="${DOCKER_BIN:-docker}"
  local k6_bin="${K6_BIN:-$HOME/.local/bin/k6}"
  local compose_digest
  local k6_version
  compose_digest="$("$docker_bin" compose \
    -f "$root_dir/deploy/local/compose.yml" \
    -f "$root_dir/deploy/local/compose.load-test.yml" \
    --profile full config | ruby -rdigest -e 'puts Digest::SHA256.hexdigest(STDIN.read)')" || return $?
  k6_version="$("$k6_bin" version | sed -nE '1s/^k6 v([^ ]+).*/\1/p')" || return $?
  [[ -n "$k6_version" ]] || return 1
  ruby -rjson -rdigest -e '
    root, compose_digest, k6_version, resolved_config_json = ARGV
    canonical = lambda do |value|
      case value
      when Hash then value.keys.sort.to_h { |key| [key, canonical.call(value[key])] }
      when Array then value.map { |item| canonical.call(item) }
      else value
      end
    end
    digest = Digest::SHA256.new
    defaults = JSON.parse(File.read(File.join(root, "im-test/im-load-test/config/defaults.json")))
    digest.update(JSON.generate(canonical.call(defaults)))
    digest.update("\0resolved\0")
    digest.update(JSON.generate(canonical.call(JSON.parse(resolved_config_json))))
    %w[connection-capacity.js message-throughput.js].each do |name|
      digest.update("\0#{name}\0")
      digest.update(File.binread(File.join(root, "im-test/im-load-test/scenarios", name)))
    end
    digest.update("\0compose\0#{compose_digest}\0k6\0#{k6_version}")
    puts digest.hexdigest
  ' "$root_dir" "$compose_digest" "$k6_version" "$resolved_config"
}

# routing capacity 使用独立配置摘要，避免历史单机 load-test capacity 证据被误认作共识路由容量。
routing_capacity_config_digest() {
  local root_dir="$1"
  local config_path="${2:-$root_dir/im-test/im-load-test/config/routing-capacity.json}"
  if [[ -n "${ROUTING_CAPACITY_CONFIG_DIGEST_VALUE:-}" ]]; then
    printf '%s\n' "$ROUTING_CAPACITY_CONFIG_DIGEST_VALUE"
    return 0
  fi
  [[ -f "$config_path" ]] || return 1
  ruby -rdigest -e '
    root, config = ARGV
    digest = Digest::SHA256.new
    [config, File.join(root, "scripts/routing_capacity.sh"),
      File.join(root, "scripts/run-routing-capacity-scenarios.sh"),
      File.join(root, "deploy/local/compose.routing-ha.yml")].each do |path|
      next unless File.file?(path)
      digest.update("\0#{path.delete_prefix(root + "/")}\0")
      digest.update(File.binread(path))
    end
    puts digest.hexdigest
  ' "$root_dir" "$config_path"
}

# 导出三业务 Schema 的有序纯数据，不包含 DDL、AUTO_INCREMENT 或 dump 时间戳。
load_test_ordinary_data_checksum() {
  local root_dir="$1"
  if [[ -n "${LOAD_TEST_ORDINARY_CHECKSUM_FILE:-}" ]]; then
    sed -n '1p' "$LOAD_TEST_ORDINARY_CHECKSUM_FILE"
    return 0
  fi
  if [[ -n "${LOAD_TEST_ORDINARY_CHECKSUM_VALUE:-}" ]]; then
    printf '%s\n' "$LOAD_TEST_ORDINARY_CHECKSUM_VALUE"
    return 0
  fi
  local docker_bin="${DOCKER_BIN:-docker}"
  "$docker_bin" compose -f "$root_dir/deploy/local/compose.yml" --profile full \
    exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --databases \
        im_chat_account im_chat_social im_chat_message \
        --skip-comments --skip-dump-date --skip-add-drop-table --no-create-info \
        --order-by-primary --hex-blob --skip-triggers --compact --single-transaction --skip-lock-tables' \
    | ruby -rdigest -e 'puts Digest::SHA256.hexdigest(STDIN.read)'
}
