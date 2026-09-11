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
