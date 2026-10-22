#!/usr/bin/env bash
# 用途：自测 routing-ha 场景证据边界、就绪预算及成功/零场景/失败/超时/残留资源传播契约。
# 输入：无位置参数；通过临时 fixture 注入场景命令和状态目录。
# 输出/副作用：只写入 mktemp 目录并在退出时删除，不启动仓库 Compose。
# 依赖：bash、mktemp、Ruby，以及 scripts/routing_ha.sh。
# 退出码：全部正反例符合 fail-closed 契约时返回 0。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNNER="$ROOT_DIR/scripts/routing_ha.sh"
SCENARIO_RUNNER="$ROOT_DIR/scripts/run-routing-ha-scenarios.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

restart_timeout_default="$(ruby -e '
  source = File.read(ARGV.fetch(0))
  match = source.match(/RESTART_READY_TIMEOUT_SECONDS="\$\{ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS:-(\d+)\}"/)
  abort "routing-ha restart readiness timeout must be configurable" unless match
  puts match[1]
' "$SCENARIO_RUNNER")"
if (( restart_timeout_default < 120 )); then
  printf 'routing-ha restart readiness timeout must cover the observed slow startup budget.\n' >&2
  exit 1
fi
restart_timeout_uses="$(rg -c 'poll_http .* "\$RESTART_READY_TIMEOUT_SECONDS"' "$SCENARIO_RUNNER")"
if (( restart_timeout_uses != 2 )); then
  printf 'Gateway and Broker restart probes must use the configured readiness timeout.\n' >&2
  exit 1
fi

ruby -e '
  root = ARGV.fetch(0)
  manifest = File.readlines(File.join(root, "im-test/im-routing-ha/src/test/resources/routing-ha-scenarios.tsv"))
  row = manifest.find { |line| line.start_with?("RH-05\t") }.to_s.chomp.split("\t")
  abort "RH-05 must use real isolated TLS runtime evidence" unless
    row[2] == "isolated-runtime-test" && row[3] == "AutomaticFailureTlsHaTest"
  runner = File.read(File.join(root, "scripts/run-routing-ha-scenarios.sh"))
  abort "isolated TLS runtime must execute in the reactor" unless
    runner.include?("-pl im-test/im-routing-ha -am") &&
    runner.include?("-Dtest=RoutingHaScenarioManifestTest,AutomaticFailureTlsHaTest")
  abort "existing recovery regressions must remain executed" unless
    runner.include?("BrokerFailureRecoveryAppServiceTest")
' "$ROOT_DIR"

ruby -ryaml -e '
  compose = YAML.load_file(ARGV.fetch(0))
  health = compose.fetch("services").fetch("routing-ha-nacos").fetch("healthcheck").fetch("test").last
  abort "routing-ha Nacos readiness must include naming gRPC" unless health.include?("9848")
' "$ROOT_DIR/deploy/local/compose.routing-ha.yml"

run_fixture() {
  local name="$1"
  local command="$2"
  local timeout_seconds="${3:-15}"
  local state_dir="$FIXTURE_ROOT/$name/state"
  local detail="$FIXTURE_ROOT/$name/detail.json"
  mkdir -p "$state_dir"
  # runner 必须删除旧账本；本次未生成账本时不能借旧空文件宣称清理通过。
  if [[ "$name" == missing-ledger ]]; then
    : > "$state_dir/resources.jsonl"
  fi
  ROUTING_HA_SCENARIO_COMMAND="$command" \
    ROUTING_HA_STATE_DIR="$state_dir" \
    ROUTING_HA_DETAIL_PATH="$detail" \
    ROUTING_HA_TIMEOUT_SECONDS="$timeout_seconds" \
    "$RUNNER" verify
}

success_command="$FIXTURE_ROOT/success.sh"
cat > "$success_command" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
for index in $(seq 1 25); do
  printf '{"id":"RH-%02d","name":"fixture","status":"passed","evidenceKind":"fixture","evidence":"fixture","assertion":"fixture passed","durationMillis":1,"finishedAt":"2026-09-21T00:00:00Z"}\n' "$index"
done > "$ROUTING_HA_STATE_DIR/scenarios.jsonl"
EOF
chmod +x "$success_command"
# 与 25 条成功场景相比，缺失资源账本仍不是清理成功。
if run_fixture missing-ledger "$success_command"; then
  printf 'routing-ha must reject a missing owned-resource ledger.\n' >&2
  exit 1
