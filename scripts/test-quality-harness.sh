#!/usr/bin/env bash
# 用途：验证 quality request、独立 response 校验、自动分数合成和 A/B/C/D 映射。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; FIXTURE_ROOT="$(mktemp -d)"; trap 'rm -rf "$FIXTURE_ROOT"' EXIT
mkdir -p "$FIXTURE_ROOT/scripts/lib" "$FIXTURE_ROOT/docs/references" "$FIXTURE_ROOT/im-common" "$FIXTURE_ROOT/.harness"
cp "$ROOT_DIR/scripts/harness-quality.sh" "$FIXTURE_ROOT/scripts/harness-quality.sh"; cp "$ROOT_DIR/scripts/lib/harness-evidence.sh" "$FIXTURE_ROOT/scripts/lib/harness-evidence.sh"; chmod +x "$FIXTURE_ROOT/scripts/harness-quality.sh"
printf 'module readme\n' > "$FIXTURE_ROOT/im-common/README.md"; cp "$ROOT_DIR/docs/references/QUALITY_REVIEW_PROMPT.md" "$FIXTURE_ROOT/docs/references/QUALITY_REVIEW_PROMPT.md"
mkdir -p "$FIXTURE_ROOT/im-common/src/main/java" "$FIXTURE_ROOT/im-common/src/test/java"
printf 'source\n' > "$FIXTURE_ROOT/im-common/src/main/java/Fixture.java"
printf 'test\n' > "$FIXTURE_ROOT/im-common/src/test/java/FixtureTest.java"
printf 'unlisted\n' > "$FIXTURE_ROOT/im-common/not-listed.txt"
printf '.harness/\nlogs/\nim-common/not-listed.txt\n' > "$FIXTURE_ROOT/.gitignore"
git -C "$FIXTURE_ROOT" init -q
git -C "$FIXTURE_ROOT" add .
git -C "$FIXTURE_ROOT" -c user.name=fixture -c user.email=fixture@example.invalid commit -qm fixture
cat > "$FIXTURE_ROOT/.harness/report.json" <<'EOF'
{"status":"passed","tests":{"status":"current","failures":0},"runtimeEvidence":{"e2e":{"status":"verified"}}}
EOF
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then printf 'Missing response should require review.\n' >&2; exit 1; else code=$?; [[ $code -eq 3 ]] || exit 1; fi
[[ -f "$FIXTURE_ROOT/.harness/quality/review-request.json" ]] || { printf 'Fresh clone must create a local review request under .harness.\n' >&2; exit 1; }
request="$FIXTURE_ROOT/.harness/quality/review-request.json"
ruby -E UTF-8 -rjson -e '
  request = JSON.parse(File.read(ARGV.fetch(0)))
  expected = %w[pom.xml scripts deploy docs]
  abort "quality request must disclose shared review paths" unless
    expected.all? { |path| request.fetch("sharedReviewPaths").include?(path) }
  unit = request.fetch("units").find { |entry| entry.fetch("unit") == "im-common" }
  evidence = unit.fetch("evidence")
  %w[im-common/README.md im-common/src/main/java/Fixture.java im-common/src/test/java/FixtureTest.java scripts/harness-quality.sh].each do |path|
    abort "quality request must include complete evidence closure: #{path}" unless evidence.include?(path)
  end
  %w[PROGRESS.md .harness/report.json im-common/logs/runtime.log].each do |path|
    abort "quality request must exclude state or ignored output: #{path}" if evidence.include?(path)
  end
' "$request"
ruby -E UTF-8 -rjson -rfileutils -e '
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
ruby -E UTF-8 -rjson -e '
  request = JSON.parse(File.read(ARGV.fetch(0))); units = request.fetch("units");
  response = {"requestId" => request["requestId"], "reviewScopeFingerprint" => request["reviewScopeFingerprint"], "verdict" => "Accept", "units" => units.map { |unit| {"unit" => unit["unit"], "unitFingerprint" => unit["unitFingerprint"], "scores" => {"correctness" => 2, "scopeDiscipline" => 2, "maintainability" => 2}, "evidence" => {"correctness" => ["#{unit["paths"].first}/README.md"], "scopeDiscipline" => ["#{unit["paths"].first}/README.md"], "maintainability" => ["#{unit["paths"].first}/README.md"]}, "findings" => []} } }
  File.write(ARGV.fetch(1), JSON.pretty_generate(response) + "\n")
' "$request" "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null
cp "$FIXTURE_ROOT/.harness/quality/reviewer-response.json" "$FIXTURE_ROOT/.harness/quality/reviewer-response.valid.json"

