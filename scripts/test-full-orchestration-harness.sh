#!/usr/bin/env bash
# 输入：无参数；完整运行 full 编排合成 fixture，父 Harness 与 focused 共用。
# 输出：合成断言结果；成功仅删除自建 root，失败保留日志并打印路径。
# 隔离：不运行真实仓库 full；只向本次未 reap 的直接子 PID 发信号。
# 合成后代通过本次 root 的 release 自退并有最终期限；不保证任意生产后代回收。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
full_root=""
cleanup_full_fixture() {
  local code=$?
  if [[ -n "$full_root" ]]; then
    if (( code == 0 )); then
      rm -rf "$full_root"
    else
      printf 'Full orchestration fixture failed (%s); evidence retained at %s\n' "$code" "$full_root" >&2
    fi
  fi
  return "$code"
}
trap cleanup_full_fixture EXIT
# full 必须自行包含环境、清洁状态、Harness fixture 和 Maven verify，不能依赖调用者记住前置命令。
full_root="$(mktemp -d)"
full_root="$(cd "$full_root" && pwd -P)"
mkdir -p "$full_root/scripts/lib" "$full_root/bin" \
  "$full_root/docs/exec-plans/active" "$full_root/.harness"
cp "$ROOT_DIR/scripts/verify.sh" "$full_root/scripts/verify.sh"
cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$full_root/scripts/lib/harness-evidence.sh"
cp "$ROOT_DIR/scripts/lib/load-test-evidence.sh" "$full_root/scripts/lib/load-test-evidence.sh"
cat > "$full_root/scripts/check-drift.sh" <<'EOF'
#!/usr/bin/env bash
printf 'drift\n' >> "$FULL_FIXTURE_TRACE"
EOF
cat > "$full_root/scripts/test-harness.sh" <<'EOF'
#!/usr/bin/env bash
printf 'harness-tests\n' >> "$FULL_FIXTURE_TRACE"
EOF
cat > "$full_root/scripts/routing_ha.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "${HTTPS_PROXY:-}" == 'http://127.0.0.1:7890' ]] || exit 46
for proxy_key in HTTP_PROXY ALL_PROXY NO_PROXY http_proxy https_proxy all_proxy no_proxy DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG; do
  [[ -z "${!proxy_key+x}" ]] || exit 46
done
printf 'routing-ha\n' >> "$FULL_FIXTURE_TRACE"
[[ "${FULL_HA_PROXY_FAILURE:-0}" == 0 ]] || exit 47
if [[ "${FULL_REPLACE_MANIFEST:-0}" == 1 ]]; then
  ruby -E UTF-8 -rjson -e 'path = ARGV[0]; data = JSON.parse(File.read(path)); data["replacement"] = true; File.write(path, JSON.generate(data))' "$ROUTING_HA_REACTOR_EVIDENCE"
fi
[[ -n "${ROUTING_HA_REACTOR_EVIDENCE:-}" ]] || exit 1
mkdir -p "$(dirname "$0")/../.harness/verifications"
printf '{"scenarioCount":25,"cleanupStatus":"passed","command":"forged","headRevision":"forged","finishedAt":"forged","durationSeconds":999,"customBusinessField":{"preserved":true}}\n' > "$(dirname "$0")/../.harness/verifications/routing-ha-detail.json"
EOF
cat > "$full_root/scripts/harness-report.sh" <<'EOF'
#!/usr/bin/env bash
printf 'report\n' >> "$FULL_FIXTURE_TRACE"
exit "${FULL_REPORT_EXIT:-0}"
EOF
cat > "$full_root/scripts/harness-handoff.sh" <<'EOF'
#!/usr/bin/env bash
printf 'handoff\n' >> "$FULL_FIXTURE_TRACE"
exit "${FULL_HANDOFF_EXIT:-0}"
EOF
chmod +x "$full_root/scripts/check-drift.sh" "$full_root/scripts/test-harness.sh" \
  "$full_root/scripts/routing_ha.sh"