fi
printf ': > "$ROUTING_HA_STATE_DIR/resources.jsonl"\n' >> "$success_command"
run_fixture success "$success_command"

zero_command="$FIXTURE_ROOT/zero.sh"
cat > "$zero_command" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
: > "$ROUTING_HA_STATE_DIR/scenarios.jsonl"
EOF
chmod +x "$zero_command"
if run_fixture zero "$zero_command"; then
  printf 'routing-ha must reject zero executed scenarios.\n' >&2
  exit 1
fi

failure_command="$FIXTURE_ROOT/failure.sh"
cat > "$failure_command" <<'EOF'
#!/usr/bin/env bash
exit 23
EOF
chmod +x "$failure_command"
if run_fixture failure "$failure_command"; then
  printf 'routing-ha must propagate a scenario command failure.\n' >&2
  exit 1
fi

timeout_command="$FIXTURE_ROOT/timeout.sh"
cat > "$timeout_command" <<'EOF'
#!/usr/bin/env bash
exec sleep 30
EOF
chmod +x "$timeout_command"
set +e
run_fixture timeout "$timeout_command" 1
timeout_status=$?
set -e
if (( timeout_status != 124 )); then
  printf 'routing-ha must fail when the scenario command times out.\n' >&2
  exit 1
fi

leftover_command="$FIXTURE_ROOT/leftover.sh"
cat > "$leftover_command" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
for index in $(seq 1 25); do
  printf '{"id":"RH-%02d","name":"fixture","status":"passed","evidenceKind":"fixture","evidence":"fixture","assertion":"fixture passed","durationMillis":1,"finishedAt":"2026-09-21T00:00:00Z"}\n' "$index"
done > "$ROUTING_HA_STATE_DIR/scenarios.jsonl"
printf '{"kind":"container","id":"broker-orphan"}\n' \
  > "$ROUTING_HA_STATE_DIR/resources.jsonl"
EOF
chmod +x "$leftover_command"
if run_fixture leftover "$leftover_command"; then
  printf 'routing-ha must reject a non-empty owned-resource ledger.\n' >&2
  exit 1
fi

printf 'routing-ha Harness self-test passed.\n'

# 只验证编排分支：fake helper 保留调用 trace，源码/XML/摘要负例由真实 helper 自测覆盖。
branch_root="$FIXTURE_ROOT/reactor branch"
mkdir -p "$branch_root/scripts/lib" "$branch_root/bin" "$branch_root/state/diagnostics"
cp "$SCENARIO_RUNNER" "$branch_root/scripts/run-routing-ha-scenarios.sh"
cp "$ROOT_DIR/scripts/lib/routing-ha-process.rb" "$branch_root/scripts/lib/"
cat > "$branch_root/scripts/lib/harness-reactor-evidence.rb" <<'RUBY'
require 'json'
command, root, epoch, output, topology, manifest = ARGV
manifest = epoch if command == 'check'
File.open(File.join(root, 'trace'), 'a') { |file| file.puts(command) }
case command
when 'check', 'ha-evidence'
  abort 'invalid or tampered manifest' unless JSON.parse(File.read(manifest))['valid'] == true
  File.write(output, "mapped\n") if command == 'ha-evidence'
else
  abort 'unexpected evidence command'
end
RUBY
cat > "$branch_root/bin/mvn" <<'SH'
#!/bin/bash
printf 'maven\n' >> "$BRANCH_ROOT/trace"
exit 19
SH
cat > "$branch_root/bin/docker" <<'SH'
#!/bin/bash
printf 'docker %s\n' "$*" >> "$BRANCH_ROOT/trace"
exit 0
SH
# 真实 process helper 仍发 begin/end；仅隔离 HTTP 及 cluster projection 网络输入。
real_ruby="$(command -v ruby)"
cat > "$branch_root/bin/ruby" <<'SH'
#!/bin/bash
if [[ "${1:-}" == -rnet/http || ( "${1:-}" == -rjson && "${3:-}" != *'fetch("startedAt")'* ) ]]; then
  exit 0
fi
exec "$REAL_RUBY" "$@"
SH
chmod +x "$branch_root/bin/"*
printf '{"startedAt":1,"valid":true}' > "$branch_root/manifest.json"
BRANCH_ROOT="$branch_root" REAL_RUBY="$real_ruby" PATH="$branch_root/bin:$PATH" \
  ROUTING_HA_STATE_DIR="$branch_root/state" ROUTING_HA_REACTOR_EVIDENCE="$branch_root/manifest.json" \
  bash "$branch_root/scripts/run-routing-ha-scenarios.sh" > "$branch_root/output" 2>&1
