#!/usr/bin/env bash
# 用途：提供 readiness、clean、drift、architecture、behavior、e2e、quick、affected、full、report、handoff 统一验证入口。
# 输入：第一个参数为模式；affected 模式可接收 baseline revision。
# 输出/副作用：执行对应检查，并始终写入绑定当前 HEAD/worktree 的 .harness/verifications/<mode>.json。
# 依赖：Maven、Ruby、rg、find、Harness 子脚本，以及 UI 构建所需 Node/npm。
# 退出码：透传所选验证模式结果；未知模式返回 2，失败结果仍会被记录。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-quick}"
source "$ROOT_DIR/scripts/lib/harness-evidence.sh"
cd "$ROOT_DIR"

# 记录单次模式的状态、命令、耗时、起止 revision/fingerprint 和运行期间稳定性。
record_verification() {
  local mode="$1"
  local status="$2"
  local duration="$3"
  local started_epoch="$4"
  local command="$5"
  local started_head="$6"
  local finished_head="$7"
  local started_fingerprint="$8"
  local finished_fingerprint="$9"
  local worktree_stable="${10}"
  local log_path=""
  local test_count=0
  local workload=""
  local test_summary_json=""
  local output_dir="$ROOT_DIR/.harness/verifications"
  mkdir -p "$output_dir"
  if [[ "$mode" == "e2e" ]]; then
    log_path=".harness/runtime/e2e.log"
    workload="realtime-messaging-golden-path"
    test_count="$(ruby -r rexml/document -e '
      puts ARGV.sum { |file| REXML::Document.new(File.read(file)).root.attributes["tests"].to_i }
    ' $(find "$ROOT_DIR/im-test/im-e2e-test/target/surefire-reports" \
      -type f -name 'TEST-*.xml' -print 2>/dev/null))"
  elif [[ "$mode" == "full" ]]; then
    test_summary_json="$(ruby -rjson -rrexml/document -e '
      root = ARGV.fetch(0)
      threshold = ARGV.fetch(1).to_i
      totals = Hash.new(0)
      Dir.glob(File.join(root, "**/target/surefire-reports/TEST-*.xml")).each do |file|
        next if File.mtime(file).to_i < threshold
        suite = REXML::Document.new(File.read(file)).root
        %w[tests failures errors skipped].each do |name|
          totals[name] += suite.attributes[name].to_i
        end
      end
      print JSON.generate(%w[tests failures errors skipped].to_h { |name| [name, totals[name]] })
    ' "$ROOT_DIR" "$started_epoch")"
  fi
  ruby -rjson -e '
    output, mode, command, status, duration, started_epoch, finished_at,
      started_head, finished_head, started_fingerprint, finished_fingerprint,
      stable, log_path, test_count, workload, test_summary_json = ARGV
    evidence = {
      "mode" => mode,
      "command" => command,
      "status" => status,
      "durationSeconds" => duration.to_i,
      "startedAtEpoch" => started_epoch.to_i,
      "finishedAt" => finished_at,
      "headRevision" => finished_head,
      "worktreeFingerprint" => finished_fingerprint,
      "startedHeadRevision" => started_head,
      "startedWorktreeFingerprint" => started_fingerprint,
      "worktreeStable" => stable == "true"
    }
    unless log_path.empty?
      evidence["logPath"] = log_path
      evidence["testCount"] = test_count.to_i
      evidence["workload"] = workload
    end
    evidence["testSummary"] = JSON.parse(test_summary_json) unless test_summary_json.empty?
    File.write(output, JSON.generate(evidence) + "\n")
  ' "$output_dir/$mode.json" "$mode" "$command" "$status" "$duration" \
    "$started_epoch" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$started_head" \
    "$finished_head" "$started_fingerprint" "$finished_fingerprint" "$worktree_stable" \
    "$log_path" "$test_count" "$workload" "$test_summary_json"
}

# 比较点分隔数字版本；两个参数分别为实际版本和最低版本，满足 actual >= minimum 时成功。
version_at_least() {
  local actual="$1"
  local minimum="$2"
  local index
  local actual_part
  local minimum_part
  local -a actual_parts
  local -a minimum_parts
  IFS=. read -r -a actual_parts <<< "$actual"
  IFS=. read -r -a minimum_parts <<< "$minimum"
  for index in 0 1 2; do
    actual_part="${actual_parts[$index]:-0}"
    minimum_part="${minimum_parts[$index]:-0}"
    (( actual_part > minimum_part )) && return 0
    (( actual_part < minimum_part )) && return 1
  done
  return 0
}