chmod +x "$full_root/scripts/harness-report.sh" "$full_root/scripts/harness-handoff.sh"
write_full_tool() {
  local tool="$1"
  local body="$2"
  printf '#!/usr/bin/env bash\n%s\n' "$body" > "$full_root/bin/$tool"
  chmod +x "$full_root/bin/$tool"
}
write_full_tool java 'printf '\''openjdk version "21.0.10"\n'\'''
write_full_tool mvn 'printf '\''maven %s\n'\'' "$*" >> "$FULL_FIXTURE_TRACE"; if [[ "$*" == "-q verify" ]]; then mkdir -p "$FULL_FIXTURE_ROOT/module/target/surefire-reports"; printf '\''<testsuite tests="3" failures="0" errors="0" skipped="1"/>\n'\'' > "$FULL_FIXTURE_ROOT/module/target/surefire-reports/TEST-full.xml"; fi; printf '\''Apache Maven 3.8.4\n'\'''
write_full_tool node 'printf '\''v20.19.5\n'\'''
write_full_tool npm 'printf '\''10.8.2\n'\'''
write_full_tool rg 'exec /usr/bin/grep "$@"'
# 这里只隔离 full 编排；严格 XML/源码映射由 test-reactor-evidence.sh 的真实 helper fixture 覆盖。
cat > "$full_root/scripts/lib/harness-reactor-evidence.rb" <<'EOF'
require 'json'
require 'fileutils'
command, root, manifest, output, summary = ARGV
case command
when 'model-attest'
  File.open(ENV.fetch('FULL_FIXTURE_TRACE'), 'a') { |file| file.puts('model attest') }
  abort 'fixture source rejected' if ENV['FULL_MODEL_SOURCE_FAILURE'] == '1'
  require 'digest'
  raise 'original PATH lost' unless ENV.fetch('PATH').start_with?(root + '/bin:')
  puts Digest::SHA256.hexdigest(ENV.fetch('PATH') + (File.exist?(root + '/.harness/source-drift') ? 'changed' : ''))
when 'model-execution'
  abort 'fixture execution source rejected' if ENV['FULL_MODEL_EXECUTION_FAILURE'] == '1'
  require 'shellwords'
  # fixture控制仅写在私有测试root，不扩展生产环境schema；受控子阶段实际不继承这些变量。
  controls = ENV.select { |key, _| key.start_with?('FULL_') }
  controls['FULL_FIXTURE_TRACE'] ||= root + '/.harness/full-trace.log'
  controls['FULL_FIXTURE_ROOT'] ||= root
  FileUtils.mkdir_p(root + '/.harness')
  File.write(root + '/.harness/fixture-controls.sh', controls.map { |key, value| 'export ' + key + '=' + value.shellescape }.join("\n"))
  puts [ENV.fetch('HOME'), ENV.fetch('JAVA_HOME', '/fixture/jdk'), root + '/execution-bin:/usr/bin:/bin']
when 'model-collect'
  File.open(ENV.fetch('FULL_FIXTURE_TRACE'), 'a') { |file| file.puts('model collect') }
  abort 'fixture collect rejected' if ENV['FULL_MODEL_COLLECT_FAILURE'] == '1'
  FileUtils.mkdir_p(File.dirname(manifest))
  Dir.mkdir(manifest, 0700)
  File.write(manifest + '/collection.json', '{}')
when 'model-prepare'
  File.open(ENV.fetch('FULL_FIXTURE_TRACE'), 'a') { |file| file.puts('model prepare') }
  abort 'fixture proof changed' if ENV['FULL_MODEL_PROOF_FAILURE'] == '1'
  [output, summary].each { |file| File.delete(file) if File.exist?(file) }
  File.write(output, JSON.generate({'startedAt' => Time.now.to_f}))
when 'model-verify'
  File.open(ENV.fetch('FULL_FIXTURE_TRACE'), 'a') { |file| file.puts('model verify') }
  abort 'fixture environment changed' if ENV['FULL_MODEL_ENV_FAILURE'] == '1'
  execution = {'HOME' => ENV.fetch('HOME'), 'PATH' => root + '/execution-bin:/usr/bin:/bin'}
  if ENV['FULL_CANCEL_PHASE'] && ENV['FULL_CANCEL_PHASE'] != 'maven'
    system({'PATH' => '/usr/bin:/bin'}, '/bin/mkdir', '-p', root + '/module/target/surefire-reports', unsetenv_others: true)
    File.write(root + '/module/target/surefire-reports/TEST-full.xml', '<testsuite tests="3" failures="0" errors="0" skipped="1"/>')
  elsif ENV['FULL_MODEL_RUNNER_HELPER']
    require 'open3'
    helper = ENV.fetch('FULL_MODEL_RUNNER_HELPER')
    eval(File.read(helper, encoding: Encoding::UTF_8).split("\nif $PROGRAM_NAME == __FILE__\nbegin\n  command, root").first, TOPLEVEL_BINDING, helper)
    status = ReactorEvidence.new(root).run_model_command(execution, ['mvn', '-q', 'verify'], capture: ENV['FULL_MODEL_CAPTURE'] == '1')
    status = status[2] if status.is_a?(Array)
    exit(status.exitstatus || 1) unless status.success?
  else
    system(execution, 'mvn', '-q', 'verify', unsetenv_others: true)
    exit($?.exitstatus || 1) unless $?.success?
  end
