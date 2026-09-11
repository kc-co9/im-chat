#!/usr/bin/env bash
# 用途：聚合低误报源码规则、SQL 规则、必需文档、生成物和 Markdown 链接漂移检查。
# 输入：无位置参数；检查当前工作树，Markdown 链接范围限定为 Git 已跟踪文件。
# 输出/副作用：集中输出全部违规组；除读取子 checker 外不修改源码或 Git index。
# 依赖：git、rg、Ruby，以及 check-java-style.sh、check-sql.sh。
# 退出码：所有检查通过返回 0；存在任一违规组或 Markdown 枚举失败返回 1。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

if ! java_style_output="$("$ROOT_DIR/scripts/check-java-style.sh" 2>&1)"; then
  printf '%s\n' "$java_style_output"
  failures=$((failures + 1))
fi

if ! sql_output="$("$ROOT_DIR/scripts/check-sql.sh" 2>&1)"; then
  printf '%s\n' "$sql_output"
  failures=$((failures + 1))
fi

# 汇总一个违规组；参数为标题和匹配内容，非空时输出诊断并累加失败数。
report_matches() {
  local title="$1"
  local matches="$2"
  local why="该状态会使仓库事实、验证反馈或后续变更发生漂移。"
  local fix="按下方路径修复违规，并重新运行 scripts/check-drift.sh。"
  if [[ -n "$matches" ]]; then
    case "$title" in
      "Generated output or local metadata is tracked")
        why="生成物和本机元数据不可复现，会污染 Review 与发布内容。"
        fix="从版本控制中移除列出的生成物，并由构建流程按需重新生成。"
        ;;
      "Required Harness documentation is missing"|"PROGRESS.md current-work index is stale"|"Active execution plan structure is incomplete"|"Active execution plan task state is invalid")
        why="缺失或陈旧的状态入口会让新会话无法恢复当前事实。"
        fix="补齐列出的文档或同步 PROGRESS 与 active execution plan。"
        ;;
      "Local Markdown links must resolve")
        why="断链会隐藏所属规范、模块事实或执行状态。"
        fix="修正下方源文件中的相对路径，或恢复其明确依赖的目标文档。"
        ;;
      "Feature catalog structure is invalid")
        why="行为 ID、验证入口或覆盖状态不可靠时，Harness 无法判断哪些稳定行为真正受保护。"
        fix="按 docs/product-specs/FEATURES.md 的列和枚举修正条目；当前运行新鲜度由 Harness report 维护。"
        ;;
      "Tests must use controllable time or synchronization instead of real waits")
        why="真实等待依赖机器调度，会产生慢且偶发的测试。"
        fix="注入可控 Clock、Scheduler 或同步信号替代 sleep。"
        ;;
    esac
    printf '\n[drift] WHAT: %s\nWHY: %s\nFIX: %s\n%s\n' \
      "$title" "$why" "$fix" "$matches"
    failures=$((failures + 1))
  fi
}

tracked_generated="$(git ls-files | rg '(^|/)(target|node_modules|dist|\.idea)(/|$)|(^|/)\.DS_Store$|\.log$' || true)"
report_matches "Generated output or local metadata is tracked" "$tracked_generated"

java_var="$(rg -n --glob '*.java' --glob '!**/target/**' '(^|[[:space:]])var[[:space:]]+[A-Za-z_$][A-Za-z0-9_$]*[[:space:]]*=' . || true)"
report_matches "Java var declarations are not allowed; use an explicit type" "$java_var"

production_test_doubles="$(rg -n --glob '*/src/main/java/**/*.java' '(class|record|interface)[[:space:]]+(Noop|Fake|Stub|Mock)[A-Za-z0-9_$]*' . || true)"
report_matches "Test doubles must not live in production source sets" "$production_test_doubles"

test_console_output="$(rg -n --glob '**/src/test/**/*.java' '(System\.(out|err)\.|printStackTrace\()' . || true)"
report_matches "Tests must use assertions instead of console output" "$test_console_output"

production_console_output="$(rg -n --glob '**/src/main/java/**/*.java' '(System\.(out|err)\.|printStackTrace\()' . || true)"
report_matches "Production code must use the project logger instead of console output" "$production_console_output"

manual_object_mapper="$(rg -n --glob '**/src/main/java/**/*.java' 'new[[:space:]]+ObjectMapper[[:space:]]*\(' . --glob '!im-common/src/main/java/com/co/kc/imchat/common/utils/JsonUtils.java' || true)"
report_matches "Production code must use JsonUtils instead of creating ObjectMapper" "$manual_object_mapper"

