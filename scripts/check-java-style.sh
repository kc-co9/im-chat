#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

ruby - "$ROOT_DIR" <<'RUBY'
root = ARGV.fetch(0)
violations = []
application_scalar_violations = []
declarative_lock_key_violations = []
facade_request_package_violations = []
database_enum_violations = []
base_entity_violations = []
conditional_application_violations = []
repository_mapper_violations = []
redundant_mapper_scan_candidates = []
aggregate_equality_violations = []
http_request_name_violations = []
rpc_contract_input_violations = []
package_types = Hash.new { |types, package_name| types[package_name] = [] }
deployable_source_roots = []

Dir.glob(File.join(root, "**/src/main/java/**/*.java")).sort.each do |path|
  next unless File.read(path).match?(/@SpringBootApplication\b/)

  deployable_source_roots << path.split("/src/main/java/", 2).first + "/src/main/java/"
end

Dir.glob(File.join(root, "**/src/main/java/**/*.java")).sort.each do |path|
  source = File.read(path)
  package_name = source[/^package\s+([^;]+);/, 1]
  package_types[package_name] << File.basename(path, ".java") if package_name

  deployable_application = deployable_source_roots.any? { |source_root| path.start_with?(source_root) }
  if deployable_application && source.match?(/@ConditionalOnProperty\b/)
    conditional_application_violations << path.delete_prefix(root + File::SEPARATOR)
  end

  if path.match?(%r{/src/main/java/.*/infrastructure/domain/repository/}) &&
      source.match?(/^import\s+[^;]+\.infrastructure\.mybatis\.mapper\.[^;]+;/)
    repository_mapper_violations << path.delete_prefix(root + File::SEPARATOR)
  end

  if source.match?(/@MapperScan\b/) &&
      source.match?(/@Configuration\b/) &&
      source.match?(/class\s+\w+\s*\{\s*\}\s*\z/m)
    redundant_mapper_scan_candidates << path
  end

  if source.match?(/\bextends\s+Identification\b/) &&
      source.match?(/@EqualsAndHashCode\s*\([^)]*\bonlyExplicitlyIncluded\s*=\s*true[^)]*\)/m)
    aggregate_equality_violations << path.delete_prefix(root + File::SEPARATOR)
  end

  if path.match?(%r{/[^/]+-facade/src/main/java/.*/facade/(?:command|query)/})
    facade_request_package_violations << path.delete_prefix(root + File::SEPARATOR)
  end

  if source.match?(/@DistributeLock\b.{0,500}?key\s*=\s*"#[^"]*\.toString\(\)[^"]*"/m)
    declarative_lock_key_violations << path.delete_prefix(root + File::SEPARATOR)
  end

  source.scan(
    /@RequestBody(?:\s*\([^)]*\))?(?:\s+@[\w.$]+(?:\([^)]*\))?)*\s+([\w.$]+)\s+\w+/m
  ).each do |request_type|
    simple_name = request_type.first.split(".").last
    unless simple_name.end_with?("Request")
      relative_path = path.delete_prefix(root + File::SEPARATOR)
      http_request_name_violations << "#{relative_path}:#{simple_name}"
    end
  end


  if path.match?(%r{/src/main/java/.*/(?:facade|sdk/rpc)/[^/]*Service\.java$})
    source.scan(/\b[\w.$<>?, \[\]]+\s+\w+\s*\(([^)]*)\)\s*;/m).each do |parameter_list|
      parameter_list.first.split(",").each do |parameter|
        parameter = parameter.sub(/@[\w.$]+(?:\([^)]*\))?\s*/, "").strip
        next if parameter.empty?

        parameter_type = parameter.split(/\s+/).first
        simple_name = parameter_type.split(".").last
        unless simple_name.end_with?("Params")
          relative_path = path.delete_prefix(root + File::SEPARATOR)
          rpc_contract_input_violations << "#{relative_path}:#{simple_name}"
        end
      end
    end
  end

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

  if path.match?(%r{/src/main/java/.*/infrastructure/mybatis/entity/})
    source.scan(/\bprivate\s+String\s+(action|kind|outcome|result|status|type)\s*;/).each do |field|
      relative_path = path.delete_prefix(root + File::SEPARATOR)
      database_enum_violations << "#{relative_path}:#{field.first}"
    end
    standard_fields = %w[id createTime updateTime isDeleted]
    duplicates_standard_shape = standard_fields.all? do |field|
      source.match?(/\bprivate\s+[\w.$<>?, \[\]]+\s+#{field}\s*(?:=\s*[^;]+)?;/)
    end
    if duplicates_standard_shape && !source.match?(/\bextends\s+BaseEntity\b/)
      base_entity_violations << path.delete_prefix(root + File::SEPARATOR)
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

redundant_mapper_scan_violations = redundant_mapper_scan_candidates.map do |path|
  module_root = path.split("/src/main/java/", 2).first
  mapper_paths = Dir.glob(File.join(
    module_root, "src/main/java/**/infrastructure/mybatis/mapper/*.java"
  ))
  next if mapper_paths.empty?
  next unless mapper_paths.all? { |mapper_path| File.read(mapper_path).match?(/@Mapper\b/) }

  path.delete_prefix(root + File::SEPARATOR)
end.compact

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

unless declarative_lock_key_violations.empty?
  warn "[java-style] Declarative lock keys must rely on the lock aspect string conversion; remove explicit toString():"
  declarative_lock_key_violations.uniq.each { |path| warn path }
  exit 1
end

unless facade_request_package_violations.empty?
  warn "[java-style] Facade request contracts must use a params package; command/query are server-side CQRS concepts:"
  facade_request_package_violations.uniq.each { |path| warn path }
  exit 1
end

unless database_enum_violations.empty?
  warn "[java-style] Closed-set database fields must use database-layer enums instead of String:"
  database_enum_violations.uniq.each { |violation| warn violation }
  exit 1
end

unless base_entity_violations.empty?
  warn "[java-style] Entities with the standard persistence fields must extend BaseEntity instead of redeclaring them:"
  base_entity_violations.uniq.each { |path| warn path }
  exit 1
end

unless conditional_application_violations.empty?
  warn "[java-style] Deployable applications must fail fast instead of conditionally disabling production beans:"
  conditional_application_violations.uniq.each { |path| warn path }
  exit 1
end

unless repository_mapper_violations.empty?
  warn "[java-style] Repository implementations must use MyBatis Service instead of directly orchestrating Mapper:"
  repository_mapper_violations.uniq.each { |path| warn path }
  exit 1
end

unless redundant_mapper_scan_violations.empty?
  warn "[java-style] Mapper interfaces already use @Mapper; remove the redundant empty @MapperScan configuration:"
  redundant_mapper_scan_violations.uniq.each { |path| warn path }
  exit 1
end

unless aggregate_equality_violations.empty?
  warn "[java-style] Aggregates extending Identification must not define business-ID-only equality; use @EqualsAndHashCode(callSuper = false):"
  aggregate_equality_violations.uniq.each { |path| warn path }
  exit 1
end

unless http_request_name_violations.empty?
  warn "[java-style] HTTP request body types must use the Request suffix:"
  http_request_name_violations.uniq.each { |violation| warn violation }
  exit 1
end

unless rpc_contract_input_violations.empty?
  warn "[java-style] RPC contract input types must use the Params suffix:"
  rpc_contract_input_violations.uniq.each { |violation| warn violation }
  exit 1
end

unless mixed_package_violations.empty?
  warn "[java-style] package mixes contracts, implementations and models; split by responsibility:"
  mixed_package_violations.sort.each { |package_name| warn package_name }
  exit 1
end

puts "Java style checks passed."
RUBY