# 输出 readiness 的可操作诊断；参数依次为 WHAT、WHY、FIX。
readiness_failure() {
  printf '\n[readiness] WHAT: %s\nWHY: %s\nFIX: %s\n' "$1" "$2" "$3" >&2
}

# 检查本仓库构建和 Harness 所需工具及其受支持版本，一次报告全部环境缺口。
run_readiness() {
  local failures=0
  local tool
  for tool in java mvn node npm ruby git rg; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      readiness_failure \
        "缺少命令: $tool" \
        "构建、UI 或 Harness 脚本需要该工具。" \
        "安装 $tool 并确保它可以从 PATH 访问。"
      failures=$((failures + 1))
    fi
  done

  if command -v java >/dev/null 2>&1; then
    local java_version
    java_version="$(java -version 2>&1 | sed -nE '1s/.*version "?([0-9]+)(\.[^"]*)?"?.*/\1/p')"
    if [[ "$java_version" != "21" ]]; then
      readiness_failure \
        "Java 版本不受支持: ${java_version:-unknown}" \
        "根 POM Enforcer 要求 Java [21,22)。" \
        "切换到 JDK 21 后重新运行 readiness。"
      failures=$((failures + 1))
    else
      printf 'readiness java=%s\n' "$java_version"
    fi
  fi

  if command -v mvn >/dev/null 2>&1; then
    local maven_version
    maven_version="$(mvn --version 2>/dev/null | sed -nE '1s/.*Maven ([0-9]+\.[0-9]+\.[0-9]+).*/\1/p')"
    if [[ -z "$maven_version" ]] \
        || ! version_at_least "$maven_version" "3.8.4" \
        || version_at_least "$maven_version" "4.0.0"; then
      readiness_failure \
        "Maven 版本不受支持: ${maven_version:-unknown}" \
        "根 POM Enforcer 要求 Maven [3.8.4,4)。" \
        "安装 Maven 3.8.4 以上且低于 4.0.0 的版本。"
      failures=$((failures + 1))
    else
      printf 'readiness maven=%s\n' "$maven_version"
    fi
  fi

  if command -v node >/dev/null 2>&1; then
    local node_version
    local node_supported=false
    node_version="$(node --version 2>/dev/null | sed -nE '1s/^v?([0-9]+\.[0-9]+\.[0-9]+).*/\1/p')"
    if [[ -n "$node_version" ]]; then
      if version_at_least "$node_version" "20.19.0" \
          && ! version_at_least "$node_version" "21.0.0"; then
        node_supported=true
      elif version_at_least "$node_version" "22.12.0"; then
        node_supported=true
      fi
    fi
    if [[ "$node_supported" != true ]]; then
      readiness_failure \
        "Node.js 版本不受支持: ${node_version:-unknown}" \
        "当前管理 UI 使用 Vite 7，要求 ^20.19.0 或 >=22.12.0。" \
        "切换到 Node.js 20.19+、22.12+ 或更高受支持版本。"
      failures=$((failures + 1))
    else
      printf 'readiness node=%s\n' "$node_version"
    fi
  fi

  if (( failures > 0 )); then
    printf '\nReadiness checks failed with %d issue(s).\n' "$failures" >&2
    return 1
  fi
  printf 'Readiness checks passed.\n'
}

# 输出 clean-state 的可操作诊断；参数依次为 WHAT、WHY、FIX。
clean_failure() {
  printf '\n[clean] WHAT: %s\nWHY: %s\nFIX: %s\n' "$1" "$2" "$3" >&2
}

# 组合只读检查判断工作树是否可交接；正常未跟踪源码只提示，不自动删除或暂存。
run_clean() {
  local failures=0
  local active_count=0
  local plan
  local active_plan_files
  local untracked_files
  local temporary_files
  local shell_file
  local shell_output
  local shell_syntax_failures=""

  printf '\n==> Checking clean state\n'
  if ! run_drift; then
    clean_failure \
      "仓库 drift 检查失败" \
      "清洁状态必须先满足已有静态、文档和架构入口约束。" \
      "按上方 checker 诊断修复后重新运行 clean。"
    failures=$((failures + 1))
  fi
  while IFS= read -r -d '' shell_file; do
    if ! shell_output="$(bash -n "$shell_file" 2>&1)"; then
      shell_syntax_failures+="$shell_output"$'\n'
    fi
  done < <(find "$ROOT_DIR/scripts" -type f -name '*.sh' -print0)
  if [[ -n "$shell_syntax_failures" ]]; then
    printf '%s' "$shell_syntax_failures" >&2
    clean_failure \
      "Shell 脚本语法检查失败" \
      "不可解析的验证脚本会破坏后续反馈入口。" \
      "修复 bash -n 报告的文件和行号。"
    failures=$((failures + 1))
  fi
  if ! git diff --check; then
    clean_failure \
      "Git diff 包含空白错误" \
      "尾随空白或冲突标记会污染 Review 和补丁应用。" \
      "修复 git diff --check 报告的位置。"
    failures=$((failures + 1))
  fi

  active_plan_files="$(find "$ROOT_DIR/docs/exec-plans/active" -type f -name '*.md' ! -name README.md -print 2>/dev/null)"
  if [[ -n "$active_plan_files" ]]; then
    while IFS= read -r plan; do
      active_count=$((active_count + $(rg -o '\|[[:space:]]*`?active`?[[:space:]]*\|' "$plan" | wc -l | tr -d ' ')))
      if ! rg -q '^## (跨会话)?恢复状态$' "$plan"; then
        clean_failure \
          "活跃计划缺少恢复状态: ${plan#$ROOT_DIR/}" \
          "跨会话无法确定当前任务、阻塞项和下一步。" \
          "在计划中增加恢复状态章节并填写可执行下一步。"
        failures=$((failures + 1))
      fi
    done <<< "$active_plan_files"
  fi
  if (( active_count > 1 )); then
    clean_failure \
      "同时存在 $active_count 个 active task" \
      "执行计划要求同一时刻至多一个 active task，避免范围并发漂移。" \
      "完成、阻塞或重新规划其他任务，只保留一个 active task。"
    failures=$((failures + 1))
  fi

  untracked_files="$(git ls-files --others --exclude-standard)"
  temporary_files="$(printf '%s\n' "$untracked_files" | rg \
    '(^|/)(\.tmp[^/]*|tmp-[^/]*)(/|$)|\.(orig|rej|tmp|log)$' || true)"
  if [[ -n "$temporary_files" ]]; then
    clean_failure \
      "存在未分类临时或调试工件" \
      "临时文件会污染后续 Review、打包或会话恢复。" \
      "删除这些文件，或将确需保留的产物移动到有所有权的路径并记录用途。"
    printf '%s\n' "$temporary_files" >&2
    failures=$((failures + 1))
  fi
  if [[ -n "$untracked_files" ]]; then
    printf 'Clean-state note: untracked files are present and require scope review.\n'
  fi

  if (( failures > 0 )); then
    printf '\nClean-state checks failed with %d issue(s).\n' "$failures" >&2
    return 1
  fi
  printf 'Clean-state checks passed.\n'
}

# 执行聚合 drift checker。
run_drift() {
  printf '\n==> Checking repository drift\n'
  "$ROOT_DIR/scripts/check-drift.sh"
}

# 先构建 Architecture 测试依赖，再运行独立 ArchUnit 模块，避免使用旧本地工件。
run_architecture() {
  printf '\n==> Running architecture tests\n'
  mvn -q -pl im-test/im-architecture-test -am -Dmaven.test.skip=true install
  mvn -q -pl im-test/im-architecture-test test
}

# 运行 Harness 自测，证明 checker 的正反 fixture 与报告契约。
run_script_tests() {
  printf '\n==> Running Harness script tests\n'
  "$ROOT_DIR/scripts/test-harness.sh"
}

# 执行交付前完整门禁：环境就绪、可交接状态、Harness 自测和 Maven verify。
run_full() {
  run_readiness
  run_clean
  run_script_tests
  printf '\n==> Running full Maven verification suite\n'
  mvn -q verify
}

# 运行标记为 realtime-behavior 的 Broker/Gateway 场景，并拒绝零测试假通过。
run_behavior() {
  local behavior_tag="realtime-behavior"
  local broker_reports="$ROOT_DIR/im-broker/im-broker-server/target/surefire-reports"
  local gateway_reports="$ROOT_DIR/im-gateway/im-ws-gateway/im-ws-gateway-server/target/surefire-reports"

  if ! rg -q "@Tag\\(\"$behavior_tag\"\\)" \
      "$ROOT_DIR/im-broker/im-broker-server/src/test" \
      "$ROOT_DIR/im-gateway/im-ws-gateway/im-ws-gateway-server/src/test"; then
    printf 'No approved behavior tests use JUnit tag: %s\n' "$behavior_tag" >&2
    return 1
  fi

  # 先删除旧报告，确保测试数量只来自本次 behavior 执行。
  find "$broker_reports" "$gateway_reports" -type f -name 'TEST-*.xml' -delete 2>/dev/null || true

  printf '\n==> Running approved realtime behavior scenarios\n'
  # 先构建 reactor 依赖；Facade 没有测试引擎，不能直接对整个 -am reactor 应用 groups。
  mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server -am \
    -DskipTests install
  mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server \
    -Dgroups="$behavior_tag" test

  local executed_tests
  executed_tests="$(ruby -r rexml/document -e '
    total = ARGV.sum do |file|
      REXML::Document.new(File.read(file)).root.attributes["tests"].to_i
    end
    puts total
  ' $(find "$broker_reports" "$gateway_reports" -type f -name 'TEST-*.xml' -print 2>/dev/null))"
  if (( executed_tests == 0 )); then
    printf 'No tests executed for JUnit tag: %s\n' "$behavior_tag" >&2
    return 1
  fi
  printf 'Executed %d approved realtime behavior tests.\n' "$executed_tests"
}

# 运行真实 HTTP/WebSocket/Bolt 网络边界的实时黄金旅程，并保存可查询日志。
run_e2e() {
  local reports="$ROOT_DIR/im-test/im-e2e-test/target/surefire-reports"
  local runtime_dir="$ROOT_DIR/.harness/runtime"
  local runtime_log="$runtime_dir/e2e.log"
  local executed_tests
  mkdir -p "$reports" "$runtime_dir"
  find "$reports" -type f -name 'TEST-*.xml' -delete 2>/dev/null || true

  printf '\n==> Running realtime E2E golden path\n'
  if ! {
    mvn -q -pl im-test/im-e2e-test -am -DskipTests install \
      && mvn -q -pl im-test/im-e2e-test -Dgroups=e2e test
  } 2>&1 | tee "$runtime_log"; then
    printf '\n[e2e] WHAT: 实时 E2E 黄金旅程失败。\n' >&2
    printf 'WHY: HTTP、WebSocket、Bolt、Broker 路由或 Message Facade 边界没有按契约协作。\n' >&2
    printf 'FIX: 查看 %s，定位首个失败边界后重跑相同 workload。\n' \
      "${runtime_log#$ROOT_DIR/}" >&2
    return 1
  fi

  executed_tests="$(ruby -r rexml/document -e '
    puts ARGV.sum { |file| REXML::Document.new(File.read(file)).root.attributes["tests"].to_i }
  ' $(find "$reports" -type f -name 'TEST-*.xml' -print 2>/dev/null))"
  if (( executed_tests == 0 )); then
    printf '\n[e2e] WHAT: E2E 入口没有执行任何测试。\n' >&2
    printf 'WHY: 零测试会让缺失或错误的 JUnit Tag 冒充验证通过。\n' >&2
    printf 'FIX: 确认 im-e2e-test 存在 @Tag("e2e") 的黄金旅程。\n' >&2
    return 1
  fi
  printf 'Executed %d realtime E2E test(s); log: %s\n' \
    "$executed_tests" "${runtime_log#$ROOT_DIR/}"
}

# 验证全部可部署 Spring Boot 应用均有隔离的 startup-smoke，并拒绝零测试报告。
run_startup() {
  printf '\n==> Running all application startup smoke tests\n'
  "$ROOT_DIR/scripts/harness-startup.sh"
}

# 对齐课程 init：准备环境、运行基础门禁、验证启动入口并打印标准启动命令；默认不启动后台进程。
run_init() {
  run_readiness
  run_drift
  run_script_tests
  run_startup
  printf '\nStandard local startup order:\n'
  printf '  1. MySQL, Redis, Nacos\n'
  printf '  2. im-account, im-social, im-message\n'
  printf '  3. im-broker\n'
  printf '  4. im-ws-gateway\n'
  printf '  5. im-http-gateway\n'
  if [[ "${RUN_START_COMMAND:-0}" == "1" ]]; then
    if [[ -z "${START_COMMAND:-}" ]]; then
      printf '[init] RUN_START_COMMAND=1 requires explicit START_COMMAND; refusing implicit background startup.\n' >&2
      return 2
    fi
    printf '==> Executing explicit START_COMMAND\n'
    bash -lc "$START_COMMAND"
  else
    printf 'Set RUN_START_COMMAND=1 START_COMMAND="..." to execute an explicit foreground command.\n'
  fi
}

# 扫描 Harness 自有临时文件和陈旧 PID；默认只读，--apply 才执行严格 allowlist 清理。
run_cleanup() {
  "$ROOT_DIR/scripts/harness-cleanup.sh" "${2:-}"
}

# 生成或校验独立 AI Reviewer 质量结果；无 response 时返回 review_required。
run_quality() {
  "$ROOT_DIR/scripts/harness-quality.sh"
}

# 根据 baseline 或当前工作树计算模块；共享输入变化时保守升级为全量测试。
run_affected() {
  run_drift
  modules="$($ROOT_DIR/scripts/affected-modules.sh "${2:-}")"
  case "$modules" in
    none|docs) printf '\n==> No Java module tests are affected (%s)\n' "$modules" ;;
    all) printf '\n==> Shared build input changed; running full suite\n'; mvn -q test ;;
    *) printf '\n==> Running affected modules: %s\n' "$modules"; mvn -q -pl "$modules" -am test ;;
  esac
}

