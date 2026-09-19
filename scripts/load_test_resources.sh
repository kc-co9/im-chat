#!/usr/bin/env bash
# 用途：压测期间每 5 秒采集容器资源、k6 CPU/RSS、逻辑核和宿主可用内存。
# 输入：`load_test_resources.sh <k6-pid> <absolute-output.jsonl>`；PID 必须为正整数。
# 输出/副作用：以 0600 权限追加 NDJSON；单项采集失败写 null/status=unknown，进程退出后停止。
# 依赖：bash、docker、ps、sysctl、vm_stat、awk、Node.js。

set -euo pipefail

[[ $# -eq 2 && "$1" =~ ^[1-9][0-9]*$ && "$2" == /* ]] || {
  printf 'usage: %s <k6-pid> <absolute-output.jsonl>\n' "$0" >&2
  exit 2
}

K6_PID=$1
OUTPUT=$2
SAMPLE_SECONDS="${LOAD_TEST_RESOURCE_INTERVAL_SECONDS:-5}"
[[ "$SAMPLE_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]] || { printf 'resource interval is invalid\n' >&2; exit 2; }
mkdir -p "$(dirname "$OUTPUT")"
umask 077
: >"$OUTPUT"
chmod 0600 "$OUTPUT"

available_memory_bytes() {
  local page_size pages
  page_size="$(vm_stat 2>/dev/null | awk 'NR==1 { for (i=1; i<=NF; i++) if ($i ~ /^[0-9]+$/) { print $i; exit } }')"
  pages="$(vm_stat 2>/dev/null | awk '
    /Pages free|Pages inactive|Pages speculative|Pages purgeable/ {
      gsub(/\./, "", $NF); total += $NF
    }
    END { if (total >= 0) printf "%.0f", total }
  ')"
  [[ "$page_size" =~ ^[0-9]+$ && "$pages" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$((page_size * pages))"
}

while kill -0 "$K6_PID" 2>/dev/null; do
  sample_started_ms="$(node -e 'process.stdout.write(String(Date.now()))')"
  docker_sample="$("${DOCKER_BIN:-docker}" stats --no-stream --format '{{json .}}' 2>/dev/null || true)"
  process_sample="$(ps -o %cpu=,rss= -p "$K6_PID" 2>/dev/null \
    | awk 'NF == 2 { print $1 "," $2 * 1024 }' || true)"
  [[ -n "$process_sample" ]] || break
  logical_cpu="$(sysctl -n hw.logicalcpu 2>/dev/null || true)"
  available_memory="$(available_memory_bytes 2>/dev/null || true)"
  RESOURCE_DOCKER="$docker_sample" \
  RESOURCE_PROCESS="$process_sample" \
  RESOURCE_LOGICAL_CPU="$logical_cpu" \
  RESOURCE_AVAILABLE_MEMORY="$available_memory" \
    node -e '
      const docker = process.env.RESOURCE_DOCKER.split("\n").filter(Boolean).flatMap((line) => {
        try { return [JSON.parse(line)]; } catch { return []; }
      });
      const number = (raw) => raw.trim() === "" ? null : Number(raw);
      const [cpu, rss] = process.env.RESOURCE_PROCESS.split(",").map(number);
      const logicalCpu = number(process.env.RESOURCE_LOGICAL_CPU);
      const availableMemoryBytes = number(process.env.RESOURCE_AVAILABLE_MEMORY);
      const sample = {
        timestamp: Date.now() / 1_000,
        status: docker.length > 0 && Number.isFinite(cpu) && Number.isFinite(rss)
          && Number.isFinite(logicalCpu) && Number.isFinite(availableMemoryBytes) ? "complete" : "unknown",
        k6CpuPercent: Number.isFinite(cpu) ? cpu : null,
        k6RssBytes: Number.isFinite(rss) ? rss : null,
        logicalCpu: Number.isFinite(logicalCpu) ? logicalCpu : null,
        availableMemoryBytes: Number.isFinite(availableMemoryBytes) ? availableMemoryBytes : null,
        docker
      };
      process.stdout.write(JSON.stringify(sample) + "\n");
    ' >>"$OUTPUT"
  remaining_seconds="$(node -e '
    const elapsed = (Date.now() - Number(process.argv[1])) / 1_000;
    const remaining = Math.max(0, Number(process.argv[2]) - elapsed);
    if (remaining > 0) process.stdout.write(remaining.toFixed(3));
  ' "$sample_started_ms" "$SAMPLE_SECONDS")"
  [[ -z "$remaining_seconds" ]] || sleep "$remaining_seconds"
done
