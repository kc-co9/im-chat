#!/usr/bin/env bash
# 用途：为 verify 与 Harness report 提供一致的 Git revision 和 worktree 内容指纹。
# 输入：仓库根目录；不读取 target 等 Git ignore 产物。
# 输出/副作用：函数只向标准输出返回 revision 或 SHA-256 指纹，不修改工作树和 Git index。
# 依赖：Git、Ruby 标准库 digest/open3。
# 失败语义：不在 Git 工作树或指纹计算失败时返回字面值 unavailable，调用方不得据此声明证据新鲜。

# 返回当前 HEAD；仓库没有可解析 HEAD 时返回 unavailable。
harness_head_revision() {
  local root_dir="$1"
  git -C "$root_dir" rev-parse --verify HEAD 2>/dev/null || printf 'unavailable\n'
}

# 指纹覆盖 HEAD 与当前文件树内容，不编码 staged/untracked 状态；相同内容暂存前后得到相同结果。
harness_worktree_fingerprint() {
  local root_dir="$1"
  local fingerprint
  if fingerprint="$(ruby -rdigest -ropen3 -e '
    root = File.expand_path(ARGV.fetch(0))

    def git_output(root, *arguments)
      output, error, status = Open3.capture3("git", "-C", root, *arguments, binmode: true)
      raise "git #{arguments.join(" ")} failed: #{error}" unless status.success?
      output
    end

    head = git_output(root, "rev-parse", "--verify", "HEAD").strip
    paths = git_output(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z")
      .split("\0").reject(&:empty?).uniq.sort

    digest = Digest::SHA256.new
    digest.update("HEAD\0#{head}\0WORKTREE\0")
    paths.each do |relative_path|
      full_path = File.join(root, relative_path)
      if File.symlink?(full_path)
        digest.update("\0SYMLINK\0#{relative_path}\0#{File.readlink(full_path)}")
      elsif File.file?(full_path)
        executable = File.stat(full_path).mode & 0o111 == 0 ? "regular" : "executable"
        digest.update("\0#{executable}\0#{relative_path}\0")
        digest.update(File.binread(full_path))
      end
    end
    puts digest.hexdigest
  ' "$root_dir" 2>/dev/null)"; then
    printf '%s\n' "$fingerprint"
  else
    printf 'unavailable\n'
  fi
}

# mkdir 是唯一抢锁操作；仅本进程持有的目录可在 EXIT 时释放，陈旧锁需人工核对。
harness_execution_lock() {
  local directory="$ROOT_DIR" part
  for part in .harness verifications; do
    directory="$directory/$part"
    [[ ! -L "$directory" ]] || return 2
    mkdir -p "$directory" || return $?
  done
  EXECUTION_LOCK="$ROOT_DIR/.harness/verifications/execution.lock"
  if ! mkdir "$EXECUTION_LOCK" 2>/dev/null; then
    printf 'Harness repository execution lock exists (possibly interrupted); inspect owner before manual removal: %s\n' "$EXECUTION_LOCK" >&2
    return 2
  fi
  printf '%s\n' "$$" > "$EXECUTION_LOCK/owner-pid"
  trap 'harness_release_locks' EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
}

harness_release_locks() {
  if [[ -n "${EXECUTION_LOCK:-}" && -f "$EXECUTION_LOCK/owner-pid" && "$(cat "$EXECUTION_LOCK/owner-pid")" == "$$" ]]; then
    rm -f "$EXECUTION_LOCK/owner-pid"; rmdir "$EXECUTION_LOCK"
  fi
}