field_injection="$(rg -n -U --pcre2 --glob '**/src/main/java/**/*.java' '@Autowired[[:space:]]*\n[[:space:]]*(private|protected|public)?[[:space:]]*(final[[:space:]]+)?[A-Za-z_$][A-Za-z0-9_$<>?, .]*[[:space:]]+[A-Za-z_$][A-Za-z0-9_$]*[[:space:]]*;' . || true)"
report_matches "Production dependencies must use constructor injection" "$field_injection"

test_real_waits="$(rg -n --glob '**/src/test/**/*.java' '(Thread\.sleep\(|TimeUnit\.[A-Z]+\.sleep\()' . || true)"
report_matches "Tests must use controllable time or synchronization instead of real waits" "$test_real_waits"

disabled_without_reason="$(rg -n --glob '**/src/test/**/*.java' '@Disabled[[:space:]]*$|@Disabled\([[:space:]]*\)' . || true)"
report_matches "Disabled tests must document a reason" "$disabled_without_reason"

legacy_modules="$(for module in im-application im-bootstrap im-domain im-infrastructure im-interfaces; do
  [[ -e "$module" ]] && printf '%s\n' "$module"
done; true)"
report_matches "Removed top-level modules must not be reintroduced" "$legacy_modules"

required_docs="$(for file in \
  AGENTS.md \
  ARCHITECTURE.md \
  PROGRESS.md \
  .java-version \
  .nvmrc \
  im-test/AGENTS.md \
  im-test/README.md \
  im-test/im-architecture-test/README.md \
  im-test/im-e2e-test/README.md \
  im-gateway/ARCHITECTURE.md \
  im-broker/ARCHITECTURE.md \
  im-service/im-message/ARCHITECTURE.md \
  docs/design-docs/index.md \
  docs/exec-plans/TEMPLATE.md \
  docs/exec-plans/active/README.md \
  docs/exec-plans/completed/README.md \
  docs/exec-plans/tech-debt-tracker.md \
  docs/product-specs/index.md \
  docs/product-specs/FEATURES.md \
  docs/references/index.md \
  docs/PLANS.md \
  docs/RELIABILITY.md \
  docs/SECURITY.md; do
  [[ -f "$file" ]] || printf '%s\n' "$file"
done; true)"
report_matches "Required Harness documentation is missing" "$required_docs"

# Feature catalog 只保存稳定覆盖关系；当前运行结果由 revision-bound report 维护。
feature_catalog_violations=""
if [[ -f docs/product-specs/FEATURES.md ]]; then
  feature_catalog_violations="$(ruby -e '
    required = ["ID", "Owner", "可观察行为", "验证入口", "覆盖状态", "证据边界"]
    allowed = %w[uncovered partial covered blocked]
    lines = File.readlines("docs/product-specs/FEATURES.md", encoding: "UTF-8")
    header_index = nil
    columns = nil
    lines.each_with_index do |line, index|
      next unless line.start_with?("|")
      cells = line.split("|", -1)[1...-1].map(&:strip)
      if (required - cells).empty?
        header_index = index
        columns = cells
        break
      end
    end
    if header_index.nil?
      puts "docs/product-specs/FEATURES.md: missing required columns: #{required.join(", ")}"
      exit
    end

    indexes = required.to_h { |name| [name, columns.index(name)] }
    ids = {}
    rows = 0
    lines[(header_index + 1)..].to_a.each_with_index do |line, offset|
      break unless line.start_with?("|")
      cells = line.split("|", -1)[1...-1].map(&:strip)
      next if cells.all? { |cell| cell.match?(/\A:?-+:?\z/) }
      rows += 1
      line_number = header_index + offset + 2
      values = indexes.to_h { |name, column| [name, cells.fetch(column, "").delete("`").strip] }
      required.each do |name|
        puts "docs/product-specs/FEATURES.md:#{line_number}: #{name} must not be empty" if values[name].empty?
      end
      id = values["ID"]
      if !id.empty? && ids.key?(id)
        puts "docs/product-specs/FEATURES.md:#{line_number}: duplicate ID #{id} (first at line #{ids[id]})"
      elsif !id.empty?
        ids[id] = line_number
      end
      status = values["覆盖状态"]
      unless status.empty? || allowed.include?(status)
        puts "docs/product-specs/FEATURES.md:#{line_number}: unsupported coverage status #{status.inspect}; expected #{allowed.join(", ")}"
      end
    end
    puts "docs/product-specs/FEATURES.md: catalog must contain at least one behavior row" if rows.zero?
  ')"
fi
report_matches "Feature catalog structure is invalid" "$feature_catalog_violations"

