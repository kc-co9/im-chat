#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

ruby - "$ROOT_DIR" <<'RUBY'
root = ARGV.fetch(0)
violations = []
application_scalar_violations = []
package_types = Hash.new { |types, package_name| types[package_name] = [] }

Dir.glob(File.join(root, "**/src/main/java/**/*.java")).sort.each do |path|
  source = File.read(path)
  package_name = source[/^package\s+([^;]+);/, 1]
  package_types[package_name] << File.basename(path, ".java") if package_name

  if path.match?(%r{/src/main/java/.*/application/.*AppService\.java$})
    source.scan(/public\s+(?!class\b|interface\b|enum\b)([\w.$<>?, \[\]]+)\s+\w+\s*\(([^)]*)\)/m).each do |return_type, parameter_list|
      scalar_parameter = parameter_list.split(",").any? do |parameter|
        parameter = parameter.sub(/@[\w.$]+(?:\([^)]*\))?\s*/, "").strip
        parameter.match?(/\A(?:final\s+)?(?:byte|short|int|long|float|double|boolean|char|String|Byte|Short|Integer|Long|Float|Double|Boolean|Character)\b/)
      end
      if scalar_parameter
        application_scalar_violations << path.delete_prefix(root + File::SEPARATOR)
      end
    end
  end

  next if source.match?(/@(Data|Getter|Setter|Value)\b/)

  simple_getters = source.scan(
    /public\s+[\w.$<>?, \[\]]+\s+(?:get|is)[A-Z]\w*\s*\(\s*\)\s*\{\s*return\s+(?:this\.)?\w+\s*;\s*\}/m
  ).length
  simple_setters = source.scan(
    /public\s+void\s+set[A-Z]\w*\s*\(\s*[\w.$<>?, \[\]]+\s+(\w+)\s*\)\s*\{\s*(?:this\.)?\w+\s*=\s*\1\s*;\s*\}/m
  ).length

  if simple_getters >= 2 && simple_setters >= 2
    violations << path.delete_prefix(root + File::SEPARATOR)
  end
end

mixed_package_violations = package_types.each_with_object([]) do |(package_name, type_names), result|
  next if type_names.length < 6

  has_contract = type_names.any? { |name| name.match?(/(?:Service|Client|Repository|Store|Registry)\z/) }
  has_implementation = type_names.any? do |name|
    name.match?(/(?:Codec|Adapter|Impl)\z/) || name.match?(/\A(?:Jwt|Redis|Mysql)/)
  end
  has_model = type_names.any? { |name| name.match?(/(?:DTO|Params|Result|Event|Claims|Type|Status)\z/) }
  result << package_name if has_contract && has_implementation && has_model
end

unless violations.empty?
  warn "[java-style] Simple handwritten JavaBean accessors found; use Lombok or a record:"
  violations.each { |path| warn path }
  exit 1
end

unless application_scalar_violations.empty?
  warn "[java-style] Application service methods must accept CQRS command/query objects, not scalar business parameters:"
  application_scalar_violations.uniq.each { |path| warn path }
  exit 1
end

unless mixed_package_violations.empty?
  warn "[java-style] package mixes contracts, implementations and models; split by responsibility:"
  mixed_package_violations.sort.each { |package_name| warn package_name }
  exit 1
end

puts "Java style checks passed."
RUBY