# 语义证据必须逐项属于 request allowlist，且不能用绝对路径或未列文件冒充当前证据。
ruby -E UTF-8 -rjson -e '
  path = ARGV.fetch(0); response = JSON.parse(File.read(path)); unit = response.fetch("units").first
  unit.fetch("evidence")["correctness"] = ["/etc/passwd"]
  File.write(path, JSON.pretty_generate(response) + "\n")
' "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint \
    "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Unlisted absolute evidence path should require review.\n' >&2
  exit 1
else
  code=$?
  [[ $code -eq 1 ]] || { printf 'Invalid evidence path must fail schema validation, got %d.\n' "$code" >&2; exit 1; }
fi
cp "$FIXTURE_ROOT/.harness/quality/reviewer-response.valid.json" "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"

ruby -E UTF-8 -rjson -e '
  path = ARGV.fetch(0); response = JSON.parse(File.read(path)); unit = response.fetch("units").first
  unit.fetch("evidence")["correctness"] = []
  File.write(path, JSON.pretty_generate(response) + "\n")
' "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint \
    "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Empty evidence must require review.\n' >&2
  exit 1
else
  code=$?
  [[ $code -eq 1 ]] || { printf 'Empty evidence must fail schema validation, got %d.\n' "$code" >&2; exit 1; }
fi
cp "$FIXTURE_ROOT/.harness/quality/reviewer-response.valid.json" "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"

ruby -E UTF-8 -rjson -e '
  path = ARGV.fetch(0); response = JSON.parse(File.read(path)); unit = response.fetch("units").first
  unit.fetch("evidence")["correctness"] = ["../im-common/README.md"]
  File.write(path, JSON.pretty_generate(response) + "\n")
' "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint \
    "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Parent traversal evidence path should require review.\n' >&2
  exit 1
else
  code=$?
  [[ $code -eq 1 ]] || { printf 'Parent traversal evidence must fail schema validation, got %d.\n' "$code" >&2; exit 1; }
fi
cp "$FIXTURE_ROOT/.harness/quality/reviewer-response.valid.json" "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"

ruby -E UTF-8 -rjson -e '
  path = ARGV.fetch(0); response = JSON.parse(File.read(path)); unit = response.fetch("units").first
  unit.fetch("evidence")["correctness"] = ["im-common/not-listed.txt"]
  File.write(path, JSON.pretty_generate(response) + "\n")
' "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head HARNESS_CURRENT_WORKTREE_FINGERPRINT=fingerprint \
    "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Unlisted relative evidence path should require review.\n' >&2
  exit 1
else
  code=$?
  [[ $code -eq 1 ]] || { printf 'Unlisted relative evidence must fail schema validation, got %d.\n' "$code" >&2; exit 1; }
fi
cp "$FIXTURE_ROOT/.harness/quality/reviewer-response.valid.json" "$FIXTURE_ROOT/.harness/quality/reviewer-response.json"
ruby -E UTF-8 -rjson -e 'snapshot = JSON.parse(File.read(ARGV.fetch(0))); exit(snapshot.dig("units", 0, "grade") == "B" ? 0 : 1)' "$FIXTURE_ROOT/.harness/quality/quality-snapshot.json"

# Reviewer 也可以把带证据的维度对象直接放在 unit 顶层，校验器必须保持同一语义。
ruby -E UTF-8 -rjson -e '
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

# Git ignore 的运行日志不属于 review scope，不得使已验证 response 失效。
mkdir -p "$FIXTURE_ROOT/im-common/logs"
printf 'runtime log\n' > "$FIXTURE_ROOT/im-common/logs/runtime.log"
if ! HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head \
    HARNESS_CURRENT_WORKTREE_FINGERPRINT=ignored-log-changed \
    "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Ignored runtime files must not invalidate the quality response.\n' >&2
  exit 1
fi

# 共享 Harness/构建/部署范围变化必须使十二个单元共同进入复评。
printf '# shared review input\n' > "$FIXTURE_ROOT/scripts/shared-rule.sh"
if HARNESS_ROOT_DIR="$FIXTURE_ROOT" HARNESS_CURRENT_HEAD=head \
    HARNESS_CURRENT_WORKTREE_FINGERPRINT=shared-scope-changed \
    "$FIXTURE_ROOT/scripts/harness-quality.sh" >/dev/null 2>&1; then
  printf 'Shared review scope changes must invalidate the quality response.\n' >&2
  exit 1
else
  code=$?
  [[ $code -eq 3 ]] || { printf 'Shared scope change must return review_required (3), got %d.\n' "$code" >&2; exit 1; }
fi
printf 'Quality Harness tests passed.\n'
