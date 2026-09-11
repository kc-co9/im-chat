#!/usr/bin/env bash
# 用途：扫描 DDL、Mapper XML 和 Java 注解 SQL 中可稳定识别的安全与结构违规。
# 输入：无位置参数；测试可通过 SQL_HARNESS_ROOT 指向隔离 fixture。
# 输出/副作用：按规则组输出来源文件与原因；只读解析，不执行任何 SQL。
# 依赖：Ruby 标准库，以及仓库约定的 sql/resources/mapper/Java 源码目录。
# 退出码：没有违规返回 0；发现任一违规返回 1。

set -euo pipefail

ROOT_DIR="${SQL_HARNESS_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

ruby - "$ROOT_DIR" <<'RUBY'
root = File.expand_path(ARGV.fetch(0))
groups = Hash.new { |hash, key| hash[key] = [] }

# 将绝对文件名转换为面向诊断的仓库相对路径。
def relative(root, file)
  file.delete_prefix(root + "/")
end

# 把单条诊断追加到规则组，最终统一输出全部违规而不是首错退出。
def report(groups, group, file, message)
  groups[group] << "#{file}: #{message}"
end

# 判断去除 MySQL 反引号后的标识符是否为 lower_snake_case。
def lower_snake_identifier?(token)
  name = token.start_with?("`") && token.end_with?("`") ? token[1...-1] : token
  name.match?(/\A[a-z][a-z0-9]*(?:_[a-z0-9]+)*\z/)
end

# 压平空白并转小写，供不关心格式的 SQL 结构规则匹配。
def normalized(sql)
  sql.gsub(/<!--.*?-->/m, " ").gsub(/\s+/, " ").strip
end

# 识别 SELECT 列表中的 * 或 alias.*，但不误报 COUNT(*)。
def select_wildcard?(sql)
  normalized(sql).scan(/\bSELECT\b(.*?)\bFROM\b/im).any? do |match|
    projection = match.fetch(0).dup
    loop do
      reduced = projection.gsub(/\([^()]*\)/m, " ")
      break if reduced == projection
      projection = reduced
    end
    projection.split(",").any? do |item|
      item.strip.match?(/\A(?:DISTINCT\s+)?(?:[A-Za-z_][A-Za-z0-9_]*\s*\.\s*)?\*\z/i)
    end
  end
end

# 屏蔽 SQL 注释，并按调用方选择是否屏蔽字符串字面量，避免文档示例和内容文本误报。
def sql_mask(source, mask_literals:)
  result = source.dup
  state = :code
  index = 0
  while index < source.length
    case state
    when :code
      if source[index, 2] == "--" || (source[index] == "#" && source[index + 1] != "{")
        length = source[index, 2] == "--" ? 2 : 1
        result[index, length] = " " * length
        state = :line_comment
        index += length
      elsif source[index, 2] == "/*"
        result[index, 2] = "  "
        state = :block_comment
        index += 2
      elsif source[index] == "'"
        result[index] = " " if mask_literals
        state = :single_quote
        index += 1
      elsif source[index] == '"'
        result[index] = " " if mask_literals
        state = :double_quote
        index += 1
      elsif source[index] == "`"
        result[index] = " " if mask_literals
        state = :backtick
        index += 1
      else
        index += 1
      end
    when :line_comment
      if source[index] == "\n"
        state = :code
      else
        result[index] = " "
      end
      index += 1
    when :block_comment
      if source[index, 2] == "*/"
        result[index, 2] = "  "
        state = :code
        index += 2
      else
        result[index] = " " unless source[index] == "\n"
        index += 1
      end
    when :single_quote, :double_quote, :backtick
      quote = { single_quote: "'", double_quote: '"', backtick: "`" }.fetch(state)
      result[index] = " " if mask_literals && source[index] != "\n"
      if source[index] == "\\"
        result[index + 1] = " " if mask_literals && index + 1 < source.length
        index += 2
      elsif source[index, 2] == quote * 2
        result[index, 2] = "  " if mask_literals
        index += 2
      elsif source[index] == quote
        state = :code
        index += 1
      else
        index += 1
      end
    end
  end
  result
end