# 集中分发模式，使外围逻辑能为成功、失败和未知模式统一记录证据。
dispatch() {
  case "$MODE" in
  readiness)
    run_readiness
    ;;
  clean)
    run_clean
    ;;
  drift)
    run_drift
    ;;
  architecture)
    run_architecture
    ;;
  quick)
    run_drift
    run_script_tests
    run_architecture
    ;;
  full)
    run_full
    ;;
  affected)
    run_affected "$@"
    ;;
  behavior)
    run_behavior
    ;;
  e2e)
    run_e2e
    ;;
  startup)
    run_startup
    ;;
  init)
    run_init
    ;;
  cleanup)
    run_cleanup "$@"
    ;;
  quality)
    run_quality
    ;;
  report)
    "$ROOT_DIR/scripts/harness-report.sh"
    ;;
  handoff)
    "$ROOT_DIR/scripts/harness-handoff.sh"
    ;;
  *)
    printf 'Usage: %s {readiness|clean|cleanup|quality|drift|architecture|behavior|e2e|startup|init|quick|affected|full|report|handoff}\n' "$0" >&2
    return 2
    ;;
  esac
}

verification_command="./scripts/verify.sh${*:+ $*}"
started_at="$(date +%s)"
started_head="$(harness_head_revision "$ROOT_DIR")"
started_fingerprint="$(harness_worktree_fingerprint "$ROOT_DIR")"
set +e
(set -e; dispatch "$@")
exit_code=$?
set -e

