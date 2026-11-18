#!/usr/bin/env ruby
# 当前 reactor 专用证明：prepare 在 Maven 前删除必需及条件产物；verify 固定摘要；check 拒绝覆盖。
# CLI参数为ROOT及各入口固定参数；model-collect/model-verify会执行固定Maven，须另获运行许可。
# model-attest/model-execution只读受审来源；不读取历史缓存、不运行容器。
require 'json'
require 'rexml/document'
require 'digest'
require 'fileutils'
require 'time'
require 'date'
require 'open3'
require 'shellwords'

class ReactorEvidence
  FORMAL_CANONICAL = {
    'pom.xml' => 'com.co.kc.im:im-chat:0.0.1-SNAPSHOT',
    'im-common/pom.xml' => 'com.co.kc.im:im-common:0.0.1-SNAPSHOT',
    'im-plugin/pom.xml' => 'com.co.kc.im:im-plugin:0.0.1-SNAPSHOT',
    'im-plugin/im-cache/pom.xml' => 'com.co.kc.im:im-cache:0.0.1-SNAPSHOT',
    'im-plugin/im-identity/pom.xml' => 'com.co.kc.im:im-identity:0.0.1-SNAPSHOT',
    'im-plugin/im-excel/pom.xml' => 'com.co.kc.im:im-excel:0.0.1-SNAPSHOT',
    'im-plugin/im-metrics/pom.xml' => 'com.co.kc.im:im-metrics:0.0.1-SNAPSHOT',
    'im-plugin/im-tracing/pom.xml' => 'com.co.kc.im:im-tracing:0.0.1-SNAPSHOT',
    'im-plugin/im-lock/pom.xml' => 'com.co.kc.im:im-lock:0.0.1-SNAPSHOT',
    'im-plugin/im-datasource/pom.xml' => 'com.co.kc.im:im-datasource:0.0.1-SNAPSHOT',
    'im-plugin/im-session/pom.xml' => 'com.co.kc.im:im-session:0.0.1-SNAPSHOT',
    'im-plugin/im-web/pom.xml' => 'com.co.kc.im:im-web:0.0.1-SNAPSHOT',
    'im-plugin/im-dubbo/pom.xml' => 'com.co.kc.im:im-dubbo:0.0.1-SNAPSHOT',
    'im-plugin/im-bolt/pom.xml' => 'com.co.kc.im:im-bolt:0.0.1-SNAPSHOT',
    'im-plugin/im-mq-kafka/pom.xml' => 'com.co.kc.im:im-mq-kafka:0.0.1-SNAPSHOT',
    'im-plugin/im-gossip/pom.xml' => 'com.co.kc.im:im-gossip:0.0.1-SNAPSHOT',
    'im-plugin/im-raft/pom.xml' => 'com.co.kc.im:im-raft:0.0.1-SNAPSHOT',
    'im-plugin/im-nacos/pom.xml' => 'com.co.kc.im:im-nacos:0.0.1-SNAPSHOT',
    'im-gateway/pom.xml' => 'com.co.kc.im:im-gateway:0.0.1-SNAPSHOT',
    'im-gateway/im-http-gateway/pom.xml' => 'com.co.kc.im:im-http-gateway:0.0.1-SNAPSHOT',
    'im-gateway/im-ws-gateway/pom.xml' => 'com.co.kc.im:im-ws-gateway:0.0.1-SNAPSHOT',
    'im-gateway/im-ws-gateway/im-ws-gateway-sdk/pom.xml' => 'com.co.kc.im:im-ws-gateway-sdk:0.0.1-SNAPSHOT',
    'im-gateway/im-ws-gateway/im-ws-gateway-server/pom.xml' => 'com.co.kc.im:im-ws-gateway-server:0.0.1-SNAPSHOT',
    'im-broker/pom.xml' => 'com.co.kc.im:im-broker:0.0.1-SNAPSHOT',
    'im-broker/im-broker-sdk/pom.xml' => 'com.co.kc.im:im-broker-sdk:0.0.1-SNAPSHOT',
    'im-broker/im-broker-server/pom.xml' => 'com.co.kc.im:im-broker-server:0.0.1-SNAPSHOT',
    'im-service/pom.xml' => 'com.co.kc.im:im-service:0.0.1-SNAPSHOT',
    'im-service/im-account/pom.xml' => 'com.co.kc.im:im-account:0.0.1-SNAPSHOT',
    'im-service/im-account/im-account-facade/pom.xml' => 'com.co.kc.im:im-account-facade:0.0.1-SNAPSHOT',
    'im-service/im-account/im-account-admin-facade/pom.xml' => 'com.co.kc.im:im-account-admin-facade:0.0.1-SNAPSHOT',
    'im-service/im-account/im-account-server/pom.xml' => 'com.co.kc.im:im-account-server:0.0.1-SNAPSHOT',
    'im-service/im-social/pom.xml' => 'com.co.kc.im:im-social:0.0.1-SNAPSHOT',
    'im-service/im-social/im-social-facade/pom.xml' => 'com.co.kc.im:im-social-facade:0.0.1-SNAPSHOT',
    'im-service/im-social/im-social-server/pom.xml' => 'com.co.kc.im:im-social-server:0.0.1-SNAPSHOT',
    'im-service/im-message/pom.xml' => 'com.co.kc.im:im-message:0.0.1-SNAPSHOT',
    'im-service/im-message/im-message-facade/pom.xml' => 'com.co.kc.im:im-message-facade:0.0.1-SNAPSHOT',
    'im-service/im-message/im-message-server/pom.xml' => 'com.co.kc.im:im-message-server:0.0.1-SNAPSHOT',
    'im-management/pom.xml' => 'com.co.kc.im:im-management:0.0.1-SNAPSHOT',
    'im-management/im-iam/pom.xml' => 'com.co.kc.im:im-iam:0.0.1-SNAPSHOT',
    'im-management/im-iam/im-iam-server/pom.xml' => 'com.co.kc.im:im-iam-server:0.0.1-SNAPSHOT',
    'im-management/im-iam/im-iam-sdk/pom.xml' => 'com.co.kc.im:im-iam-sdk:0.0.1-SNAPSHOT',
    'im-management/im-admin/pom.xml' => 'com.co.kc.im:im-admin:0.0.1-SNAPSHOT',
    'im-management/im-ops/pom.xml' => 'com.co.kc.im:im-ops:0.0.1-SNAPSHOT',
    'im-management/im-ops/im-ops-server/pom.xml' => 'com.co.kc.im:im-ops-server:0.0.1-SNAPSHOT',
    'im-management/im-ops/im-ops-cli/pom.xml' => 'com.co.kc.im:im-ops-cli:0.0.1-SNAPSHOT',
    'im-management/im-audit/pom.xml' => 'com.co.kc.im:im-audit:0.0.1-SNAPSHOT',
    'im-management/im-audit/im-audit-sdk/pom.xml' => 'com.co.kc.im:im-audit-sdk:0.0.1-SNAPSHOT',
    'im-management/im-audit/im-audit-server/pom.xml' => 'com.co.kc.im:im-audit-server:0.0.1-SNAPSHOT',
    'im-test/pom.xml' => 'com.co.kc.im:im-test:0.0.1-SNAPSHOT',
    'im-test/im-architecture-test/pom.xml' => 'com.co.kc.im:im-architecture-test:0.0.1-SNAPSHOT',
    'im-test/im-e2e-test/pom.xml' => 'com.co.kc.im:im-e2e-test:0.0.1-SNAPSHOT',
    'im-test/im-load-test/pom.xml' => 'com.co.kc.im:im-load-test:0.0.1-SNAPSHOT',
    'im-test/im-routing-ha/pom.xml' => 'com.co.kc.im:im-routing-ha:0.0.1-SNAPSHOT'
  }.freeze
  # 受审 53 个 canonical POM 的 packaging：其余显式 pom；列明模块为缺省 jar 或显式 jar。
  # 正文合同独立于 caller oracle；fixture 正文/期望不从本表生成。
  FORMAL_JAR_ARTIFACTS = %w[
    im-common im-cache im-identity im-excel im-metrics im-tracing im-lock im-datasource
    im-session im-web im-dubbo im-bolt im-mq-kafka im-gossip im-raft im-nacos
    im-http-gateway im-ws-gateway-sdk im-ws-gateway-server im-broker-sdk im-broker-server
    im-account-facade im-account-admin-facade im-account-server im-social-facade im-social-server
    im-message-facade im-message-server im-iam-server im-iam-sdk im-admin im-ops-server im-ops-cli
    im-audit-sdk im-audit-server im-architecture-test im-e2e-test im-load-test im-routing-ha
  ].freeze
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
      source = File.read(file, encoding: Encoding::UTF_8).gsub(%r{/\*.*?\*/|//[^\n]*}m, '')
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
    apps = Dir.glob(path('**/src/main/java/**/*.java')).sort.select { |file| File.read(file, encoding: Encoding::UTF_8).match?(/@(?:org\.springframework\.boot\.autoconfigure\.)?SpringBootApplication\b/) }
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
    File.readlines(path('im-test/im-routing-ha/src/test/resources/routing-ha-scenarios.tsv'), encoding: Encoding::UTF_8, chomp: true).reject { |line| line.empty? || line.start_with?('#') }.map do |line|
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
    source = File.read(path(entry.fetch('testPath')), encoding: Encoding::UTF_8).gsub(%r{/\*.*?\*/|//[^\n]*}m, '')
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
    suite = REXML::Document.new(File.read(file, encoding: Encoding::UTF_8)).root
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

  # 仅内部 bootstrap seam。baseline 必须由独立 Review 固定，不能由本方法扫描后自证。
  # layout 显式描述发行版/JDK/user；system_root 默认 /，仅 fixture 映射到新隔离根。
  # 固定检查 /usr/local/etc/mavenrc 与 /etc/mavenrc；runner 接收固定 argv/cwd/env，不允许 lifecycle 或清理。
  def collect_host_model(layout:, baseline:, environment:, oracle:, runner:, system_root: '/', settings_identity_sha: 'aca5b36fe4d0bbcf3203544c6b13da4c303d9431eaa317f373d7305250ceb4ca')
    snapshot = snapshot_host_inputs(layout, baseline, environment, system_root)
    validate_repository_settings(File.binread(layout.fetch('mavenHome') + '/conf/settings.xml'), settings_identity_sha)
    output = @root + '/.harness/runtime/effective-pom.xml'
    help = 'org.apache.maven.plugins:maven-help-plugin:3.5.1:'
    commands = [
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'effective-pom', '-Dverbose=false', '-Doutput=' + output],
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'evaluate', '-Dexpression=settings.localRepository', '-DforceStdout'],
      ['mvn', '-o', '-f', 'pom.xml', help + 'active-profiles']
    ]
    results = commands.map do |argv|
      raise 'host input changed before command' unless snapshot_host_inputs(layout, baseline, environment, system_root) == snapshot
      result = runner.call(argv.dup, @root, environment.dup)
      raise 'offline model command failed' unless result.is_a?(Hash) && result.fetch('exitCode') == 0
      raise 'host input changed after command' unless snapshot_host_inputs(layout, baseline, environment, system_root) == snapshot
      result
    end
    repository = results[1].fetch('stdout').strip
    provenance_absolute_path(repository)
    raise 'noncanonical local repository' unless File.directory?(repository) && File.realpath(repository) == repository
    # profiles 的真实 Maven 输出与已审 settings 激活来源还未接入；不可把它误作完整 provenance。
    { 'commands' => commands, 'results' => results, 'sourceSha256' => Digest::SHA256.hexdigest(JSON.generate(snapshot)),
      'localRepository' => repository, 'effectivePomPath' => output, 'bootstrapComplete' => false }
  end

  def snapshot_host_inputs(layout, baseline, environment, system_root)
    provenance_absolute_path(system_root)
    raise 'unapproved host input system root' unless File.directory?(system_root) && File.realpath(system_root) == system_root
    %w[usr/local/etc/mavenrc etc/mavenrc].each do |relative_rc|
      rc = File.join(system_root, relative_rc)
      raise 'unapproved host input global rc' if File.exist?(rc) || File.symlink?(rc)
    end
    raise 'unapproved host input layout' unless layout.is_a?(Hash) && layout.keys.sort == %w[jdk launcher mavenHome userHome]
    layout.each_value do |value|
      provenance_absolute_path(value)
      raise 'unapproved host input canonical path' unless File.realpath(value) == value
    end
    raise 'unapproved host input launcher location' unless layout.fetch('launcher').start_with?(layout.fetch('mavenHome') + '/')
    %w[MAVEN_ARGS MAVEN_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_PROJECTBASEDIR MAVEN_CONFIG MAVEN_BASEDIR MAVEN_DEBUG_OPTS _JAVA_OPTIONS].each do |key|
      raise 'unapproved host input environment' if environment.key?(key)
    end
    raise 'unapproved host input HOME' unless environment.fetch('HOME') == layout.fetch('userHome')
    raise 'unapproved host input JAVA_HOME' unless environment.fetch('JAVA_HOME') == layout.fetch('jdk')
    search = environment.fetch('PATH').split(File::PATH_SEPARATOR)
    raise 'unapproved host input PATH' unless search.all? { |entry| entry.start_with?('/') && File.expand_path(entry) == entry }
    launcher = search.map { |entry| entry + '/mvn' }.find { |entry| File.file?(entry) && File.executable?(entry) }
    raise 'unapproved host input launcher' unless launcher == layout.fetch('launcher') && !File.symlink?(launcher)
    cursor = @root
    loop do
      raise 'unapproved host input ancestor .mvn' if File.exist?(cursor + '/.mvn') || File.symlink?(cursor + '/.mvn')
      break if cursor == '/'
      cursor = File.dirname(cursor)
    end
    raise 'unapproved host input baseline' unless baseline.is_a?(Hash) && !baseline.empty?
    %w[.mavenrc .m2/settings.xml .m2/toolchains.xml].each do |rel|
      raise 'unapproved host input user source' if File.exist?(layout.fetch('userHome') + '/' + rel) || File.symlink?(layout.fetch('userHome') + '/' + rel)
    end
    actual = {}
    { 'home' => layout.fetch('mavenHome'), 'jdk' => layout.fetch('jdk') }.each do |label, directory|
      Dir.glob(directory + '/**/*', File::FNM_DOTMATCH).each do |file|
        next if File.directory?(file) && !File.symlink?(file)
        raise 'unapproved host input type' unless File.file?(file) && !File.symlink?(file)
        actual[label + '/' + file.delete_prefix(directory + '/')] = Digest::SHA256.file(file).hexdigest
      end
    end
    raise 'unapproved host input distribution' unless actual == baseline
    actual
  end

  # 仅消费显式传入的 Mojo 正文（不含 logger 外壳）；不接 collect/正式来源链。
  # settings bytes 在本次调用内重验整文件 SHA 和语义，不能用 caller 摘要代替。
  def read_active_profiles_body(body, oracle:, settings_xml:, settings_sha256:, settings_identity_sha:)
    raise 'invalid active profiles settings' unless settings_sha256.is_a?(String) && settings_sha256.match?(/\A[0-9a-f]{64}\z/) && Digest::SHA256.hexdigest(settings_xml) == settings_sha256
    settings = validate_repository_settings(settings_xml, settings_identity_sha)
    begin
      raw_oracle = read_raw_pom_oracle
      keys = %w[gav pom basedir packaging modules sha256]
      raise 'invalid active profiles oracle' unless oracle.map { |entry| entry.select { |key, _| keys.include?(key) } } == raw_oracle.map { |entry| entry.select { |key, _| keys.include?(key) } }
      oracle = raw_oracle
    rescue StandardError
      raise 'invalid active profiles oracle'
    end
    expected = oracle.map do |entry|
      group, artifact, version = entry.fetch('gav').split(':')
      [group, artifact, entry.fetch('packaging'), version].join(':')
    end
    raise 'invalid active profiles oracle' unless expected.size == 53 && expected.uniq.size == 53 && oracle.map { |entry| entry.fetch('gav') }.sort == FORMAL_CANONICAL.values.sort
    raise 'invalid active profiles oracle' unless oracle.all? do |entry|
      artifact = entry.fetch('gav').split(':')[1]
      entry.fetch('packaging') == (FORMAL_JAR_ARTIFACTS.include?(artifact) ? 'jar' : 'pom')
    end
    text = body.dup.force_encoding(Encoding::UTF_8)
    raise 'invalid active profiles body' unless text.valid_encoding? && !text.start_with?("﻿") && !text.match?(/[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/)
    block = /\nActive Profiles for Project '([^\n]+)':\n\nThe following profiles are active:\n\n - ([^\n]+) \(source: ([^\n]+)\)\n\n\n/
    records = []
    offset = 0
    while offset < text.length
      match = block.match(text, offset)
      raise 'invalid active profiles body' unless match && match.begin(0) == offset
      records << match.captures
      offset = match.end(0)
    end
    raise 'invalid active profiles projects' unless records.map(&:first).sort == expected.sort
    raise 'invalid active profiles source' unless records.all? { |record| record[2] == 'external' }
    raise 'invalid active profiles identity' unless records.all? do |record|
      identity = record[1]
      !identity.empty? && !identity.match?(/\A[ \t\r\n]|[ \t\r\n]\z/) && Digest::SHA256.hexdigest(identity.encode(Encoding::UTF_8)) == settings.fetch('activeProfileIdentitySha256')
    end
    { 'projectCount' => records.size, 'bodySha256' => Digest::SHA256.hexdigest(body), 'settings' => settings }
  end

  # 外壳第一段专用 inert seam；segment 仅含 scanning/build-order，非完整 stdout。
  # 同次验证 bound XML/raw 来源；不接受 caller 自填 displayProjects。
  def read_active_profiles_build_order(segment, xml:, oracle:, binding:)
    model = read_bound_effective_model(xml, oracle, binding)
    projects = model.fetch('displayProjects')
    lines = ["[INFO] Scanning for projects...", '[INFO] ' + '-' * 72, '[INFO] Reactor Build Order:', '[INFO] ']
    projects.each do |project|
      name = project.fetch('name')
      packaging = project.fetch('packaging')
      width = name.encode(Encoding::UTF_16LE).bytesize / 2 + packaging.encode(Encoding::UTF_16LE).bytesize / 2
      lines << '[INFO] ' + name + ' ' * [1, 72 - width - 2].max + '[' + packaging + ']'
    end
    expected = lines.join("\n") + "\n"
    raise 'invalid active profiles build order' unless segment == expected
    { 'projectCount' => projects.size, 'segmentSha256' => Digest::SHA256.hexdigest(segment), 'effectivePomSha256' => model.fetch('effectivePomSha256') }
  end

  # 外壳第二段：仅根项目banner；根身份来自独立raw合同，不假定XML第一项。
  def read_active_profiles_root_banner(segment, xml:, oracle:, binding:)
    model = read_bound_effective_model(xml, oracle, binding)
    root = model.fetch('displayProjects').find { |project| project.fetch('gav') == FORMAL_CANONICAL.fetch('pom.xml') }
    units = lambda { |text| text.encode(Encoding::UTF_16LE).bytesize / 2 }
    group, artifact, version = root.fetch('gav').split(':')
    coordinate = group + ':' + artifact
    length = units.call(coordinate) + 8
    left = [0, (72 - length) / 2].max
    banner = '-' * left + '--< ' + coordinate + ' >--' + '-' * [0, 72 - length - left].max
    building = 'Building ' + root.fetch('name') + ' ' + version
    counter = ' [1/53]'
    building += ' ' * [0, 72 - units.call(building) - units.call(counter)].max + counter
    packaging = root.fetch('packaging')
    left = [0, (72 - units.call(packaging) - 4) / 2].max
    package_line = '-' * left + '[ ' + packaging + ' ]' + '-' * [0, 72 - units.call(packaging) - 4 - left].max
    expected = ['', banner, building, package_line].map { |line| '[INFO] ' + line + "\n" }.join
    raise 'invalid active profiles root banner' unless segment == expected
    { 'segmentSha256' => Digest::SHA256.hexdigest(segment), 'effectivePomSha256' => model.fetch('effectivePomSha256') }
  end

  # 第三段限定logger/plugin前缀与正文；正文由同次settings/raw guard验证。
  def read_active_profiles_logged_body(segment, xml:, oracle:, binding:, settings_xml:, settings_sha256:, settings_identity_sha:)
    model = read_bound_effective_model(xml, oracle, binding)
    root = model.fetch('displayProjects').find { |project| project.fetch('gav') == FORMAL_CANONICAL.fetch('pom.xml') }
    artifact = root.fetch('gav').split(':')[1]
    prefix = "[INFO] \n[INFO] --- maven-help-plugin:3.5.1:active-profiles (default-cli) @ " + artifact + " ---\n[INFO] "
    raise 'invalid active profiles logged body' unless segment.start_with?(prefix) && segment.end_with?("\n")
    body = segment.byteslice(prefix.bytesize, segment.bytesize - prefix.bytesize - 1)
    summary = read_active_profiles_body(body, oracle: oracle, settings_xml: settings_xml, settings_sha256: settings_sha256, settings_identity_sha: settings_identity_sha)
    expected_projects = model.fetch('displayProjects').map do |project|
      group, project_artifact, version = project.fetch('gav').split(':')
      [group, project_artifact, project.fetch('packaging'), version].join(':')
    end
    raise 'invalid active profiles logged body' unless body.scan(/^Active Profiles for Project '([^\n]+)':$/).flatten == expected_projects
    summary.merge('segmentSha256' => Digest::SHA256.hexdigest(segment), 'effectivePomSha256' => model.fetch('effectivePomSha256'))
  end

  # 仅在既有 runtime 下独占创建一层私有输出；不建父目录、不修复既有权限。
  # 目录链必须无链接、同 UID 且不可被组或其他用户写入，失败保留现场。
  def create_model_output_directory(output)
    runtime = @root + '/.harness/runtime'
    raise 'invalid model output directory' unless output.is_a?(String) && File.absolute_path(output) == output && File.dirname(output) == runtime
    path(relative(output))
    cursor = runtime
    loop do
      stat = File.lstat(cursor)
      raise 'invalid model output parent' unless stat.directory? && !stat.symlink? && stat.uid == Process.uid && stat.mode & 0022 == 0
      break if cursor == @root
      cursor = File.dirname(cursor)
    end
    raise 'model output already exists' if File.exist?(output) || File.symlink?(output)
    Dir.mkdir(output, 0700)
    stat = File.lstat(output)
    raise 'invalid model output directory' unless stat.directory? && stat.uid == Process.uid && stat.mode & 0777 == 0700
    output
  end

  # 三个固定离线命令的纯结果消费接缝；不执行 runner，不赋予宿主可信身份。
  # 每次仍经公共 reader 重读 raw/bound/settings；stderr 或额外字节失败关闭。
  def read_model_command_results(results, xml:, oracle:, binding:, settings_xml:, settings_sha256:, settings_identity_sha:)
    raise 'invalid model command results' unless results.is_a?(Array) && results.size == 3
    results.each do |result|
      raise 'invalid model command results' unless result.is_a?(Hash) && result.keys.sort == %w[exitCode stderr stdout] && result['exitCode'] == 0 && result['stderr'] == '' && result['stdout'].is_a?(String)
    end
    raise 'invalid model command results' unless results[0].fetch('stdout') == ''
    repository = results[1].fetch('stdout').dup.force_encoding(Encoding::UTF_8)
    raise 'invalid local repository output' unless repository.valid_encoding? && !repository.match?(/[\u0000-\u001f\u007f-\u009f﻿]/) && !repository.match?(/\A\s|\s\z/) && !repository.include?('${')
    provenance_absolute_path(repository)
    raise 'invalid local repository output' unless repository == binding.fetch('inputs').fetch('localRepository').fetch('path') && File.directory?(repository) && File.realpath(repository) == repository
    summary = read_active_profiles_stdout(results[2].fetch('stdout'), xml: xml, oracle: oracle, binding: binding,
      settings_xml: settings_xml, settings_sha256: settings_sha256, settings_identity_sha: settings_identity_sha)
    summary.merge('commandOutputSha256' => results.map { |result| Digest::SHA256.hexdigest(result.fetch('stdout')) },
                  'localRepository' => repository)
  end

  # 正式来源只读固定本地记录，不接受caller baseline或自动重建；无执行副作用。
  def read_approved_model_sources
    directory = path('.harness/t11/coverage-b2-approved-sources-v2')
    stat = File.lstat(directory)
    raise 'invalid approved source directory' unless stat.directory? && stat.uid == Process.uid && stat.mode & 0777 == 0700
    read_private = lambda do |name|
      file = path(relative(directory + '/' + name))
      entry = File.lstat(file)
      raise 'invalid approved source file' unless entry.file? && entry.uid == Process.uid && entry.mode & 0777 == 0600
      File.binread(file)
    end
    aggregate = JSON.parse(read_private.call('aggregate.json'))
    raise 'approved aggregate bytes changed' unless Digest::SHA256.hexdigest(read_private.call('aggregate.json')) == 'd0b07770df0542582c67331646ab2ccf91c27e1c7b46b9fa0ad305cb742d6e55'
    raise 'invalid approved source schema' unless aggregate.keys.sort == %w[candidates executionPath helpers launchers limitations localTools reviews schema trustDecision version] && aggregate['schema'] == 'coverage-b2-approved-sources' && aggregate['version'] == 2
    expected = {
      'mavenJdk' => ['maven-jdk.json', '05c9eec880b19fe7560ef11d256a54327c7cfcd4223c5755562018658e0260fd'],
      'nodeNpm' => ['node-npm.json', '0e223c5ff1e894ac60540c90b339c84385f4afa2ea56641daa45e92712a9e038']
    }
    raise 'invalid approved candidate keys' unless aggregate.fetch('candidates').keys.sort == expected.keys.sort
    parsed = expected.to_h do |key, (name, sha)|
      raise 'invalid approved candidate reference' unless aggregate.fetch('candidates').fetch(key) == { 'file' => name, 'sha256' => sha }
      bytes = read_private.call(name)
      raise 'approved candidate bytes changed' unless Digest::SHA256.hexdigest(bytes) == sha
      [key, JSON.parse(bytes)]
    end
    layout = parsed.fetch('mavenJdk').fetch('layout')
    node_bin = File.dirname(parsed.fetch('nodeNpm').fetch('node').fetch('canonical'))
    expected_path = [layout.fetch('mavenHome') + '/bin', layout.fetch('jdk') + '/bin', node_bin, path('scripts/bin'), '/usr/bin', '/bin', '/usr/sbin', '/sbin']
    raise 'approved execution PATH changed' unless aggregate.fetch('executionPath') == expected_path
    local_tools = aggregate.fetch('localTools').to_h do |name, reference|
      raise 'unknown local tool record' unless { 'tools' => 'tools.json', 'pcre' => 'pcre.json', 'mavenMetadata' => 'maven-metadata.json' }[name] == reference.fetch('file')
      bytes = read_private.call(reference.fetch('file'))
      raise 'local tool record changed' unless Digest::SHA256.hexdigest(bytes) == reference.fetch('sha256')
      [name, JSON.parse(bytes)]
    end
    raise 'missing local tool record' unless local_tools.keys.sort == %w[mavenMetadata pcre tools]
    parsed.merge('aggregate' => aggregate, 'localTools' => local_tools)
  end

  # 固定v2精确安装链；仅已记录metadata可接受共享写入，未知路径不授信。
  def approved_local_tool(name)
    records = read_approved_model_sources.fetch('localTools')
    raise 'unknown approved local tool' unless %w[rg docker compose docker-credential-desktop].include?(name)
    %w[rg docker docker-credential-desktop].each do |entry|
      launcher = path('scripts/bin/' + entry)
      raise 'source launcher changed' unless File.file?(launcher) && !File.symlink?(launcher) && Digest::SHA256.file(launcher).hexdigest == read_approved_model_sources.fetch('aggregate').fetch('launchers').fetch(entry)
    end
    tool = records.fetch('tools').fetch('tools').fetch(name)
    chains = records.fetch('tools').fetch('permissionChains')
    verify_local_metadata(chains)
    if name == 'rg'
      pcre = records.fetch('pcre')
      verify_local_metadata(pcre.fetch('permissionChains'))
      raise 'PCRE resolution changed' unless File.realpath(pcre.fetch('lexical')) == pcre.fetch('canonical')
      raise 'PCRE bytes changed' unless Digest::SHA256.file(pcre.fetch('canonical')).hexdigest == pcre.fetch('sha256')
    end
    raise 'local tool resolution changed' unless File.realpath(tool.fetch('lexical')) == tool.fetch('canonical')
    file = tool.fetch('canonical')
    raise 'local tool identity changed' unless File.file?(file) && !File.symlink?(file) && File.executable?(file) && Digest::SHA256.file(file).hexdigest == tool.fetch('sha256')
    file
  end

  def verify_local_metadata(chains)
    chains.each do |file, expected|
      stat = File.lstat(file)
      actual = { 'mode' => stat.mode & 0777, 'uid' => stat.uid, 'gid' => stat.gid, 'link' => File.symlink?(file) ? File.readlink(file) : nil }
      raise 'approved local chain changed' unless actual.all? { |key, value| expected.fetch(key) == value }
      raise 'invalid local chain type' unless stat.file? || stat.directory? || stat.symlink?
    end
  end

  def approved_installation_metadata
    sources = read_approved_model_sources
    metadata = {}
    snapshot = sources.fetch('localTools').fetch('mavenMetadata')
    snapshot.fetch('pathChains').each_value do |entries|
      entries.each { |entry| metadata[entry.fetch('path')] = { 'mode' => Integer(entry.fetch('mode')), 'uid' => entry.fetch('uid'), 'gid' => entry.fetch('gid'), 'link' => entry['target'] } if entry['exists'] }
    end
    sources.fetch('nodeNpm').fetch('permissionChain').each { |file, entry| metadata[file] = entry }
    metadata
  end

  def approved_installation_stat?(file, stat, metadata)
    expected = metadata[file]
    return false unless expected
    !stat.symlink? && (stat.mode & 0777) == expected.fetch('mode') && stat.uid == expected.fetch('uid') && stat.gid == expected.fetch('gid')
  end

  # 仅绑定已知cwd/首文件目录dotenv，不解析变量或宣称完整搜索闭包；不输出正文。
  def compose_dotenv_inputs
    [path('.env'), path('deploy/local/.env')].uniq.to_h do |file|
      unless File.exist?(file) || File.symlink?(file)
        next [file, { 'present' => false }]
      end
      stat = File.lstat(file)
      raise 'invalid Compose dotenv identity' unless stat.file? && !stat.symlink? && File.realpath(file) == file
      bytes = File.binread(file)
      [file, { 'present' => true, 'canonical' => File.realpath(file), 'mode' => stat.mode, 'uid' => stat.uid, 'gid' => stat.gid, 'sha256' => Digest::SHA256.hexdigest(bytes) }]
    end
  end

  # 固定已审输入；改变文件须先审查额外内容读取形状，不从现场学习允许项。
  def approved_compose_yaml
    { 'deploy/local/compose.yml' => 'c19fffab3b47756f8516eb96b33efa5caaa376b44ca8f3ce435ee19d0ff78835',
      'deploy/local/compose.load-test.yml' => '518a9670d12094827ea6f9dde391b85263e72740bae94b3dc7c1a8279233bd43' }
  end

  def compose_yaml_inputs(files)
    files.to_h do |relative_file|
      file = path(relative_file)
      stat = File.lstat(file)
      raise 'invalid Compose YAML identity' unless stat.file? && !stat.symlink? && File.realpath(file) == file
      sha = Digest::SHA256.file(file).hexdigest
      raise 'unapproved Compose YAML bytes' unless approved_compose_yaml.fetch(relative_file) == sha
      [file, sha]
    end
  end

  def compose_null_stat
    File.lstat('/dev/null')
  end

  # 两个纯config调用固定argv/cwd；不经过Docker插件发现，不接受caller入口或环境替换。
  def compose_config(scope)
    expected = {
      'local' => ['-f', path('deploy/local/compose.yml'), '--profile', 'infra', '--profile', 'full', 'config', '--format', 'json'],
      'load' => ['-f', path('deploy/local/compose.yml'), '-f', path('deploy/local/compose.load-test.yml'), '--profile', 'full', 'config', '--format', 'json']
    }
    raise 'expected fixed config scope' unless expected.key?(scope)
    home = read_approved_model_sources.fetch('mavenJdk').fetch('layout').fetch('userHome')
    raise 'unapproved Compose HOME' unless ENV['HOME'] == home
    dotenv = compose_dotenv_inputs
    executable = approved_local_tool('compose')
    inputs = compose_yaml_inputs(expected.fetch(scope).each_cons(2).select { |pair| pair[0] == '-f' }.map { |pair| relative(pair[1]) })
    null_stat = compose_null_stat
    raise 'invalid Compose null env-file' unless null_stat.chardev? && !null_stat.symlink?
    config = path('.harness/t11/coverage-b2-approved-sources-v2/docker-config')
    config_stat = File.lstat(config)
    config_file = File.lstat(config + '/config.json')
    raise 'invalid private Compose config' unless config_stat.directory? && config_stat.uid == Process.uid && config_stat.mode & 0777 == 0700 && config_file.file? && config_file.uid == Process.uid && config_file.mode & 0777 == 0600 && File.binread(config + '/config.json') == "{}\n"
    pid = Process.spawn({ 'HOME' => home, 'PATH' => '/usr/bin:/bin', 'DOCKER_CONFIG' => config }, executable, '--env-file', '/dev/null', *expected.fetch(scope), chdir: @root, unsetenv_others: true)
    _, status = Process.wait2(pid)
    raise 'Compose source changed during config' unless approved_local_tool('compose') == executable
    raise 'Compose config changed during command' unless [File.lstat(config).mode, File.lstat(config).uid] == [config_stat.mode, config_stat.uid] && [File.lstat(config + '/config.json').mode, File.lstat(config + '/config.json').uid] == [config_file.mode, config_file.uid] && File.binread(config + '/config.json') == "{}\n"
    raise 'Compose dotenv changed during config' unless compose_dotenv_inputs == dotenv
    raise 'Compose input changed during config' unless inputs.all? { |file, sha| Digest::SHA256.file(file).hexdigest == sha }
    status.exitstatus || 1
  end

  # 子命令留在dispatch进程组；取消只标记信号并等待直接子进程，父shell负责整组退出后释锁。
  # Open3并行排空两条管道，避免stderr满管造成等待死锁；不另起进程组。
  def run_model_command(execution, argv, capture: true)
    cancelled = nil
    previous = %w[INT TERM].to_h { |signal| [signal, Signal.trap(signal) { cancelled ||= signal }] }
    if capture
      stdout = stderr = nil
      status = nil
      Open3.popen3(execution, *argv, chdir: @root, unsetenv_others: true) do |input, output, error, waiter|
        input.close
        readers = [Thread.new { output.read }, Thread.new { error.read }]
        status = waiter.value
        stdout, stderr = readers.map(&:value)
      end
      exit(cancelled == 'INT' ? 130 : 143) if cancelled
      [stdout, stderr, status]
    else
      pid = Process.spawn(execution, *argv, chdir: @root, unsetenv_others: true)
      _, status = Process.wait2(pid)
      exit(cancelled == 'INT' ? 130 : 143) if cancelled
      status
    end
  ensure
    previous&.each { |signal, handler| Signal.trap(signal, handler) }
  end

  # 固定受审settings身份；无caller/env/CLI覆盖，collector与consumer共同使用。
  def approved_settings_identity_sha
    'aca5b36fe4d0bbcf3203544c6b13da4c303d9431eaa317f373d7305250ceb4ca'
  end

  # 正式collect-only入口没有runner/baseline/system_root注入；不清理、不运行verify。
  # 调用本方法即执行三条Maven命令，必须另外取得精确运行登记与工具许可。
  def collect_approved_model(output:)
    snapshot = snapshot_approved_model_inputs
    guard = lambda { raise 'approved model inputs changed' unless snapshot_approved_model_inputs == snapshot }
    sources = read_approved_model_sources
    layout = sources.fetch('mavenJdk').fetch('layout')
    settings_xml = File.binread(layout.fetch('mavenHome') + '/conf/settings.xml')
    settings_sha = snapshot.fetch('settingsSha256')
    identity = approved_settings_identity_sha
    validate_repository_settings(settings_xml, identity)
    oracle = read_raw_pom_oracle
    create_model_output_directory(output)
    xml_path = output + '/effective-pom.xml'
    help = 'org.apache.maven.plugins:maven-help-plugin:3.5.1:'
    commands = [
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'effective-pom', '-Dverbose=false', '-Doutput=' + xml_path],
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'evaluate', '-Dexpression=settings.localRepository', '-DforceStdout'],
      ['mvn', '-o', '-f', 'pom.xml', help + 'active-profiles']
    ]
    execution = snapshot.fetch('environment').fetch('execution')
    results = commands.each_with_index.map do |argv, index|
      guard.call
      old_umask = File.umask(0077)
      begin
        stdout, stderr, status = run_model_command(execution, argv)
      ensure
        File.umask(old_umask)
      end
      guard.call
      result = { 'stdout' => stdout, 'stderr' => stderr, 'exitCode' => status.exitstatus }
      %w[stdout stderr].each do |stream|
        File.open(output + '/command-' + index.to_s + '.' + stream, File::WRONLY | File::CREAT | File::EXCL, 0600) { |file| file.binmode; file.write(result.fetch(stream)) }
      end
      raise 'approved offline model command failed' unless status.success? && stderr == '' && (index != 0 || stdout == '')
      result
    end
    guard.call
    path(relative(xml_path))
    stat = File.lstat(xml_path)
    raise 'invalid approved model XML' unless stat.file? && stat.size > 0 && stat.uid == Process.uid && stat.mode & 0077 == 0
    xml = File.binread(xml_path)
    repository = results[1].fetch('stdout')
    rejected = snapshot.fetch('environment').fetch('absentInjection')
    common = { 'canonicalRoot' => @root, 'cwd' => @root, 'dotMvn' => @root + '/.mvn', 'maven' => layout.fetch('launcher'),
      'jdk' => layout.fetch('jdk'), 'settings' => layout.fetch('mavenHome') + '/conf/settings.xml', 'environment' => rejected.select { |key, _| %w[JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS MAVEN_PROJECTBASEDIR].include?(key) } }
    binding = bind_model_provenance(model: common.merge('argv' => commands.first), verify: common.merge('argv' => ['mvn', '-q', 'verify']),
      inputs: { 'effectivePom' => { 'path' => xml_path, 'sha256' => Digest::SHA256.hexdigest(xml) }, 'localRepository' => { 'path' => repository, 'sha256' => Digest::SHA256.hexdigest(repository) } })
    summary = read_model_command_results(results, xml: xml, oracle: oracle, binding: binding, settings_xml: settings_xml,
      settings_sha256: settings_sha, settings_identity_sha: identity)
    guard.call
    proof = { 'version' => 1, 'commands' => commands, 'binding' => binding, 'sourceSnapshot' => snapshot,
      'verifyInvocation' => ['mvn', '-q', 'verify'], 'output' => output,
      'outputs' => results.map { |result| { 'stdoutSha256' => Digest::SHA256.hexdigest(result.fetch('stdout')), 'stderrSha256' => Digest::SHA256.hexdigest(result.fetch('stderr')), 'exitCode' => result.fetch('exitCode') } } }
    summary.merge('collectionProof' => proof, 'collectionSha256' => Digest::SHA256.hexdigest(JSON.generate(proof)), 'hostProvenance' => true, 'bootstrapComplete' => true)
  end

  # 正式结果消费前重新读全部现场与私有输出，再运行公共reader；禁止E0/fixture晋级。
  # 只返回供原reactor manifest合并的事实，不清理、不执行verify。
  def read_approved_collection(collection)
    raise 'untrusted model collection' unless collection.is_a?(Hash) && collection['hostProvenance'] == true && collection['bootstrapComplete'] == true
    validate_model_collection(collection)
  end

  private def validate_model_collection(collection)
    proof = collection.fetch('collectionProof')
    raise 'model collection digest changed' unless Digest::SHA256.hexdigest(JSON.generate(proof)) == collection.fetch('collectionSha256')
    raise 'model collection environment stale' unless snapshot_approved_model_inputs == proof.fetch('sourceSnapshot')
    output = proof.fetch('output')
    raise 'model collection output escaped' unless File.dirname(output) == @root + '/.harness/runtime'
    path(relative(output))
    raise 'model collection output not private' unless File.stat(output).mode & 0777 == 0700
    results = proof.fetch('outputs').each_with_index.map do |expected, index|
      result = { 'exitCode' => expected.fetch('exitCode') }
      %w[stdout stderr].each do |stream|
        file = path(relative(output + '/command-' + index.to_s + '.' + stream))
        stat = File.lstat(file)
        raise 'model collection stream not private' unless stat.file? && stat.uid == Process.uid && stat.mode & 0777 == 0600
        bytes = File.binread(file)
        raise 'model collection stream changed' unless Digest::SHA256.hexdigest(bytes) == expected.fetch(stream + 'Sha256')
        result[stream] = bytes
      end
      result
    end
    raise 'invalid model collection command count' unless results.size == 3 && proof.fetch('commands').size == 3 && proof.fetch('verifyInvocation') == ['mvn', '-q', 'verify']
    help = 'org.apache.maven.plugins:maven-help-plugin:3.5.1:'
    expected_commands = [
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'effective-pom', '-Dverbose=false', '-Doutput=' + output + '/effective-pom.xml'],
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'evaluate', '-Dexpression=settings.localRepository', '-DforceStdout'],
      ['mvn', '-o', '-f', 'pom.xml', help + 'active-profiles']
    ]
    raise 'model collection command changed' unless proof.fetch('commands') == expected_commands
    binding = proof.fetch('binding')
    xml_file = path(relative(binding.fetch('inputs').fetch('effectivePom').fetch('path')))
    raise 'model collection XML escaped' unless xml_file == output + '/effective-pom.xml'
    xml_stat = File.lstat(xml_file)
    raise 'model collection XML not private' unless xml_stat.file? && xml_stat.uid == Process.uid && xml_stat.mode & 0077 == 0
    xml = File.binread(xml_file)
    settings_path = read_approved_model_sources.fetch('mavenJdk').fetch('layout').fetch('mavenHome') + '/conf/settings.xml'
    raise 'model collection settings path changed' unless binding.fetch('model').fetch('settings') == settings_path
    settings = File.binread(settings_path)
    summary = read_model_command_results(results, xml: xml, oracle: read_raw_pom_oracle, binding: binding, settings_xml: settings,
      settings_sha256: proof.fetch('sourceSnapshot').fetch('settingsSha256'), settings_identity_sha: approved_settings_identity_sha)
    raise 'model collection changed during consumption' unless snapshot_approved_model_inputs == proof.fetch('sourceSnapshot')
    summary.merge('collectionProof' => proof, 'collectionSha256' => collection.fetch('collectionSha256'))
  end

  # CLI实际调用的生产编排；全部消费和clear前现场校验通过后才删除旧证据。
  # 参数仅文件位置，不接收caller runner/baseline；后续漂移不回滚或伪造未清理事实。
  def prepare_approved_model(collection_file, manifest_file, summary_file)
    collection = JSON.parse(File.binread(collection_file))
    evidence = self.read_approved_collection(collection)
    binding = evidence.fetch('collectionProof').fetch('binding')
    model = self.read_bound_effective_model(File.binread(self.path(self.relative(binding.fetch('inputs').fetch('effectivePom').fetch('path')))), self.read_raw_pom_oracle, binding)
    [manifest_file, summary_file].each { |file| self.path(self.relative(file)) }
    raise 'model changed before clear' unless self.snapshot_approved_model_inputs == evidence.fetch('collectionProof').fetch('sourceSnapshot')
    self.prepare(manifest_file, model.fetch('modules'), [manifest_file, summary_file].map { |file| self.relative(file) })
    manifest = JSON.parse(File.binread(manifest_file))
    self.save(manifest_file, manifest.merge('modelCollection' => evidence))
  end

  # 使用同次manifest关联与显式环境，执行前后均消费完整来源/输出/reader证明。
  def verify_approved_model(collection_file, manifest_file)
    collection = JSON.parse(File.binread(collection_file))
    evidence = self.read_approved_collection(collection)
    manifest = self.load_manifest(manifest_file)
    raise 'reactor model association changed' unless manifest.fetch('modelCollection') == evidence
    execution = evidence.fetch('collectionProof').fetch('sourceSnapshot').fetch('environment').fetch('execution')
    raise 'model changed before verify' unless self.snapshot_approved_model_inputs == evidence.fetch('collectionProof').fetch('sourceSnapshot')
    status = self.run_model_command(execution, ['mvn', '-q', 'verify'], capture: false)
    exit(status.exitstatus || 1) unless status.success?
    self.read_approved_collection(collection)
  end

  # full前置只读证明：固定v2来源与专用源码入口，不执行目标，不用原PATH补位。
  # 精确本地安装及已知共享写入链为用户接受边界，未知来源/漂移仍失败关闭。
  def attest_full_environment
    snapshot = snapshot_approved_model_inputs
    execution = snapshot.fetch('environment').fetch('execution')
    %w[ruby git rg docker].each do |name|
      resolved = execution.fetch('PATH').split(':').map { |directory| directory + '/' + name }.find { |file| File.file?(file) && File.executable?(file) }
      expected = %w[ruby git].include?(name) ? '/usr/bin/' + name : path('scripts/bin/' + name)
      raise 'unapproved full Harness tool source' unless resolved == expected
      unless %w[ruby git].include?(name)
        raise 'source launcher changed' unless Digest::SHA256.file(expected).hexdigest == read_approved_model_sources.fetch('aggregate').fetch('launchers').fetch(name)
        approved_local_tool(name)
      end
    end
    raise 'full sources changed during attestation' unless snapshot_approved_model_inputs == snapshot
    snapshot
  end

  # 仅隔离fixture验证前置顺序；不能发布来源证明或调用正式执行入口。
  def run_full_preflight_fixture(attest:, readiness:, clean:, selftests:, collect:, prepare:, verify:)
    original = JSON.generate(attest.call)
    readiness.call
    raise 'fixture source changed after readiness' unless JSON.generate(attest.call) == original
    clean.call
    selftests.call
    raise 'fixture source changed after preflight' unless JSON.generate(attest.call) == original
    result = collect.call
    raise 'fixture source changed before prepare' unless JSON.generate(attest.call) == original
    prepare.call(result)
    verify.call(result)
  end

  # 正式快照固定system root=/；只有精确受审来源与受控解析可进入后续命令。
  # 与mock无callback共享，不执行工具链；每次返回内容与现场权限分列的有限事实。
  def snapshot_approved_model_inputs
    raise 'unsupported model host OS' unless RUBY_PLATFORM.include?('darwin')
    sources = read_approved_model_sources
    baseline = sources.fetch('mavenJdk')
    layout = baseline.fetch('layout')
    raise 'approved model root mismatch' unless layout.fetch('root') == @root
    execution = { 'HOME' => layout.fetch('userHome'), 'JAVA_HOME' => layout.fetch('jdk'), 'PATH' => sources.fetch('aggregate').fetch('executionPath').join(':') }
    node = sources.fetch('nodeNpm')
    environment = model_environment_record(original: ENV.to_h, execution: execution, layout: layout, node_bin: File.dirname(node.fetch('node').fetch('canonical')))
    absent = ['/usr/local/etc/mavenrc', '/etc/mavenrc'] + %w[.mavenrc .m2/settings.xml .m2/toolchains.xml].map { |name| layout.fetch('userHome') + '/' + name }
    cursor = @root
    loop do
      absent << cursor + '/.mvn'
      break if cursor == '/'
      cursor = File.dirname(cursor)
    end
    raise 'unapproved model auxiliary source' if absent.any? { |file| File.exist?(file) || File.symlink?(file) }
    members = baseline.fetch('members')
    trees = {}
    %w[maven jdk].each do |label|
      home = layout.fetch(label == 'maven' ? 'mavenHome' : 'jdk')
      files = members.select { |name, _| name.start_with?(label + '/') }.to_h { |name, sha| [name.delete_prefix(label + '/'), sha] }
      links = baseline.fetch('nonRegularMembers').select { |name, _| name.start_with?(label + '/') }.to_h do |name, record|
        target = record.fetch('canonical').delete_prefix(home + '/')
        [name.delete_prefix(label + '/'), { 'link' => record.fetch('target'), 'target' => target, 'sha256' => files.fetch(target) }]
      end
      member_metadata = sources.fetch('localTools').fetch('mavenMetadata').fetch(label).to_h do |name, entry|
        [name, { 'mode' => Integer(entry.fetch('mode')), 'uid' => entry.fetch('uid'), 'gid' => entry.fetch('gid') }]
      end
      trees[label] = read_reviewed_tree(home, members: files, links: links, metadata: member_metadata)
    end
    npm = node.fetch('npm')
    npm_files = npm.fetch('members').select { |entry| entry.fetch('type') == 'file' }.to_h { |entry| [entry.fetch('path'), entry.fetch('sha256')] }
    npm_metadata = npm.fetch('members').to_h { |entry| [entry.fetch('path'), entry] }
    trees['npm'] = read_reviewed_tree(npm.fetch('packageRoot'), members: npm_files, links: {}, metadata: npm_metadata)
    raise 'npm reviewed shebang changed' unless File.binread(npm.fetch('canonical')).lines.first == "#!/usr/bin/env node\n"
    expected = { 'mvn' => layout.fetch('launcher'), 'java' => layout.fetch('java'), 'node' => node.fetch('node').fetch('canonical'),
      'npm' => npm.fetch('lexical'), 'uname' => '/usr/bin/uname', 'dirname' => '/usr/bin/dirname', 'env' => '/usr/bin/env' }
    permissions = {}
    approved_metadata = approved_installation_metadata
    execution.fetch('PATH').split(':').each do |directory|
      cursor = directory
      loop do
        stat = File.lstat(cursor)
        raise 'unsafe execution PATH parent' unless stat.directory? && !stat.symlink? && (approved_metadata.key?(cursor) ? approved_installation_stat?(cursor, stat, approved_metadata) : (stat.mode & 0022 == 0 && [0, Process.uid].include?(stat.uid)))
        permissions[cursor] = { 'mode' => stat.mode & 07777, 'uid' => stat.uid, 'gid' => stat.gid }
        break if cursor == '/'
        cursor = File.dirname(cursor)
      end
    end
    expected.merge('sh' => '/bin/sh').each do |name, file|
      resolved = name == 'sh' ? file : execution.fetch('PATH').split(':').map { |directory| directory + '/' + name }.find { |candidate| File.file?(candidate) && File.executable?(candidate) }
      raise 'unapproved model executable resolution' unless resolved == file
      if name == 'npm'
        raise 'npm entry link changed' unless File.symlink?(file) && File.readlink(file) == '../lib/node_modules/npm/bin/npm-cli.js' && File.realpath(file) == npm.fetch('canonical')
      else
        raise 'model executable link forbidden' if File.symlink?(file)
      end
      if name == 'npm'
        parent = File.dirname(file)
        loop do
          parent_stat = File.lstat(parent)
          raise 'unsafe npm entry parent' unless parent_stat.directory? && !parent_stat.symlink? && (approved_metadata.key?(parent) ? approved_installation_stat?(parent, parent_stat, approved_metadata) : (parent_stat.mode & 0022 == 0 && [0, Process.uid].include?(parent_stat.uid)))
          permissions[parent] = { 'mode' => parent_stat.mode & 07777, 'uid' => parent_stat.uid, 'gid' => parent_stat.gid }
          break if parent == '/'
          parent = File.dirname(parent)
        end
      end
      cursor = name == 'npm' ? npm.fetch('canonical') : file
      loop do
        stat = File.lstat(cursor)
        raise 'unsafe model executable chain' unless !stat.symlink? && (approved_metadata.key?(cursor) ? approved_installation_stat?(cursor, stat, approved_metadata) : (stat.mode & 0022 == 0 && [0, Process.uid].include?(stat.uid)))
        permissions[cursor] = { 'mode' => stat.mode & 07777, 'uid' => stat.uid, 'gid' => stat.gid }
        break if cursor == '/'
        cursor = File.dirname(cursor)
      end
    end
    raise 'node binary changed' unless Digest::SHA256.file(expected.fetch('node')).hexdigest == node.fetch('node').fetch('sha256')
    helpers = %w[uname dirname env sh].to_h { |name| [name, Digest::SHA256.file(name == 'sh' ? '/bin/sh' : expected.fetch(name)).hexdigest] }
    raise 'host env changed' unless helpers.fetch('env') == sources.fetch('aggregate').fetch('helpers').fetch('/usr/bin/env').fetch('sha256')
    { 'environment' => environment, 'sources' => trees, 'helpers' => helpers, 'permissions' => permissions,
      'absent' => absent.sort, 'resolved' => expected, 'localTools' => %w[rg docker compose docker-credential-desktop].to_h { |name| [name, approved_local_tool(name)] }, 'launchers' => %w[rg docker docker-credential-desktop].to_h { |name| [name, Digest::SHA256.file(path('scripts/bin/' + name)).hexdigest] }, 'settingsSha256' => members.fetch('maven/conf/settings.xml') }
  end

  # 精确普通文件清单及有限JDK legal文件链接；未知成员/目录链接一律拒绝。
  # links只描述受审relative路径、原link文本、同根普通目标与目标摘要，不动态学习。
  def read_reviewed_tree(directory, members:, links:, metadata: {})
    provenance_absolute_path(directory)
    raise 'invalid reviewed tree root' unless File.directory?(directory) && File.realpath(directory) == directory && !File.symlink?(directory) && File.stat(directory).mode & 0022 == 0
    raise 'invalid reviewed tree schema' unless members.is_a?(Hash) && links.is_a?(Hash)
    actual = {}
    actual_links = {}
    Dir.glob(directory + '/**/*', File::FNM_DOTMATCH).sort.each do |file|
      next if %w[. ..].include?(File.basename(file))
      relative_name = file.delete_prefix(directory + '/')
      stat = File.lstat(file)
      expected_metadata = metadata[relative_name]
      if expected_metadata
        raise 'reviewed member metadata changed' unless stat.mode & 0777 == expected_metadata.fetch('mode') && stat.uid == expected_metadata.fetch('uid') && stat.gid == expected_metadata.fetch('gid')
      else
        raise 'writable reviewed tree member' unless stat.symlink? || stat.mode & 0022 == 0
      end
      next if stat.directory?
      if stat.symlink?
        expected = links.fetch(relative_name) { raise 'unknown reviewed tree link' }
        raise 'invalid reviewed tree link schema' unless expected.is_a?(Hash) && expected.keys.sort == %w[link sha256 target]
        raise 'invalid reviewed legal link' unless relative_name.start_with?('legal/') && File.readlink(file) == expected.fetch('link')
        target = File.expand_path(expected.fetch('target'), directory)
        raise 'invalid reviewed link target' unless target.start_with?(directory + '/') && File.realpath(file) == target && members.fetch(expected.fetch('target')) == expected.fetch('sha256')
        cursor = target
        until cursor == directory
          raise 'reviewed target chain link' if File.symlink?(cursor)
          cursor = File.dirname(cursor)
        end
        raise 'invalid reviewed link target bytes' unless File.file?(target) && Digest::SHA256.file(target).hexdigest == expected.fetch('sha256')
        actual_links[relative_name] = expected
      else
        raise 'invalid reviewed tree type' unless stat.file?
        actual[relative_name] = Digest::SHA256.file(file).hexdigest
      end
    end
    raise 'reviewed tree membership changed' unless actual == members && actual_links == links
    actual
  end

  # 有限环境描述：保留原PATH身份，拒绝原注入，不以构造环境隐藏违规。
  # 这里只验证结构；实际来源、首候选与权限仍须由现场快照逐次验证。
  def model_environment_record(original:, execution:, layout:, node_bin:)
    rejected = %w[MAVEN_ARGS MAVEN_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_PROJECTBASEDIR MAVEN_CONFIG MAVEN_BASEDIR MAVEN_DEBUG_OPTS _JAVA_OPTIONS]
    raise 'invalid original model environment' unless original.is_a?(Hash) && rejected.none? { |key| original.key?(key) }
    provenance_absolute_path(node_bin)
    expected = { 'HOME' => layout.fetch('userHome'), 'JAVA_HOME' => layout.fetch('jdk'),
      'PATH' => [layout.fetch('mavenHome') + '/bin', layout.fetch('jdk') + '/bin', node_bin, path('scripts/bin'), '/usr/bin', '/bin', '/usr/sbin', '/sbin'].join(':') }
    raise 'invalid execution model environment' unless execution == expected
    raise 'invalid original model HOME/JAVA_HOME' unless original['HOME'] == expected['HOME'] && original['JAVA_HOME'] == expected['JAVA_HOME']
    identity = { 'present' => original.key?('PATH') }
    if identity['present']
      raise 'invalid original PATH' unless original['PATH'].is_a?(String)
      identity['sha256'] = Digest::SHA256.hexdigest(original.fetch('PATH'))
    end
    { 'version' => 1, 'originalPath' => identity, 'execution' => expected,
      'executionSha256' => Digest::SHA256.hexdigest(JSON.generate(expected)),
      'absentInjection' => rejected.to_h { |key| [key, { 'present' => false }] } }
  end

  # 生命周期顺序仅隔离fixture演练；collect失败时clear/verify均不可达。
  # 不发布正式manifest，callbacks不能作为真实prepare/verify入口。
  def run_model_lifecycle_fixture(collect:, clear:, verify:)
    summary = collect.call
    raise 'invalid fixture collection' unless summary.is_a?(Hash) && summary['hostProvenance'] == false && summary['bootstrapComplete'] == false && summary['projectCount'] == 53
    clear.call
    verify.call
  end

  # 显式隔离fixture入口；callback无宿主可信身份，结果禁止用于正式manifest。
  # 三命令前后及消费前均比较完整来源/环境快照；不持有clear/verify callback。
  def collect_model_fixture(output:, snapshot:, runner:, model_context:, verify_context:, settings_xml:, settings_sha256:, settings_identity_sha:)
    original = JSON.generate(snapshot.call)
    guard = lambda { raise 'model fixture input changed' unless JSON.generate(snapshot.call) == original }
    oracle = read_raw_pom_oracle
    create_model_output_directory(output)
    xml_path = output + '/effective-pom.xml'
    help = 'org.apache.maven.plugins:maven-help-plugin:3.5.1:'
    commands = [
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'effective-pom', '-Dverbose=false', '-Doutput=' + xml_path],
      ['mvn', '-o', '-q', '-f', 'pom.xml', help + 'evaluate', '-Dexpression=settings.localRepository', '-DforceStdout'],
      ['mvn', '-o', '-f', 'pom.xml', help + 'active-profiles']
    ]
    results = commands.map do |argv|
      guard.call
      result = runner.call(argv.dup, @root)
      guard.call
      raise 'model fixture command failed' unless result.is_a?(Hash) && result.keys.sort == %w[exitCode stderr stdout] && result['exitCode'] == 0 && result['stderr'] == '' && result['stdout'].is_a?(String)
      result
    end
    guard.call
    path(relative(xml_path))
    stat = File.lstat(xml_path)
    raise 'invalid model fixture XML file' unless stat.file? && stat.size > 0
    xml = File.binread(xml_path)
    repository = results[1].fetch('stdout')
    inputs = { 'effectivePom' => { 'path' => xml_path, 'sha256' => Digest::SHA256.hexdigest(xml) },
               'localRepository' => { 'path' => repository, 'sha256' => Digest::SHA256.hexdigest(repository) } }
    binding = bind_model_provenance(model: model_context.merge('argv' => commands.first), verify: verify_context, inputs: inputs)
    summary = read_model_command_results(results, xml: xml, oracle: oracle, binding: binding,
      settings_xml: settings_xml, settings_sha256: settings_sha256, settings_identity_sha: settings_identity_sha)
    guard.call
    summary.merge('commands' => commands, 'hostProvenance' => false, 'bootstrapComplete' => false)
  end

  # 纯展示函数，非来源验证入口；正式调用者只能传同次验证派生的projects。
  # mixed-version仅格式能力，不扩大formal GAV；返回字符串不发布provenance。
  def format_active_profiles_summary(projects, duration)
    raise 'invalid active profiles duration' unless active_profiles_duration?(duration)
    units = lambda { |text| text.encode(Encoding::UTF_16LE).bytesize / 2 }
    root = projects.find { |project| project.fetch('gav') == FORMAL_CANONICAL.fetch('pom.xml') }
    raise 'invalid active profiles summary' unless root
    uniform = projects.map { |project| project.fetch('version') }.uniq.size == 1
    title = uniform ? 'Reactor Summary for ' + root.fetch('name') + ' ' + root.fetch('version') + ':' : 'Reactor Summary:'
    lines = ['-' * 72, title, '']
    projects.each do |project|
      text = project.fetch('name') + ' '
      text += project.fetch('version') + ' ' unless uniform
      length = units.call(text)
      text += '.' * (52 - length) + ' ' if length <= 52
      if project.fetch('gav') == root.fetch('gav')
        text += 'SUCCESS [' + ' ' * [0, 9 - units.call(duration)].max + duration + ']'
      else
        text += 'SKIPPED'
      end
      lines << text
    end
    lines.map { |line| '[INFO] ' + line + "\n" }.join
  end

  # 完整stdout纯输入入口：固定段顺序与EOF；每个公共consumer仍重读raw/bound。
  # 不执行runner/采集，不接受caller展示表；时间只验证格式，不证明鲜度。
  def read_active_profiles_stdout(stdout, xml:, oracle:, binding:, settings_xml:, settings_sha256:, settings_identity_sha:)
    model = read_bound_effective_model(xml, oracle, binding)
    text = stdout.dup.force_encoding(Encoding::UTF_8)
    raise 'invalid active profiles stdout' unless text.valid_encoding? && !text.start_with?("﻿") && !text.match?(/[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/)
    lines = text.lines
    # scanning/H/header/E/53 rows，根banner四行，其后正文直到唯一summary H。
    order = lines.shift(57).join
    banner = lines.shift(4).join
    separator = '[INFO] ' + '-' * 72 + "\n"
    summary_index = lines.index(separator)
    raise 'invalid active profiles stdout' unless summary_index
    logged = lines.shift(summary_index).join
    summary = lines.shift(56).join
    tail = lines.join
    root_duration = summary.match(/^\[INFO\] .*SUCCESS \[ *([^\]\n]+)\]\n/)
    raise 'invalid active profiles stdout' unless root_duration
    duration = root_duration[1]
    expected_summary = format_active_profiles_summary(model.fetch('displayProjects'), duration)
    raise 'invalid active profiles stdout' unless summary == expected_summary
    stats = /\A\[INFO\] -{72}\n\[INFO\] BUILD SUCCESS\n\[INFO\] -{72}\n\[INFO\] Total time:  ([^\n]+)\n\[INFO\] Finished at: ([^\n]+)\n\[INFO\] -{72}\n\z/.match(tail)
    raise 'invalid active profiles stdout' unless stats && active_profiles_duration?(stats[1]) && active_profiles_timestamp?(stats[2])
    read_active_profiles_build_order(order, xml: xml, oracle: oracle, binding: binding)
    read_active_profiles_root_banner(banner, xml: xml, oracle: oracle, binding: binding)
    result = read_active_profiles_logged_body(logged, xml: xml, oracle: oracle, binding: binding, settings_xml: settings_xml, settings_sha256: settings_sha256, settings_identity_sha: settings_identity_sha)
    result.merge('stdoutSha256' => Digest::SHA256.hexdigest(stdout))
  end

  private def active_profiles_duration?(duration)
    duration.is_a?(String) && duration.match?(/\A(?:[1-9][0-9]* d (?:[01][0-9]|2[0-3]):[0-5][0-9] h|(?:0[1-9]|1[0-9]|2[0-3]):[0-5][0-9] h|(?:0[1-9]|[1-5][0-9]):[0-5][0-9] min|(?:0|[1-9]|[1-5][0-9])\.[0-9]{3} s)\z/)
  end

  private def active_profiles_timestamp?(timestamp)
    match = /\A([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})(Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])\z/.match(timestamp)
    return false unless match
    year, month, day, hour, minute, second = match.captures.first(6).map(&:to_i)
    year > 0 && Date.valid_date?(year, month, day) && hour < 24 && minute < 60 && second < 60
  end

  # 整文件 SHA 由 bootstrap 独立 baseline 比较；此处仅有限语义，不输出 XML/身份/URL。
  def validate_repository_settings(xml, expected_identity)
    raise 'invalid settings structure' if xml.match?(/<!DOCTYPE|<!ENTITY/i)
    root = REXML::Document.new(xml).root
    namespace = 'http://maven.apache.org/SETTINGS/1.2.0'
    check = lambda do |node, children|
      raise 'invalid settings structure' unless node && node.namespace == namespace
      attrs = node.attributes.to_a.reject { |a| a.expanded_name == 'xmlns' || a.prefix == 'xmlns' }
      if node == root
        raise 'invalid settings structure' unless attrs.size == 1 && attrs.first.name == 'schemaLocation' && attrs.first.namespace == 'http://www.w3.org/2001/XMLSchema-instance'
        raise 'invalid settings structure' unless node.namespaces.values.sort == [namespace, 'http://www.w3.org/2001/XMLSchema-instance'].sort
      else
        raise 'invalid settings structure' unless attrs.empty?
      end
      raise 'invalid settings structure' unless node.elements.to_a.map(&:name).sort == children.sort
      raise 'invalid settings structure' unless node.texts.all? { |t| t.value.match?(/\A[ \t\r\n]*\z/) }
    end
    scalar = lambda do |node|
      raise 'invalid settings scalar' unless node && node.namespace == namespace && node.elements.to_a.empty? && node.attributes.empty?
      text = node.texts.map(&:value).join
      raise 'invalid settings scalar' if text.empty? || text.match?(/\A[ \t\r\n]|[ \t\r\n]\z|\$\{|@\{/)
      text
    end
    raise 'invalid settings structure' unless root && root.name == 'settings'
    check.call(root, %w[pluginGroups proxies servers mirrors profiles activeProfiles])
    %w[pluginGroups proxies servers mirrors].each { |name| check.call(root.elements[name], []) }
    profiles = root.elements['profiles']
    check.call(profiles, %w[profile profile])
    identities = profiles.elements.to_a.map do |profile|
      check.call(profile, %w[id repositories])
      identity = Digest::SHA256.hexdigest(scalar.call(profile.elements['id']).encode(Encoding::UTF_8))
      repositories = profile.elements['repositories']
      check.call(repositories, ['repository'])
      repository = repositories.elements['repository']
      check.call(repository, %w[id url releases snapshots])
      %w[id url].each { |name| scalar.call(repository.elements[name]) }
      { 'releases' => ['enabled'], 'snapshots' => %w[enabled updatePolicy] }.each do |name, fields|
        policy = repository.elements[name]
        check.call(policy, fields)
        fields.each { |field| scalar.call(policy.elements[field]) }
      end
      identity
    end
    raise 'invalid settings identity' unless identities.uniq.size == 2
    active = root.elements['activeProfiles']
    check.call(active, ['activeProfile'])
    active_sha = Digest::SHA256.hexdigest(scalar.call(active.elements['activeProfile']).encode(Encoding::UTF_8))
    raise 'invalid settings identity' unless identities.include?(active_sha) && active_sha == expected_identity
    { 'settingsSha256' => Digest::SHA256.hexdigest(xml), 'profileIdentitySha256' => identities.sort, 'activeProfileIdentitySha256' => active_sha }
  rescue REXML::ParseException, EncodingError
    raise 'invalid settings structure'
  end

  # 未受信 caller 描述的结构/内容绑定；不采集宿主来源、不执行命令或清理产物。
  def bind_model_provenance(model:, verify:, inputs:)
    validate_provenance_context(model, verify)
    raise 'invalid provenance input keys' unless inputs.is_a?(Hash) && inputs.keys.sort == %w[effectivePom localRepository]
    inputs.each_value do |value|
      raise 'invalid provenance input schema' unless value.is_a?(Hash) && value.keys.sort == %w[path sha256]
      provenance_absolute_path(value.fetch('path'))
      raise 'invalid provenance SHA' unless value.fetch('sha256').is_a?(String) && value.fetch('sha256').match?(/\A[0-9a-f]{64}\z/)
    end
    raise 'effective model output mismatch' unless '-Doutput=' + inputs.fetch('effectivePom').fetch('path') == model.fetch('argv').last
    payload = { 'model' => model, 'verify' => verify, 'inputs' => inputs }
    copy = JSON.parse(JSON.generate(payload))
    copy.merge('bindingSha256' => Digest::SHA256.hexdigest(JSON.generate(copy)))
  end

  def read_bound_effective_model(xml, oracle, binding)
    raise 'invalid model provenance binding schema' unless binding.is_a?(Hash) && binding.keys.sort == %w[bindingSha256 inputs model verify]
    rebuilt = bind_model_provenance(model: binding.fetch('model'), verify: binding.fetch('verify'), inputs: binding.fetch('inputs'))
    raise 'model provenance digest mismatch' unless rebuilt.fetch('bindingSha256') == binding.fetch('bindingSha256')
    raise 'effective model bytes mismatch' unless xml.is_a?(String) && Digest::SHA256.hexdigest(xml) == binding.fetch('inputs').fetch('effectivePom').fetch('sha256')
    context = { 'verifyArgv' => binding.fetch('verify').fetch('argv'), 'sourceReviewed' => true,
                'localRepository' => binding.fetch('inputs').fetch('localRepository').fetch('path') }
    summary = read_effective_model(xml, oracle, context, contract: :formal)
    # records 保留 XML 原始顺序；排序 oracle 只验证完整集合，不提供 reactor 展示顺序。
    projection = summary.fetch('modules').map { |entry| entry.select { |key, _| %w[gav name packaging version].include?(key) } }
    summary.merge('displayProjects' => projection, 'effectivePomSha256' => Digest::SHA256.hexdigest(xml))
  end

  def provenance_absolute_path(value)
    raise 'invalid provenance absolute path' unless value.is_a?(String) && value.start_with?('/') && !value.match?(/[\x00\r\n$]/) && File.expand_path(value) == value
  end

  # argv 記錄實際 shell 命令；resolved launcher 是獨立欄位，不能替換 argv[0]。
  def validate_provenance_context(model, verify)
    keys = %w[argv canonicalRoot cwd dotMvn environment jdk maven settings]
    [model, verify].each do |value|
      raise 'invalid provenance context schema' unless value.is_a?(Hash) && value.keys.sort == keys.sort
      %w[canonicalRoot cwd dotMvn jdk maven settings].each { |key| provenance_absolute_path(value.fetch(key)) }
      raise 'canonical root mismatch' unless value.fetch('canonicalRoot') == @root && value.fetch('cwd') == @root && value.fetch('dotMvn') == @root + '/.mvn'
      environment = value.fetch('environment')
      raise 'invalid provenance environment' unless environment.is_a?(Hash) && environment.keys.sort == %w[JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS MAVEN_PROJECTBASEDIR]
      raise 'environment injection detected' unless environment.values.all? { |entry| entry == { 'present' => false } }
    end
    (keys - ['argv']).each { |key| raise "provenance #{key} mismatch" unless model.fetch(key) == verify.fetch(key) }
    raise 'invalid verify invocation' unless verify.fetch('argv') == ['mvn', '-q', 'verify']
    expected = ['mvn', '-o', '-q', '-f', 'pom.xml', 'org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom', '-Dverbose=false', '-Doutput=' + @root + '/.harness/runtime/effective-pom.xml']
    actual = model.fetch('argv')
    dynamic_output = actual.is_a?(Array) && actual.last.is_a?(String) && actual.last.start_with?('-Doutput=') ? actual.last.delete_prefix('-Doutput=') : ''
    if actual != expected
      directory = File.dirname(dynamic_output)
      raise 'invalid model invocation' unless File.dirname(directory) == @root + '/.harness/runtime' && File.basename(dynamic_output) == 'effective-pom.xml'
      provenance_absolute_path(dynamic_output)
      expected[-1] = '-Doutput=' + dynamic_output
    end
    raise 'invalid model invocation' unless actual == expected
    true
  end

  # 只读当前受审 canonical POM；有限父继承及声明顺序 DFS，不解析依赖或通用插值。
  # 每次调用重读 bytes 并派生事实和摘要；不得以 caller 自报来源标记代替。
  def read_raw_pom_oracle
    namespace = 'http://maven.apache.org/POM/4.0.0'
    records = {}
    visited = []
    visit = lambda do |pom, parent_record|
      raise 'invalid raw POM oracle' unless FORMAL_CANONICAL.key?(pom) && !records.key?(pom)
      bytes = File.binread(path(pom)).force_encoding(Encoding::UTF_8)
      raise 'invalid raw POM oracle' unless bytes.valid_encoding? && !bytes.include?("\x00")
      raise 'invalid raw POM oracle' if bytes.match?(/<!DOCTYPE|<!ENTITY/i)
      document = REXML::Document.new(bytes)
      project = document.root
      raise 'invalid raw POM oracle' unless document.elements.to_a.size == 1 && project.name == 'project' && project.namespace == namespace
      scalar = lambda do |container, name, required = false, empty = false|
        fields = container.elements.to_a.select { |child| child.name == name }
        raise 'invalid raw POM oracle' unless fields.size <= 1 && (!required || fields.size == 1)
        next nil if fields.empty?
        field = fields.first
        value = field.texts.map(&:value).join
        raise 'invalid raw POM oracle' unless field.namespace == namespace && field.attributes.empty? && field.elements.to_a.empty? && (empty ? value.empty? : !value.empty? && !value.match?(/\A[\x20\t\r\n]|[\x20\t\r\n]\z/))
        raise 'invalid raw POM oracle' if value.include?('${')
        value
      end
      container = lambda do |name, allowed, required|
        fields = project.elements.to_a.select { |child| child.name == name }
        raise 'invalid raw POM oracle' unless fields.size <= 1 && (!required || fields.size == 1)
        next nil if fields.empty?
        field = fields.first
        raise 'invalid raw POM oracle' unless field.namespace == namespace && field.attributes.empty? && field.texts.map(&:value).join.match?(/\A[\x20\t\r\n]*\z/) && field.elements.to_a.all? { |child| child.namespace == namespace && allowed.include?(child.name) }
        field
      end
      parent = container.call('parent', parent_record ? %w[groupId artifactId version] : %w[groupId artifactId version relativePath], true)
      parent_gav = %w[groupId artifactId version].map { |name| scalar.call(parent, name, true) }.join(':')
      if parent_record
        raise 'invalid raw POM oracle' unless parent_gav == parent_record.fetch('gav') && File.expand_path('../pom.xml', File.dirname(path(pom))) == parent_record.fetch('pom')
      else
        raise 'invalid raw POM oracle' unless pom == 'pom.xml' && parent_gav == 'org.springframework.boot:spring-boot-starter-parent:3.5.14' && scalar.call(parent, 'relativePath', true, true) == ''
      end
      group = scalar.call(project, 'groupId', !parent_record)
      version = scalar.call(project, 'version', !parent_record)
      artifact = scalar.call(project, 'artifactId', true)
      gav = [group || parent_record.fetch('gav').split(':')[0], artifact, version || parent_record.fetch('gav').split(':')[2]].join(':')
      declared_packaging = scalar.call(project, 'packaging')
      raise 'invalid raw POM oracle' unless declared_packaging.nil? || declared_packaging == 'pom'
      packaging = declared_packaging || 'jar'
      expected_packaging = FORMAL_JAR_ARTIFACTS.include?(artifact) ? 'jar' : 'pom'
      raise 'invalid raw POM oracle' unless gav == FORMAL_CANONICAL.fetch(pom) && packaging == expected_packaging
      modules_container = container.call('modules', ['module'], false)
      modules = modules_container ? modules_container.elements.to_a.map do |field|
        value = field.texts.map(&:value).join
        raise 'invalid raw POM oracle' unless field.attributes.empty? && field.elements.to_a.empty? && value.match?(/\A[A-Za-z0-9_-]+\z/)
        value
      end : []
      raise 'invalid raw POM oracle' unless modules.uniq == modules && (packaging == 'pom' || modules.empty?)
      base = File.dirname(path(pom))
      record = { 'gav' => gav, 'pom' => path(pom), 'basedir' => base, 'packaging' => packaging, 'modules' => modules,
                 'sha256' => Digest::SHA256.hexdigest(bytes), 'groupSource' => group ? 'direct' : 'parent', 'versionSource' => version ? 'direct' : 'parent' }
      records[pom] = record
      visited << pom
      modules.each { |name| visit.call((pom == 'pom.xml' ? '' : File.dirname(pom) + '/') + name + '/pom.xml', record) }
    end
    visit.call('pom.xml', nil)
    raise 'invalid raw POM oracle' unless visited == FORMAL_CANONICAL.keys
    records.values.sort_by { |entry| entry.fetch('gav') }
  rescue StandardError
    raise 'invalid raw POM oracle'
  end

  # 纯输入 reader；formal 合同另从当前 canonical raw bytes 派生独立事实。
  # context 是已审来源的显式描述，不代表本方法采集或验证了宿主机来源。
  def read_effective_model(xml, oracle, context, contract: :local)
    raise 'unknown model contract' unless %i[local formal].include?(contract)
    if contract == :formal
      raw_oracle = read_raw_pom_oracle
      keys = %w[gav pom basedir packaging modules sha256]
      raise 'formal oracle differs from raw POM bytes' unless oracle.map { |entry| entry.select { |key, _| keys.include?(key) } } == raw_oracle.map { |entry| entry.select { |key, _| keys.include?(key) } }
      oracle = raw_oracle
      raise 'formal oracle must contain exactly 53 entries' unless oracle.size == 53
      expected = FORMAL_CANONICAL.map { |pom, gav| [gav, File.dirname(pom) == '.' ? @root : @root + '/' + File.dirname(pom)] }.sort_by(&:first)
      actual = oracle.map { |entry| [entry.fetch('gav'), entry.fetch('basedir')] }.sort_by(&:first)
      raise 'formal oracle must use reviewed canonical identity set' unless actual == expected && oracle == oracle.sort_by { |entry| entry.fetch('gav') } && oracle.all? { |entry| entry.fetch('pom') == @root + '/' + FORMAL_CANONICAL.key(entry.fetch('gav')) }
    end
    raise 'unreviewed invocation/source context' unless context.fetch('verifyArgv') == ['mvn', '-q', 'verify'] && context.fetch('sourceReviewed') == true
    repository = context.fetch('localRepository')
    raise 'invalid local repository' unless repository.start_with?('/') && File.expand_path(repository) == repository && !repository.include?('${')
    raise 'empty independent oracle' if oracle.empty?
    %w[gav pom basedir].each do |key|
      values = oracle.map { |entry| entry.fetch(key) }
      raise "duplicate oracle #{key}" unless values.uniq.size == values.size
    end
    oracle.each do |entry|
      base = entry.fetch('basedir')
      raise 'oracle path mismatch' unless base == @root || base.start_with?(@root + '/')
      raise 'noncanonical oracle path' unless File.expand_path(base) == base && entry.fetch('pom') == base + '/pom.xml'
      path(relative(entry.fetch('pom')))
      raise 'invalid oracle identity' unless entry.fetch('gav').match?(/\A[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+\z/)
    end
    raise 'DTD not supported' if xml.match?(/<!DOCTYPE|<!ENTITY/i)
    wrapper = REXML::Document.new(xml).root
    raise 'effective wrapper must be projects' unless wrapper && wrapper.name == 'projects' && wrapper.namespace.to_s.empty?
    raise 'invalid effective wrapper children' unless wrapper.elements.to_a.all? { |entry| entry.name == 'project' }
    projects = wrapper.get_elements('project')
    records = projects.map do |project|
      raise 'wrong POM namespace' unless project.namespace == 'http://maven.apache.org/POM/4.0.0'
      gav = %w[groupId artifactId version].map { |key| model_scalar(project, key) }.join(':')
      source = oracle.find { |entry| entry.fetch('gav') == gav }
      raise "unknown model identity #{gav}" unless source
      # Maven 模型默认 packaging 为 jar，effective-pom 可省略该默认字段；不是继承父 packaging。
      packaging_field = model_optional(project, 'packaging')
      packaging = packaging_field ? model_scalar(project, 'packaging') : 'jar'
      raise 'packaging mismatch' unless packaging == source.fetch('packaging')
      modules = model_optional(project, 'modules')
      module_names = modules ? model_list(modules, 'module') : []
      raise 'module declaration mismatch' unless module_names == source.fetch('modules')
      build = model_one(project, 'build')
      base = source.fetch('basedir')
      { 'directory' => '/target', 'sourceDirectory' => '/src/main/java', 'testSourceDirectory' => '/src/test/java',
        'outputDirectory' => '/target/classes', 'testOutputDirectory' => '/target/test-classes' }.each do |key, suffix|
        raise "non-default model #{key}" unless model_scalar(build, key) == base + suffix
        path(relative(base + suffix))
      end
      plugins = model_one(build, 'plugins').get_elements('plugin')
      identities = plugins.map { |plugin| model_plugin(plugin, base, source, context) }
      raise 'duplicate effective plugin' unless identities.uniq.size == identities.size
      required = %w[maven-enforcer-plugin jacoco-maven-plugin maven-clean-plugin maven-install-plugin maven-deploy-plugin maven-site-plugin]
      required += %w[maven-resources-plugin maven-compiler-plugin maven-surefire-plugin maven-jar-plugin] if source.fetch('packaging') == 'jar'
      raise 'missing effective lifecycle plugin' unless (required - identities).empty?
      names = project.elements.to_a.select { |child| child.name == 'name' }
      raise 'invalid model display name' unless names.size <= 1
      name = gav.split(':')[1]
      unless names.empty?
        field = names.first
        name = field.texts.map(&:value).join
        raise 'invalid model display name' unless field.namespace == 'http://maven.apache.org/POM/4.0.0' && field.attributes.empty? && field.elements.to_a.empty?
      end
      raise 'invalid model display name' unless name.valid_encoding? && !name.match?(/[\u0000-\u001f\u007f-\u009f]/) && !name.include?('${')
      { 'gav' => gav, 'pom' => source.fetch('pom'), 'basedir' => base, 'packaging' => source.fetch('packaging'), 'plugins' => identities.sort,
        'name' => name, 'version' => gav.split(':')[2] }
    end
    raise 'effective module set differs from independent oracle' unless records.map { |entry| entry.fetch('gav') }.sort == oracle.map { |entry| entry.fetch('gav') }.sort && records.map { |entry| entry.fetch('gav') }.uniq.size == records.size
    { 'modules' => records, 'verifyArgv' => context.fetch('verifyArgv'), 'localRepository' => repository }
  end

  def model_one(element, name)
    entries = element.get_elements(name)
    raise "unique model field required: #{name}" unless entries.size == 1
    entries.first
  end

  def model_optional(element, name)
    entries = element.get_elements(name)
    raise "duplicate model field: #{name}" if entries.size > 1
    entries.first
  end

  def model_scalar(element, name)
    child = model_one(element, name)
    raise "plain model scalar required: #{name}" unless child.elements.to_a.empty? && child.attributes.empty?
    child.texts.map(&:value).join.strip
  end

  def model_list(element, name)
    raise "unexpected list member: #{name}" unless element.elements.to_a.all? { |child| child.name == name && child.elements.to_a.empty? && child.attributes.empty? }
    element.get_elements(name).map { |child| child.texts.map(&:value).join.strip }
  end

  # 结构值保留 multiplicity 和属性；仅用于小型已命名配置合同，不保存整份有效 POM 快照。
  def model_shape(element)
    return nil unless element
    [element.name, element.attributes.to_a.map { |attribute| [attribute.expanded_name, attribute.value] }.sort,
     element.texts.map(&:value).join.strip, model_children_shape(element)]
  end

  def model_children_shape(element)
    children = element.elements.to_a.map { |child| model_shape(child) }
    %w[arguments additionalClasspathElements].include?(element.name) ? children : children.sort_by(&:inspect)
  end

  def model_config(element, expected)
    actual = model_optional(element, 'configuration')
    wanted = expected && REXML::Document.new('<configuration>' + expected + '</configuration>').root
    raise 'effective configuration differs from finite contract' unless model_shape(actual) == model_shape(wanted)
  end

  def model_plugin(plugin, base, source, context)
    artifact = model_scalar(plugin, 'artifactId')
    group = model_optional(plugin, 'groupId') ? model_scalar(plugin, 'groupId') : 'org.apache.maven.plugins'
    versions = { 'maven-enforcer-plugin' => '3.5.0', 'jacoco-maven-plugin' => '0.8.13', 'maven-clean-plugin' => '3.4.1',
      'maven-install-plugin' => '3.1.4', 'maven-deploy-plugin' => '3.1.4', 'maven-site-plugin' => '3.3',
      'maven-resources-plugin' => '3.3.1', 'maven-compiler-plugin' => '3.14.1', 'maven-surefire-plugin' => '3.5.5',
      'maven-jar-plugin' => '3.4.2', 'spring-boot-maven-plugin' => '3.5.14', 'maven-shade-plugin' => '3.6.0' }
    expected_group = case artifact
                     when 'jacoco-maven-plugin' then 'org.jacoco'
                     when 'spring-boot-maven-plugin' then 'org.springframework.boot'
                     when 'exec-maven-plugin' then 'org.codehaus.mojo'
                     else 'org.apache.maven.plugins'
                     end
    rel = base == @root ? '' : relative(base)
    ui = { 'im-management/im-iam/im-iam-server' => 'iam', 'im-management/im-admin' => 'admin', 'im-management/im-ops/im-ops-server' => 'ops', 'im-management/im-audit/im-audit-server' => 'audit' }
    boot = %w[im-gateway/im-http-gateway im-gateway/im-ws-gateway/im-ws-gateway-server im-broker/im-broker-server im-service/im-account/im-account-server im-service/im-social/im-social-server im-service/im-message/im-message-server] + ui.keys
    if artifact == 'exec-maven-plugin'
      version = ui.key?(rel) ? '3.6.3' : (rel == 'im-test/im-load-test' ? '3.5.0' : nil)
    else
      version = versions[artifact]
    end
    raise "unknown effective plugin #{artifact}" unless version && group == expected_group && model_scalar(plugin, 'version') == version
    allowed = %w[groupId artifactId version executions configuration]
    allowed << 'dependencies' if artifact == 'maven-shade-plugin'
    raise 'unknown plugin field' unless (plugin.elements.to_a.map(&:name) - allowed).empty?
    raise 'Boot outside approved module' if artifact == 'spring-boot-maven-plugin' && !boot.include?(rel)
    raise 'Shade outside CLI' if artifact == 'maven-shade-plugin' && rel != 'im-management/im-ops/im-ops-cli'
    compiler = '<release>21</release><parameters>true</parameters><annotationProcessorPaths><path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>1.18.42</version></path><path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>1.5.5.Final</version></path></annotationProcessorPaths>'
    arg = '@{argLine} -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xshare:off -javaagent:${settings.localRepository}/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar'
    surefire = '<argLine>' + arg + '</argLine>'
    if rel == 'im-test/im-architecture-test'
      surefire += '<additionalClasspathElements>' + %w[im-management/im-admin im-management/im-audit/im-audit-server].map { |mod| '<additionalClasspathElement>' + @root + '/' + mod + '/target/classes</additionalClasspathElement>' }.join + '</additionalClasspathElements>'
    end
    resources = '<propertiesEncoding>UTF-8</propertiesEncoding><delimiters><delimiter>@</delimiter></delimiters><useDefaultDelimiters>false</useDefaultDelimiters>'
    jar = '<archive><manifest><mainClass>${start-class}</mainClass><addDefaultImplementationEntries>true</addDefaultImplementationEntries></manifest></archive>'
    site = '<outputDirectory>' + base + '/target/site</outputDirectory><reportPlugins><reportPlugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-project-info-reports-plugin</artifactId></reportPlugin></reportPlugins>'
    boot_config = '<classifier>exec</classifier><mainClass>${start-class}</mainClass>'
    signature = '<filters><filter><artifact>*:*</artifact><excludes><exclude>META-INF/*.SF</exclude><exclude>META-INF/*.DSA</exclude><exclude>META-INF/*.RSA</exclude></excludes></filter></filters>'
    shade_global = '<keepDependenciesWithProvidedScope>true</keepDependenciesWithProvidedScope><createDependencyReducedPom>true</createDependencyReducedPom>' + signature
    shade_execution = '<createDependencyReducedPom>false</createDependencyReducedPom><transformers combine.self="override"><transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer"><mainClass>com.co.kc.imchat.management.ops.cli.OpsCli</mainClass></transformer></transformers><keepDependenciesWithProvidedScope>true</keepDependenciesWithProvidedScope>' + signature
    configs = { 'maven-compiler-plugin' => compiler, 'maven-surefire-plugin' => surefire, 'maven-resources-plugin' => resources, 'maven-jar-plugin' => jar, 'maven-site-plugin' => site, 'spring-boot-maven-plugin' => boot_config, 'maven-shade-plugin' => shade_global }
    configs['exec-maven-plugin'] = '<classpathScope>test</classpathScope>' if rel == 'im-test/im-load-test'
    begin
      model_config(plugin, configs[artifact])
    rescue StandardError => error
      raise "#{artifact}: #{error.message}"
    end
    if artifact == 'maven-shade-plugin'
      dependencies = model_one(plugin, 'dependencies')
      expected = REXML::Document.new('<dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId><version>3.5.14</version><scope>compile</scope></dependency></dependencies>').root
      raise 'unapproved Shade plugin dependency' unless model_shape(dependencies) == model_shape(expected)
    end
    executions = model_optional(plugin, 'executions')
    raise 'unknown executions member' if executions && executions.elements.to_a.any? { |child| child.name != 'execution' }
    entries = executions ? executions.get_elements('execution') : []
    # 缺 phase 仅对应已经审阅的三个插件默认绑定；不推测未知 goal 的默认 phase。
    contracts = case artifact
    when 'maven-enforcer-plugin' then [['enforce-build-environment', '', 'enforce']]
    when 'jacoco-maven-plugin' then [['', '', 'prepare-agent'], ['report', 'verify', 'report']]
    when 'maven-clean-plugin' then [['default-clean', 'clean', 'clean']]
    when 'maven-install-plugin' then [['default-install', 'install', 'install']]
    when 'maven-deploy-plugin' then [['default-deploy', 'deploy', 'deploy']]
    when 'maven-site-plugin' then [['default-site', 'site', 'site'], ['default-deploy', 'site-deploy', 'deploy']]
    when 'maven-resources-plugin' then [['default-testResources', 'process-test-resources', 'testResources'], ['default-resources', 'process-resources', 'resources']]
    when 'maven-compiler-plugin' then [['default-compile', 'compile', 'compile'], ['default-testCompile', 'test-compile', 'testCompile']]
    when 'maven-surefire-plugin' then [['default-test', 'test', 'test']]
    when 'maven-jar-plugin' then [['default-jar', 'package', 'jar']]
    when 'spring-boot-maven-plugin' then [['repackage', '', 'repackage']]
    when 'maven-shade-plugin' then [['', 'package', 'shade']]
    when 'exec-maven-plugin'
      ui.key?(rel) ? [["install-#{ui.fetch(rel)}-ui", 'prepare-package', 'exec'], ["build-#{ui.fetch(rel)}-ui", 'prepare-package', 'exec']] : []
    end
    actual = entries.map do |execution|
      raise 'unknown execution field' unless (execution.elements.to_a.map(&:name) - %w[id phase goals configuration]).empty?
      id = model_optional(execution, 'id') ? model_scalar(execution, 'id') : ''
      phase = model_optional(execution, 'phase') ? model_scalar(execution, 'phase') : ''
      goals = model_list(model_one(execution, 'goals'), 'goal')
      raise 'unique finite goal required' unless goals.size == 1
      expected = configs[artifact]
      expected = '<rules><requireJavaVersion><version>[21,22)</version></requireJavaVersion><requireMavenVersion><version>[3.8.4,4)</version></requireMavenVersion></rules>' if artifact == 'maven-enforcer-plugin'
      expected = goals.first == 'prepare-agent' ? '<includes><include>com.co.kc.imchat.*</include></includes>' : nil if artifact == 'jacoco-maven-plugin'
      expected = shade_execution if artifact == 'maven-shade-plugin'
      if artifact == 'exec-maven-plugin'
        args = id.start_with?('install-') ? %w[ci --prefer-offline --no-audit --no-fund] : %w[run build]
        expected = '<workingDirectory>' + base + '/ui</workingDirectory><executable>npm</executable><arguments>' + args.map { |arg| '<argument>' + arg + '</argument>' }.join + '</arguments>'
      end
      model_config(execution, expected)
      [id, phase, goals.first]
    end
    raise "unapproved lifecycle execution #{artifact}" unless actual.sort == contracts.sort
    artifact
  end

  def prepare(output, model_modules = [], state_files = [])
    groups = selection
    files = groups.values.flatten.map { |entry| report(entry) }.uniq + JARS.flat_map { |mod| Dir.glob(path("#{mod}/target/*-exec.jar")).map { |file| relative(file) } }
    bases = model_modules.map do |mod|
      base = mod.fetch('basedir')
      raise 'invalid output module' unless %w[jar pom].include?(mod.fetch('packaging')) && File.expand_path(base) == base && (base == @root || path(relative(base)) == base)
      base
    end
    raise 'duplicate output module' unless bases.uniq == bases
    directories = []
    model_modules.select { |mod| mod.fetch('packaging') == 'jar' }.each do |mod|
      %w[classes generated-sources/annotations maven-status/maven-compiler-plugin jacoco.exec site/jacoco].each do |suffix|
        file = relative(mod.fetch('basedir') + '/target/' + suffix)
        files << file
        directories << file unless suffix == 'jacoco.exec'
      end
    end
    clear_targets(files, directories, state_files)
    save(output, { 'version' => 1, 'root' => @root, 'startedAt' => Time.now.to_f, 'groups' => groups })
  end

  # 先校验整批路径再删除，避免后一个符号链接失败前已删除前面的报告。
  def clear_targets(files, directories = [], state_files = [])
    targets = files.map do |file|
      full = path(file)
      raise "refusing non-target removal #{file}" unless file.include?('/target/') && relative(full) == file && !file.end_with?('/target/')
      full
    end
    targets += state_files.map { |file| path(file) }
    targets.each do |full|
      ancestor = File.dirname(full)
      until ancestor == @root
        raise "invalid removal parent #{ancestor}" if File.exist?(ancestor) && !File.directory?(ancestor)
        ancestor = File.dirname(ancestor)
      end
      if File.exist?(full)
        expected_directory = directories.include?(relative(full))
        raise "invalid removal target type #{full}" unless expected_directory ? File.directory?(full) : File.file?(full)
      end
      members = [full] + Dir.glob(full + '/**/*', File::FNM_DOTMATCH)
      members.each do |member|
        next if %w[. ..].include?(File.basename(member))
        checked = path(relative(member))
        next unless File.exist?(checked)
        stat = File.lstat(checked)
        raise "invalid removal type #{checked}" unless stat.file? || stat.directory?
      end
    end
    targets.each do |full|
      FileUtils.remove_entry(full) if File.exist?(full)
      raise "failed to clear #{full}" if File.exist?(full)
    end
  end

  def startup_clear
    clear_targets(startup.map { |entry| report(entry) })
  end

  def load_manifest(file)
    data = JSON.parse(File.read(file, encoding: Encoding::UTF_8))
    raise 'invalid reactor manifest' unless data['version'] == 1 && data['root'] == @root && data['groups'] == selection
    epoch(data.fetch('startedAt'))
    data
  end

  # 只读取 JVMS constant pool 和 this_class；每次读取受字段长度限制，不解析方法体。
  # 完整 class 摘要另行计算；此处不代替 JVM verifier 或生成鲜度证明。
  def class_identity(file)
    File.open(file, 'rb') do |input|
      read = lambda do |length|
        bytes = input.read(length)
        raise "class ownership: truncated header #{relative(file)}" unless bytes && bytes.bytesize == length
        bytes
      end
      magic, minor, major, count = read.call(10).unpack('Nnnn')
      raise "class ownership: invalid header #{relative(file)}" unless magic == 0xcafebabe && major.between?(45, 65) && count > 1 && (minor == 0 || (major == 45 && minor <= 3) || (major >= 56 && minor == 65535))
      pool = Array.new(count)
      index = 1
      while index < count
        tag = read.call(1).unpack1('C')
        case tag
        when 1
          pool[index] = [tag, read.call(read.call(2).unpack1('n'))]
        when 7
          pool[index] = [tag, read.call(2).unpack1('n')]
        when 3, 4, 9, 10, 11, 12, 17, 18
          read.call(4)
          pool[index] = [tag]
        when 5, 6
          raise "class ownership: invalid double slot #{relative(file)}" unless index + 1 < count
          read.call(8)
          pool[index] = [tag]
          index += 1
        when 8, 16, 19, 20
          read.call(2)
          pool[index] = [tag]
        when 15
          read.call(3)
          pool[index] = [tag]
        else
          raise "class ownership: unknown constant tag #{tag} #{relative(file)}"
        end
        index += 1
      end
      access_flags, this_class, = read.call(6).unpack('nnn')
      entry = pool[this_class] if this_class > 0 && this_class < count
      name_entry = pool[entry[1]] if entry && entry[0] == 7 && entry[1] > 0 && entry[1] < count
      raise "class ownership: invalid this_class #{relative(file)}" unless name_entry && name_entry[0] == 1
      name = name_entry[1]
      raise "class ownership: foreign or unsupported internal name #{relative(file)}" unless name.match?(%r{\Acom/co/kc/imchat/(?:[A-Za-z_$][A-Za-z0-9_$]*/)*(?:[A-Za-z_$][A-Za-z0-9_$]*|package-info)\z})
      { 'internalName' => name, 'accessFlags' => access_flags }
    end
  end

  def class_internal_name(file)
    class_identity(file).fetch('internalName')
  end

  # raw oracle 与既有有效模型合同使用同一正式模块/默认输出；不扫描实验或测试 classes。
  # 有普通生产源码必须有非空 class 清单；此处不代替清除后同轮鲜度证明。
  def production_class_inventory(since = nil)
    names = {}
    read_raw_pom_oracle.map do |mod|
      source_root = path(relative(mod.fetch('basedir') + '/src/main/java'))
      raise "class ownership: invalid source root #{relative(source_root)}" if File.exist?(source_root) && !File.directory?(source_root)
      production_sources = Dir.glob(source_root + '/**/*', File::FNM_DOTMATCH).sort.map do |candidate|
        next if %w[. ..].include?(File.basename(candidate))
        file = path(relative(candidate))
        stat = File.lstat(file)
        raise "class ownership: invalid source type #{relative(file)}" unless stat.directory? || stat.file?
        next unless stat.file? && File.extname(file) == '.java'
        if File.basename(file) == 'package-info.java'
          tokens = java_tokens(file)
          next if !tokens.include?('@') && tokens.first == 'package' && tokens.last == ';' && tokens.count(';') == 1
        end
        relative(file)
      end.compact
      directory = path(relative(mod.fetch('basedir') + '/target/classes'))
      classes = []
      if File.exist?(directory)
        raise "class ownership: invalid output directory #{relative(directory)}" unless File.directory?(directory)
        Dir.glob(directory + '/**/*', File::FNM_DOTMATCH).sort.each do |candidate|
          next if %w[. ..].include?(File.basename(candidate))
          file = path(relative(candidate))
          stat = File.lstat(file)
          raise "class ownership: invalid output type #{relative(file)}" unless stat.directory? || stat.file?
          next unless stat.file? && File.extname(file) == '.class'
          identity = class_identity(file)
          name = identity.fetch('internalName')
          raise "class ownership: path mismatch #{relative(file)}" unless file.delete_prefix(directory + '/') == name + '.class'
          raise "class ownership: duplicate internal name #{name}" if names.key?(name)
          fresh_production_output(file, since) if since
          names[name] = relative(file)
          classes << identity.merge('path' => relative(file), 'sha256' => Digest::SHA256.file(file).hexdigest)
        end
      end
      raise "class ownership: production classes missing #{relative(mod.fetch('pom'))}" if !production_sources.empty? && classes.empty?
      { 'pom' => relative(mod.fetch('pom')), 'sources' => production_sources, 'classes' => classes }
    end
  end

  def fresh_production_output(file, since)
    stat = File.stat(file)
    raise "stale production output #{relative(file)}" unless stat.file? && stat.size > 0 && stat.mtime.to_f >= since && stat.mtime.to_f <= Time.now.to_f
  end

  def generated_source_inventory(since)
    read_raw_pom_oracle.flat_map do |mod|
      directory = path(relative(mod.fetch('basedir') + '/target/generated-sources/annotations'))
      raise "invalid generated root #{relative(directory)}" if File.exist?(directory) && !File.directory?(directory)
      Dir.glob(directory + '/**/*', File::FNM_DOTMATCH).sort.map do |candidate|
        next if %w[. ..].include?(File.basename(candidate))
        file = path(relative(candidate))
        stat = File.lstat(file)
        raise "invalid generated output type #{relative(file)}" unless stat.file? || stat.directory?
        next unless stat.file? && File.extname(file) == '.java'
        fresh_production_output(file, since)
        { 'path' => relative(file), 'sha256' => Digest::SHA256.file(file).hexdigest }
      end.compact
    end
  end

  def coverage_artifact_inventory(since, inventory, mappers)
    inventory.map do |mod|
      next if mod.fetch('classes').empty?
      base = File.dirname(path(mod.fetch('pom')))
      exec = path(relative(base + '/target/jacoco.exec'))
      xml = path(relative(base + '/target/site/jacoco/jacoco.xml'))
      present = [exec, xml].select { |file| File.exist?(file) }
      executed = Dir.glob(path(relative(base + '/target/surefire-reports/TEST-*.xml'))).any? do |candidate|
        file = path(relative(candidate))
        next false unless File.file?(file) && File.mtime(file).to_f >= since
        suite = REXML::Document.new(File.read(file, encoding: Encoding::UTF_8)).root
        suite && suite.name == 'testsuite' && !suite.get_elements('testcase').empty?
      end
      next if present.empty? && !executed
      raise "coverage artifact pair incomplete #{relative(base)}" unless present.size == 2
      [exec, xml].each { |file| fresh_production_output(file, since) }
      document = REXML::Document.new(File.read(xml, encoding: Encoding::UTF_8))
      raise "invalid coverage report #{relative(xml)}" unless document.root && document.root.name == 'report'
      document.get_elements('//counter').group_by(&:parent).each_value do |entries|
        types = entries.map do |counter|
          type = counter.attributes['type']
          values = %w[missed covered].map { |key| counter.attributes[key] }
          raise "invalid coverage counter #{relative(xml)}" unless %w[INSTRUCTION BRANCH LINE COMPLEXITY METHOD CLASS].include?(type) && values.all? { |value| value && value.match?(/\A[0-9]+\z/) }
          type
        end
        raise "duplicate coverage counter #{relative(xml)}" unless types.uniq.size == types.size
      end
      classes = mod.fetch('classes')
      filtered = classes.select { |entry| entry.fetch('accessFlags') & 0x1000 != 0 }
      records = document.get_elements('report/package/class').map do |entry|
        name = entry.attributes['name']
        raise "coverage package mismatch #{relative(xml)}" unless name && entry.parent.attributes['name'] == name.split('/')[0...-1].join('/')
        counters = entry.get_elements('counter').map do |counter|
          type = counter.attributes['type']
          missed, covered = %w[missed covered].map { |key| counter.attributes[key] }
          { 'type' => type, 'missed' => Integer(missed, 10), 'covered' => Integer(covered, 10) }
        end
        { 'internalName' => name, 'counters' => counters.sort_by { |counter| counter.fetch('type') } }
      end
      names = records.map { |entry| entry.fetch('internalName') }
      expected = (classes - filtered).map { |entry| entry.fetch('internalName') }
      raise "coverage class set mismatch #{relative(xml)}" unless names.uniq.size == names.size && names.sort == expected.sort
      mapper_classes = mappers.map { |entry| entry.fetch('class') }
      raise "generated implementation filtered #{relative(xml)}" if filtered.any? { |entry| mapper_classes.include?(entry.fetch('path')) }
      { 'module' => relative(base), 'exec' => relative(exec), 'report' => relative(xml),
        'classes' => records.sort_by { |entry| entry.fetch('internalName') },
        'filteredClasses' => filtered.map { |entry| entry.merge('reason' => 'jacoco-0.8.13-class-synthetic') },
        'sha256' => [exec, xml].to_h { |file| [relative(file), Digest::SHA256.file(file).hexdigest] } }
    end.compact
  end

  def java_tokens(file)
    source = File.read(file, encoding: Encoding::UTF_8)
    raise "unsupported Java syntax #{relative(file)}" unless source.valid_encoding? && !source.match?(/\\u+[0-9a-fA-F]{4}/)
    tokens = []
    until source.empty?
      if source.sub!(/\A(?:\s+|\/\/[^\n]*(?:\n|\z)|\/\*.*?\*\/)/m, '')
        next
      elsif source.sub!(/\A"""(?:\\.|(?!""").)*"""/m, '') ||
          source.sub!(/\A"(?:\\.|[^"\\\r\n])*"/, '') ||
          source.sub!(/\A'(?:\\.|[^'\\\r\n])*'/, '')
        tokens << '<literal>'
      elsif source.start_with?('"', "'", '/*')
        raise "unsupported Java syntax #{relative(file)}"
      else
        tokens << (source.slice!(/\A[A-Za-z_$][A-Za-z0-9_$]*/) || source.slice!(0))
      end
    end
    tokens
  end

  def mapper_interface(file)
    tokens = java_tokens(file)
    imports = tokens.join(' ').scan(/\bimport (?:static )?([^;]+) ;/).flatten.map { |entry| entry.delete(' ') }
    declarations = []
    depth = 0
    index = 0
    while index < tokens.size
      token = tokens[index]
      depth += 1 if token == '{'
      depth -= 1 if token == '}'
      if token == '@'
        index += 1
        annotation = tokens[index].to_s
        while tokens[index + 1] == '.'
          index += 2
          annotation += '.' + tokens[index].to_s
        end
        raise "unsupported mapper config #{relative(file)}" if %w[MapperConfig org.mapstruct.MapperConfig].include?(annotation)
        if %w[Mapper org.mapstruct.Mapper].include?(annotation)
          if annotation == 'Mapper'
            raise "ambiguous mapper import #{relative(file)}" if imports.any? { |entry| entry.end_with?('.*') } || imports.count { |entry| entry.end_with?('.Mapper') } > 1 || tokens.each_cons(2).any? { |pair| %w[class interface enum record].include?(pair[0]) && pair[1] == 'Mapper' }
            unless imports.include?('org.mapstruct.Mapper')
              index += 1
              next
            end
          end
          raise "unsupported nested mapper #{relative(file)}" unless depth == 0
          if tokens[index + 1] == '('
            index += 2
            balance = []
            key = true
            loop do
              current = tokens[index]
              raise "unsupported mapper arguments #{relative(file)}" unless current
              break if current == ')' && balance.empty?
              if balance.empty? && key
                raise "unsupported mapper customization #{relative(file)}" unless %w[imports uses unmappedTargetPolicy].include?(current) && tokens[index + 1] == '='
                key = false
              elsif balance.empty? && current == ','
                key = true
              end
              if ['(', '{', '['].include?(current)
                balance << current
              elsif [')', '}', ']'].include?(current)
                raise "unsupported mapper arguments #{relative(file)}" unless balance.pop == { ')' => '(', '}' => '{', ']' => '[' }.fetch(current)
              end
              index += 1
            end
          end
          following = index + 1
          following += 1 while %w[public abstract strictfp].include?(tokens[following])
          name = tokens[following + 1]
          raise "unsupported mapper declaration #{relative(file)}" unless tokens[following] == 'interface' && name && name.match?(/\A[A-Za-z_$][A-Za-z0-9_$]*\z/)
          declarations << name
        end
      end
      index += 1
    end
    raise "ambiguous mapper declaration #{relative(file)}" if declarations.size > 1
    return nil if declarations.empty?
    packages = tokens.join(' ').scan(/\bpackage ([^;]+) ;/).flatten.map { |entry| entry.delete(' ') }
    package = packages.first
    raise "invalid mapper package #{relative(file)}" unless packages.size == 1 && package.match?(/\Acom\.co\.kc\.imchat(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*\z/)
    internal = package.tr('.', '/') + '/' + declarations.first
    raise "invalid mapper path #{relative(file)}" unless file.end_with?('/src/main/java/' + internal + '.java')
    internal
  end

  def generated_mapper_inventory
    read_raw_pom_oracle.flat_map do |mod|
      base = mod.fetch('basedir')
      source_root = path(relative(base + '/src/main/java'))
      Dir.glob(source_root + '/**/*', File::FNM_DOTMATCH).sort.map do |candidate|
        next if %w[. ..].include?(File.basename(candidate))
        file = path(relative(candidate))
        stat = File.lstat(file)
        raise "invalid mapper source type #{relative(file)}" unless stat.directory? || stat.file?
        next unless stat.file? && File.extname(file) == '.java'
        internal = mapper_interface(file)
        next unless internal
        generated = path(relative(base + '/target/generated-sources/annotations/' + internal + 'Impl.java'))
        compiled = path(relative(base + '/target/classes/' + internal + 'Impl.class'))
        raise "mapper output missing #{relative(file)}" unless File.file?(generated) && File.file?(compiled)
        generated_tokens = java_tokens(generated)
        name = internal.split('/').last
        package = internal.split('/')[0...-1].join('.')
        declaration = generated_tokens.join(' ')
        depth = 0
        classes = []
        generated_tokens.each_with_index do |token, index|
          classes << generated_tokens[index, 5] if token == 'class' && depth == 0 && generated_tokens[index - 1] != '.'
          depth += 1 if token == '{'
          depth -= 1 if token == '}'
        end
        raise "invalid mapper implementation #{relative(generated)}" unless declaration.scan(/\bpackage ([^;]+) ;/).flatten.map { |entry| entry.delete(' ') } == [package] && classes == [['class', name + 'Impl', 'implements', name, '{']]
        raise "invalid mapper class #{relative(compiled)}" unless class_internal_name(compiled) == internal + 'Impl'
        { 'source' => relative(file), 'generated' => relative(generated), 'class' => relative(compiled),
          'sha256' => [file, generated, compiled].to_h { |entry| [relative(entry), Digest::SHA256.file(entry).hexdigest] } }
      end.compact
    end
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
    summaries['productionClasses'] = production_class_inventory(since)
    summaries['generatedMappers'] = generated_mapper_inventory
    summaries['generatedSources'] = generated_source_inventory(since)
    summaries['coverageArtifacts'] = coverage_artifact_inventory(since, summaries['productionClasses'], summaries['generatedMappers'])
    files = data['groups'].values.flatten.map { |entry| report(entry) }.uniq + jars(since) + summaries['productionClasses'].flat_map { |mod| mod.fetch('classes').map { |entry| entry.fetch('path') } } + summaries['generatedMappers'].flat_map { |entry| entry.fetch('sha256').keys } + summaries['generatedSources'].map { |entry| entry.fetch('path') }
    files += summaries['coverageArtifacts'].flat_map { |entry| entry.fetch('sha256').keys }
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

if $PROGRAM_NAME == __FILE__
begin
  command, root, *args = ARGV
  helper = ReactorEvidence.new(root || '')
  case command
  when 'compose-config'
    raise 'expected fixed config scope' unless args.size == 1
    exit helper.compose_config(args[0])
  when 'model-attest'
    raise 'expected ROOT only' unless args.empty?
    snapshot = helper.attest_full_environment
    puts Digest::SHA256.hexdigest(JSON.generate(snapshot))
  when 'model-execution'
    raise 'expected ROOT only' unless args.empty?
    snapshot = helper.attest_full_environment
    execution = snapshot.fetch('environment').fetch('execution')
    values = %w[HOME JAVA_HOME PATH].map { |key| execution.fetch(key) }
    raise 'invalid execution environment bytes' if values.any? { |value| value.match?(/[\r\n\x00]/) }
    puts values
  when 'model-collect'
    raise 'expected ROOT NEW_OUTPUT_DIRECTORY' unless args.size == 1
    collection = helper.collect_approved_model(output: args[0])
    File.open(args[0] + '/collection.json', File::WRONLY | File::CREAT | File::EXCL, 0600) { |file| file.write(JSON.generate(collection) + "\n") }
  when 'model-check'
    raise 'expected ROOT COLLECTION' unless args.size == 1
    helper.read_approved_collection(JSON.parse(File.binread(args[0])))
  when 'model-prepare'
    raise 'expected ROOT COLLECTION MANIFEST SUMMARY' unless args.size == 3
    helper.prepare_approved_model(*args)
  when 'model-verify'
    raise 'expected ROOT COLLECTION MANIFEST' unless args.size == 2
    helper.verify_approved_model(*args)
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
end
