#!/usr/bin/env bash
# 用途：把当前 Git、执行计划和 Harness report 派生为可供下一会话直接读取的交接报告。
# 输入：可选 Markdown 输出路径；测试可通过 HARNESS_ROOT_DIR 和当前 revision 环境变量使用隔离 fixture。
# 输出/副作用：写入 .harness/session-handoff.json 与 Markdown，不修改状态事实源或 Git index。
# 依赖：harness-evidence.sh、Ruby JSON/Open3 和 Git。
# 退出码：输入可读取且报告成功生成时返回 0；解析或写入失败时返回非 0。

set -euo pipefail

ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
OUTPUT_MD="${1:-$ROOT_DIR/.harness/session-handoff.md}"
OUTPUT_JSON="${HARNESS_HANDOFF_JSON:-$ROOT_DIR/.harness/session-handoff.json}"
REPORT_PATH="${HARNESS_REPORT_PATH:-$ROOT_DIR/.harness/report.json}"
source "$ROOT_DIR/scripts/lib/harness-evidence.sh"

current_head="${HARNESS_CURRENT_HEAD:-$(harness_head_revision "$ROOT_DIR")}"
current_fingerprint="${HARNESS_CURRENT_WORKTREE_FINGERPRINT:-$(harness_worktree_fingerprint "$ROOT_DIR")}"
mkdir -p "$(dirname "$OUTPUT_MD")" "$(dirname "$OUTPUT_JSON")"