# Active plan 使用稳定章节作为跨会话和工具消费契约；只检查结构，业务语义保留给 Review。
active_plan_structure=""
active_plan_paths="$(find docs/exec-plans/active -type f -name '*.md' ! -name README.md -print | sort)"
if [[ -n "$active_plan_paths" ]]; then
  while IFS= read -r active_plan; do
    for required_heading in \
      '^## (Sprint Contract|迭代契约（Sprint Contract）)$' \
      '^## 事实源$' \
      '^## 验证分层$' \
      '^## 任务状态$' \
      '^## (跨会话)?恢复状态$' \
      '^## 回滚与残余风险$'; do
      if ! rg -q "$required_heading" "$active_plan"; then
        active_plan_structure+="$active_plan: missing heading matching $required_heading"$'\n'
      fi
    done
  done <<< "$active_plan_paths"
fi
report_matches "Active execution plan structure is incomplete" "$active_plan_structure"

# 按 Markdown 表头定位任务状态列，只约束稳定枚举；目标、范围和证据内容仍由 Review 判断。
invalid_plan_states="$(ruby -e '
  allowed = %w[not_started active blocked passing]
  Dir.glob("docs/exec-plans/active/*.md").sort.each do |file|
    next if File.basename(file) == "README.md"
    in_task_section = false
    state_column = nil
    found_state_column = false
    task_rows = 0
    File.readlines(file, encoding: "UTF-8").each_with_index do |line, index|
      if line.start_with?("## ")
        in_task_section = line.strip == "## 任务状态"
        state_column = nil if in_task_section
        next
      end
      next unless in_task_section && line.start_with?("|")
      cells = line.split("|", -1)[1...-1].map(&:strip)
      if state_column.nil?
        state_column = cells.index("状态") || cells.index("Status")
        found_state_column = !state_column.nil?
        next
      end
      next if cells.all? { |cell| cell.match?(/\A:?-+:?\z/) }
      task_rows += 1
      state = cells.fetch(state_column, "").delete("`").strip
      next if allowed.include?(state)
      puts "#{file}:#{index + 1}: unsupported task state #{state.inspect}; expected #{allowed.join(", ")}"
    end
    puts "#{file}: task-state table must contain a 状态 column" unless found_state_column
    puts "#{file}: task-state table must contain at least one task row" if found_state_column && task_rows.zero?
  end
')"
report_matches "Active execution plan task state is invalid" "$invalid_plan_states"

progress_drift=""
if [[ -f PROGRESS.md ]]; then
  if [[ -n "$active_plan_paths" ]]; then
    while IFS= read -r active_plan; do
      if ! rg -Fq "$active_plan" PROGRESS.md; then
        progress_drift+="PROGRESS.md does not reference active plan: $active_plan"$'\n'
      fi
    done <<< "$active_plan_paths"
  elif ! rg -q '活跃计划：.*`?none`?' PROGRESS.md; then
    progress_drift="PROGRESS.md must state active plan: none when no active plan exists"
  fi
fi
report_matches "PROGRESS.md current-work index is stale" "$progress_drift"

legacy_docs="$([[ -e docs/superpowers ]] && printf '%s\n' docs/superpowers || true)"
report_matches "Legacy documentation directories must not be reintroduced" "$legacy_docs"

broken_links="$(ruby -e '
  root = Dir.pwd
  begin
    git_output = IO.popen(["git", "ls-files", "-z", "--", "*.md"], &:read)
  rescue SystemCallError => error
    puts "git ls-files -- *.md: failed (#{error.class}: #{error.message})"
    exit
  end
  git_status = $?
  unless git_status.success?
    detail = git_status.exitstatus || "signal #{git_status.termsig}"
    puts "git ls-files -- *.md: failed with #{detail}"
    exit
  end

  files = git_output.split("\0").reject(&:empty?)
  files.each do |file|
    # Git index 仍列出的工作树删除项不是待检查源文件；指向它的现存链接仍会在目标解析时失败。
    next unless File.exist?(file)
    begin
      content = File.read(file)
    rescue SystemCallError, IOError => error
      puts "#{file}: tracked Markdown file is missing or unreadable (#{error.class}: #{error.message})"
      next
    end
    content.scan(/\[[^\]]+\]\(([^)]+)\)/).flatten.each do |target|
      next if target.match?(/\A(?:https?:|mailto:|#)/)
      path = target.split("#", 2).first
      next if path.empty?
      resolved = File.expand_path(path, File.dirname(File.join(root, file)))
      puts "#{file}: #{target}" unless File.exist?(resolved)
    end
  end
')"
report_matches "Local Markdown links must resolve" "$broken_links"

if (( failures > 0 )); then
  printf '\nDrift checks failed with %d violation group(s).\n' "$failures"
  exit 1
fi

printf 'Drift checks passed.\n'
