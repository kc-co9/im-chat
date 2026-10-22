#!/usr/bin/env ruby
# 当前 reactor 专用证明：prepare 在 Maven 前删除必需及条件产物；verify 固定摘要；check 拒绝覆盖。
# CLI 参数均为 ROOT 后接输出清单或 epoch；不读取历史缓存，不运行 Maven/容器。
require 'json'
require 'rexml/document'
require 'digest'
require 'fileutils'
require 'time'

class ReactorEvidence
  HA_EXTRA = %w[RoutingHaScenarioManifestTest BrokerFailureRecoveryAppServiceTest LocalRouteAppServiceReceiveTest ConnectionCloseDeduplicatorTest ConnectionRegistryTest GatewayBrokerAuthorizationTest ImMessageNotifierInvokerTest].freeze
  JARS = %w[im-broker/im-broker-server im-gateway/im-ws-gateway/im-ws-gateway-server].freeze

  def initialize(root)
    @root = File.realpath(root)
  end

  def path(relative)
    full = File.expand_path(relative, @root)
    raise "unsafe path: #{relative}" unless full.start_with?(@root + '/')
    cursor = full
    until cursor == @root
      raise "symlink not allowed: #{cursor}" if File.symlink?(cursor)
      cursor = File.dirname(cursor)
    end
    full
  end

  def relative(file)
    file.delete_prefix(@root + '/')
  end

  def epoch(value)
    number = Float(value)
    raise 'epoch must be positive, finite and not in the future' unless number.finite? && number > 0 && number <= Time.now.to_f
    number
  end

  def sources
    @sources ||= Dir.glob(path('**/src/test/java/**/*.java')).sort.map do |file|
      source = File.read(file).gsub(%r{/\*.*?\*/|//[^\n]*}m, '')
      package = source[/^\s*package\s+([\w.]+)\s*;/, 1]
      klass = File.basename(file, '.java')
      next unless package && source.match?(/\bclass\s+#{Regexp.escape(klass)}\b/)
      { 'testPath' => relative(file), 'testClass' => "#{package}.#{klass}",
        'module' => relative(file).split('/src/test/java/').first, 'source' => source }
    end.compact
  end

  def tagged(tag)
    sources.select { |entry| entry['source'].match?(/@(?:org\.junit\.jupiter\.api\.)?Tag\s*\(\s*"#{tag}"\s*\)/) }
  end

  def startup
    apps = Dir.glob(path('**/src/main/java/**/*.java')).sort.select { |file| File.read(file).match?(/@(?:org\.springframework\.boot\.autoconfigure\.)?SpringBootApplication\b/) }
    raise '没有发现 @SpringBootApplication' if apps.empty?
    entries = apps.map do |file|
      application = File.basename(file, '.java')
      mod = relative(file).split('/src/main/java/').first
      candidates = tagged('startup-smoke').select do |entry|
        entry['source'].match?(/classes\s*=\s*#{Regexp.escape(application)}\.class\b/) || File.basename(entry['testPath']).start_with?(application)
      end
      candidates = tagged('startup-smoke').select { |entry| entry['module'] == mod } if candidates.empty?
      raise "#{application}: 需要唯一 startup-smoke test，实际 #{candidates.size}" unless candidates.size == 1
      entry = candidates.first
      raise "#{application}: startup test module mismatch" unless entry['module'] == mod
      %w[discovery config].each do |kind|
        raise "#{application}: 未显式关闭 Nacos #{kind}" unless entry['source'].include?("spring.cloud.nacos.#{kind}.enabled=false")
      end
      entry.reject { |key, _| key == 'source' }.merge('application' => application, 'applicationPath' => relative(file))
    end
    raise 'startup tests must map one-to-one' unless entries.map { |entry| entry['testClass'] }.uniq.size == entries.size
    entries
  end

  def scenarios
    File.readlines(path('im-test/im-routing-ha/src/test/resources/routing-ha-scenarios.tsv'), chomp: true).reject { |line| line.empty? || line.start_with?('#') }.map do |line|
      fields = line.split("\t", -1)
      raise 'invalid HA scenario row' unless fields.size == 5
      fields
    end.tap { |rows| raise 'expected exactly 25 scenarios' unless rows.size == 25 && rows.map(&:first).uniq.size == 25 }
  end

  def resolve(short)
    matches = sources.select { |entry| entry['testClass'].split('.').last == short }
    raise "ambiguous/missing test class #{short}" unless matches.size == 1
    matches.first
  end

  def ha_classes
    (scenarios.reject { |row| row[2] == 'topology' }.map { |row| row[3].split('#').first } + HA_EXTRA).uniq.map { |short| resolve(short) }
  end

  # 仅识别顶层类声明之前的显式注解；方法级条件及其他条件仍按必需项严格校验。
  def architecture_condition(entry)
    header, body = entry.fetch('source').split(/\bclass\s+#{Regexp.escape(entry['testClass'].split('.').last)}\b/, 2)
    return if body.to_s.match?(/@(?:[\w.]+\.)?(?:Enabled\w*|Disabled\w*)\b/)
    annotations = header.scan(/@(?:org\.junit\.jupiter\.api\.condition\.)?EnabledIfSystemProperty\s*\(\s*named\s*=\s*"([^"\n]+)"\s*,\s*matches\s*=\s*"([^"\n]+)"\s*\)/)
    conditions = header.scan(/@(?:[\w.]+\.)?(?:Enabled\w*|Disabled\w*)\b/)
    return unless annotations.size == 1 && conditions.size == 1
    { 'annotation' => 'EnabledIfSystemProperty', 'named' => annotations[0][0], 'matches' => annotations[0][1] }
  end

  def selection
    groups = { 'startup' => startup, 'architecture' => sources.select { |entry| entry['testPath'].start_with?('im-test/im-architecture-test/src/test/java/com/') && File.basename(entry['testPath']).end_with?('Test.java') },
               'behavior' => tagged('realtime-behavior'), 'e2e' => tagged('e2e'), 'ha' => ha_classes }
    conditional, groups['architecture'] = groups['architecture'].partition { |entry| architecture_condition(entry) }
    groups.each { |name, entries| raise "no required tests for #{name}" if entries.empty? }
    groups['conditionalArchitecture'] = conditional.map { |entry| entry.merge('condition' => architecture_condition(entry)) }
    groups.transform_values { |entries| entries.map { |entry| entry.reject { |key, _| key == 'source' } } }
  end

  def report(entry)
    "#{entry.fetch('module')}/target/surefire-reports/TEST-#{entry.fetch('testClass')}.xml"
  end

  # 只支持当前源码的普通/参数化 JUnit 方法；不猜测 Nested、工厂或继承测试。
  def methods(entry)
    source = File.read(path(entry.fetch('testPath'))).gsub(%r{/\*.*?\*/|//[^\n]*}m, '')
    raise "unsupported JUnit shape: #{entry['testClass']}" if source.match?(/@(?:[\w.]+\.)?(?:Nested|TestFactory|RepeatedTest)\b/) || source.match?(/\bclass\s+#{Regexp.escape(entry['testClass'].split('.').last)}\s+extends\s/)
    names = source.scan(/@(?:org\.junit\.(?:jupiter\.api|jupiter\.params)\.)?(?:Test|ParameterizedTest)\b(?:(?!\bvoid\b).)*\bvoid\s+(\w+)\s*\(/m).flatten
    raise "cannot resolve JUnit methods: #{entry['testClass']}" if names.empty? || names.uniq.size != names.size
    names
  end

  def matching_method?(actual, expected)
    actual == expected || actual.match?(/\A#{Regexp.escape(expected)}\([^()]*\)(?:\[\d+\])?\z/)
  end

  def cases(entry, since, conditional: false)
    file = path(report(entry))
    raise "#{entry['testClass']}: 没有产生测试报告" unless File.file?(file)
    raise "stale report: #{file}" if File.mtime(file).to_f < since
    suite = REXML::Document.new(File.read(file)).root
    raise "wrong suite classname: #{file}" unless suite && suite.name == 'testsuite' && suite.attributes['name'] == entry['testClass']
    tests = suite.get_elements('testcase')
    raise "zero/inconsistent tests: #{file}" unless !tests.empty? && Integer(suite.attributes['tests']) == tests.size
    %w[failures errors].each { |key| raise "#{key} report: #{file}" unless Integer(suite.attributes[key] || '0') == 0 }
    skipped = tests.select { |test| test.elements['skipped'] }
    raise "inconsistent skipped count: #{file}" unless Integer(suite.attributes['skipped'] || '0') == skipped.size
    allow_skipped = conditional && skipped.size == tests.size
    raise "partial/required skipped report: #{file}" unless skipped.empty? || allow_skipped
    tests.each do |test|
      raise "invalid/skipped testcase: #{file}" unless test.attributes['classname'] == entry['testClass'] && test.attributes['name'].to_s != '' && %w[failure error].none? { |key| test.elements[key] }
    end
    if allow_skipped
      raise "missing skip reason: #{file}" unless skipped.all? { |test| !skip_reason(test).empty? }
      return tests
    end
    methods(entry).each do |method|
      raise "missing executed method #{entry['testClass']}##{method}" unless tests.any? { |test| matching_method?(test.attributes['name'], method) }
    end
    tests
  end

  def skip_reason(test)
    skipped = test.elements['skipped']
    return '' unless skipped
    message = skipped.attributes['message'].to_s.strip
    message.empty? ? skipped.texts.map(&:value).join.strip : message
  end

  def jars(since = nil)
    JARS.map do |mod|
      files = Dir.glob(path("#{mod}/target/*-exec.jar"))
      raise "expected one exec JAR: #{mod}" unless files.size == 1
      file = path(relative(files.first))
      raise "stale/empty JAR: #{file}" unless File.file?(file) && File.size(file) > 0 && (!since || File.mtime(file).to_f >= since)
      relative(file)
    end
  end

  def save(output, data)
    FileUtils.mkdir_p(File.dirname(output))
    File.write(output, JSON.pretty_generate(data) + "\n")
  end

  def prepare(output)
    groups = selection
    files = groups.values.flatten.map { |entry| report(entry) }.uniq + JARS.flat_map { |mod| Dir.glob(path("#{mod}/target/*-exec.jar")).map { |file| relative(file) } }
    clear_targets(files)
    save(output, { 'version' => 1, 'root' => @root, 'startedAt' => Time.now.to_f, 'groups' => groups })
  end

  # 先校验整批路径再删除，避免后一个符号链接失败前已删除前面的报告。
  def clear_targets(files)
    targets = files.map do |file|
      raise "refusing non-target removal #{file}" unless file.include?('/target/')
      path(file)
    end
    targets.each do |full|
      File.delete(full) if File.exist?(full)
      raise "failed to clear #{full}" if File.exist?(full)
    end
  end

  def startup_clear
    clear_targets(startup.map { |entry| report(entry) })
  end

  def load_manifest(file)
    data = JSON.parse(File.read(file))
    raise 'invalid reactor manifest' unless data['version'] == 1 && data['root'] == @root && data['groups'] == selection
    epoch(data.fetch('startedAt'))
    data
  end

  def validate(data)
    since = epoch(data.fetch('startedAt'))
    summaries = {}
    data.fetch('groups').each do |name, entries|
      if name == 'conditionalArchitecture'
        records = entries.map do |entry|
          tests = cases(entry, since, conditional: true)
          reasons = tests.select { |test| test.elements['skipped'] }.map { |test| skip_reason(test) }
          { 'testClass' => entry['testClass'], 'condition' => entry.fetch('condition'),
            'status' => reasons.empty? ? 'executed' : 'conditional-skipped',
            'tests' => tests.size, 'skipped' => reasons.size, 'reasons' => reasons }
        end
        summaries[name] = { 'reports' => entries.map { |entry| report(entry) }, 'tests' => records.sum { |record| record['tests'] },
                            'skipped' => records.sum { |record| record['skipped'] }, 'records' => records }
        next
      end
      summaries[name] = { 'reports' => entries.map { |entry| report(entry) }, 'tests' => entries.sum { |entry| cases(entry, since).size } }
    end
    files = data['groups'].values.flatten.map { |entry| report(entry) }.uniq + jars(since)
    [files.to_h { |file| [file, Digest::SHA256.file(path(file)).hexdigest] }, summaries]
  end

  def verify(file)
    data = load_manifest(file)
    raise 'manifest already verified' if data.key?('sha256')
    data['sha256'], data['summaries'] = validate(data)
    conditional = data['groups'].fetch('conditionalArchitecture')
    counts = data['groups'].values.flatten.uniq { |entry| report(entry) }.map do |entry|
      tests = cases(entry, data['startedAt'], conditional: conditional.include?(entry))
      [tests.size, tests.count { |test| test.elements['skipped'] }]
    end
    data['testSummary'] = { 'tests' => counts.sum(&:first), 'failures' => 0, 'errors' => 0, 'skipped' => counts.sum(&:last) }
    data['evidenceSource'] = 'full-reactor'
    save(file, data)
  end

  def check(file)
    data = load_manifest(file)
    hashes, summaries = validate(data)
    raise 'reactor output changed or not verified' unless data['sha256'] == hashes && data['summaries'] == summaries
    data
  end

  def ha_check(since, fresh_jars = true)
    since = epoch(since)
    ha_classes.each { |entry| cases(entry, since) }
    jars(fresh_jars ? since : nil)
    scenarios.each do |row|
      next if row[2] == 'topology'
      klass, method = row[3].split('#', 2)
      next unless method
      entry = resolve(klass)
      raise "unknown selector #{row[3]}" unless methods(entry).include?(method)
      raise "unexecuted selector #{row[3]}" unless cases(entry, since).any? { |test| matching_method?(test.attributes['name'], method) }
    end
  end

  def ha_evidence(since, output, topology, fresh_jars = true)
    since = epoch(since)
    ha_check(since, fresh_jars)
    rows = scenarios.map do |id, name, kind, selector, assertion|
      duration = 0
      evidence = selector
      if kind == 'topology'
        evidence = { 'cluster-state-consistent' => topology, 'gateway-stop-start' => File.join(File.dirname(topology), 'diagnostics/compose.log') }.fetch(selector)
        raise "missing topology evidence #{evidence}" unless File.file?(evidence)
      else
        klass, method = selector.split('#', 2)
        selected = cases(resolve(klass), since).select { |test| !method || matching_method?(test.attributes['name'], method) }
        duration = selected.sum { |test| (Float(test.attributes['time'] || '0') * 1000).round }
      end
      { 'id' => id, 'name' => name, 'status' => 'passed', 'evidenceKind' => kind, 'evidence' => evidence, 'assertion' => assertion, 'durationMillis' => duration, 'finishedAt' => Time.now.utc.iso8601 }
    end
    File.write(output, rows.map { |row| JSON.generate(row) }.join("\n") + "\n")
  end
end

begin
  command, root, *args = ARGV
  helper = ReactorEvidence.new(root || '')
  case command
  when 'prepare', 'verify', 'check'
    raise 'expected ROOT MANIFEST' unless args.size == 1
    helper.public_send(command, args[0])
  when 'startup-manifest'
    raise 'expected ROOT OUTPUT' unless args.size == 1
    helper.save(args[0], { 'applications' => helper.startup })
  when 'startup-clear'
    raise 'expected ROOT only' unless args.empty?
    helper.startup_clear
  when 'startup-check'
    raise 'expected ROOT EPOCH' unless args.size == 1
    since = helper.epoch(args[0])
    helper.startup.each { |entry| helper.cases(entry, since) }
  when 'ha-check'
    raise 'expected ROOT EPOCH' unless args.size == 1
    helper.ha_check(args[0])
  when 'ha-standalone-evidence'
    raise 'expected ROOT EPOCH OUTPUT TOPOLOGY' unless args.size == 3
    helper.ha_evidence(*args, false)
  when 'ha-evidence'
    raise 'expected ROOT EPOCH OUTPUT TOPOLOGY [MANIFEST]' unless [3, 4].include?(args.size)
    if args[3]
      data = helper.check(args[3])
      raise 'reactor epoch mismatch' unless data['startedAt'] == helper.epoch(args[0])
    end
    helper.ha_evidence(*args.first(3))
  else
    raise 'unknown reactor-evidence command'
  end
rescue StandardError => error
  warn "reactor evidence: #{error.message}"
  exit 1
end