when 'prepare'
  File.write(manifest, JSON.generate({'startedAt' => Time.now.to_f}))
when 'verify'
  data = JSON.parse(File.read(manifest))
  data['summaries'] = %w[architecture startup behavior e2e ha].to_h { |mode| [mode, {'tests' => 3}] }
  data['summaries']['full'] = {'tests' => 3, 'failures' => 0, 'errors' => 0, 'skipped' => 1}
  data['groups'] = {'startup' => [{}]}
  File.write(manifest, JSON.generate(data))
when 'check'
  abort 'not verified' unless JSON.parse(File.read(manifest)).key?('summaries')
else
  abort 'unexpected helper command'
end
EOF
# 真实区分原PATH和执行PATH；控制数据由fixture脚本自行读本地测试文件，非生产放行。
mkdir -p "$full_root/execution-bin"
for tool in java mvn node npm rg; do
  printf '#!/bin/bash\n/usr/bin/ruby -e '\''expected = %%w[HOME JAVA_HOME PATH PWD SHLVL _ __CF_USER_TEXT_ENCODING]; abort "extra child environment: #{ENV.keys - expected}" unless (ENV.keys - expected).empty?; abort "wrong execution PATH" unless ENV.fetch("PATH").start_with?(ARGV[0] + "/execution-bin:")'\'' "%s" || exit $?\nsource "%s/.harness/fixture-controls.sh"\nexec "%s/bin/%s" "$@"\n' "$full_root" "$full_root" "$full_root" "$tool" > "$full_root/execution-bin/$tool"
  chmod +x "$full_root/execution-bin/$tool"
done
for fixture_script in check-drift test-harness routing_ha harness-report harness-handoff; do
  ruby -E UTF-8 -e '
    file, root = ARGV
    text = File.read(file)
    if File.basename(file) == "routing_ha.sh"
      text.sub!("#!/usr/bin/env bash\n", "#!/bin/bash\n")
      text.sub!("printf '\''routing-ha", "source \"#{root}/.harness/fixture-controls.sh\"\nprintf '\''routing-ha")
    else
      guard = %q(/usr/bin/ruby -e '\''abort "proxy leaked to non-HA stage" unless (ENV.keys & %w[HTTPS_PROXY HTTP_PROXY ALL_PROXY NO_PROXY https_proxy http_proxy all_proxy no_proxy]).empty?'\'')
      text.sub!("#!/usr/bin/env bash\n", "#!/bin/bash\n#{guard}\nsource \"#{root}/.harness/fixture-controls.sh\"\n")
    end
    File.write(file, text)
  ' "$full_root/scripts/$fixture_script.sh" "$full_root"
done
for tool in ruby git date; do
  printf '#!/bin/bash\nprintf "shadow tool executed\\n" >&2\nexit 99\n' > "$full_root/bin/$tool"
  chmod +x "$full_root/bin/$tool"
