#!/usr/bin/env bash
# 用途：枚举全部 Spring Boot application，并执行一一对应、隔离外部发现的 startup smoke。
# 输入：无；测试可通过 HARNESS_ROOT_DIR 指向隔离 fixture。
# 输出/副作用：运行 Maven、刷新各应用的 Surefire 报告，并写入 .harness/startup-manifest.json。
# 依赖：Maven、Ruby JSON/REXML、rg 和 find。
# 退出码：映射/隔离契约不满足、Maven 失败、零测试或报告异常时返回非 0。

set -euo pipefail

ROOT_DIR="${HARNESS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
MANIFEST="$ROOT_DIR/.harness/startup-manifest.json"
mkdir -p "$(dirname "$MANIFEST")"

# 输出统一 WHAT/WHY/FIX，确保新增应用漏接 startup 门禁时可以直接修复。
startup_failure() {
  printf '\n[startup] WHAT: %s\nWHY: %s\nFIX: %s\n' "$1" "$2" "$3" >&2
}

# 从源码构建 application -> tagged context test -> Maven module 的唯一映射。
if ! ruby -rjson -rpathname -e '
  root, output = ARGV
  applications = Dir.glob(File.join(root, "**/src/main/java/**/*.java"))
    .select { |path| File.read(path).match?(/@(?:org\.springframework\.boot\.autoconfigure\.)?SpringBootApplication\b/) }
  tests = Dir.glob(File.join(root, "**/src/test/java/**/*.java"))
    .select { |path| File.read(path).match?(/@(?:org\.junit\.jupiter\.api\.)?Tag\(\"startup-smoke\"\)/) }
  failures = []
  entries = applications.sort.map do |application_path|
    source = File.read(application_path)
    application = source[/\bclass\s+([A-Za-z0-9_]+Application)\b/, 1]
    unless application
      failures << "#{application_path.delete_prefix(root + File::SEPARATOR)}: 无法解析 Application 类名"
      next
    end
    candidates = tests.select do |test_path|
      test_source = File.read(test_path)
      test_source.match?(/classes\s*=\s*#{Regexp.escape(application)}\.class/) ||
        File.basename(test_path, ".java").start_with?(application)
    end
    if candidates.empty?
      module_dir = Pathname.new(application_path).dirname
      module_dir = module_dir.parent until module_dir.root? || File.file?(module_dir.join("pom.xml"))
      module_tests = tests.select { |test_path| test_path.start_with?(module_dir.to_s + File::SEPARATOR) }
      candidates = module_tests if module_tests.length == 1
    end
    if candidates.length != 1
      failures << "#{application}: 需要 1 个 startup-smoke test，实际为 #{candidates.length}"
      next
    end
    test_path = candidates.first
    test_source = File.read(test_path)
    unless test_source.include?("spring.cloud.nacos.discovery.enabled=false") &&
        test_source.include?("spring.cloud.nacos.config.enabled=false")
      failures << "#{application}: startup-smoke 未显式关闭 Nacos discovery/config"
      next
    end
    module_dir = Pathname.new(application_path).dirname
    module_dir = module_dir.parent until module_dir.root? || File.file?(module_dir.join("pom.xml"))
    if module_dir.root?
      failures << "#{application}: 找不到所属 Maven module"
      next
    end
    package_name = test_source[/^package\s+([^;]+);/, 1]
    test_class = test_source[/\bclass\s+([A-Za-z0-9_]+)\b/, 1]
    if package_name.nil? || test_class.nil?
      failures << "#{application}: 无法解析 startup-smoke test 类名"
      next
    end
    {
      "application" => application,
      "applicationPath" => application_path.delete_prefix(root + File::SEPARATOR),
      "testPath" => test_path.delete_prefix(root + File::SEPARATOR),
      "testClass" => "#{package_name}.#{test_class}",
      "module" => module_dir.to_s.delete_prefix(root + File::SEPARATOR)
    }
  end.compact
  if applications.empty?
    failures << "仓库中没有发现 @SpringBootApplication"
  end
  unless failures.empty?
    warn failures.join("\n")
    exit 1
  end
  File.write(output, JSON.pretty_generate({"applications" => entries}) + "\n")
' "$ROOT_DIR" "$MANIFEST"; then
  startup_failure \
    "Spring Boot application 与 startup-smoke test 映射不完整。" \
    "漏测、重复映射或未隔离 Nacos 会让标准启动路径出现盲区或误连真实环境。" \
    "为上方 Application 增加唯一的 @Tag(\"startup-smoke\") context test，并显式关闭 Nacos discovery/config。"
  exit 1
fi

modules="$(ruby -rjson -e '
  data = JSON.parse(File.read(ARGV.fetch(0)))
  puts data.fetch("applications").map { |entry| entry.fetch("module") }.uniq.join(",")
' "$MANIFEST")"
application_count="$(ruby -rjson -e '
  puts JSON.parse(File.read(ARGV.fetch(0))).fetch("applications").length
' "$MANIFEST")"

# 删除每个映射测试的旧报告，确保后续成功只能来自本次 startup 执行。
ruby -rjson -e '
  root, manifest = ARGV
  JSON.parse(File.read(manifest)).fetch("applications").each do |entry|
    report = File.join(root, entry.fetch("module"), "target/surefire-reports",
      "TEST-#{entry.fetch("testClass")}.xml")
    File.delete(report) if File.file?(report)
  end
' "$ROOT_DIR" "$MANIFEST"

printf '\n==> Building startup-smoke modules\n'
mvn -q -pl "$modules" -am -DskipTests install
printf '\n==> Running %d application startup smoke(s)\n' "$application_count"
mvn -q -pl "$modules" -Dgroups=startup-smoke test

# 逐项读取本次 Surefire XML；不以 Maven 退出 0 代替测试实际执行证据。
if ! ruby -rjson -rrexml/document -e '
  root, manifest = ARGV
  missing = []
  JSON.parse(File.read(manifest)).fetch("applications").each do |entry|
    report = File.join(root, entry.fetch("module"), "target/surefire-reports",
      "TEST-#{entry.fetch("testClass")}.xml")
    unless File.file?(report)
      missing << "#{entry.fetch("application")}: 没有产生测试报告"
      next
    end
    suite = REXML::Document.new(File.read(report)).root
    tests = suite.attributes["tests"].to_i
    failures = suite.attributes["failures"].to_i + suite.attributes["errors"].to_i
    if tests.zero?
      missing << "#{entry.fetch("application")}: 测试报告为零测试"
    elsif failures.positive?
      missing << "#{entry.fetch("application")}: 测试报告包含 #{failures} 个失败"
    end
  end
  unless missing.empty?
    warn missing.join("\n")
    exit 1
  end
' "$ROOT_DIR" "$MANIFEST"; then
  startup_failure \
    "startup-smoke 没有为全部应用产生有效测试报告。" \
    "Maven 成功但测试未执行会让错误 Tag 或筛选配置冒充启动通过。" \
    "检查 @Tag、Surefire groups 和 target/surefire-reports 后重跑 startup。"
  exit 1
fi

printf 'Application startup smoke passed for %d application(s).\n' "$application_count"