finished_head="$(harness_head_revision "$ROOT_DIR")"
finished_fingerprint="$(harness_worktree_fingerprint "$ROOT_DIR")"
worktree_stable=true
if [[ "$started_head" != "$finished_head" \
    || "$started_fingerprint" != "$finished_fingerprint" ]]; then
  worktree_stable=false
  if (( exit_code == 0 )); then
    printf '\n[verification] WHAT: 验证期间仓库内容发生变化。\n' >&2
    printf 'WHY: 结果无法绑定到单一 revision/worktree，不能作为完成证据。\n' >&2
    printf 'FIX: 停止并发修改后重新运行相同验证命令。\n' >&2
    exit_code=1
  fi
fi

status="passed"
if (( exit_code != 0 )); then
  status="failed"
fi
record_verification "$MODE" "$status" "$(( $(date +%s) - started_at ))" "$started_at" \
  "$verification_command" "$started_head" "$finished_head" "$started_fingerprint" \
  "$finished_fingerprint" "$worktree_stable"

# full 无论成功或失败都尝试刷新报告与交接。原门禁失败优先保留；仅当门禁成功时才采用后处理失败码。
if [[ "$MODE" == "full" ]]; then
  set +e
  "$ROOT_DIR/scripts/harness-report.sh"
  report_exit=$?
  "$ROOT_DIR/scripts/harness-handoff.sh"
  handoff_exit=$?
  set -e
  if (( exit_code == 0 && report_exit != 0 )); then
    exit_code=$report_exit
  elif (( exit_code == 0 && handoff_exit != 0 )); then
    exit_code=$handoff_exit
  fi
fi
exit "$exit_code"
