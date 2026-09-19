#!/usr/bin/env bash
# 用途：扫描并（可选）清理 Harness 自己产生的临时文件和陈旧 PID 记录。
# 输入：默认只读；传入 --apply 才删除明确 allowlist 内的目标。
# 输出/副作用：默认输出诊断；apply 只删除 .harness/tmp 和带当前仓库 provenance 的陈旧 PID 文件；压测恢复状态只报告不删除。
# 依赖：Git、Ruby、find、realpath、kill。
# 退出码：发现问题时只读模式返回 1；apply 成功清理后返回 0；参数错误返回 2。
set -euo pipefail
ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
APPLY=false
[[ "${1:-}" == "--apply" ]] && APPLY=true
[[ -n "${1:-}" && "${1:-}" != "--apply" ]] && { printf 'Usage: %s [--apply]\n' "$0" >&2; exit 2; }
cleanup_failure() { printf '[cleanup] WHAT: %s\nWHY: %s\nFIX: %s\n' "$1" "$2" "$3" >&2; }
tmp_root="$ROOT_DIR/.harness/tmp"; pid_root="$ROOT_DIR/.harness/pids"; issues=0
load_test_root="$ROOT_DIR/.harness/load-test"
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
if [[ -d "$load_test_root" ]]; then
  state_file="$load_test_root/runtime-state.jsonl"
  if [[ -f "$state_file" ]]; then
    load_state="$(ruby -rjson -e '
      lines = File.readlines(ARGV.fetch(0)).map(&:strip).reject(&:empty?)
      abort "empty state journal" if lines.empty?
      value = JSON.parse(lines.last)
      puts [value["state"], value["batchId"]].map { |item| item.to_s }.join("\t")
    ' "$state_file" 2>/dev/null || true)"
    if [[ -z "$load_state" ]]; then
      cleanup_failure \
        "压测状态 journal 无法解析" \
        "无法证明服务和批次数据已恢复。" \
        "先检查 .harness/load-test/runtime-state.jsonl，再运行 scripts/load_test.sh restore 或 cleanup --resume。"
      issues=$((issues + 1))
    else
      IFS=$'\t' read -r load_state_name load_batch_id <<<"$load_state"
      if [[ "$load_state_name" != BASE_READY ]]; then
        cleanup_failure \
          "压测仍处于 $load_state_name 状态，batch=${load_batch_id:-unknown}" \
          "owner 服务、SQL override 或批次数据可能尚未恢复。" \
          "运行 ./scripts/load_test.sh cleanup --resume ${load_batch_id:-<batch>}；无批次时运行 ./scripts/load_test.sh restore。"
        issues=$((issues + 1))
      fi
    fi
  fi

  for load_lock in "$load_test_root/runner.lock" "$load_test_root/runner.lock.recovery"; do
    [[ -d "$load_lock" ]] || continue
    load_pid="$(cat "$load_lock/pid" 2>/dev/null || true)"
    if [[ "$load_pid" =~ ^[1-9][0-9]*$ ]] && kill -0 "$load_pid" 2>/dev/null; then
      reason="压测 runner PID $load_pid 仍存活。"
    else
      reason="压测 runner 锁已陈旧或缺少有效 PID。"
    fi
    cleanup_failure \
      "压测锁仍存在: ${load_lock#$ROOT_DIR/}" \
      "$reason" \
      "确认无 runner 运行后，按状态 journal 使用 load_test.sh cleanup --resume 或 restore；不要由 Harness 自动删除锁。"
    issues=$((issues + 1))
  done

  while IFS= read -r -d '' sensitive_file; do
    batch_id="$(basename "$(dirname "$sensitive_file")")"
    cleanup_failure \
      "压测敏感或恢复文件仍存在: ${sensitive_file#$ROOT_DIR/}" \
      "批次 ${batch_id} 尚未完成凭据销毁或数据库目标清理。" \
      "运行 ./scripts/load_test.sh cleanup --resume ${batch_id}；Harness 不直接删除该文件。"
    issues=$((issues + 1))
  done < <(find "$load_test_root" -mindepth 2 -maxdepth 2 -type f \
    \( -name 'credentials*.json' -o -name 'database-targets.json' \) -print0)
fi
(( issues > 0 )) && exit 1
printf 'Harness cleanup scan passed%s.\n' "$($APPLY && printf ' and applied allowlisted cleanup' || true)"