[[ "$(grep -c '^check$' "$branch_root/trace")" == 1 ]]
[[ "$(grep -c '^ha-evidence$' "$branch_root/trace")" == 1 ]]
! grep -q '^maven$' "$branch_root/trace"
[[ -f "$branch_root/state/scenarios.jsonl" ]]
# 无效清单必须在 Maven/Engine/Compose 之前失败。
printf invalid > "$branch_root/manifest.json"
: > "$branch_root/trace"
if BRANCH_ROOT="$branch_root" REAL_RUBY="$real_ruby" PATH="$branch_root/bin:$PATH" \
  ROUTING_HA_STATE_DIR="$branch_root/state" ROUTING_HA_REACTOR_EVIDENCE="$branch_root/manifest.json" \
  bash "$branch_root/scripts/run-routing-ha-scenarios.sh" > "$branch_root/output" 2>&1; then
  printf 'Invalid reactor manifest must fail before containers.\n' >&2
  exit 1
fi
[[ "$(cat "$branch_root/trace")" == check ]]
# 场景末尾篡改制品也不能写出有效场景证据。
sed -i.bak '/exit 0/i\
if [[ "$*" == *"start "* ]]; then printf tampered > "$BRANCH_ROOT/manifest.json"; fi' "$branch_root/bin/docker"
printf '{"startedAt":1,"valid":true}' > "$branch_root/manifest.json"
if BRANCH_ROOT="$branch_root" REAL_RUBY="$real_ruby" PATH="$branch_root/bin:$PATH" \
  ROUTING_HA_STATE_DIR="$branch_root/state" ROUTING_HA_REACTOR_EVIDENCE="$branch_root/manifest.json" \
  bash "$branch_root/scripts/run-routing-ha-scenarios.sh" > "$branch_root/output" 2>&1; then
  printf 'Tampered reactor evidence must not publish 25 valid scenarios.\n' >&2
  exit 1
fi
[[ ! -s "$branch_root/state/scenarios.jsonl" ]]
# 直接 runner 的自洽替换：helper 仍接受新 JSON，但原始字节身份必须拒绝。
cp "$RUNNER" "$branch_root/scripts/routing_ha.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$branch_root/scripts/lib/"
printf '/state/\n/trace\n/output\n/manifest.json\n' > "$branch_root/.gitignore"
git -C "$branch_root" init -q
git -C "$branch_root" add .
git -C "$branch_root" -c user.name=Fixture -c user.email=fixture@example.invalid commit -qm fixture
cat > "$branch_root/replacement.sh" <<'SH'
#!/bin/bash
set -eu
ruby -rjson -e 'path = ARGV[0]; data = JSON.parse(File.read(path)); data["replacement"] = true; File.write(path, JSON.generate(data))' "$ROUTING_HA_REACTOR_EVIDENCE"
for index in $(seq 1 25); do
  printf '{"id":"RH-%02d","name":"fixture","status":"passed","evidenceKind":"fixture","evidence":"fixture","assertion":"passed","durationMillis":1,"finishedAt":"2000-01-01T00:00:00Z"}\n' "$index"
done > "$ROUTING_HA_STATE_DIR/scenarios.jsonl"
: > "$ROUTING_HA_STATE_DIR/resources.jsonl"
SH
chmod +x "$branch_root/replacement.sh"
git -C "$branch_root" add replacement.sh
git -C "$branch_root" -c user.name=Fixture -c user.email=fixture@example.invalid commit -qm replacement-fixture
printf '{"startedAt":1,"valid":true}' > "$branch_root/manifest.json"
if ROUTING_HA_SCENARIO_COMMAND="$branch_root/replacement.sh" \
  ROUTING_HA_STATE_DIR="$branch_root/state" ROUTING_HA_REACTOR_EVIDENCE="$branch_root/manifest.json" \
  bash "$branch_root/scripts/routing_ha.sh" verify > "$branch_root/output" 2>&1; then
  printf 'Direct HA runner must reject self-consistent manifest replacement.\n' >&2
  exit 1
fi
[[ ! -f "$branch_root/.harness/verifications/routing-ha-detail.json" ]]
printf 'routing-ha same-reactor integration fixtures passed.\n'
