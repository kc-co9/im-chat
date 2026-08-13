#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

"$ROOT_DIR/scripts/check-drift.sh"

# Governance checks surface stale work without mutating plans or trackers automatically.
stale_plans="$(find docs/exec-plans/active -type f -name '*.md' ! -name README.md -mtime +30 -print)"
if [[ -n "$stale_plans" ]]; then
  printf 'Active plans unchanged for more than 30 days:\n%s\n' "$stale_plans" >&2
  exit 1
fi

open_debt="$(awk -F'|' '/^\| TD-[0-9]+ / && $7 !~ /(DONE|CLOSED)/ {print $0}' docs/exec-plans/tech-debt-tracker.md)"
if [[ -n "$open_debt" ]]; then
  printf 'Tracked technical debt requires review:\n%s\n' "$open_debt"
fi

open_feedback="$(awk -F'|' '/^\| HF-[0-9]+ / && $7 !~ /CLOSED/ {print $0}' docs/feedback/HARNESS_FEEDBACK.md)"
if [[ -n "$open_feedback" ]]; then
  printf 'Harness feedback requires review:\n%s\n' "$open_feedback"
fi

"$ROOT_DIR/scripts/harness-report.sh"
