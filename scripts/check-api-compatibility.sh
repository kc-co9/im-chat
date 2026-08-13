#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_REF="${1:-origin/main}"
MODULES=(
  im-broker/im-broker-sdk
  im-gateway/im-ws-gateway/im-ws-gateway-sdk
  im-service/im-account/im-account-facade
  im-service/im-social/im-social-facade
  im-service/im-message/im-message-facade
)

cd "$ROOT_DIR"
if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  printf 'API baseline %s is unavailable; skipping compatibility check.\n' "$BASE_REF"
  exit 0
fi

worktree="$(mktemp -d /tmp/im-chat-api-baseline.XXXXXX)"
trap 'git worktree remove --force "$worktree" >/dev/null 2>&1 || true' EXIT
git worktree add --detach "$worktree" "$BASE_REF" >/dev/null

(cd "$worktree" && mvn -q -Dmaven.test.skip=true install)
mvn -q -pl "$(IFS=,; echo "${MODULES[*]}")" -am -Dmaven.test.skip=true package

printf 'API baseline %s and current contracts compile successfully.\n' "$BASE_REF"
printf 'This command prepares release baselines; it does not claim binary compatibility until released artifacts are versioned.\n'
