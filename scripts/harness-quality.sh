#!/usr/bin/env bash
# 用途：生成 AI Reviewer request，校验独立 response，并合成模块 A/B/C/D 质量快照。
# 输入：无；response 可预先写入 .harness/quality/reviewer-response.json。
# 输出/副作用：写入 .harness/quality 下的忽略产物；缺 response 时返回 3（review_required）。
# 依赖：Ruby JSON、Git、rg；不调用模型、不读取密钥、不修改稳定状态文件。
set -euo pipefail
ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
QUALITY_DIR="$ROOT_DIR/.harness/quality"; mkdir -p "$QUALITY_DIR"
REQUEST="$QUALITY_DIR/review-request.json"; RESPONSE="$QUALITY_DIR/reviewer-response.json"; SNAPSHOT="$QUALITY_DIR/quality-snapshot.json"; SNAPSHOT_MD="$QUALITY_DIR/quality-snapshot.md"
HEAD="${HARNESS_CURRENT_HEAD:-$(git -C "$ROOT_DIR" rev-parse --verify HEAD 2>/dev/null || printf unavailable)}"
FINGERPRINT="${HARNESS_CURRENT_WORKTREE_FINGERPRINT:-$(source "$ROOT_DIR/scripts/lib/harness-evidence.sh"; harness_worktree_fingerprint "$ROOT_DIR")}"

ruby -rjson -rdigest -e '
  root, output, head, fingerprint = ARGV
  units = {
    "im-common" => ["im-common"], "im-plugin" => ["im-plugin"], "im-gateway" => ["im-gateway"], "im-broker" => ["im-broker"],
    "im-account" => ["im-service/im-account"], "im-social" => ["im-service/im-social"], "im-message" => ["im-service/im-message"],
    "im-iam" => ["im-management/im-iam"], "im-admin" => ["im-management/im-admin"], "im-monitor" => ["im-management/im-monitor"],
    "im-audit" => ["im-management/im-audit"], "im-test" => ["im-test"]
  }
  entries = units.map do |name, paths|
    digest = Digest::SHA256.new
    files = paths.flat_map { |path| Dir.glob(File.join(root, path, "**/*")).select { |file| File.file?(file) } }
      .reject { |file| file.include?("/.harness/") || file.include?("/target/") || file.end_with?("PROGRESS.md") }
      .sort
    files.each { |file| digest.update(file.delete_prefix(root + File::SEPARATOR)); digest.update(File.binread(file)) }
    {"unit" => name, "paths" => paths, "unitFingerprint" => digest.hexdigest,
     "evidence" => paths.map { |path| "#{path}/README.md" }}
  end
  review_scope_fingerprint = Digest::SHA256.hexdigest(entries.to_json)
  request = {"requestId" => "quality-#{head}-#{review_scope_fingerprint[0, 12]}", "headRevision" => head,
    "worktreeFingerprint" => fingerprint, "reviewScopeFingerprint" => review_scope_fingerprint,
    "reviewerPrompt" => "docs/references/QUALITY_REVIEW_PROMPT.md", "units" => entries,
    "instructions" => "Score correctness, scopeDiscipline, maintainability only; do not edit repository files."}
  File.write(output, JSON.pretty_generate(request) + "\n")
' "$ROOT_DIR" "$REQUEST" "$HEAD" "$FINGERPRINT"

if [[ ! -f "$RESPONSE" ]]; then
  printf 'Quality review required. Request: %s\n' "$REQUEST"
  exit 3
fi

ruby -rjson -rtime -e '
  root, request_path, response_path, snapshot, snapshot_md = ARGV
  request = JSON.parse(File.read(request_path)); response = JSON.parse(File.read(response_path))
  unless response["requestId"] == request["requestId"] &&
      response["reviewScopeFingerprint"] == request["reviewScopeFingerprint"]
    warn "Quality review required: reviewer response does not match the current request/scope."
    exit 3
  end
  units = response.fetch("units"); expected = request.fetch("units").map { |u| [u["unit"], u["unitFingerprint"]] }.to_h
  abort "reviewer response unit set mismatch" unless units.map { |u| u["unit"] }.sort == expected.keys.sort
  semantic = %w[correctness scopeDiscipline maintainability]
  units.each do |unit|
    abort "unit fingerprint mismatch" unless unit["unitFingerprint"] == expected.fetch(unit["unit"])
    semantic.each do |dimension|
      score_entry = unit.dig("scores", dimension) || unit[dimension]
      score = score_entry.is_a?(Hash) ? score_entry["score"] : score_entry
      evidence = score_entry.is_a?(Hash) ? score_entry["evidence"] : unit.dig("evidence", dimension)
      abort "invalid score" unless score.is_a?(Integer) && score.between?(0, 2)
      abort "missing evidence" unless Array(evidence).any? { |path| File.file?(File.join(root, path)) }
    end
  end
  auto = {"verification" => 0, "reliability" => 0, "handoffReadiness" => 0}
  report_path = File.join(root, ".harness/report.json")
  report = File.file?(report_path) ? JSON.parse(File.read(report_path)) : {}
  auto["verification"] = 2 if report.dig("tests", "status") == "current" && report.dig("tests", "failures").to_i == 0
  auto["reliability"] = 2 if report.dig("runtimeEvidence", "e2e", "status") == "verified"
  auto["handoffReadiness"] = 2 if report["status"] == "passed" && File.file?(File.join(root, ".harness/session-handoff.json"))
  rows = units.map do |unit|
    normalized_semantic_scores = semantic.to_h do |dimension|
      entry = unit.dig("scores", dimension) || unit[dimension]
      [dimension, (entry.is_a?(Hash) ? entry["score"] : entry).to_i]
    end
    semantic_score = normalized_semantic_scores.values.sum
    total = semantic_score + auto.values.sum
    verdict = response["verdict"] || "Revise"
    grade = if verdict == "Block" then "D" elsif verdict == "Revise" then [total, 10].min >= 11 ? "B" : (total >= 9 ? "B" : (total >= 6 ? "C" : "D")) elsif total >= 11 then "A" elsif total >= 9 then "B" elsif total >= 6 then "C" else "D" end
    {"unit" => unit["unit"], "unitFingerprint" => unit["unitFingerprint"], "scores" => auto.merge(normalized_semantic_scores), "total" => total, "grade" => grade, "findings" => unit["findings"] || []}
  end
  output = {"generatedAt" => Time.now.utc.iso8601, "requestId" => request["requestId"], "reviewScopeFingerprint" => request["reviewScopeFingerprint"], "headRevision" => request["headRevision"], "verdict" => response["verdict"], "units" => rows}
  File.write(snapshot, JSON.pretty_generate(output) + "\n")
  md = ["# 模块质量快照", "", "本快照由当前 review scope 与独立 AI Reviewer response 合成。", "", "| 模块 | 等级 | 总分 | 关键缺口 |", "|---|---:|---:|---|"]
  rows.each { |row| md << "| `#{row["unit"]}` | **#{row["grade"]}** | #{row["total"]}/12 | #{Array(row["findings"]).join("；")} |" }
  File.write(snapshot_md, md.join("\n") + "\n")
' "$ROOT_DIR" "$REQUEST" "$RESPONSE" "$SNAPSHOT" "$SNAPSHOT_MD"
printf 'Quality snapshot: %s\n' "$SNAPSHOT_MD"
