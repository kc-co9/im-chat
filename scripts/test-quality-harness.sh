#!/usr/bin/env bash
# 用途：验证 quality request、独立 response 校验、自动分数合成和 A/B/C/D 映射。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; FIXTURE_ROOT="$(mktemp -d)"; trap 'rm -rf "$FIXTURE_ROOT"' EXIT
mkdir -p "$FIXTURE_ROOT/scripts/lib" "$FIXTURE_ROOT/docs/references" "$FIXTURE_ROOT/im-common" "$FIXTURE_ROOT/.harness"
cp "$ROOT_DIR/scripts/harness-quality.sh" "$FIXTURE_ROOT/scripts/harness-quality.sh"; cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$FIXTURE_ROOT/scripts/lib/harness-evidence.sh"; chmod +x "$FIXTURE_ROOT/scripts/harness-quality.sh"
printf 'module readme\n' > "$FIXTURE_ROOT/im-common/README.md"; cp "$ROOT_DIR/docs/references/QUALITY_REVIEW_PROMPT.md" "$FIXTURE_ROOT/docs/references/QUALITY_REVIEW_PROMPT.md"
cat > "$FIXTURE_ROOT/.harness/report.json" <<'EOF'
{"status":"passed","tests":{"status":"current","failures":0},"runtimeEvidence":{"e2e":{"status":"verified"}}}
EOF
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then printf 'Missing response should require review.\n' >&2; exit 1; else code=$?; [[ $code -eq 3 ]] || exit 1; fi
[[ -f "$FIXTURE_ROOT/.harness/quality/review-request.json" ]] || { printf 'Fresh clone must create a local review request under .harness.\n' >&2; exit 1; }
request="$FIXTURE_ROOT/.harness/quality/review-request.json"
ruby -rjson -rfileutils -e '
  request = JSON.parse(File.read(ARGV.fetch(0))); root = ARGV.fetch(1)
  request.fetch("units").each { |unit| unit.fetch("paths").each { |path| dir = File.join(root, path); FileUtils.mkdir_p(dir); File.write(File.join(dir, "README.md"), "unit\n") } }
' "$request" "$FIXTURE_ROOT"
rmm="${FIXTURE_ROOT}/.harness/quality/reviewer-response.json"; rm -f "$rmm"
HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1 || true
request="$FIXTURE_ROOT/.harness/quality/review-request.json"
printf '{"requestId":"stale","reviewScopeFingerprint":"stale","verdict":"Accept","units":[]}\n' > "$rmm"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Stale reviewer response should require a new review.\n' >&2
  exit 1
else
  code=$?
  [[ $code -eq 3 ]] || { printf 'Stale reviewer response must return review_required (3), got %d.\n' "$code" >&2; exit 1; }
fi
ruby -rjson -e '
  request = JSON.parse(File.read(ARGV.fetch(0))); units = request.fetch("units");
  response = {"requestId" => request["requestId"], "reviewScopeFingerprint" => request["reviewScopeFingerprint"], "verdict" => "Accept", "units" => units.map { |unit| {"unit" => unit["unit"], "unitFingerprint" => unit["unitFingerprint"], "scores" => {"correctness" => 2, "scopeDiscipline" => 2, "maintainability" => 2}, "evidence" => {"correctness" => ["#{unit["paths"].first}/README.md"], "scopeDiscipline" => ["#{unit["paths"].first}/README.md"], "maintainability" => ["#{unit["paths"].first}/README.md"]}, "findings" => []} } }
  File.write(ARGV.fetch(1), JSON.pretty_generate(response) + "\n")
' "$request" "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null
ruby -rjson -e 'snapshot = JSON.parse(File.read(ARGV.fetch(0))); exit(snapshot.dig("units", 0, "grade") == "B" ? 0 : 1)' "$FIXTURE_ROOT/.harness/quality/quality-snapshot.json"

# Reviewer 也可以把带证据的维度对象直接放在 unit 顶层，校验器必须保持同一语义。
ruby -rjson -e '
  path = ARGV.fetch(0); response = JSON.parse(File.read(path))
  response.fetch("units").each do |unit|
    scores = unit.delete("scores"); evidence = unit.delete("evidence")
    scores.each { |dimension, score| unit[dimension] = {"score" => score, "evidence" => evidence.fetch(dimension), "reason" => "fixture"} }
  end
  File.write(path, JSON.pretty_generate(response) + "\n")
' "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null

# 执行状态变化只改变 worktree fingerprint，模块 review scope 未变时必须复用同一 Reviewer response。
if ! HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head \
    HARNESS_CURRENT_WORKTREE_FINGERPRINT=execution-state-changed \
    "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Unchanged review scope should reuse the existing Reviewer response.\n' >&2
  exit 1
fi
printf 'Quality Harness tests passed.\n'