done
# 指纹使用真实Git；只隔离外部构建工具，生成物不进入验证输入。
printf '.harness/\nbin/\nexecution-bin/\n**/target/\nfull.out\nmodel-failure.out\n' > "$full_root/.gitignore"
git -C "$full_root" init --quiet
git -C "$full_root" add scripts docs .gitignore
git -C "$full_root" -c user.name=Harness -c user.email=harness@example.invalid commit -qm 'Initialize full lifecycle fixture'
full_trace="$full_root/.harness/full-trace.log"
# readonly导出不能靠unset清除；真实子进程边界必须拒绝其继承，父变量仍存在。
(
  export SHELLOPTS
  if [[ ${BASHOPTS+x} ]]; then export BASHOPTS; fi
  export HTTPS_PROXY=http://wrong-proxy.invalid:1 HTTP_PROXY=http://wrong-proxy.invalid:2
  export ALL_PROXY=socks5://wrong-proxy.invalid:3 NO_PROXY='*'
  export https_proxy=http://wrong-proxy.invalid:4 http_proxy=http://wrong-proxy.invalid:5
  export all_proxy=socks5://wrong-proxy.invalid:6 no_proxy='*'
  export DOCKER_HOST=tcp://wrong-docker.invalid:1 DOCKER_CONTEXT=wrong-context DOCKER_CONFIG=/wrong-config
  FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" "$full_root/scripts/verify.sh" full > "$full_root/full.out" 2>&1
  [[ "$HTTPS_PROXY" == http://wrong-proxy.invalid:1 && "$NO_PROXY" == '*' ]]
  [[ -n "$SHELLOPTS" ]]
  if [[ ${BASHOPTS+x} ]]; then [[ -n "$BASHOPTS" ]]; fi
)
: > "$full_trace"
if ! FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
    "$full_root/scripts/verify.sh" full >"$full_root/full.out" 2>&1; then
  printf 'Full lifecycle fixture should pass.\n' >&2
  cat "$full_root/full.out" >&2
  exit 1
fi
if ! ruby -E UTF-8 -rjson -e '
  evidence = JSON.parse(File.read(ARGV.fetch(0)))
  abort "incorrect full status" unless evidence.values_at("status", "gateStatus", "finalizationStatus") == %w[passed passed passed]
  abort "wrong log" unless evidence["logPath"] == ".harness/runtime/full-maven.log"
  summary = evidence["testSummary"]
  exit(summary == {"tests" => 3, "failures" => 0, "errors" => 0, "skipped" => 1} ? 0 : 1)
' "$full_root/.harness/verifications/full.json"; then
  printf 'Full verification must persist its test summary outside Maven target directories.\n' >&2
  exit 1
fi
source "$ROOT_DIR/scripts/lib/harness-evidence.sh"
ruby -E UTF-8 -rjson -e '
  evidence = JSON.parse(File.read(ARGV.fetch(0)))
  abort "full must publish actual final HEAD/fingerprint" unless evidence.values_at("headRevision", "worktreeFingerprint", "worktreeStable") == [ARGV[1], ARGV[2], true]
' "$full_root/.harness/verifications/full.json" "$(harness_head_revision "$full_root")" "$(harness_worktree_fingerprint "$full_root")"
for expected in 'readiness java=21' 'Readiness checks passed.' 'Checking clean state'; do
  if ! /usr/bin/grep -Fq "$expected" "$full_root/full.out"; then
    printf 'Full lifecycle output is missing %s\n' "$expected" >&2
    exit 1
  fi
done
if [[ "$(<"$full_trace")" != $'model attest\nmodel attest\nmaven --version\nmodel attest\ndrift\nharness-tests\nmodel attest\nmodel collect\nmodel prepare\nmodel verify\nmaven -q verify\nrouting-ha\nreport\nhandoff' ]]; then
  printf 'Full lifecycle order is incorrect:\n%s\n' "$(<"$full_trace")" >&2
  exit 1
fi

set +e
FULL_HA_PROXY_FAILURE=1 FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
  "$full_root/scripts/verify.sh" full > "$full_root/full.out" 2>&1
proxy_failure_status=$?
set -e
[[ "$proxy_failure_status" == 47 ]] || { printf 'HA proxy failure was not preserved: %s\n' "$proxy_failure_status" >&2; exit 1; }
ruby -rjson -e 'data = JSON.parse(File.read(ARGV.fetch(0))); abort "HA proxy failure accepted" unless data.values_at("status", "gateStatus", "originalExitCode") == ["failed", "failed", 47]' "$full_root/.harness/verifications/full.json"

# 仅本轮full fixture：来源/reader失败不得删除既有manifest/summary或触发prepare/verify。
for failure_kind in SOURCE EXECUTION COLLECT PROOF; do
  printf 'old-manifest' > "$full_root/.harness/runtime/full-reactor-evidence.json"
  printf '{"tests":17,"sentinel":"old-summary"}' > "$full_root/.harness/runtime/full-reactor-summary.json"
  : > "$full_trace"
  if env "FULL_MODEL_${failure_kind}_FAILURE=1" FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
      "$full_root/scripts/verify.sh" full > "$full_root/model-failure.out" 2>&1; then
    printf 'Model failure fixture must fail.\n' >&2
    exit 1
  fi
  if [[ "$(<"$full_root/.harness/runtime/full-reactor-evidence.json")" != 'old-manifest' || "$(<"$full_root/.harness/runtime/full-reactor-summary.json")" != '{"tests":17,"sentinel":"old-summary"}' ]]; then
    printf 'Model failure deleted existing reactor records.\n' >&2
    exit 1
  fi
  if /usr/bin/grep -Eq '^model verify$|^maven -q verify$' "$full_trace"; then
    printf 'Model failure reached verify.\n' >&2
    exit 1
  fi
  if [[ "$failure_kind" == SOURCE ]] && [[ "$(<"$full_trace")" != 'model attest' ]]; then
    printf 'Source failure launched readiness or later tools.\n' >&2
    exit 1
  fi
  if [[ "$failure_kind" == EXECUTION ]] && /usr/bin/grep -Eq '^maven |^drift$|^harness-tests$|^model collect$' "$full_trace"; then
    printf 'Execution environment failure launched a stage.\n' >&2; exit 1
  fi
  if [[ "$failure_kind" == COLLECT ]] && /usr/bin/grep -Fq 'model prepare' "$full_trace"; then
    printf 'Collect failure reached prepare.\n' >&2
    exit 1
  fi
done
# readiness失败和其后来源漂移都不能进入collection；不人为破坏生产代码制造RED。
for failure_kind in READINESS DRIFT; do
  cp "$full_root/bin/java" "$full_root/.harness/java.saved"
  if [[ "$failure_kind" == READINESS ]]; then
    printf '#!/bin/bash\nprintf '\''openjdk version "17.0.1"\\n'\''\n' > "$full_root/bin/java"
  else
    printf '#!/bin/bash\n: > "%s/.harness/source-drift"\nprintf '\''openjdk version "21.0.10"\\n'\''\n' "$full_root" > "$full_root/bin/java"
  fi
  : > "$full_trace"
  if FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" "$full_root/scripts/verify.sh" full > "$full_root/model-failure.out" 2>&1; then
    printf 'Preflight failure must reject full.\n' >&2; exit 1
  fi
  if /usr/bin/grep -Fq 'model collect' "$full_trace"; then
    printf 'Preflight failure reached collection.\n' >&2; exit 1
  fi
  cp "$full_root/.harness/java.saved" "$full_root/bin/java"
  rm -f "$full_root/.harness/source-drift"
done
# 后续既有断言需要恢复同一合法fixture窗口，仍仅调用mock工具。
FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
  "$full_root/scripts/verify.sh" full > "$full_root/full.out" 2>&1

# 同一 Maven 窗口要发布普通模式记录，详情不得覆盖身份与命令。
ruby -E UTF-8 -rjson -e '
  root = ARGV[0]
  %w[architecture startup behavior e2e].each do |mode|
    data = JSON.parse(File.read(File.join(root, mode + ".json")))
    abort "missing reactor evidence #{mode}" unless data["evidenceSource"] == "full-reactor" && data["testCount"] == 3 && data["command"].include?("full-reactor validation") && data["logPath"] == ".harness/runtime/full-maven.log"
    abort "wrong Maven window" unless data["startedAtEpoch"] == data["mavenStartedAtEpoch"].to_i
  end
  ha = JSON.parse(File.read(File.join(root, "routing-ha.json")))
  abort "routing HA detail clobbered source" unless ha["scenarioCount"] == 25 && ha["headRevision"] != "forged" && ha["command"].include?("full-reactor") && ha["finishedAt"] != "forged" && ha["durationSeconds"] != 999 && ha.dig("customBusinessField", "preserved") == true
' "$full_root/.harness/verifications"

# helper 仍接受的自洽清单替换，也必须被首次固定的字节摘要拒绝。
set +e
FULL_REPLACE_MANIFEST=1 FULL_FIXTURE_ROOT="$full_root" FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
  "$full_root/scripts/verify.sh" full >"$full_root/full.out" 2>&1
replacement_status=$?
set -e
[[ "$replacement_status" != 0 ]] || { printf 'Self-consistent reactor manifest replacement must fail.\n' >&2; exit 1; }
ruby -E UTF-8 -rjson -e 'abort "replacement published full passed" if JSON.parse(File.read(ARGV[0]))["status"] == "passed"' "$full_root/.harness/verifications/full.json"

# full 提前失败时仍生成派生证据，并保留最初的 Maven 退出码。
cat > "$full_root/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf 'maven %s\n' "$*" >> "$FULL_FIXTURE_TRACE"
if [[ "$*" == "--version" ]]; then
  printf 'Apache Maven 3.8.4\n'
elif [[ "$*" == "-q verify" ]]; then
  exit 7
fi
EOF
chmod +x "$full_root/bin/mvn"
: > "$full_trace"
set +e
FULL_REPORT_EXIT=9 FULL_HANDOFF_EXIT=11 FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
  "$full_root/scripts/verify.sh" full >/dev/null 2>&1
full_failure_status=$?
set -e
if (( full_failure_status != 7 )); then
  printf 'Full lifecycle must preserve the original gate exit code, got %d.\n' \
    "$full_failure_status" >&2
  exit 1
fi
if [[ "$(<"$full_trace")" != $'model attest\nmodel attest\nmaven --version\nmodel attest\ndrift\nharness-tests\nmodel attest\nmodel collect\nmodel prepare\nmodel verify\nmaven -q verify\nreport\nhandoff' ]]; then
  printf 'Failed full lifecycle did not run both evidence post-processors:\n%s\n' \
    "$(<"$full_trace")" >&2
  exit 1
fi

ruby -E UTF-8 -rjson -e '
  evidence = JSON.parse(File.read(ARGV.fetch(0)))
  abort "gate failure metadata" unless evidence.values_at("status", "gateStatus", "finalizationStatus", "originalExitCode", "reportExitCode", "handoffExitCode") == ["failed", "failed", "failed", 7, 9, 11]
' "$full_root/.harness/verifications/full.json"

# full 原本通过但 report 失败时必须整体失败，并继续尝试生成 handoff。
cat > "$full_root/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf 'maven %s\n' "$*" >> "$FULL_FIXTURE_TRACE"
if [[ "$*" == "--version" ]]; then
  printf 'Apache Maven 3.8.4\n'
fi
EOF
chmod +x "$full_root/bin/mvn"
: > "$full_trace"
set +e
FULL_REPORT_EXIT=9 FULL_FIXTURE_TRACE="$full_trace" PATH="$full_root/bin:/usr/bin:/bin" \
  "$full_root/scripts/verify.sh" full >/dev/null 2>&1
postprocess_failure_status=$?
set -e
if (( postprocess_failure_status != 9 )); then
  printf 'Successful full with failed report must fail with report status, got %d.\n' \
    "$postprocess_failure_status" >&2
  exit 1
fi
if [[ "$(<"$full_trace")" != $'model attest\nmodel attest\nmaven --version\nmodel attest\ndrift\nharness-tests\nmodel attest\nmodel collect\nmodel prepare\nmodel verify\nmaven -q verify\nrouting-ha\nreport\nhandoff' ]]; then
  printf 'Report failure prevented handoff post-processing:\n%s\n' "$(<"$full_trace")" >&2
  exit 1
fi
ruby -E UTF-8 -rjson -e '
  evidence = JSON.parse(File.read(ARGV.fetch(0)))
  abort "postprocessor failure metadata" unless evidence.values_at("status", "gateStatus", "finalizationStatus", "originalExitCode", "reportExitCode", "handoffExitCode") == ["failed", "passed", "failed", 0, 9, 0]
' "$full_root/.harness/verifications/full.json"

# 合成根取消后先退；后代关闭输出管道，避免直接 wait 或 Open3 EOF 掩盖整组等待。
# release 仅控制本次新 root 的合成后代；后代另有单调自退期限，不按扫描结果发信号。
cat > "$full_root/bin/mvn" <<'EOF'
#!/bin/bash
if [[ "$*" == '--version' ]]; then printf 'Apache Maven 3.8.4\n'; exit; fi
exec /usr/bin/ruby -e '
  root = ARGV[0]
  cancelled = false
  Signal.trap("TERM") { exit! 143 }
  child = fork do
    Signal.trap("TERM") { cancelled = true }
    STDIN.reopen(File::NULL)
    STDOUT.reopen(File::NULL, "w")
    STDERR.reopen(File::NULL, "w")
    deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 60
    File.write(root + "/.harness/child-ready.tmp", Process.pid.to_s)
    File.rename(root + "/.harness/child-ready.tmp", root + "/.harness/child-ready")
    loop do
      File.write(root + "/.harness/child-hold", "holding") if cancelled
      break if File.exist?(root + "/.harness/child-release")
      if Process.clock_gettime(Process::CLOCK_MONOTONIC) > deadline
        File.write(root + "/.harness/child-expired", "expired")
        break
      end
      sleep 0.05
    end
    File.write(root + "/.harness/child-done", "done")
    exit! 0
  end
  File.write(root + "/.harness/child-owner.tmp", [Process.pid, child].join(" "))
  File.rename(root + "/.harness/child-owner.tmp", root + "/.harness/child-owner")
  Process.wait(child)
' "$FULL_FIXTURE_ROOT"
EOF
chmod +x "$full_root/bin/mvn"
/usr/bin/ruby -E UTF-8 -rjson -e '
  root, helper = ARGV
  monotonic = -> { Process.clock_gettime(Process::CLOCK_MONOTONIC) }
  # 仅查询本次合成根通过 fork 返回值登记的确切 PID；不全机扫描，不据此发送信号。
  stopped = lambda do |owned_pid|
    output = IO.popen(["/bin/ps", "-p", owned_pid.to_s, "-o", "stat="], &:read)
    query = $?
    state = output.strip
    next true if query.exitstatus == 1 && state.empty?
    raise "owned PID query failed: #{owned_pid}" unless query.exitstatus == 0 && state.match?(/\A[A-Za-z][A-Za-z0-9+<>-]*\z/)
    state.start_with?("Z")
  end
  await = lambda do |seconds, message, &condition|
    deadline = monotonic.call + seconds
    until condition.call
      raise message if monotonic.call > deadline
      sleep 0.05
    end
  end
  %w[maven report handoff].product(%w[INT TERM]).each_with_index do |(phase, signal), index|
    %w[harness-report harness-handoff].each do |name|
      target = name == "harness-report" ? "report" : "handoff"
      body = "#!/bin/bash\nsource \"#{root}/.harness/fixture-controls.sh\"\n"
      body += phase == target ? "exec \"#{root}/bin/mvn\" -q verify\n" : "exit 0\n"
      File.write(root + "/scripts/" + name + ".sh", body)
    end
    %w[child-ready child-owner child-hold child-release child-done child-expired].each { |name| file = root + "/.harness/" + name; File.delete(file) if File.exist?(file) }
    env = {"FULL_FIXTURE_ROOT" => root, "FULL_FIXTURE_TRACE" => root + "/.harness/full-trace.log", "FULL_MODEL_RUNNER_HELPER" => helper, "FULL_MODEL_CAPTURE" => (index % 2).to_s, "FULL_CANCEL_PHASE" => phase, "PATH" => root + "/bin:/usr/bin:/bin"}
    pid = Process.spawn(env, "/bin/bash", root + "/scripts/verify.sh", "full", out: root + "/full.out", err: [:child, :out])
    status = nil
    child_pid = nil
    begin
    deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 45
    until File.exist?(root + "/.harness/child-ready")
      waited = Process.waitpid2(pid, Process::WNOHANG)
      if waited
        status = waited[1]
        raise "cancel fixture exited before synthetic Maven: #{status.exitstatus}; log=#{root}/full.out"
      end
      raise "cancel fixture did not reach synthetic Maven" if Process.clock_gettime(Process::CLOCK_MONOTONIC) > deadline
      sleep 0.05
    end
    await.call(5, "synthetic owner registration missing") { File.exist?(root + "/.harness/child-owner") }
    identity = File.read(root + "/.harness/child-owner")
    raise "invalid synthetic fork registration" unless identity.match?(/\A[1-9][0-9]* [1-9][0-9]*\z/)
    owner_pid, child_pid = identity.split.map { |value| Integer(value) }
    raise "synthetic fork identity mismatch" unless owner_pid != child_pid && child_pid == Integer(File.read(root + "/.harness/child-ready"))
    Process.kill(signal, pid)
    await.call(10, "root-exited / descendant-holding window missing") do
      File.exist?(root + "/.harness/child-hold") && stopped.call(owner_pid)
    end
    # hold 期间观察直接 verify 的实际退出；截止仍存活是正常结果，不用超时冒充 RED。
    # 2 秒是有限观察预算，不承诺任意调度下都能捕获 mutant；两份入口使用相同预算。
    observation_deadline = monotonic.call + 2
    loop do
      waited = Process.waitpid2(pid, Process::WNOHANG)
      status = waited[1] if waited
      break if status || monotonic.call >= observation_deadline
      sleep 0.05
    end
    raise "descendant hold window already ended" if stopped.call(child_pid) || File.exist?(root + "/.harness/child-done") || File.exist?(root + "/.harness/child-expired")
    warn "verify exited while descendant held: phase=#{phase} signal=#{signal} exit=#{status.exitstatus}" if status
    raise "lock released with live descendant" unless File.directory?(root + "/.harness/verifications/execution.lock")
    raise "verify exited while execution lock remained" if status
    File.write(root + "/.harness/child-release", "release")
    await.call(10, "released descendant did not stop") { stopped.call(child_pid) }
    raise "descendant used emergency deadline" if File.exist?(root + "/.harness/child-expired")
    cancel_deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 45
    loop do
      waited = Process.waitpid2(pid, Process::WNOHANG)
      if waited
        status = waited[1]
        break
      end
      raise "cancel fixture did not exit; log=#{root}/full.out" if Process.clock_gettime(Process::CLOCK_MONOTONIC) > cancel_deadline
      sleep 0.05
    end
    raise "wrong cancel exit" unless status.exitstatus == (signal == "INT" ? 130 : 143)
    raise "descendant not awaited" unless File.exist?(root + "/.harness/child-done")
    raise "lock not released" if File.exist?(root + "/.harness/verifications/execution.lock")
    data = JSON.parse(File.read(root + "/.harness/verifications/full.json"))
    raise "cancel published complete" unless data["status"] == "incomplete" && data["finalizationStatus"] == "incomplete" && data["gateStatus"] == (phase == "maven" ? "incomplete" : "passed")
    ensure
      original_error = $!
      cleanup_errors = []
      begin
        # 即使 verify 已被 reap，也要让本例后代自行退出；不向登记的后代 PID 发信号。
        File.write(root + "/.harness/child-release", "release")
        if child_pid
          await.call(65, "cleanup descendant did not stop") { stopped.call(child_pid) }
        end
      rescue StandardError => error
        cleanup_errors << error.message
      end
      begin
        unless status
          %w[TERM KILL].each do |cleanup_signal|
            break if status
            begin
              Process.kill(cleanup_signal, pid)
            rescue Errno::ESRCH
            end
            deadline = monotonic.call + 5
            loop do
              waited = Process.waitpid2(pid, Process::WNOHANG)
              status = waited[1] if waited
              break if status || monotonic.call > deadline
              sleep 0.05
            end
          end
          cleanup_errors << "direct verify PID not reaped: #{pid}" unless status
        end
      rescue StandardError => error
        cleanup_errors << "direct verify cleanup: #{error.message}"
      end
      unless child_pid
        begin
          await.call(5, "descendant identity unavailable; cleanup unconfirmed") { File.exist?(root + "/.harness/child-ready") }
          identity = File.read(root + "/.harness/child-ready")
          raise "invalid descendant observation identity" unless identity.match?(/\A[1-9][0-9]*\z/)
          child_pid = Integer(identity)
          await.call(65, "cleanup descendant did not stop") { stopped.call(child_pid) }
        rescue StandardError => error
          cleanup_errors << error.message
        end
      end
      unless cleanup_errors.empty?
        warn "cancel fixture cleanup failed: #{cleanup_errors.join("; ")}; evidence=#{root}"
        raise cleanup_errors.join("; ") unless original_error
      end
    end
  end
' "$full_root" "$ROOT_DIR/scripts/lib/harness-reactor-evidence.rb"

# 延期的 full 扩展参数必须明确拒绝，不能忽略后执行整组门禁。
for arguments in '--through scripts' '--resume old-chain' '--unknown'; do
  set +e
  PATH="$full_root/bin:/usr/bin:/bin" "$full_root/scripts/verify.sh" full $arguments >/dev/null 2>&1
  unsupported_status=$?
  set -e
  [[ "$unsupported_status" == 2 ]] || { printf 'Unsupported full argument must return 2: %s (%s)\n' "$arguments" "$unsupported_status" >&2; exit 1; }
done
set +e
PATH="$full_root/bin:/usr/bin:/bin" "$full_root/scripts/verify.sh" archive-audit prepare >/dev/null 2>&1
unsupported_status=$?
set -e
[[ "$unsupported_status" == 2 ]] || exit 1


printf 'Full orchestration Harness tests passed.\n'