# 屏蔽 Java 注释和普通字符串，仅保留可用于定位注解 SQL 的代码形状。
def java_code_mask(source)
  masked = source.dup
  state = :code
  index = 0
  while index < source.length
    case state
    when :code
      if source[index, 2] == "//"
        masked[index, 2] = "  "
        state = :line_comment
        index += 2
      elsif source[index, 2] == "/*"
        masked[index, 2] = "  "
        state = :block_comment
        index += 2
      elsif source[index, 3] == '\"\"\"'
        masked[index, 3] = "   "
        state = :text_block
        index += 3
      elsif source[index] == '"'
        masked[index] = " "
        state = :string
        index += 1
      elsif source[index] == "'"
        masked[index] = " "
        state = :character
        index += 1
      else
        index += 1
      end
    when :line_comment
      if source[index] == "\n"
        state = :code
      else
        masked[index] = " "
      end
      index += 1
    when :block_comment
      if source[index, 2] == "*/"
        masked[index, 2] = "  "
        state = :code
        index += 2
      else
        masked[index] = " " unless source[index] == "\n"
        index += 1
      end
    when :text_block
      if source[index, 3] == '\"\"\"'
        masked[index, 3] = "   "
        state = :code
        index += 3
      else
        masked[index] = " " unless source[index] == "\n"
        index += 1
      end
    when :string, :character
      quote = state == :string ? '"' : "'"
      if source[index] == "\\"
        masked[index, 2] = "  "
        index += 2
      elsif source[index] == quote
        masked[index] = " "
        state = :code
        index += 1
      else
        masked[index] = " " unless source[index] == "\n"
        index += 1
      end
    end
  end
  masked
end

# 删除 Java 注释但保留字符串内容，供注解 SQL 提取阶段继续解析。
def without_java_comments(source)
  result = source.dup
  state = :code
  index = 0
  while index < source.length
    case state
    when :code
      if source[index, 2] == "//"
        result[index, 2] = "  "
        state = :line_comment
        index += 2
      elsif source[index, 2] == "/*"
        result[index, 2] = "  "
        state = :block_comment
        index += 2
      elsif source[index, 3] == '\"\"\"'
        state = :text_block
        index += 3
      elsif source[index] == '"'
        state = :string
        index += 1
      elsif source[index] == "'"
        state = :character
        index += 1
      else
        index += 1
      end
    when :line_comment
      if source[index] == "\n"
        state = :code
      else
        result[index] = " "
      end
      index += 1
    when :block_comment
      if source[index, 2] == "*/"
        result[index, 2] = "  "
        state = :code
        index += 2
      else
        result[index] = " " unless source[index] == "\n"
        index += 1
      end
    when :text_block
      if source[index, 3] == '\"\"\"'
        state = :code
        index += 3
      else
        index += 1
      end
    when :string, :character
      quote = state == :string ? '"' : "'"
      if source[index] == "\\"
        index += 2
      elsif source[index] == quote
        state = :code
        index += 1
      else
        index += 1
      end
    end
  end
  result
end

# 建立生产文件清单；测试、构建产物和 fixture 由 Harness 自测负责，不进入仓库规则扫描。
excluded = %r{/(?:src/test|target|fixtures?)(?:/|\z)}
all_files = Dir.glob(File.join(root, "**", "*"), File::FNM_DOTMATCH).select { |file| File.file?(file) && !file.match?(excluded) }

resource_sql = all_files.select { |file| file.match?(%r{/src/main/resources/.+\.sql\z}i) }
resource_sql.each { |file| report(groups, "SQL placement", relative(root, file), "Production resource SQL files are forbidden; use the owning module's sql/*.sql") }

sql_files = all_files.select do |file|
  file.end_with?(".sql") && !file.match?(%r{/src/main/resources/}i)
end
owned_sql = sql_files.select do |file|
  sql_directory = File.dirname(file)
  module_directory = File.dirname(sql_directory)
  File.basename(sql_directory) == "sql" && File.directory?(File.join(module_directory, "src", "main"))
end
(sql_files - owned_sql).each do |file|
  report(
    groups,
    "SQL placement",
    relative(root, file),
    "Service DDL must be placed in the owning Server module's sql/*.sql"
  )