# Ruby 负责结构化解析 Markdown/JSON 和 JSON 编码，避免 Shell 对引号与多行文本的脆弱拼接。
ruby -rjson -ropen3 -rtime -e '
  root, report_path, output_json, output_md, current_head, current_fingerprint = ARGV

  def section(markdown, heading)
    match = markdown.match(/^## #{Regexp.escape(heading)}\s*$\n(.*?)(?=^## |\z)/m)
    match ? match[1].strip : ""
  end

  def bullet_value(markdown, label)
    match = markdown.match(/^- #{Regexp.escape(label)}[：:]\s*(.+)$/)
    match ? match[1].strip.gsub(/`/, "") : ""
  end

  def markdown_item(value, fallback)
    normalized = value.to_s.strip
    normalized.empty? ? fallback : normalized
  end

  report = if File.file?(report_path)
    JSON.parse(File.read(report_path))
  else
    {
      "status" => "incomplete",
      "repository" => {},
      "tests" => {"status" => "unknown", "total" => 0},
      "verifications" => [],
      "failedVerifications" => []
    }
  end

  recorded_head = report.dig("repository", "headRevision")
  recorded_fingerprint = report.dig("repository", "worktreeFingerprint")
  freshness = if [current_head, current_fingerprint, recorded_head, recorded_fingerprint]
      .any? { |value| value.nil? || value == "unavailable" }
    "unavailable"
  elsif current_head == recorded_head && current_fingerprint == recorded_fingerprint
    "fresh"
  else
    "stale"
  end
  verified_status = freshness == "fresh" ? report.fetch("status", "incomplete") : freshness

  active_plans = Dir.glob(File.join(root, "docs/exec-plans/active/*.md"))
    .reject { |path| File.basename(path) == "README.md" }
    .sort
  active_plan = active_plans.first
  recovery = active_plan ? section(File.read(active_plan), "恢复状态") : ""
  progress_path = File.join(root, "PROGRESS.md")
  progress = File.file?(progress_path) ? File.read(progress_path) : ""
  next_steps = section(progress, "下一步（Next Steps）")

  current_task = bullet_value(recovery, "当前任务")
  completed = bullet_value(recovery, "已完成")
  blockers = bullet_value(recovery, "阻塞项")
  next_action = bullet_value(recovery, "下一步")
  do_not_touch = bullet_value(recovery, "不要修改")
  if active_plan.nil?
    current_task = bullet_value(section(progress, "当前状态（Current State）"), "当前任务")
    blockers = bullet_value(section(progress, "阻塞项"), "")
    next_action = next_steps.lines.map(&:strip).reject(&:empty?).join(" ")
  end

  status_output, status_error, status = Open3.capture3("git", "-C", root, "status", "--short")
  raise "git status failed: #{status_error}" unless status.success?
  changes = status_output.lines.map(&:rstrip).reject(&:empty?)

  verifications = Array(report["verifications"])
  verification_summary = verifications.map do |entry|
    {
      "mode" => entry["mode"],
      "status" => entry["status"],
      "freshness" => entry["freshness"],
      "command" => entry["command"]
    }
  end
  stale_or_missing = verification_summary
    .select { |entry| entry["freshness"] != "fresh" }
    .map { |entry| entry["mode"] }
    .compact

  handoff = {
    "generatedAt" => Time.now.utc.iso8601,
    "repository" => {
      "headRevision" => current_head,
      "worktreeFingerprint" => current_fingerprint
    },
    "sourceReport" => report_path.delete_prefix(root + File::SEPARATOR),
    "evidenceFreshness" => freshness,
    "currentlyVerified" => {
      "status" => verified_status,
      "testStatus" => report.dig("tests", "status") || "unknown",
      "testTotal" => report.dig("tests", "total").to_i,
      "verifications" => verification_summary
    },
    "changes" => {
      "count" => changes.length,
      "gitStatus" => changes,
      "completedSummary" => completed
    },
    "unverified" => {
      "failedVerifications" => Array(report["failedVerifications"]),
      "staleOrMissingVerifications" => stale_or_missing,
      "knownBlockers" => blockers
    },
    "workState" => {
      "activePlan" => active_plan&.delete_prefix(root + File::SEPARATOR),
      "currentTask" => current_task
    },
    "nextAction" => {
      "priority" => next_action,
      "reason" => active_plan ? "继续唯一 active execution plan 的恢复步骤。" : "当前没有 active plan，等待明确批准的下一项工作。",
      "passingCriteria" => active_plan ? "完成计划中的当前任务并记录可复查证据。" : "建立已批准的目标与 active execution plan。",
      "doNotTouch" => do_not_touch
    },
    "commands" => {
      "startup" => "./scripts/verify.sh init",
      "verify" => "./scripts/verify.sh full",
      "focusedDebug" => "./scripts/verify.sh affected"
    }
  }
  File.write(output_json, JSON.pretty_generate(handoff) + "\n")

  verification_lines = verification_summary.map do |entry|
    "- `#{entry["mode"]}`：#{entry["status"] || "unknown"} / #{entry["freshness"] || "unknown"}（`#{entry["command"] || "unknown"}`）"
  end
  verification_lines = ["- 尚无验证记录。"] if verification_lines.empty?
  change_lines = changes.map { |change| "- `#{change.gsub("`", "\\`")}`" }
  change_lines = ["- 当前 Git 状态没有改动。"] if change_lines.empty?
  failed = Array(report["failedVerifications"])
  failed_lines = failed.map { |mode| "- 失败验证：`#{mode}`" }
  failed_lines << "- 证据新鲜度：`#{freshness}`。" unless freshness == "fresh"
  failed_lines << "- 陈旧或未完成入口：#{stale_or_missing.map { |mode| "`#{mode}`" }.join(", ")}。" unless stale_or_missing.empty?
  failed_lines << "- 已知阻塞：#{markdown_item(blockers, "none")}" unless blockers.empty? || blockers == "none" || blockers == "none。"
  failed_lines = ["- 没有从当前事实源发现失败、陈旧证据或阻塞项。"] if failed_lines.empty?

  markdown = <<~MARKDOWN
    # 会话交接

    本报告由 Harness 自动生成，绑定 HEAD `#{current_head}` 与 worktree fingerprint `#{current_fingerprint}`。它是派生视图，任务状态仍以 `PROGRESS.md` 和 active execution plan 为准。

    ## 当前已验证

    - 综合状态：`#{verified_status}`；证据新鲜度：`#{freshness}`。
    - 测试状态：`#{handoff.dig("currentlyVerified", "testStatus")}`；当前统计：#{handoff.dig("currentlyVerified", "testTotal")}。
    #{verification_lines.join("\n")}

    ## 本轮改动

    - Git 状态条目：#{changes.length}。
    - 计划已完成摘要：#{markdown_item(completed, "未记录")}
    #{change_lines.join("\n")}

    ## 仍损坏或未验证

    #{failed_lines.join("\n")}

    ## 下一步最佳动作

    - 最高优先级：#{markdown_item(next_action, "等待明确任务")}
    - 原因：#{handoff.dig("nextAction", "reason")}
    - Passing 标准：#{handoff.dig("nextAction", "passingCriteria")}
    - 不要修改：#{markdown_item(do_not_touch, "未记录额外边界")}

    ## 命令

    - 初始化与启动说明：`#{handoff.dig("commands", "startup")}`
    - 完整验证：`#{handoff.dig("commands", "verify")}`
    - 定向调试：`#{handoff.dig("commands", "focusedDebug")}`
  MARKDOWN
  File.write(output_md, markdown)
' "$ROOT_DIR" "$REPORT_PATH" "$OUTPUT_JSON" "$OUTPUT_MD" \
  "$current_head" "$current_fingerprint"

printf 'Harness handoff JSON: %s\n' "$OUTPUT_JSON"
printf 'Harness handoff Markdown: %s\n' "$OUTPUT_MD"
