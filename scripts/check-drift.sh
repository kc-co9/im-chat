#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

# Report all low-noise violations in one run instead of stopping at the first match.
report_matches() {
  local title="$1"
  local matches="$2"
  if [[ -n "$matches" ]]; then
    printf '\n[drift] %s\n%s\n' "$title" "$matches"
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
  docs/design-docs/index.md \
  docs/exec-plans/active/README.md \
  docs/exec-plans/completed/README.md \
  docs/exec-plans/tech-debt-tracker.md \
  docs/product-specs/index.md \
  docs/references/index.md \
  docs/PLANS.md \
  docs/RELIABILITY.md \
  docs/SECURITY.md; do
  [[ -f "$file" ]] || printf '%s\n' "$file"
done; true)"
report_matches "Required Harness documentation is missing" "$required_docs"

legacy_docs="$([[ -e docs/superpowers ]] && printf '%s\n' docs/superpowers || true)"
report_matches "Legacy documentation directories must not be reintroduced" "$legacy_docs"

broken_links="$(ruby -e '
  root = Dir.pwd
  files = ["README.md", "AGENTS.md", "ARCHITECTURE.md"] + Dir.glob("docs/**/*.md")
  files.each do |file|
    content = File.read(file)
    content.scan(/\[[^\]]+\]\(([^)]+)\)/).flatten.each do |target|
      next if target.match?(/\A(?:https?:|mailto:|#)/)
      path = target.split("#", 2).first
      next if path.empty?
      resolved = File.expand_path(path, File.dirname(File.join(root, file)))
      puts "#{file}: #{target}" unless File.exist?(resolved)
    end
  end
' || true)"
report_matches "Local Markdown links must resolve" "$broken_links"

if (( failures > 0 )); then
  printf '\nDrift checks failed with %d violation group(s).\n' "$failures"
  exit 1
fi

printf 'Drift checks passed.\n'