end
# 校验 Server 自有 DDL 的危险语句、命名、模板字段、主键、引擎和索引约定。
owned_sql.each do |file|
  path = relative(root, file)
  content = File.read(file)
  uncommented = sql_mask(content, mask_literals: false)
  compact = normalized(uncommented)
  keyword_sql = normalized(sql_mask(content, mask_literals: true))
  report(groups, "Dangerous SQL", path, "TRUNCATE is forbidden") if keyword_sql.match?(/\bTRUNCATE\b/i)
  report(groups, "Dangerous SQL", path, "DROP DATABASE is forbidden") if keyword_sql.match?(/\bDROP\s+DATABASE\b/i)
  report(groups, "SELECT wildcard", path, "SELECT * and SELECT table.* are forbidden") if select_wildcard?(keyword_sql)
  keyword_sql.scan(/\bDROP\s+TABLE\b.*?(?:;|\z)/i).each do |statement|
    if File.basename(path) != "ddl.sql"
      report(groups, "DROP TABLE", path, "DROP TABLE is only allowed in an owned sql/ddl.sql")
    elsif !statement.match?(/\ADROP\s+TABLE\s+IF\s+EXISTS\b/i)
      report(groups, "DROP TABLE", path, "DROP TABLE IF EXISTS is required in an owned sql/ddl.sql")
    end
  end

  compact.split(";").map { |statement| /\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(`[^`]+`|\S+)\s*\((.*)\)\s*(.*)\z/im.match(statement) }.compact.each do |create_match|
    table_token, body, options = create_match.captures
    unless lower_snake_identifier?(table_token)
      report(groups, "DDL naming", path, "CREATE TABLE requires a lower snake_case table name: #{table_token}")
    end
    body.scan(/(?:\A|,)\s*(`[^`]+`|[A-Za-z][A-Za-z0-9_]*)\s+/).flatten.each do |column|
      next if column.match?(/\A(?:PRIMARY|UNIQUE|KEY|CONSTRAINT|FOREIGN|CHECK)\z/i)
      unless lower_snake_identifier?(column)
        report(groups, "DDL naming", path, "CREATE TABLE requires lower snake_case column names: #{column}")
      end
    end
    template_columns = %w[id create_time update_time is_deleted]
    missing_template_columns = template_columns.reject do |column|
      body.match?(/(?:\A|,)\s*`?#{Regexp.escape(column)}`?\s+/i)
    end
    unless missing_template_columns.empty?
      report(
        groups,
        "DDL structure",
        path,
        "CREATE TABLE requires standard template columns id/create_time/update_time/is_deleted; missing: #{missing_template_columns.join(', ')}"
      )
    end
    report(groups, "DDL structure", path, "CREATE TABLE requires an explicit PRIMARY KEY") unless body.match?(/\bPRIMARY\s+KEY\b/i)
    report(groups, "DDL structure", path, "CREATE TABLE requires ENGINE=InnoDB") unless options.match?(/\bENGINE\s*=\s*InnoDB\b/i)
    report(groups, "DDL structure", path, "CREATE TABLE requires a table COMMENT") unless options.match?(/\bCOMMENT\s*=\s*(['"])/i)
    body.scan(/\b(UNIQUE\s+)?(?:KEY|INDEX)\s+(`[^`]+`|[A-Za-z][A-Za-z0-9_]*)/i).each do |unique, name|
      raw_name = name.delete("`")
      prefix = unique ? "uk_" : "idx_"
      label = unique ? "unique indexes" : "normal indexes"
      unless raw_name.match?(/\A#{prefix}[a-z0-9]+(?:_[a-z0-9]+)*\z/)
        report(groups, "DDL naming", path, "named #{label} require the #{prefix} prefix and lower snake_case: #{name}")
      end
    end
  end
end

# Mapper XML 同时检查替换注入、通配列和可确定的无条件静态写入。
xml_files = all_files.select { |file| file.end_with?(".xml") && File.read(file).include?("<mapper") }
xml_files.each do |file|
  path = relative(root, file)
  content = File.read(file)
  content.scan(/<(select|insert|update|delete)\b[^>]*>(.*?)<\/\1\s*>/im).each do |kind, body|
    sql = body.gsub(/<!\[CDATA\[(.*?)\]\]>/m, '\\1')
    uncommented = sql_mask(sql, mask_literals: false)
    keyword_sql = normalized(sql_mask(sql, mask_literals: true))
    report(groups, "SQL substitution", path, 'Mapper SQL must not contain ${...}') if uncommented.match?(/\$\{[^}]*\}/)
    report(groups, "DROP TABLE", path, "DROP TABLE is forbidden in Mapper XML") if keyword_sql.match?(/\bDROP\s+TABLE\b/i)
    report(groups, "SELECT wildcard", path, "SELECT * and SELECT table.* are forbidden") if select_wildcard?(keyword_sql)
    dynamic = sql.match?(/<(?:if|where|choose|when|otherwise|foreach|set|trim|bind|include)\b/i)
    if !dynamic && %w[update delete].include?(kind.downcase) && !keyword_sql.match?(/\bWHERE\b/i)
      report(groups, "Unsafe write", path, "static #{kind.upcase} without WHERE is forbidden")
    end
  end
  content.scan(/<sql\b[^>]*>(.*?)<\/sql\s*>/im).each do |fragment|
    sql = fragment.fetch(0).gsub(/<!\[CDATA\[(.*?)\]\]>/m, '\\1')
    report(groups, "SQL substitution", path, 'Mapper SQL must not contain ${...}') if sql_mask(sql, mask_literals: false).match?(/\$\{[^}]*\}/)
  end
end

# Java 注解 SQL 先按括号深度提取完整表达式，仅对可静态还原的字符串执行内容规则。
java_files = all_files.select { |file| file.end_with?(".java") }
java_files.each do |file|
  path = relative(root, file)
  content = File.read(file)
  code_mask = java_code_mask(content)
  annotations = []
  cursor = 0
  regex = /@(?:org\.apache\.ibatis\.annotations\.)?(Select|Insert|Update|Delete)\s*\(/
  while match = regex.match(code_mask, cursor)
    depth = 1
    index = match.end(0)
    while index < content.length && depth > 0
      char = code_mask[index]
      depth += 1 if char == '('
      depth -= 1 if char == ')'
      index += 1
    end
    annotations << [match[1], content[match.end(0)...index - 1]]
    cursor = index
  end
  next if annotations.empty?

  package_name = content[/\bpackage\s+([\w.]+)\s*;/, 1].to_s
  location = "#{path} #{package_name}".downcase
  unless location.include?("infrastructure") && location.include?("mapper")
    report(groups, "Annotation location", path, "MyBatis SQL annotations must reside in an infrastructure mapper path/package")
  end

  annotations.each do |kind, expression|
    expression = without_java_comments(expression)
    report(groups, "SQL substitution", path, 'MyBatis annotation SQL must not contain ${...}') if expression.match?(/\$\{[^}]*\}/)
    supported = nil
    stripped = expression.strip.sub(/\Avalue\s*=\s*/m, "")
    if match = /\A"""(.*)"""\z/m.match(stripped)
      supported = match[1]
    elsif match = /\A"((?:\\.|[^"\\])*)"\z/m.match(stripped)
      supported = match[1]
    end
    next unless supported
    keyword_sql = normalized(sql_mask(supported, mask_literals: true))
    report(groups, "DROP TABLE", path, "DROP TABLE is forbidden in MyBatis annotations") if keyword_sql.match?(/\bDROP\s+TABLE\b/i)
    report(groups, "SELECT wildcard", path, "SELECT * and SELECT table.* are forbidden") if select_wildcard?(keyword_sql)
    if %w[Update Delete].include?(kind) && !keyword_sql.match?(/\bWHERE\b/i)
      report(groups, "Unsafe write", path, "static #{kind.upcase} without WHERE is forbidden")
    end
  end
end

# 最终一次性输出全部规则组，便于单轮修复多个低噪声问题。
if groups.empty?
  puts "SQL checks passed."
  exit 0
end

puts "\n[sql] WHAT: SQL 来源违反了可静态确认的安全、所有权或结构规则。"
puts "WHY: 这些违规可能造成注入、全表写入、Schema 漂移或不可维护的持久化契约。"
puts "FIX: 按后续规则组和文件消息修复；动态语义无法确认时补测试并交由 Review。"
groups.each do |group, messages|
  puts "\n[sql] #{group}"
  messages.uniq.each { |message| puts message }
end
puts "\nSQL checks failed with #{groups.length} violation group(s)."
exit 1
RUBY
