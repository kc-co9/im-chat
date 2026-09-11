#!/usr/bin/env bash
# 用途：扫描并（可选）清理 Harness 自己产生的临时文件和陈旧 PID 记录。
# 输入：默认只读；传入 --apply 才删除明确 allowlist 内的目标。
# 输出/副作用：默认输出诊断；apply 只删除 .harness/tmp 和带当前仓库 provenance 的陈旧 PID 文件。
# 依赖：Git、Ruby、find、realpath、kill。
# 退出码：发现问题时只读模式返回 1；apply 成功清理后返回 0；参数错误返回 2。
set -euo pipefail
ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
APPLY=false
[[ "${1:-}" == "--apply" ]] && APPLY=true
[[ -n "${1:-}" && "${1:-}" != "--apply" ]] && { printf 'Usage: %s [--apply]\n' "$0" >&2; exit 2; }
cleanup_failure() { printf '[cleanup] WHAT: %s\nWHY: %s\nFIX: %s\n' "$1" "$2" "$3" >&2; }
tmp_root="$ROOT_DIR/.harness/tmp"; pid_root="$ROOT_DIR/.harness/pids"; issues=0
if [[ -d "$tmp_root" ]]; then
  while IFS= read -r -d '' path; do
    canonical="$(realpath "$path" 2>/dev/null || true)"; allowed_prefix="$(realpath "$tmp_root")/"
    if [[ -z "$canonical" || "$canonical" != "$allowed_prefix"* ]]; then
      cleanup_failure "临时路径逃逸 allowlist: ${path#$ROOT_DIR/}" "符号链接或无法解析路径可能删除 Harness 目录之外的数据。" "移除逃逸路径；只允许 .harness/tmp 内的 canonical 文件。"; issues=$((issues + 1)); continue
    fi
    printf 'Harness temporary file: %s\n' "${path#$ROOT_DIR/}"; "$APPLY" && rm -f -- "$path"
  done < <(find "$tmp_root" \( -type f -o -type l \) -print0)
fi
if [[ -d "$pid_root" ]]; then
  while IFS= read -r -d '' pid_file; do
    pid="$(sed -n 's/^pid=//p' "$pid_file" | head -n1)"; provenance="$(sed -n 's/^root=//p' "$pid_file" | head -n1)"; command="$(sed -n 's/^command=//p' "$pid_file" | head -n1)"
    if [[ "$provenance" != "$ROOT_DIR" || -z "$pid" || ! "$pid" =~ ^[0-9]+$ || -z "$command" ]]; then
      cleanup_failure "PID 记录缺少当前仓库 provenance: ${pid_file#$ROOT_DIR/}" "无法证明记录属于本仓库的 Harness 进程。" "补全 root 和 command 字段，或手动处理该记录。"; issues=$((issues + 1)); continue
    fi
    if kill -0 "$pid" 2>/dev/null; then printf 'Live Harness process retained: PID %s (%s)\n' "$pid" "$command"; else printf 'Stale Harness PID record: %s\n' "${pid_file#$ROOT_DIR/}"; "$APPLY" && rm -f -- "$pid_file"; fi
  done < <(find "$pid_root" -type f -name '*.pid' -print0)
fi
(( issues > 0 )) && exit 1
printf 'Harness cleanup scan passed%s.\n' "$($APPLY && printf ' and applied allowlisted cleanup' || true)"
