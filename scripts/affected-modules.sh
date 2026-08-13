#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_REF="${1:-}"
cd "$ROOT_DIR"

if [[ -n "${IM_CHANGED_FILES:-}" ]]; then
  changed_files="$IM_CHANGED_FILES"
elif [[ -n "$BASE_REF" ]] && git rev-parse --verify --quiet "$BASE_REF^{commit}" >/dev/null; then
  changed_files="$(git diff --name-only "$BASE_REF"...HEAD)"
elif [[ -n "$BASE_REF" ]]; then
  printf 'all\n'
  exit 0
else
  changed_files="$(git diff --name-only HEAD; git ls-files --others --exclude-standard)"
fi

if [[ -z "$changed_files" ]]; then
  printf 'none\n'
  exit 0
fi

# Shared build inputs can affect every reactor module, so use the conservative full scope.
if printf '%s\n' "$changed_files" | rg -q '^(pom\.xml|im-common/|scripts/|\.github/)'; then
  printf 'all\n'
  exit 0
fi

modules=()
add_module() {
  local module="$1"
  [[ " ${modules[*]-} " == *" $module "* ]] || modules+=("$module")
}

while IFS= read -r file; do
  case "$file" in
    im-plugin/*/*) add_module "$(cut -d/ -f1-2 <<< "$file")" ;;
    im-broker/im-broker-sdk/*) add_module "im-broker/im-broker-sdk" ; add_module "im-broker/im-broker-server" ;;
    im-broker/im-broker-server/*) add_module "im-broker/im-broker-server" ;;
    im-gateway/im-http-gateway/*) add_module "im-gateway/im-http-gateway" ;;
    im-gateway/im-ws-gateway/im-ws-gateway-sdk/*)
      add_module "im-gateway/im-ws-gateway/im-ws-gateway-sdk"
      add_module "im-gateway/im-ws-gateway/im-ws-gateway-server"
      add_module "im-broker/im-broker-server"
      ;;
    im-gateway/im-ws-gateway/im-ws-gateway-server/*) add_module "im-gateway/im-ws-gateway/im-ws-gateway-server" ;;
    im-service/*/*-facade/*)
      service="$(cut -d/ -f1-2 <<< "$file")"
      add_module "$service/$(basename "$service")-facade"
      add_module "$service/$(basename "$service")-server"
      ;;
    im-service/*/*-server/*) add_module "$(cut -d/ -f1-3 <<< "$file")" ;;
    im-architecture/*) add_module "im-architecture" ;;
  esac
done <<< "$changed_files"

if (( ${#modules[@]} == 0 )); then
  printf 'docs\n'
else
  (IFS=,; printf '%s\n' "${modules[*]},im-architecture")
fi
