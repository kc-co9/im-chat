#!/usr/bin/env bash
# 用途：在临时源码/XML/JAR/class header 中验证同轮证明、class 身份及摘要，拒绝缺失、旧产物、必需项跳过与摘要覆盖。
# 输入：无。输出：契约通过消息；仅创建自动清理的临时目录，不运行 Maven/容器。
# 依赖：Ruby 标准库；任一正反例不符时非零退出。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ruby -E UTF-8 - "$ROOT_DIR/scripts/lib/harness-reactor-evidence.rb" <<'RUBY'
# encoding: UTF-8
require 'tmpdir'
require 'fileutils'
require 'json'
helper = ARGV.fetch(0)
# 加载定义而不运行 CLI。
eval(File.read(helper).split("\nif $PROGRAM_NAME == __FILE__\nbegin\n  command, root").first, TOPLEVEL_BINDING, helper)
def assert_failure(label)
  begin
    yield
  rescue StandardError
    return
  end
  raise "expected rejection: #{label}"
end
Dir.mktmpdir('reactor-coverage-') do |root|
  evidence = ReactorEvidence.new(root)
  since = Time.now.to_f - 10
  base = root + '/module'
  FileUtils.mkdir_p(base + '/target/site/jacoco')
  FileUtils.mkdir_p(base + '/target/surefire-reports')
  File.write(base + '/target/surefire-reports/TEST-Sample.xml', '<testsuite><testcase name="works"/></testsuite>')
  File.write(base + '/target/jacoco.exec', 'fixture exec')
  ordinary = { 'path' => 'module/target/classes/com/co/kc/imchat/Sample.class', 'internalName' => 'com/co/kc/imchat/Sample', 'accessFlags' => 33, 'sha256' => 'ordinary' }
  synthetic = { 'path' => 'module/target/classes/com/co/kc/imchat/Sample$1.class', 'internalName' => 'com/co/kc/imchat/Sample$1', 'accessFlags' => 4128, 'sha256' => 'synthetic' }
  inventory = [{ 'pom' => 'module/pom.xml', 'classes' => [ordinary, synthetic] }]
  xml = '<report><package name="com/co/kc/imchat"><class name="com/co/kc/imchat/Sample"><counter type="INSTRUCTION" missed="2" covered="0"/></class></package></report>'
  report = base + '/target/site/jacoco/jacoco.xml'
  File.write(report, xml)
  summary = evidence.coverage_artifact_inventory(since, inventory, [])
  raise 'synthetic classification missing' unless summary.first.fetch('filteredClasses') == [synthetic.merge('reason' => 'jacoco-0.8.13-class-synthetic')]
  raise 'ordinary report missing' unless summary.first.fetch('classes').map { |entry| entry.fetch('internalName') } == [ordinary.fetch('internalName')]
  {
    'missing ordinary class' => '<report/>',
    'duplicate class' => xml.sub('</package>', '<class name="com/co/kc/imchat/Sample"/></package>'),
    'extra class' => xml.sub('</package>', '<class name="com/co/kc/imchat/Extra"/></package>'),
    'synthetic XML class' => xml.sub('</package>', '<class name="com/co/kc/imchat/Sample$1"/></package>'),
    'wrong package' => xml.sub('package name="com/co/kc/imchat"', 'package name="foreign"'),
    'negative counter' => xml.sub('missed="2"', 'missed="-1"'),
    'unknown counter' => xml.sub('INSTRUCTION', 'UNKNOWN'),
    'method negative counter' => xml.sub('</class>', '<method><counter type="INSTRUCTION" missed="-1" covered="0"/></method></class>'),
    'package duplicate counter' => xml.sub('</package>', '<counter type="CLASS" missed="0" covered="1"/><counter type="CLASS" missed="0" covered="1"/></package>'),
    'report unknown counter' => xml.sub('</report>', '<counter type="UNKNOWN" missed="0" covered="0"/></report>'),
    'duplicate counter' => xml.sub('</class>', '<counter type="INSTRUCTION" missed="0" covered="0"/></class>')
  }.each do |label, content|
    File.write(report, content)
    assert_failure(label) { evidence.coverage_artifact_inventory(since, inventory, []) }
  end
  File.write(report, xml.sub('<counter type="INSTRUCTION" missed="2" covered="0"/>', ''))
  evidence.coverage_artifact_inventory(since, inventory, [])
  File.write(report, xml)
  assert_failure('top-level generated synthetic') { evidence.coverage_artifact_inventory(since, inventory, [{ 'class' => synthetic.fetch('path') }]) }
  changed = Marshal.load(Marshal.dump(inventory))
  changed.first.fetch('classes').last['accessFlags'] = 32
  assert_failure('synthetic flag removed') { evidence.coverage_artifact_inventory(since, changed, []) }
  File.utime(Time.at(1), Time.at(1), report)
  assert_failure('old report') { evidence.coverage_artifact_inventory(since, inventory, []) }
  File.delete(report)
  assert_failure('missing report') { evidence.coverage_artifact_inventory(since, inventory, []) }
  File.delete(base + '/target/jacoco.exec')
  assert_failure('executed module missing pair') { evidence.coverage_artifact_inventory(since, inventory, []) }
  File.write(base + '/target/surefire-reports/TEST-Sample.xml', '<testsuite><testcase name="conditional"><skipped/></testcase></testsuite>')
  assert_failure('all-skipped fork missing pair') { evidence.coverage_artifact_inventory(since, inventory, []) }
  File.delete(base + '/target/surefire-reports/TEST-Sample.xml')
  raise 'no-test module fabricated coverage' unless evidence.coverage_artifact_inventory(since, inventory, []) == []
end
Dir.mktmpdir('reactor-output-clear-') do |root|
  evidence = ReactorEvidence.new(root)
  outputs = %w[classes generated-sources/annotations maven-status/maven-compiler-plugin site/jacoco]
  targets = outputs.map { |suffix| 'module/target/' + suffix } + ['module/target/jacoco.exec']
  outputs.each do |suffix|
    FileUtils.mkdir_p(root + '/module/target/' + suffix)
    File.write(root + '/module/target/' + suffix + '/old', 'old')
  end
  File.write(root + '/module/target/jacoco.exec', 'old')
  File.write(root + '/module/target/keep', 'keep')
  directories = targets.reject { |target| target.end_with?('jacoco.exec') }
  evidence.clear_targets(targets, directories)
  raise 'controlled output survived clear' if targets.any? { |target| File.exist?(root + '/' + target) }
  raise 'unselected output deleted' unless File.read(root + '/module/target/keep') == 'keep'
  FileUtils.mkdir_p(root + '/module/target/classes')
  File.write(root + '/module/target/classes/old', 'old')
  FileUtils.mkdir_p(root + '/module/target/site/jacoco')
  File.symlink(root + '/module/target/keep', root + '/module/target/site/jacoco/link')
  assert_failure('descendant link batch rejection') { evidence.clear_targets(targets, directories) }
  raise 'batch partially deleted before rejecting link' unless File.read(root + '/module/target/classes/old') == 'old'
  assert_failure('noncanonical deletion') { evidence.clear_targets(['module/target/../target/keep']) }
  assert_failure('target root deletion') { evidence.clear_targets(['module/target/']) }
  File.write(root + '/module/target/not-directory', 'keep')
  assert_failure('file parent batch rejection') { evidence.clear_targets(['module/target/classes', 'module/target/not-directory/child'], ['module/target/classes']) }
  raise 'batch partially deleted before rejecting parent' unless File.read(root + '/module/target/classes/old') == 'old'
  assert_failure('report directory rejection') { evidence.clear_targets(['module/target/classes']) }
  assert_failure('directory output file rejection') { evidence.clear_targets(['module/target/keep'], ['module/target/keep']) }
end
Dir.mktmpdir('reactor-evidence-') do |root|
  root = File.realpath(root)
  write = lambda do |file, content|
    FileUtils.mkdir_p(File.dirname(File.join(root, file)))
    File.write(File.join(root, file), content)
  end
  source = lambda do |mod, klass, tag = nil|
    write.call("#{mod}/src/test/java/example/#{klass}.java", "package example;\n#{tag ? '@Tag("' + tag + '")' : ''}\nclass #{klass} {\nString text = \"中文\";\n@Test void works() {}\n@ParameterizedTest void parameter(String value) {}\n}\n")
  end
  write.call('app/src/main/java/example/DemoApplication.java', '/* 中文启动入口 */ @SpringBootApplication public class DemoApplication {}')
  source.call('app', 'DemoApplicationTest', 'startup-smoke')
  app = 'app/src/test/java/example/DemoApplicationTest.java'
  write.call(app, File.read(File.join(root, app)).sub('class Demo', '@SpringBootTest(classes = DemoApplication.class, properties = {"spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false"}) class Demo'))
  source.call('im-test/im-architecture-test', 'BoundaryTest')
  FileUtils.mkdir_p(File.join(root, 'im-test/im-architecture-test/src/test/java/com'))
  FileUtils.mv(File.join(root, 'im-test/im-architecture-test/src/test/java/example/BoundaryTest.java'), File.join(root, 'im-test/im-architecture-test/src/test/java/com/BoundaryTest.java'))
  conditional_source = 'im-test/im-architecture-test/src/test/java/com/OptionalDatabaseTest.java'
  conditional_annotation = '@EnabledIfSystemProperty(named = "fixture.database", matches = "true")'
  write.call(conditional_source, "package example;\n/* @Disabled */\n#{conditional_annotation}\nclass OptionalDatabaseTest {\n@Test void works() {}\n@Test void second() {}\n}\n")
  source.call('behavior', 'BehaviorTest', 'realtime-behavior')
  source.call('e2e', 'RealtimeTest', 'e2e')
  (ReactorEvidence::HA_EXTRA + ['ScenarioTest']).each { |klass| source.call('ha', klass) }
  write.call('im-test/im-routing-ha/src/test/resources/routing-ha-scenarios.tsv', (1..25).map { |i| "RH-#{i}\t中文场景\tunit-test\tScenarioTest#parameter\tassert" }.join("\n"))
  evidence = ReactorEvidence.new(root)
  locale_manifest = root + '/startup-locale.json'
  locale_stdout, locale_stderr, locale_status = Open3.capture3({ 'HOME' => ENV.fetch('HOME'), 'PATH' => '/usr/bin:/bin', 'LC_ALL' => 'C' }, RbConfig.ruby, helper, 'startup-manifest', root, locale_manifest, unsetenv_others: true)
  raise "C locale startup reader failed: #{locale_stdout}#{locale_stderr}" unless locale_status.success?
  raise 'C locale startup selection changed' unless JSON.parse(File.read(locale_manifest)).fetch('applications') == evidence.startup
  # 有限正例按设计手写；不从 reader 常量或历史模型生成 oracle／期望。
  fixture_plugin = lambda do |artifact, version, configuration, executions, group = nil, dependencies = ''|
    '<plugin>' + (group ? '<groupId>' + group + '</groupId>' : '') + '<artifactId>' + artifact + '</artifactId><version>' + version + '</version>' + (configuration ? '<configuration>' + configuration + '</configuration>' : '') + '<executions>' + executions.map { |id, phase, goal, config| '<execution>' + (id.empty? ? '' : '<id>' + id + '</id>') + (phase.empty? ? '' : '<phase>' + phase + '</phase>') + '<goals><goal>' + goal + '</goal></goals>' + (config ? '<configuration>' + config + '</configuration>' : '') + '</execution>' }.join + '</executions>' + dependencies + '</plugin>'
  end
  processor_config = '<release>21</release><parameters>true</parameters><annotationProcessorPaths><path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>1.18.42</version></path><path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>1.5.5.Final</version></path></annotationProcessorPaths>'
  test_config = '<argLine>@{argLine} -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xshare:off -javaagent:${settings.localRepository}/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar</argLine>'
  resource_config = '<propertiesEncoding>UTF-8</propertiesEncoding><delimiters><delimiter>@</delimiter></delimiters><useDefaultDelimiters>false</useDefaultDelimiters>'
  jar_config = '<archive><manifest><mainClass>${start-class}</mainClass><addDefaultImplementationEntries>true</addDefaultImplementationEntries></manifest></archive>'
  common_plugins = lambda do |base, packaging|
    enforcer_rules = '<rules><requireJavaVersion><version>[21,22)</version></requireJavaVersion><requireMavenVersion><version>[3.8.4,4)</version></requireMavenVersion></rules>'
    site_config = '<outputDirectory>' + base + '/target/site</outputDirectory><reportPlugins><reportPlugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-project-info-reports-plugin</artifactId></reportPlugin></reportPlugins>'
    plugins = fixture_plugin.call('maven-enforcer-plugin', '3.5.0', nil, [['enforce-build-environment', '', 'enforce', enforcer_rules]])
    plugins += fixture_plugin.call('jacoco-maven-plugin', '0.8.13', nil, [['', '', 'prepare-agent', '<includes><include>com.co.kc.imchat.*</include></includes>'], ['report', 'verify', 'report', nil]], 'org.jacoco')
    plugins += fixture_plugin.call('maven-clean-plugin', '3.4.1', nil, [['default-clean', 'clean', 'clean', nil]])
    plugins += fixture_plugin.call('maven-install-plugin', '3.1.4', nil, [['default-install', 'install', 'install', nil]])
    plugins += fixture_plugin.call('maven-deploy-plugin', '3.1.4', nil, [['default-deploy', 'deploy', 'deploy', nil]])
    plugins += fixture_plugin.call('maven-site-plugin', '3.3', site_config, [['default-site', 'site', 'site', site_config], ['default-deploy', 'site-deploy', 'deploy', site_config]])
    if packaging == 'jar'
      plugins += fixture_plugin.call('maven-resources-plugin', '3.3.1', resource_config, [['default-testResources', 'process-test-resources', 'testResources', resource_config], ['default-resources', 'process-resources', 'resources', resource_config]])
      plugins += fixture_plugin.call('maven-compiler-plugin', '3.14.1', processor_config, [['default-compile', 'compile', 'compile', processor_config], ['default-testCompile', 'test-compile', 'testCompile', processor_config]])
      test_cfg = test_config
      if base.end_with?('/im-test/im-architecture-test')
        test_cfg += '<additionalClasspathElements><additionalClasspathElement>' + base.sub('/im-test/im-architecture-test', '/im-management/im-admin') + '/target/classes</additionalClasspathElement><additionalClasspathElement>' + base.sub('/im-test/im-architecture-test', '/im-management/im-audit/im-audit-server') + '/target/classes</additionalClasspathElement></additionalClasspathElements>'
      end
      plugins += fixture_plugin.call('maven-surefire-plugin', '3.5.5', test_cfg, [['default-test', 'test', 'test', test_cfg]])
      plugins += fixture_plugin.call('maven-jar-plugin', '3.4.2', jar_config, [['default-jar', 'package', 'jar', jar_config]])
    end
    plugins
  end
  # 两个模块的独立小 oracle；不是正式53集合的简化替代。
  model_oracle = [
    { 'gav' => 'example:parent:1', 'pom' => root + '/pom.xml', 'basedir' => root, 'packaging' => 'pom', 'modules' => ['child'] },
    { 'gav' => 'example:child:1', 'pom' => root + '/child/pom.xml', 'basedir' => root + '/child', 'packaging' => 'jar', 'modules' => [] }
  ]
  model_context = { 'verifyArgv' => ['mvn', '-q', 'verify'], 'sourceReviewed' => true, 'localRepository' => root + '/repository' }
  model_project = lambda do |id, base, packaging, modules, extra = ''|
    '<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>example</groupId><artifactId>' + id + '</artifactId><version>1</version><packaging>' + packaging + '</packaging><modules>' + modules.map { |m| '<module>' + m + '</module>' }.join + '</modules><build><sourceDirectory>' + base + '/src/main/java</sourceDirectory><testSourceDirectory>' + base + '/src/test/java</testSourceDirectory><directory>' + base + '/target</directory><outputDirectory>' + base + '/target/classes</outputDirectory><testOutputDirectory>' + base + '/target/test-classes</testOutputDirectory><plugins>' + common_plugins.call(base, packaging) + extra + '</plugins></build></project>'
  end
  parent_model = model_project.call('parent', root, 'pom', ['child'])
  child_model = model_project.call('child', root + '/child', 'jar', [])
  valid_model = '<projects>' + parent_model + child_model + '</projects>'
  model_summary = evidence.read_effective_model(valid_model, model_oracle, model_context, contract: :local)
  # Maven 序列化省略默认 jar；reader 比较语义值而非要求字段必然出现。
  implicit_jar_model = '<projects>' + parent_model + child_model.sub('<packaging>jar</packaging>', '') + '</projects>'
  implicit_jar_summary = evidence.read_effective_model(implicit_jar_model, model_oracle, model_context, contract: :local)
  raise 'implicit jar differs from explicit jar' unless implicit_jar_summary == model_summary
  {
    'missing pom is not inherited' => '<projects>' + parent_model.sub('<packaging>pom</packaging>', '') + child_model + '</projects>',
    'empty packaging' => valid_model.sub('<packaging>jar</packaging>', '<packaging/>'),
    'duplicate packaging' => valid_model.sub('<packaging>jar</packaging>', '<packaging>jar</packaging><packaging>jar</packaging>'),
    'wrong packaging' => valid_model.sub('<packaging>jar</packaging>', '<packaging>war</packaging>')
  }.each do |label, xml|
    assert_failure(label) { evidence.read_effective_model(xml, model_oracle, model_context, contract: :local) }
  end
  formal_oracle = [
    { 'gav' => 'com.co.kc.im:im-chat:0.0.1-SNAPSHOT', 'pom' => root + '/pom.xml', 'basedir' => root, 'packaging' => 'pom', 'modules' => ['im-common', 'im-plugin', 'im-gateway', 'im-broker', 'im-service', 'im-management', 'im-test'] },
    { 'gav' => 'com.co.kc.im:im-common:0.0.1-SNAPSHOT', 'pom' => root + '/im-common/pom.xml', 'basedir' => root + "/im-common", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-plugin:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/pom.xml', 'basedir' => root + "/im-plugin", 'packaging' => 'pom', 'modules' => ['im-cache', 'im-identity', 'im-excel', 'im-metrics', 'im-tracing', 'im-lock', 'im-datasource', 'im-session', 'im-web', 'im-dubbo', 'im-bolt', 'im-mq-kafka', 'im-gossip', 'im-raft', 'im-nacos'] },
    { 'gav' => 'com.co.kc.im:im-cache:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-cache/pom.xml', 'basedir' => root + "/im-plugin/im-cache", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-identity:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-identity/pom.xml', 'basedir' => root + "/im-plugin/im-identity", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-excel:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-excel/pom.xml', 'basedir' => root + "/im-plugin/im-excel", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-metrics:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-metrics/pom.xml', 'basedir' => root + "/im-plugin/im-metrics", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-tracing:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-tracing/pom.xml', 'basedir' => root + "/im-plugin/im-tracing", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-lock:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-lock/pom.xml', 'basedir' => root + "/im-plugin/im-lock", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-datasource:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-datasource/pom.xml', 'basedir' => root + "/im-plugin/im-datasource", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-session:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-session/pom.xml', 'basedir' => root + "/im-plugin/im-session", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-web:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-web/pom.xml', 'basedir' => root + "/im-plugin/im-web", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-dubbo:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-dubbo/pom.xml', 'basedir' => root + "/im-plugin/im-dubbo", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-bolt:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-bolt/pom.xml', 'basedir' => root + "/im-plugin/im-bolt", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-mq-kafka:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-mq-kafka/pom.xml', 'basedir' => root + "/im-plugin/im-mq-kafka", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-gossip:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-gossip/pom.xml', 'basedir' => root + "/im-plugin/im-gossip", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-raft:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-raft/pom.xml', 'basedir' => root + "/im-plugin/im-raft", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-nacos:0.0.1-SNAPSHOT', 'pom' => root + '/im-plugin/im-nacos/pom.xml', 'basedir' => root + "/im-plugin/im-nacos", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-gateway:0.0.1-SNAPSHOT', 'pom' => root + '/im-gateway/pom.xml', 'basedir' => root + "/im-gateway", 'packaging' => 'pom', 'modules' => ['im-http-gateway', 'im-ws-gateway'] },
    { 'gav' => 'com.co.kc.im:im-http-gateway:0.0.1-SNAPSHOT', 'pom' => root + '/im-gateway/im-http-gateway/pom.xml', 'basedir' => root + "/im-gateway/im-http-gateway", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ws-gateway:0.0.1-SNAPSHOT', 'pom' => root + '/im-gateway/im-ws-gateway/pom.xml', 'basedir' => root + "/im-gateway/im-ws-gateway", 'packaging' => 'pom', 'modules' => ['im-ws-gateway-sdk', 'im-ws-gateway-server'] },
    { 'gav' => 'com.co.kc.im:im-ws-gateway-sdk:0.0.1-SNAPSHOT', 'pom' => root + '/im-gateway/im-ws-gateway/im-ws-gateway-sdk/pom.xml', 'basedir' => root + "/im-gateway/im-ws-gateway/im-ws-gateway-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ws-gateway-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-gateway/im-ws-gateway/im-ws-gateway-server/pom.xml', 'basedir' => root + "/im-gateway/im-ws-gateway/im-ws-gateway-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-broker:0.0.1-SNAPSHOT', 'pom' => root + '/im-broker/pom.xml', 'basedir' => root + "/im-broker", 'packaging' => 'pom', 'modules' => ['im-broker-sdk', 'im-broker-server'] },
    { 'gav' => 'com.co.kc.im:im-broker-sdk:0.0.1-SNAPSHOT', 'pom' => root + '/im-broker/im-broker-sdk/pom.xml', 'basedir' => root + "/im-broker/im-broker-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-broker-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-broker/im-broker-server/pom.xml', 'basedir' => root + "/im-broker/im-broker-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-service:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/pom.xml', 'basedir' => root + "/im-service", 'packaging' => 'pom', 'modules' => ['im-account', 'im-social', 'im-message'] },
    { 'gav' => 'com.co.kc.im:im-account:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-account/pom.xml', 'basedir' => root + "/im-service/im-account", 'packaging' => 'pom', 'modules' => ['im-account-facade', 'im-account-admin-facade', 'im-account-server'] },
    { 'gav' => 'com.co.kc.im:im-account-facade:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-account/im-account-facade/pom.xml', 'basedir' => root + "/im-service/im-account/im-account-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-account-admin-facade:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-account/im-account-admin-facade/pom.xml', 'basedir' => root + "/im-service/im-account/im-account-admin-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-account-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-account/im-account-server/pom.xml', 'basedir' => root + "/im-service/im-account/im-account-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-social:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-social/pom.xml', 'basedir' => root + "/im-service/im-social", 'packaging' => 'pom', 'modules' => ['im-social-facade', 'im-social-server'] },
    { 'gav' => 'com.co.kc.im:im-social-facade:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-social/im-social-facade/pom.xml', 'basedir' => root + "/im-service/im-social/im-social-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-social-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-social/im-social-server/pom.xml', 'basedir' => root + "/im-service/im-social/im-social-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-message:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-message/pom.xml', 'basedir' => root + "/im-service/im-message", 'packaging' => 'pom', 'modules' => ['im-message-facade', 'im-message-server'] },
    { 'gav' => 'com.co.kc.im:im-message-facade:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-message/im-message-facade/pom.xml', 'basedir' => root + "/im-service/im-message/im-message-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-message-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-service/im-message/im-message-server/pom.xml', 'basedir' => root + "/im-service/im-message/im-message-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-management:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/pom.xml', 'basedir' => root + "/im-management", 'packaging' => 'pom', 'modules' => ['im-iam', 'im-admin', 'im-ops', 'im-audit'] },
    { 'gav' => 'com.co.kc.im:im-iam:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-iam/pom.xml', 'basedir' => root + "/im-management/im-iam", 'packaging' => 'pom', 'modules' => ['im-iam-server', 'im-iam-sdk'] },
    { 'gav' => 'com.co.kc.im:im-iam-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-iam/im-iam-server/pom.xml', 'basedir' => root + "/im-management/im-iam/im-iam-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-iam-sdk:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-iam/im-iam-sdk/pom.xml', 'basedir' => root + "/im-management/im-iam/im-iam-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-admin:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-admin/pom.xml', 'basedir' => root + "/im-management/im-admin", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ops:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-ops/pom.xml', 'basedir' => root + "/im-management/im-ops", 'packaging' => 'pom', 'modules' => ['im-ops-server', 'im-ops-cli'] },
    { 'gav' => 'com.co.kc.im:im-ops-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-ops/im-ops-server/pom.xml', 'basedir' => root + "/im-management/im-ops/im-ops-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ops-cli:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-ops/im-ops-cli/pom.xml', 'basedir' => root + "/im-management/im-ops/im-ops-cli", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-audit:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-audit/pom.xml', 'basedir' => root + "/im-management/im-audit", 'packaging' => 'pom', 'modules' => ['im-audit-sdk', 'im-audit-server'] },
    { 'gav' => 'com.co.kc.im:im-audit-sdk:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-audit/im-audit-sdk/pom.xml', 'basedir' => root + "/im-management/im-audit/im-audit-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-audit-server:0.0.1-SNAPSHOT', 'pom' => root + '/im-management/im-audit/im-audit-server/pom.xml', 'basedir' => root + "/im-management/im-audit/im-audit-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-test:0.0.1-SNAPSHOT', 'pom' => root + '/im-test/pom.xml', 'basedir' => root + "/im-test", 'packaging' => 'pom', 'modules' => ['im-architecture-test', 'im-e2e-test', 'im-load-test', 'im-routing-ha'] },
    { 'gav' => 'com.co.kc.im:im-architecture-test:0.0.1-SNAPSHOT', 'pom' => root + '/im-test/im-architecture-test/pom.xml', 'basedir' => root + "/im-test/im-architecture-test", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-e2e-test:0.0.1-SNAPSHOT', 'pom' => root + '/im-test/im-e2e-test/pom.xml', 'basedir' => root + "/im-test/im-e2e-test", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-load-test:0.0.1-SNAPSHOT', 'pom' => root + '/im-test/im-load-test/pom.xml', 'basedir' => root + "/im-test/im-load-test", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-routing-ha:0.0.1-SNAPSHOT', 'pom' => root + '/im-test/im-routing-ha/pom.xml', 'basedir' => root + "/im-test/im-routing-ha", 'packaging' => 'jar', 'modules' => [] },
  ].sort_by { |entry| entry.fetch('gav') }
  formal_oracle.each { |entry| FileUtils.mkdir_p(entry.fetch('basedir')) }
  # 独立原始 POM literal：字段源为已审 raw 事实，不引用 reader 常量或 effective/oracle。
  raw_specs = [
    ['pom.xml', 'im-chat', 'spring-boot-starter-parent', 'pom', ['im-common', 'im-plugin', 'im-gateway', 'im-broker', 'im-service', 'im-management', 'im-test']],
    ['im-common/pom.xml', 'im-common', 'im-chat', nil, []],
    ['im-plugin/pom.xml', 'im-plugin', 'im-chat', 'pom', ['im-cache', 'im-identity', 'im-excel', 'im-metrics', 'im-tracing', 'im-lock', 'im-datasource', 'im-session', 'im-web', 'im-dubbo', 'im-bolt', 'im-mq-kafka', 'im-gossip', 'im-raft', 'im-nacos']],
    ['im-plugin/im-cache/pom.xml', 'im-cache', 'im-plugin', nil, []],
    ['im-plugin/im-identity/pom.xml', 'im-identity', 'im-plugin', nil, []],
    ['im-plugin/im-excel/pom.xml', 'im-excel', 'im-plugin', nil, []],
    ['im-plugin/im-metrics/pom.xml', 'im-metrics', 'im-plugin', nil, []],
    ['im-plugin/im-tracing/pom.xml', 'im-tracing', 'im-plugin', nil, []],
    ['im-plugin/im-lock/pom.xml', 'im-lock', 'im-plugin', nil, []],
    ['im-plugin/im-datasource/pom.xml', 'im-datasource', 'im-plugin', nil, []],
    ['im-plugin/im-session/pom.xml', 'im-session', 'im-plugin', nil, []],
    ['im-plugin/im-web/pom.xml', 'im-web', 'im-plugin', nil, []],
    ['im-plugin/im-dubbo/pom.xml', 'im-dubbo', 'im-plugin', nil, []],
    ['im-plugin/im-bolt/pom.xml', 'im-bolt', 'im-plugin', nil, []],
    ['im-plugin/im-mq-kafka/pom.xml', 'im-mq-kafka', 'im-plugin', nil, []],
    ['im-plugin/im-gossip/pom.xml', 'im-gossip', 'im-plugin', nil, []],
    ['im-plugin/im-raft/pom.xml', 'im-raft', 'im-plugin', nil, []],
    ['im-plugin/im-nacos/pom.xml', 'im-nacos', 'im-plugin', nil, []],
    ['im-gateway/pom.xml', 'im-gateway', 'im-chat', 'pom', ['im-http-gateway', 'im-ws-gateway']],
    ['im-gateway/im-http-gateway/pom.xml', 'im-http-gateway', 'im-gateway', nil, []],
    ['im-gateway/im-ws-gateway/pom.xml', 'im-ws-gateway', 'im-gateway', 'pom', ['im-ws-gateway-sdk', 'im-ws-gateway-server']],
    ['im-gateway/im-ws-gateway/im-ws-gateway-sdk/pom.xml', 'im-ws-gateway-sdk', 'im-ws-gateway', nil, []],
    ['im-gateway/im-ws-gateway/im-ws-gateway-server/pom.xml', 'im-ws-gateway-server', 'im-ws-gateway', nil, []],
    ['im-broker/pom.xml', 'im-broker', 'im-chat', 'pom', ['im-broker-sdk', 'im-broker-server']],
    ['im-broker/im-broker-sdk/pom.xml', 'im-broker-sdk', 'im-broker', nil, []],
    ['im-broker/im-broker-server/pom.xml', 'im-broker-server', 'im-broker', nil, []],
    ['im-service/pom.xml', 'im-service', 'im-chat', 'pom', ['im-account', 'im-social', 'im-message']],
    ['im-service/im-account/pom.xml', 'im-account', 'im-service', 'pom', ['im-account-facade', 'im-account-admin-facade', 'im-account-server']],
    ['im-service/im-account/im-account-facade/pom.xml', 'im-account-facade', 'im-account', nil, []],
    ['im-service/im-account/im-account-admin-facade/pom.xml', 'im-account-admin-facade', 'im-account', nil, []],
    ['im-service/im-account/im-account-server/pom.xml', 'im-account-server', 'im-account', nil, []],
    ['im-service/im-social/pom.xml', 'im-social', 'im-service', 'pom', ['im-social-facade', 'im-social-server']],
    ['im-service/im-social/im-social-facade/pom.xml', 'im-social-facade', 'im-social', nil, []],
    ['im-service/im-social/im-social-server/pom.xml', 'im-social-server', 'im-social', nil, []],
    ['im-service/im-message/pom.xml', 'im-message', 'im-service', 'pom', ['im-message-facade', 'im-message-server']],
    ['im-service/im-message/im-message-facade/pom.xml', 'im-message-facade', 'im-message', nil, []],
    ['im-service/im-message/im-message-server/pom.xml', 'im-message-server', 'im-message', nil, []],
    ['im-management/pom.xml', 'im-management', 'im-chat', 'pom', ['im-iam', 'im-admin', 'im-ops', 'im-audit']],
    ['im-management/im-iam/pom.xml', 'im-iam', 'im-management', 'pom', ['im-iam-server', 'im-iam-sdk']],
    ['im-management/im-iam/im-iam-server/pom.xml', 'im-iam-server', 'im-iam', nil, []],
    ['im-management/im-iam/im-iam-sdk/pom.xml', 'im-iam-sdk', 'im-iam', nil, []],
    ['im-management/im-admin/pom.xml', 'im-admin', 'im-management', nil, []],
    ['im-management/im-ops/pom.xml', 'im-ops', 'im-management', 'pom', ['im-ops-server', 'im-ops-cli']],
    ['im-management/im-ops/im-ops-server/pom.xml', 'im-ops-server', 'im-ops', nil, []],
    ['im-management/im-ops/im-ops-cli/pom.xml', 'im-ops-cli', 'im-ops', nil, []],
    ['im-management/im-audit/pom.xml', 'im-audit', 'im-management', 'pom', ['im-audit-sdk', 'im-audit-server']],
    ['im-management/im-audit/im-audit-sdk/pom.xml', 'im-audit-sdk', 'im-audit', nil, []],
    ['im-management/im-audit/im-audit-server/pom.xml', 'im-audit-server', 'im-audit', nil, []],
    ['im-test/pom.xml', 'im-test', 'im-chat', 'pom', ['im-architecture-test', 'im-e2e-test', 'im-load-test', 'im-routing-ha']],
    ['im-test/im-architecture-test/pom.xml', 'im-architecture-test', 'im-test', nil, []],
    ['im-test/im-e2e-test/pom.xml', 'im-e2e-test', 'im-test', nil, []],
    ['im-test/im-load-test/pom.xml', 'im-load-test', 'im-test', nil, []],
    ['im-test/im-routing-ha/pom.xml', 'im-routing-ha', 'im-test', nil, []],
  ]
  raw_specs.each do |pom, artifact, parent_artifact, packaging, modules|
    parent = '<parent><groupId>' + (pom == 'pom.xml' ? 'org.springframework.boot' : 'com.co.kc.im') + '</groupId><artifactId>' + parent_artifact + '</artifactId><version>' + (pom == 'pom.xml' ? '3.5.14' : '0.0.1-SNAPSHOT') + '</version>' + (pom == 'pom.xml' ? '<relativePath/>' : '') + '</parent>'
    identity = (pom == 'pom.xml' ? '<groupId>com.co.kc.im</groupId><version>0.0.1-SNAPSHOT</version>' : '') + '<artifactId>' + artifact + '</artifactId>'
    write.call(pom, '<project xmlns="http://maven.apache.org/POM/4.0.0">' + parent + identity + (packaging ? '<packaging>' + packaging + '</packaging>' : '') + '<modules>' + modules.map { |name| '<module>' + name + '</module>' }.join + '</modules></project>')
  end
  raw_oracle = evidence.read_raw_pom_oracle
  raise 'raw oracle independent facts' unless raw_oracle.map { |entry| entry.reject { |key, _| %w[sha256 groupSource versionSource].include?(key) } } == formal_oracle
  formal_oracle.each { |entry| entry['sha256'] = raw_oracle.find { |raw| raw.fetch('pom') == entry.fetch('pom') }.fetch('sha256') }
  # 独立冻结的 effective XML 输入；不得从 formal_oracle 或 reader 常量生成。
  formal_xml_specs = [
    { 'gav' => 'com.co.kc.im:im-chat:0.0.1-SNAPSHOT', 'basedir' => root, 'packaging' => 'pom', 'modules' => ['im-common', 'im-plugin', 'im-gateway', 'im-broker', 'im-service', 'im-management', 'im-test'] },
    { 'gav' => 'com.co.kc.im:im-common:0.0.1-SNAPSHOT', 'basedir' => root + "/im-common", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-plugin:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin", 'packaging' => 'pom', 'modules' => ['im-cache', 'im-identity', 'im-excel', 'im-metrics', 'im-tracing', 'im-lock', 'im-datasource', 'im-session', 'im-web', 'im-dubbo', 'im-bolt', 'im-mq-kafka', 'im-gossip', 'im-raft', 'im-nacos'] },
    { 'gav' => 'com.co.kc.im:im-cache:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-cache", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-identity:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-identity", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-excel:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-excel", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-metrics:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-metrics", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-tracing:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-tracing", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-lock:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-lock", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-datasource:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-datasource", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-session:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-session", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-web:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-web", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-dubbo:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-dubbo", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-bolt:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-bolt", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-mq-kafka:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-mq-kafka", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-gossip:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-gossip", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-raft:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-raft", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-nacos:0.0.1-SNAPSHOT', 'basedir' => root + "/im-plugin/im-nacos", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-gateway:0.0.1-SNAPSHOT', 'basedir' => root + "/im-gateway", 'packaging' => 'pom', 'modules' => ['im-http-gateway', 'im-ws-gateway'] },
    { 'gav' => 'com.co.kc.im:im-http-gateway:0.0.1-SNAPSHOT', 'basedir' => root + "/im-gateway/im-http-gateway", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ws-gateway:0.0.1-SNAPSHOT', 'basedir' => root + "/im-gateway/im-ws-gateway", 'packaging' => 'pom', 'modules' => ['im-ws-gateway-sdk', 'im-ws-gateway-server'] },
    { 'gav' => 'com.co.kc.im:im-ws-gateway-sdk:0.0.1-SNAPSHOT', 'basedir' => root + "/im-gateway/im-ws-gateway/im-ws-gateway-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ws-gateway-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-gateway/im-ws-gateway/im-ws-gateway-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-broker:0.0.1-SNAPSHOT', 'basedir' => root + "/im-broker", 'packaging' => 'pom', 'modules' => ['im-broker-sdk', 'im-broker-server'] },
    { 'gav' => 'com.co.kc.im:im-broker-sdk:0.0.1-SNAPSHOT', 'basedir' => root + "/im-broker/im-broker-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-broker-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-broker/im-broker-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-service:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service", 'packaging' => 'pom', 'modules' => ['im-account', 'im-social', 'im-message'] },
    { 'gav' => 'com.co.kc.im:im-account:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-account", 'packaging' => 'pom', 'modules' => ['im-account-facade', 'im-account-admin-facade', 'im-account-server'] },
    { 'gav' => 'com.co.kc.im:im-account-facade:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-account/im-account-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-account-admin-facade:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-account/im-account-admin-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-account-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-account/im-account-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-social:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-social", 'packaging' => 'pom', 'modules' => ['im-social-facade', 'im-social-server'] },
    { 'gav' => 'com.co.kc.im:im-social-facade:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-social/im-social-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-social-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-social/im-social-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-message:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-message", 'packaging' => 'pom', 'modules' => ['im-message-facade', 'im-message-server'] },
    { 'gav' => 'com.co.kc.im:im-message-facade:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-message/im-message-facade", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-message-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-service/im-message/im-message-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-management:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management", 'packaging' => 'pom', 'modules' => ['im-iam', 'im-admin', 'im-ops', 'im-audit'] },
    { 'gav' => 'com.co.kc.im:im-iam:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-iam", 'packaging' => 'pom', 'modules' => ['im-iam-server', 'im-iam-sdk'] },
    { 'gav' => 'com.co.kc.im:im-iam-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-iam/im-iam-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-iam-sdk:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-iam/im-iam-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-admin:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-admin", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ops:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-ops", 'packaging' => 'pom', 'modules' => ['im-ops-server', 'im-ops-cli'] },
    { 'gav' => 'com.co.kc.im:im-ops-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-ops/im-ops-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-ops-cli:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-ops/im-ops-cli", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-audit:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-audit", 'packaging' => 'pom', 'modules' => ['im-audit-sdk', 'im-audit-server'] },
    { 'gav' => 'com.co.kc.im:im-audit-sdk:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-audit/im-audit-sdk", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-audit-server:0.0.1-SNAPSHOT', 'basedir' => root + "/im-management/im-audit/im-audit-server", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-test:0.0.1-SNAPSHOT', 'basedir' => root + "/im-test", 'packaging' => 'pom', 'modules' => ['im-architecture-test', 'im-e2e-test', 'im-load-test', 'im-routing-ha'] },
    { 'gav' => 'com.co.kc.im:im-architecture-test:0.0.1-SNAPSHOT', 'basedir' => root + "/im-test/im-architecture-test", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-e2e-test:0.0.1-SNAPSHOT', 'basedir' => root + "/im-test/im-e2e-test", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-load-test:0.0.1-SNAPSHOT', 'basedir' => root + "/im-test/im-load-test", 'packaging' => 'jar', 'modules' => [] },
    { 'gav' => 'com.co.kc.im:im-routing-ha:0.0.1-SNAPSHOT', 'basedir' => root + "/im-test/im-routing-ha", 'packaging' => 'jar', 'modules' => [] },
  ]
  formal_projects = formal_xml_specs.map do |entry|
    group, id, version = entry.fetch('gav').split(':')
    model_project.call(id, entry.fetch('basedir'), entry.fetch('packaging'), entry.fetch('modules')).sub('<groupId>example</groupId>', '<groupId>' + group + '</groupId>').sub('<version>1</version>', '<version>' + version + '</version>')
  end
  formal_xml = '<projects>' + formal_projects.join + '</projects>'
  raise 'independent effective XML fixture count' unless formal_xml_specs.size == 53
    replaced_xml = formal_projects.map.with_index { |project, i| i == 0 ? project.sub('com.co.kc.im', 'foreign.co.kc.im') : project }.join
  assert_failure('formal replaced XML identity') { evidence.read_effective_model('<projects>' + replaced_xml + '</projects>', formal_oracle, model_context, contract: :formal) }
  formal_summary = evidence.read_effective_model(formal_xml, formal_oracle, model_context, contract: :formal)
  # Architecture 所属 POM 的有序 Admin/Audit 合同；分别保护插件与 execution，拒绝旧值和结构漂移。
  %w[configuration executions/execution/configuration].each do |config_path|
    %w[stale missing extra reversed duplicate].each do |mutation|
      document = REXML::Document.new(formal_xml)
      project = document.root.get_elements('project').find { |entry| entry.elements['artifactId'].text == 'im-architecture-test' }
      plugin = project.get_elements('build/plugins/plugin').find { |entry| entry.elements['artifactId'].text == 'maven-surefire-plugin' }
      paths = plugin.elements[config_path + '/additionalClasspathElements']
      entries = paths.get_elements('additionalClasspathElement')
      case mutation
      when 'stale'
        entries[0].text = root + '/im-broker/im-broker-server/target/classes'
        entries[1].text = root + '/im-gateway/im-ws-gateway/im-ws-gateway-server/target/classes'
      when 'missing' then paths.delete_element(entries.last)
      when 'extra' then paths.add_element('additionalClasspathElement').text = root + '/foreign/target/classes'
      when 'reversed'
        first, second = entries.map(&:text)
        entries[0].text, entries[1].text = second, first
      when 'duplicate' then paths.add_element('additionalClasspathElement').text = entries.first.text
      end
      begin
        evidence.read_effective_model(document.to_s, formal_oracle, model_context, contract: :formal)
      rescue StandardError => error
        expected_error = (config_path == 'configuration' ? 'maven-surefire-plugin: ' : '') + 'effective configuration differs from finite contract'
        raise "wrong Architecture rejection: #{error.message}" unless error.message == expected_error
        next
      end
      raise "Architecture classpath accepted: #{config_path} #{mutation}"
    end
  end
  changed_oracle = Marshal.load(Marshal.dump(formal_oracle))
  changed_oracle.find { |entry| entry.fetch('gav') == 'com.co.kc.im:im-common:0.0.1-SNAPSHOT' }['packaging'] = 'pom'
  changed_xml = '<projects>' + formal_projects.map { |project| project.include?('<artifactId>im-common</artifactId>') ? project.sub('<packaging>jar</packaging>', '<packaging>pom</packaging>') : project }.join + '</projects>'
  assert_failure('joint caller oracle and XML packaging substitution') { evidence.read_effective_model(changed_xml, changed_oracle, model_context, contract: :formal) }
  # 消费时再次读取 raw bytes；摘要仅比较实际重算结果，不接受来源布尔值。
  raw_root_bytes = File.binread(root + '/pom.xml')
  raw_child_bytes = File.binread(root + '/im-common/pom.xml')
  mutations = {
    'root namespace' => raw_root_bytes.sub('http://maven.apache.org/POM/4.0.0', 'urn:foreign'),
    'multiple roots' => raw_root_bytes + '<other/>',
    'duplicate parent' => raw_root_bytes.sub('</project>', raw_root_bytes[/<parent>.*?<\/parent>/] + '</project>'),
    'parent attributes' => raw_root_bytes.sub('<parent>', '<parent extra="x">'),
    'parent text' => raw_root_bytes.sub('<parent>', '<parent>unexpected'),
    'parent unknown child' => raw_root_bytes.sub('</parent>', '<unknown/></parent>'),
    'parent namespace child' => raw_root_bytes.sub('<groupId>org.springframework.boot', '<groupId xmlns="urn:foreign">org.springframework.boot'),
    'duplicate parent field' => raw_root_bytes.sub('</parent>', '<version>3.5.14</version></parent>'),
    'missing parent field' => raw_root_bytes.sub('<version>3.5.14</version>', ''),
    'parent scalar attribute' => raw_root_bytes.sub('<version>3.5.14', '<version extra="x">3.5.14'),
    'parent scalar nested' => raw_root_bytes.sub('<version>3.5.14', '<version><nested/>3.5.14'),
    'root relativePath missing' => raw_root_bytes.sub('<relativePath/>', ''),
    'root relativePath whitespace' => raw_root_bytes.sub('<relativePath/>', '<relativePath> </relativePath>'),
    'root external parent' => raw_root_bytes.sub('spring-boot-starter-parent', 'foreign-parent'),
    'duplicate modules' => raw_root_bytes.sub('</project>', '<modules/></project>'),
    'module container attribute' => raw_root_bytes.sub('<modules>', '<modules extra="x">'),
    'module container text' => raw_root_bytes.sub('<modules>', '<modules>unexpected'),
    'module unknown child' => raw_root_bytes.sub('</modules>', '<unknown/></modules>'),
    'module namespace' => raw_root_bytes.sub('<module>', '<module xmlns="urn:foreign">'),
    'module attribute' => raw_root_bytes.sub('<module>', '<module extra="x">'),
    'module nested' => raw_root_bytes.sub('<module>', '<module><nested/>'),
    'empty module' => raw_root_bytes.sub('<module>im-common</module>', '<module/>'),
    'duplicate module' => raw_root_bytes.sub('</modules>', '<module>im-common</module></modules>'),
    'module traversal' => raw_root_bytes.sub('<module>im-common', '<module>../im-common'),
    'module order' => raw_root_bytes.sub('<module>im-common</module><module>im-plugin</module>', '<module>im-plugin</module><module>im-common</module>'),
    'missing modules' => raw_root_bytes.sub(/<modules>.*?<\/modules>/, ''),
    'empty nonleaf modules' => raw_root_bytes.sub(/<modules>.*?<\/modules>/, '<modules/>'),
    'DTD' => '<!DOCTYPE project SYSTEM "file:///not-read">' + raw_root_bytes,
    'entity declaration' => '<!DOCTYPE project [<!ENTITY x "im-chat">]>' + raw_root_bytes,
    'standalone entity declaration' => '<!ENTITY x SYSTEM "file:///not-read">' + raw_root_bytes,
    'identity expression' => raw_root_bytes.sub('<artifactId>im-chat', '<artifactId>${project.artifactId}'),
    'identity duplicate' => raw_root_bytes.sub('</project>', '<artifactId>im-chat</artifactId></project>'),
    'identity nested' => raw_root_bytes.sub('<artifactId>im-chat', '<artifactId><nested/>im-chat')
  }
  mutations.each do |label, bytes|
    raise 'ineffective raw mutation' if bytes == raw_root_bytes
    File.binwrite(root + '/pom.xml', bytes)
    begin
      evidence.read_raw_pom_oracle
      raise 'raw mutation accepted: ' + label
    rescue RuntimeError => error
      raise unless error.message == 'invalid raw POM oracle'
    ensure
      File.binwrite(root + '/pom.xml', raw_root_bytes)
    end
  end
  {
    'child relativePath' => raw_child_bytes.sub('</parent>', '<relativePath>../pom.xml</relativePath></parent>'),
    'child parent mismatch' => raw_child_bytes.sub('<artifactId>im-chat', '<artifactId>im-plugin'),
    'explicit jar unsupported declaration' => raw_child_bytes.sub('</project>', '<packaging>jar</packaging></project>'),
    'child explicit expression' => raw_child_bytes.sub('</project>', '<groupId>${parent.groupId}</groupId></project>'),
    'jar has module' => raw_child_bytes.sub('<modules></modules>', '<modules><module>child</module></modules>')
  }.each do |label, bytes|
    File.binwrite(root + '/im-common/pom.xml', bytes)
    assert_failure(label) { evidence.read_raw_pom_oracle }
    File.binwrite(root + '/im-common/pom.xml', raw_child_bytes)
  end
  # 缺席与空 modules 的叶子策略；无关 build/dependencies 不属于有限身份语法。
  File.binwrite(root + '/im-common/pom.xml', raw_child_bytes.sub('<modules></modules>', '<build><unrelated/></build><dependencies/>'))
  raise 'leaf absent modules' unless evidence.read_raw_pom_oracle.size == 53
  File.binwrite(root + '/im-common/pom.xml', raw_child_bytes)
  File.binwrite(root + '/pom.xml', raw_root_bytes.sub('</project>', '<!-- byte change --></project>'))
  assert_failure('consumer rejects bytes replaced even same identity') { evidence.read_effective_model(formal_xml, formal_oracle, model_context, contract: :formal) }
  File.binwrite(root + '/pom.xml', raw_root_bytes)
  forged_sha = Marshal.load(Marshal.dump(formal_oracle))
  forged_sha.first['sha256'] = '0' * 64
  assert_failure('consumer rejects self supplied SHA') { evidence.read_effective_model(formal_xml, forged_sha, model_context, contract: :formal) }
  File.rename(root + '/im-common/pom.xml', root + '/im-common/pom.saved')
  File.symlink(root + '/im-common/pom.saved', root + '/im-common/pom.xml')
  assert_failure('raw symlink') { evidence.read_raw_pom_oracle }
  File.unlink(root + '/im-common/pom.xml')
  File.rename(root + '/im-common/pom.saved', root + '/im-common/pom.xml')
  raise 'raw restored positive' unless evidence.read_effective_model(formal_xml, formal_oracle, model_context, contract: :formal).fetch('modules').size == 53
  provenance_root = root
  provenance_maven = '/opt/maven/bin/mvn'
  provenance_common = {
    'canonicalRoot' => provenance_root, 'cwd' => root, 'maven' => provenance_maven, 'jdk' => '/opt/jdk', 'settings' => '/opt/maven/settings.xml', 'dotMvn' => provenance_root + '/.mvn',
    'environment' => %w[JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS MAVEN_PROJECTBASEDIR].to_h { |key| [key, { 'present' => false }] }
  }
  provenance_model = provenance_common.merge('argv' => ['mvn', '-o', '-q', '-f', 'pom.xml', 'org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom', '-Dverbose=false', '-Doutput=' + provenance_root + '/.harness/runtime/effective-pom.xml'])
  provenance_verify = provenance_common.merge('argv' => ['mvn', '-q', 'verify'])
  provenance_inputs = { 'localRepository' => { 'path' => root + '/repository', 'sha256' => 'a' * 64 }, 'effectivePom' => { 'path' => root + '/.harness/runtime/effective-pom.xml', 'sha256' => Digest::SHA256.hexdigest(formal_xml) } }
  binding = evidence.bind_model_provenance(model: provenance_model, verify: provenance_verify, inputs: provenance_inputs)
  raise 'bound model fixture' unless evidence.read_bound_effective_model(formal_xml, formal_oracle, binding).fetch('modules').size == 53
  projection_projects = formal_projects.map.with_index do |project, index|
    case index
    when 0 then project.sub('</project>', '<name> Root 😀 </name></project>')
    when 1 then project.sub('</project>', '<name></name></project>')
    else project
    end
  end
  projection_xml = '<projects>' + projection_projects.join + '</projects>'
  projection_binding = lambda do |xml|
    inputs = Marshal.load(Marshal.dump(binding.fetch('inputs')))
    inputs['effectivePom']['sha256'] = Digest::SHA256.hexdigest(xml)
    evidence.bind_model_provenance(model: provenance_model, verify: provenance_verify, inputs: inputs)
  end
  raise 'named bound positive baseline' unless evidence.read_bound_effective_model(projection_xml, formal_oracle, projection_binding.call(projection_xml)).fetch('modules').size == 53
  invalid_name_xml = projection_xml.sub('<name> Root 😀 </name>', '<name><nested/>Root</name>')
  assert_failure('bound projection rejects nested name') { evidence.read_bound_effective_model(invalid_name_xml, formal_oracle, projection_binding.call(invalid_name_xml)) }
  # 独立预期 literal，按受审原始项目序；不从 XML、oracle 或生产常量投影。
  expected_projection = [
    { 'gav' => 'com.co.kc.im:im-chat:0.0.1-SNAPSHOT', 'name' => ' Root 😀 ', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-common:0.0.1-SNAPSHOT', 'name' => '', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-plugin:0.0.1-SNAPSHOT', 'name' => 'im-plugin', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-cache:0.0.1-SNAPSHOT', 'name' => 'im-cache', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-identity:0.0.1-SNAPSHOT', 'name' => 'im-identity', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-excel:0.0.1-SNAPSHOT', 'name' => 'im-excel', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-metrics:0.0.1-SNAPSHOT', 'name' => 'im-metrics', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-tracing:0.0.1-SNAPSHOT', 'name' => 'im-tracing', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-lock:0.0.1-SNAPSHOT', 'name' => 'im-lock', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-datasource:0.0.1-SNAPSHOT', 'name' => 'im-datasource', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-session:0.0.1-SNAPSHOT', 'name' => 'im-session', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-web:0.0.1-SNAPSHOT', 'name' => 'im-web', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-dubbo:0.0.1-SNAPSHOT', 'name' => 'im-dubbo', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-bolt:0.0.1-SNAPSHOT', 'name' => 'im-bolt', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-mq-kafka:0.0.1-SNAPSHOT', 'name' => 'im-mq-kafka', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-gossip:0.0.1-SNAPSHOT', 'name' => 'im-gossip', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-raft:0.0.1-SNAPSHOT', 'name' => 'im-raft', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-nacos:0.0.1-SNAPSHOT', 'name' => 'im-nacos', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-gateway:0.0.1-SNAPSHOT', 'name' => 'im-gateway', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-http-gateway:0.0.1-SNAPSHOT', 'name' => 'im-http-gateway', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-ws-gateway:0.0.1-SNAPSHOT', 'name' => 'im-ws-gateway', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-ws-gateway-sdk:0.0.1-SNAPSHOT', 'name' => 'im-ws-gateway-sdk', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-ws-gateway-server:0.0.1-SNAPSHOT', 'name' => 'im-ws-gateway-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-broker:0.0.1-SNAPSHOT', 'name' => 'im-broker', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-broker-sdk:0.0.1-SNAPSHOT', 'name' => 'im-broker-sdk', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-broker-server:0.0.1-SNAPSHOT', 'name' => 'im-broker-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-service:0.0.1-SNAPSHOT', 'name' => 'im-service', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-account:0.0.1-SNAPSHOT', 'name' => 'im-account', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-account-facade:0.0.1-SNAPSHOT', 'name' => 'im-account-facade', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-account-admin-facade:0.0.1-SNAPSHOT', 'name' => 'im-account-admin-facade', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-account-server:0.0.1-SNAPSHOT', 'name' => 'im-account-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-social:0.0.1-SNAPSHOT', 'name' => 'im-social', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-social-facade:0.0.1-SNAPSHOT', 'name' => 'im-social-facade', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-social-server:0.0.1-SNAPSHOT', 'name' => 'im-social-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-message:0.0.1-SNAPSHOT', 'name' => 'im-message', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-message-facade:0.0.1-SNAPSHOT', 'name' => 'im-message-facade', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-message-server:0.0.1-SNAPSHOT', 'name' => 'im-message-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-management:0.0.1-SNAPSHOT', 'name' => 'im-management', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-iam:0.0.1-SNAPSHOT', 'name' => 'im-iam', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-iam-server:0.0.1-SNAPSHOT', 'name' => 'im-iam-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-iam-sdk:0.0.1-SNAPSHOT', 'name' => 'im-iam-sdk', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-admin:0.0.1-SNAPSHOT', 'name' => 'im-admin', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-ops:0.0.1-SNAPSHOT', 'name' => 'im-ops', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-ops-server:0.0.1-SNAPSHOT', 'name' => 'im-ops-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-ops-cli:0.0.1-SNAPSHOT', 'name' => 'im-ops-cli', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-audit:0.0.1-SNAPSHOT', 'name' => 'im-audit', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-audit-sdk:0.0.1-SNAPSHOT', 'name' => 'im-audit-sdk', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-audit-server:0.0.1-SNAPSHOT', 'name' => 'im-audit-server', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-test:0.0.1-SNAPSHOT', 'name' => 'im-test', 'packaging' => 'pom', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-architecture-test:0.0.1-SNAPSHOT', 'name' => 'im-architecture-test', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-e2e-test:0.0.1-SNAPSHOT', 'name' => 'im-e2e-test', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-load-test:0.0.1-SNAPSHOT', 'name' => 'im-load-test', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
    { 'gav' => 'com.co.kc.im:im-routing-ha:0.0.1-SNAPSHOT', 'name' => 'im-routing-ha', 'packaging' => 'jar', 'version' => '0.0.1-SNAPSHOT' },
  ]
  projected = evidence.read_bound_effective_model(projection_xml, formal_oracle, projection_binding.call(projection_xml))
  raise 'independent display projection' unless projected.fetch('displayProjects') == expected_projection
  raise 'display XML SHA binding' unless projected.fetch('effectivePomSha256') == Digest::SHA256.hexdigest(projection_xml)
  # 独立冻结53行预期，不调用生产模板或projection生成。
  build_order_segment = <<~'BUILD_ORDER'
    [INFO] Scanning for projects...
    [INFO] ------------------------------------------------------------------------
    [INFO] Reactor Build Order:
    [INFO]
    [INFO]  Root 😀                                                           [pom]
    [INFO]                                                                    [jar]
    [INFO] im-plugin                                                          [pom]
    [INFO] im-cache                                                           [jar]
    [INFO] im-identity                                                        [jar]
    [INFO] im-excel                                                           [jar]
    [INFO] im-metrics                                                         [jar]
    [INFO] im-tracing                                                         [jar]
    [INFO] im-lock                                                            [jar]
    [INFO] im-datasource                                                      [jar]
    [INFO] im-session                                                         [jar]
    [INFO] im-web                                                             [jar]
    [INFO] im-dubbo                                                           [jar]
    [INFO] im-bolt                                                            [jar]
    [INFO] im-mq-kafka                                                        [jar]
    [INFO] im-gossip                                                          [jar]
    [INFO] im-raft                                                            [jar]
    [INFO] im-nacos                                                           [jar]
    [INFO] im-gateway                                                         [pom]
    [INFO] im-http-gateway                                                    [jar]
    [INFO] im-ws-gateway                                                      [pom]
    [INFO] im-ws-gateway-sdk                                                  [jar]
    [INFO] im-ws-gateway-server                                               [jar]
    [INFO] im-broker                                                          [pom]
    [INFO] im-broker-sdk                                                      [jar]
    [INFO] im-broker-server                                                   [jar]
    [INFO] im-service                                                         [pom]
    [INFO] im-account                                                         [pom]
    [INFO] im-account-facade                                                  [jar]
    [INFO] im-account-admin-facade                                            [jar]
    [INFO] im-account-server                                                  [jar]
    [INFO] im-social                                                          [pom]
    [INFO] im-social-facade                                                   [jar]
    [INFO] im-social-server                                                   [jar]
    [INFO] im-message                                                         [pom]
    [INFO] im-message-facade                                                  [jar]
    [INFO] im-message-server                                                  [jar]
    [INFO] im-management                                                      [pom]
    [INFO] im-iam                                                             [pom]
    [INFO] im-iam-server                                                      [jar]
    [INFO] im-iam-sdk                                                         [jar]
    [INFO] im-admin                                                           [jar]
    [INFO] im-ops                                                             [pom]
    [INFO] im-ops-server                                                      [jar]
    [INFO] im-ops-cli                                                         [jar]
    [INFO] im-audit                                                           [pom]
    [INFO] im-audit-sdk                                                       [jar]
    [INFO] im-audit-server                                                    [jar]
    [INFO] im-test                                                            [pom]
    [INFO] im-architecture-test                                               [jar]
    [INFO] im-e2e-test                                                        [jar]
    [INFO] im-load-test                                                       [jar]
    [INFO] im-routing-ha                                                      [jar]
  BUILD_ORDER
  build_order_segment = build_order_segment.sub("[INFO]\n", "[INFO] \n")
  order_binding = projection_binding.call(projection_xml)
  order_summary = evidence.read_active_profiles_build_order(build_order_segment, xml: projection_xml, oracle: formal_oracle, binding: order_binding)
  raise 'build order legal baseline' unless order_summary.fetch('projectCount') == 53 && order_summary.fetch('effectivePomSha256') == Digest::SHA256.hexdigest(projection_xml)
  assert_failure('build order missing logger blank space') { evidence.read_active_profiles_build_order(build_order_segment.sub("[INFO] \n", "[INFO]\n"), xml: projection_xml, oracle: formal_oracle, binding: order_binding) }
  root_banner_segment = <<~'ROOT_BANNER'
    [INFO]
    [INFO] ------------------------< com.co.kc.im:im-chat >------------------------
    [INFO] Building  Root 😀  0.0.1-SNAPSHOT                                 [1/53]
    [INFO] --------------------------------[ pom ]---------------------------------
  ROOT_BANNER
  root_banner_segment = root_banner_segment.sub("[INFO]\n", "[INFO] \n")
  banner_summary = evidence.read_active_profiles_root_banner(root_banner_segment, xml: projection_xml, oracle: formal_oracle, binding: order_binding)
  raise 'root banner legal baseline' unless banner_summary.fetch('effectivePomSha256') == Digest::SHA256.hexdigest(projection_xml)
  assert_failure('root banner wrong counter') { evidence.read_active_profiles_root_banner(root_banner_segment.sub('[1/53]', '[2/53]'), xml: projection_xml, oracle: formal_oracle, binding: order_binding) }
  [root_banner_segment + root_banner_segment, root_banner_segment + "[INFO] unknown\n",
   root_banner_segment.delete_suffix("\n"), root_banner_segment + "\n",
   root_banner_segment.sub("[INFO] \n", "[INFO]\n"), root_banner_segment.gsub("\n", "\r\n"),
   root_banner_segment.sub('--< ', '--<'), root_banner_segment.sub('im-chat >--', 'im-common >--'),
   root_banner_segment.sub('[1/53]', '[1/54]'), root_banner_segment.sub('[ pom ]', '[ jar ]'),
   root_banner_segment.sub('Root 😀 ', 'Root 😀  '), "﻿" + root_banner_segment].each do |segment|
    begin
      evidence.read_active_profiles_root_banner(segment, xml: projection_xml, oracle: formal_oracle, binding: order_binding)
    rescue StandardError => error
      raise 'unsafe banner rejection' unless error.message == 'invalid active profiles root banner'
      next
    end
    raise 'invalid banner accepted'
  end
  empty_banner_xml = projection_xml.sub('<name> Root 😀 </name>', '<name/>')
  empty_banner = root_banner_segment.lines[0..1].join + '[INFO] Building  0.0.1-SNAPSHOT' + ' ' * 41 + " [1/53]\n" + root_banner_segment.lines[3]
  raise 'empty root name banner positive' unless evidence.read_active_profiles_root_banner(empty_banner, xml: empty_banner_xml, oracle: formal_oracle, binding: projection_binding.call(empty_banner_xml)).fetch('effectivePomSha256') == Digest::SHA256.hexdigest(empty_banner_xml)
  long_banner_xml = projection_xml.sub('<name> Root 😀 </name>', '<name>' + '😀' * 36 + '</name>')
  long_banner = root_banner_segment.lines[0..1].join + '[INFO] Building ' + '😀' * 36 + " 0.0.1-SNAPSHOT [1/53]\n" + root_banner_segment.lines[3]
  evidence.read_active_profiles_root_banner(long_banner, xml: long_banner_xml, oracle: formal_oracle, binding: projection_binding.call(long_banner_xml))
  reordered_banner_xml = '<projects>' + projection_projects.reverse.join + '</projects>'
  evidence.read_active_profiles_root_banner(root_banner_segment, xml: reordered_banner_xml, oracle: formal_oracle, binding: projection_binding.call(reordered_banner_xml))
  assert_failure('banner XML SHA association') { evidence.read_active_profiles_root_banner(root_banner_segment, xml: reordered_banner_xml, oracle: formal_oracle, binding: order_binding) }
  reject_order = lambda do |segment|
    begin
      evidence.read_active_profiles_build_order(segment, xml: projection_xml, oracle: formal_oracle, binding: order_binding)
    rescue StandardError => error
      raise 'unsafe build order rejection' unless error.message == 'invalid active profiles build order'
      next
    end
    raise 'invalid build order accepted'
  end
  [build_order_segment + "[INFO] unknown\n", "\n" + build_order_segment,
   build_order_segment + "\n", build_order_segment.delete_suffix("\n"),
   build_order_segment.gsub("\n", "\r\n"), "﻿" + build_order_segment,
   build_order_segment.sub('[INFO]', '[WARN]'), build_order_segment.sub('[INFO]', '[INFO] '),
   build_order_segment.sub('Root 😀 ', 'Root 😀  '),
   build_order_segment.sub('[pom]', '[jar]'), build_order_segment + "\0",
   build_order_segment.sub('Scanning', "\e[0mScanning")].each { |segment| reject_order.call(segment) }
  assert_failure('build order rejects wrong XML digest') do
    evidence.read_active_profiles_build_order(build_order_segment, xml: projection_xml + ' ', oracle: formal_oracle, binding: order_binding)
  end
  # 长名称仍至少一空格；独立literal行，不能使用Ruby codepoint长度代替Java UTF-16。
  long_xml = projection_xml.sub('<name> Root 😀 </name>', '<name>' + '😀' * 36 + '</name>')
  long_segment = build_order_segment.sub(build_order_segment.lines[4], '[INFO] ' + '😀' * 36 + " [pom]\n")
  raise 'long UTF16 build order positive' unless evidence.read_active_profiles_build_order(long_segment, xml: long_xml, oracle: formal_oracle, binding: projection_binding.call(long_xml)).fetch('projectCount') == 53
  reversed_xml = '<projects>' + projection_projects.reverse.join + '</projects>'
  reversed_projection = evidence.read_bound_effective_model(reversed_xml, formal_oracle, projection_binding.call(reversed_xml))
  raise 'display preserves XML order not sorted oracle' unless reversed_projection.fetch('displayProjects') == expected_projection.reverse
  assert_failure('display cannot reuse old XML digest') { evidence.read_bound_effective_model(reversed_xml, formal_oracle, projection_binding.call(projection_xml)) }
  [
    '<name>one</name><name>two</name>', '<name extra="x">one</name>',
    '<name xmlns="urn:foreign">one</name>', '<name><nested/>one</name>',
    '<name>line&#10;break</name>', '<name>line&#13;break</name>',
    '<name>tab&#9;name</name>', '<name>&#127;</name>', '<name>${project.name}</name>'
  ].each do |name_xml|
    xml = projection_xml.sub('<name> Root 😀 </name>', name_xml)
    begin
      evidence.read_bound_effective_model(xml, formal_oracle, projection_binding.call(xml))
    rescue StandardError => error
      raise 'unsafe model display rejection' unless error.message == 'invalid model display name'
      next
    end
    raise 'malformed display name accepted'
  end
  entity_xml = projection_xml.sub('<name> Root 😀 </name>', '<name>A &amp; B</name>')
  raise 'display XML decoding without trim' unless evidence.read_bound_effective_model(entity_xml, formal_oracle, projection_binding.call(entity_xml)).fetch('displayProjects').first.fetch('name') == 'A & B'
  assert_failure('display still binds raw SHA') do
    stale_oracle = Marshal.load(Marshal.dump(formal_oracle))
    stale_oracle.first['sha256'] = '0' * 64
    evidence.read_bound_effective_model(projection_xml, stale_oracle, projection_binding.call(projection_xml))
  end
  tampered_binding = Marshal.load(Marshal.dump(binding))
  tampered_binding['bindingSha256'] = '0' * 64
  assert_failure('consumer rejects tampered binding digest') { evidence.read_bound_effective_model(formal_xml, formal_oracle, tampered_binding) }
  assert_failure('provenance argv mismatch') { evidence.bind_model_provenance(model: provenance_model.merge('argv' => provenance_model.fetch('argv') + ['-Dextra=x']), verify: provenance_verify, inputs: provenance_inputs) }
  assert_failure('provenance MAVEN_ARGS injection') { evidence.bind_model_provenance(model: provenance_model.merge('environment' => provenance_model.fetch('environment').merge('MAVEN_ARGS' => { 'present' => true })), verify: provenance_verify, inputs: provenance_inputs) }
  assert_failure('provenance root mismatch') { evidence.bind_model_provenance(model: provenance_model, verify: provenance_verify.merge('canonicalRoot' => root + '/other'), inputs: provenance_inputs) }
  consumer_rejections = {
    'repository tampering' => lambda { |x| x['inputs']['localRepository']['path'] += '/other' },
    'missing input' => lambda { |x| x['inputs'].delete('localRepository') },
    'missing SHA' => lambda { |x| x['inputs']['effectivePom'].delete('sha256') },
    'bad SHA' => lambda { |x| x['inputs']['localRepository']['sha256'] = 'bad' },
    'output path' => lambda { |x| x['inputs']['effectivePom']['path'] = root + '/other.xml' },
    'both wrong root' => lambda { |x| %w[model verify].each { |k| x[k]['canonicalRoot'] = root + '/other' } },
    'both wrong dotMvn' => lambda { |x| %w[model verify].each { |k| x[k]['dotMvn'] = root + '/other' } },
    'both wrong maven type' => lambda { |x| %w[model verify].each { |k| x[k]['maven'] = [] } },
    'verify argv adaptation' => lambda { |x| x['verify']['argv'] = ['mvn', '-q', '-f', 'pom.xml', 'verify'] },
    'profile injection' => lambda { |x| x['model']['argv'] << '-Pother' }
  }
  consumer_rejections.each do |label, mutate|
    changed = Marshal.load(Marshal.dump(binding))
    mutate.call(changed)
    assert_failure(label) { evidence.read_bound_effective_model(formal_xml, formal_oracle, changed) }
  end
  assert_failure('XML bytes replaced') { evidence.read_bound_effective_model(formal_xml + "\n", formal_oracle, binding) }
  provenance_inputs['localRepository']['path'] += '/caller-mutated'
  raise 'binding aliases caller' unless evidence.read_bound_effective_model(formal_xml, formal_oracle, binding).fetch('modules').size == 53
  # bootstrap 的 filesystem 是新隔离根；baseline 独立于生产扫描结果。
  host_dir = root + '/host-fixture'
  FileUtils.mkdir_p(host_dir + '/home/bin')
  FileUtils.mkdir_p(host_dir + '/user')
  FileUtils.mkdir_p(host_dir + '/jdk/bin')
  File.write(host_dir + '/home/bin/mvn', 'launcher')
  File.write(host_dir + '/jdk/bin/java', 'java')
  File.chmod(0755, host_dir + '/home/bin/mvn')
  host_baseline = { 'home/bin/mvn' => Digest::SHA256.hexdigest('launcher'), 'jdk/bin/java' => Digest::SHA256.hexdigest('java') }
  host_env = { 'HOME' => host_dir + '/user', 'JAVA_HOME' => host_dir + '/jdk', 'PATH' => host_dir + '/home/bin' }
  host_layout = { 'mavenHome' => host_dir + '/home', 'launcher' => host_dir + '/home/bin/mvn', 'jdk' => host_dir + '/jdk', 'userHome' => host_dir + '/user' }
  fixture_system_root = host_dir + '/system'
  FileUtils.mkdir_p(fixture_system_root + '/usr/local/etc')
  FileUtils.mkdir_p(fixture_system_root + '/etc')
  settings_xml = '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="fixture"><pluginGroups/><proxies/><servers/><mirrors/><profiles>' + ['fixture-a', 'fixture-b'].map { |id| '<profile><id>' + id + '</id><repositories><repository><id>synthetic</id><url>https://example.invalid</url><releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled><updatePolicy>never</updatePolicy></snapshots></repository></repositories></profile>' }.join + '</profiles><activeProfiles><activeProfile>fixture-a</activeProfile></activeProfiles></settings>'
  settings_sha = Digest::SHA256.hexdigest(settings_xml)
  settings_identity_sha = Digest::SHA256.hexdigest('fixture-a')
  FileUtils.mkdir_p(host_dir + '/home/conf')
  File.write(host_dir + '/home/conf/settings.xml', settings_xml)
  host_baseline['home/conf/settings.xml'] = settings_sha
  host_calls = []
  FileUtils.mkdir_p(host_dir + '/repository')
  host_runner = lambda do |argv, cwd, environment|
    host_calls << argv
    value = argv.include?('-DforceStdout') ? host_dir + '/repository' : 'fixture-output'
    { 'exitCode' => 0, 'stdout' => value }
  end
  host_result = evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: host_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
  raise 'bootstrap positive did not execute all commands' unless host_calls.size == 3 && host_result.fetch('localRepository') == host_dir + '/repository'
  host_calls.clear
  File.chmod(0644, host_layout.fetch('launcher'))
  assert_failure('non-executable selected launcher') do
    evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: host_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
  end
  raise 'non-executable launcher called runner' unless host_calls.empty?
  File.chmod(0755, host_layout.fetch('launcher'))
  File.write(host_dir + '/user/.mavenrc', 'injected')
  begin
    evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: host_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
    raise 'bootstrap accepted rc'
  rescue StandardError => error
    raise error unless error.message.include?('unapproved host input')
  end
  raise 'bootstrap executed runner before source rejection' unless host_calls.empty?
  File.unlink(host_dir + '/user/.mavenrc')
  host_calls.clear
  assert_failure('bootstrap PATH launcher mismatch') do
    evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: host_env.merge('PATH' => host_dir + '/missing'), oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
  end
  raise 'PATH rejection called runner' unless host_calls.empty?
  shadow_dir = host_dir + '/shadow'
  FileUtils.mkdir_p(shadow_dir)
  File.write(shadow_dir + '/mvn', 'shadow')
  File.chmod(0644, shadow_dir + '/mvn')
  shadow_env = host_env.merge('PATH' => shadow_dir + File::PATH_SEPARATOR + host_env.fetch('PATH'))
  evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: shadow_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
  raise 'non-executable PATH prefix did not fall through' unless host_calls.size == 3
  host_calls.clear
  File.chmod(0755, shadow_dir + '/mvn')
  assert_failure('executable PATH shadow') do
    evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: shadow_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
  end
  raise 'executable shadow called runner' unless host_calls.empty?
  # 两个全局 rc 只映射到本次新 fixture 根；不读写宿主全局文件。
  %w[etc/mavenrc usr/local/etc/mavenrc].each do |relative_rc|
    rc = fixture_system_root + '/' + relative_rc
    File.write(rc, 'injected global rc')
    begin
      evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: host_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
      raise 'global rc accepted'
    rescue StandardError => error
      raise error unless error.message == 'unapproved host input global rc'
    end
    raise 'global rc called runner' unless host_calls.empty?
    File.unlink(rc)
  end
  evidence.collect_host_model(layout: host_layout, baseline: host_baseline, environment: host_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
  raise 'absent global rc positive' unless host_calls.size == 3
  host_calls.clear
  properties_xml = settings_xml.sub('<repositories>', '<properties><fixture>value</fixture></properties><repositories>')
  File.write(host_dir + '/home/conf/settings.xml', properties_xml)
  properties_baseline = host_baseline.merge('home/conf/settings.xml' => Digest::SHA256.hexdigest(properties_xml))
  assert_failure('settings profile properties rejected semantically') do
    evidence.collect_host_model(layout: host_layout, baseline: properties_baseline, environment: host_env, oracle: formal_oracle, runner: host_runner, system_root: fixture_system_root, settings_identity_sha: settings_identity_sha)
  end
  raise 'settings properties called runner' unless host_calls.empty?
  File.write(host_dir + '/home/conf/settings.xml', settings_xml)
  assert_failure('settings active identity membership') do
    evidence.validate_repository_settings(settings_xml.sub('<activeProfile>fixture-a</activeProfile>', '<activeProfile>foreign</activeProfile>'), settings_identity_sha)
  end
  settings_invalid = [
    '<!DOCTYPE settings>' + settings_xml,
    settings_xml.sub('SETTINGS/1.2.0', 'SETTINGS/9.0.0'),
    settings_xml.sub('<profile>', '<profile bad="x">'),
    settings_xml.sub('<repositories>', '<activation/><repositories>'),
    settings_xml.sub('<repositories>', '<pluginRepositories/><repositories>'),
    settings_xml.sub('<id>fixture-a</id>', '<id>fixture-a</id><id>duplicate</id>'),
    settings_xml.sub('<id>fixture-a</id>', '<id><nested/></id>'),
    settings_xml.sub('<id>fixture-a</id>', '<id>${unknown}</id>'),
    settings_xml.sub('<id>fixture-a</id>', '<id> fixture-a</id>'),
    settings_xml.sub('<id>fixture-b</id>', '<id>fixture-a</id>'),
    settings_xml.sub('<servers/>', '<servers><server/></servers>'),
    settings_xml.sub('<enabled>true</enabled>', '<enabled>true</enabled><unknown/>')
  ]
  settings_invalid.each { |xml| assert_failure('finite settings shape') { evidence.validate_repository_settings(xml, settings_identity_sha) } }
  encoded_settings = settings_xml.gsub('fixture-a', 'fixture-&#97;')
  raise 'identity XML decoding' unless evidence.validate_repository_settings(encoded_settings, settings_identity_sha).fetch('profileIdentitySha256').include?(settings_identity_sha)
  split_settings = settings_xml.gsub('fixture-a', 'fixture-<!-- split -->a')
  evidence.validate_repository_settings(split_settings, settings_identity_sha)
  assert_failure('identity no case normalization') { evidence.validate_repository_settings(settings_xml.gsub('fixture-a', 'Fixture-a'), settings_identity_sha) }
  # 独立冻结的正文 identity literals；不引用 reader 常量、formal XML specs 或 oracle 生成。
  body_identities = <<~IDS.lines.map(&:chomp)
    com.co.kc.im:im-chat:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-common:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-plugin:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-cache:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-identity:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-excel:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-metrics:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-tracing:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-lock:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-datasource:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-session:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-web:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-dubbo:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-bolt:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-mq-kafka:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-gossip:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-raft:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-nacos:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-gateway:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-http-gateway:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-ws-gateway:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-ws-gateway-sdk:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-ws-gateway-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-broker:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-broker-sdk:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-broker-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-service:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-account:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-account-facade:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-account-admin-facade:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-account-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-social:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-social-facade:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-social-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-message:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-message-facade:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-message-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-management:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-iam:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-iam-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-iam-sdk:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-admin:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-ops:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-ops-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-ops-cli:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-audit:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-audit-sdk:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-audit-server:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-test:pom:0.0.1-SNAPSHOT
    com.co.kc.im:im-architecture-test:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-e2e-test:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-load-test:jar:0.0.1-SNAPSHOT
    com.co.kc.im:im-routing-ha:jar:0.0.1-SNAPSHOT
  IDS
  # 正文专用 oracle 的独立 packaging 事实，不沿用 reader 合成 XML 的一致性 packaging。
  body_jar_facts = %w[im-common im-cache im-identity im-excel im-metrics im-tracing im-lock im-datasource im-session im-web im-dubbo im-bolt im-mq-kafka im-gossip im-raft im-nacos im-http-gateway im-ws-gateway-sdk im-ws-gateway-server im-broker-sdk im-broker-server im-account-facade im-account-admin-facade im-account-server im-social-facade im-social-server im-message-facade im-message-server im-iam-server im-iam-sdk im-admin im-ops-server im-ops-cli im-audit-sdk im-audit-server im-architecture-test im-e2e-test im-load-test im-routing-ha]
  body_oracle = formal_oracle.map { |entry| entry.merge('packaging' => body_jar_facts.include?(entry.fetch('gav').split(':')[1]) ? 'jar' : 'pom') }
  active_body = body_identities.map { |id| "\nActive Profiles for Project '#{id}':\n\nThe following profiles are active:\n\n - fixture-a (source: external)\n\n\n" }.join
  read_body = lambda do |body, xml = settings_xml, sha = settings_sha, identity = settings_identity_sha|
    evidence.read_active_profiles_body(body, oracle: body_oracle, settings_xml: xml, settings_sha256: sha, settings_identity_sha: identity)
  end
  # 独立固定logger/plugin字面量；正文沿用独立53 identity literal，不调用生产模板。
  logged_body_segment = "[INFO] \n[INFO] --- maven-help-plugin:3.5.1:active-profiles (default-cli) @ im-chat ---\n[INFO] " + active_body + "\n"
  read_logged = lambda do |segment|
    evidence.read_active_profiles_logged_body(segment, xml: projection_xml, oracle: body_oracle, binding: order_binding, settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
  end
  logged_summary = read_logged.call(logged_body_segment)
  raise 'logged body legal baseline' unless logged_summary.fetch('projectCount') == 53 && logged_summary.fetch('bodySha256') == Digest::SHA256.hexdigest(active_body)
  assert_failure('logger must not strip INFO inside body') { read_logged.call(logged_body_segment.sub("\nActive Profiles for Project", "\n[INFO] Active Profiles for Project")) }
  [logged_body_segment.sub('3.5.1:', '3.5.2:'), logged_body_segment.sub('default-cli', 'other-cli'),
   logged_body_segment.sub('@ im-chat', '@ im-common'), logged_body_segment.sub('active-profiles', 'effective-pom'),
   logged_body_segment.sub("[INFO] \n", "[INFO]\n"), logged_body_segment.delete_suffix("\n"),
   logged_body_segment + "\n", logged_body_segment + "[INFO] unknown\n",
   logged_body_segment.gsub("\n", "\r\n"), "﻿" + logged_body_segment,
   logged_body_segment.sub('(source: external)', '(source: project)'),
   logged_body_segment.sub(' - fixture-a ', ' - foreign ')].each { |segment| assert_failure('logged body finite boundary') { read_logged.call(segment) } }
  assert_failure('logged body XML SHA binding') do
    evidence.read_active_profiles_logged_body(logged_body_segment, xml: projection_xml + ' ', oracle: body_oracle, binding: order_binding, settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
  end
  assert_failure('logged body settings SHA binding') do
    evidence.read_active_profiles_logged_body(logged_body_segment, xml: projection_xml, oracle: body_oracle, binding: order_binding, settings_xml: settings_xml, settings_sha256: '0' * 64, settings_identity_sha: settings_identity_sha)
  end
  assert_failure('logged body order must follow same XML') do
    evidence.read_active_profiles_logged_body(logged_body_segment, xml: reversed_xml, oracle: body_oracle, binding: projection_binding.call(reversed_xml), settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
  end
  stale_logged_oracle = Marshal.load(Marshal.dump(body_oracle))
  stale_logged_oracle.first['sha256'] = '0' * 64
  assert_failure('logged body raw binding') do
    evidence.read_active_profiles_logged_body(logged_body_segment, xml: projection_xml, oracle: stale_logged_oracle, binding: order_binding, settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
  end
  # B1新能力测试先行；缺方法不记真实缺陷RED。预期summary是独立冻结literal。
  summary_segment = <<~'SUMMARY'
    [INFO] ------------------------------------------------------------------------
    [INFO] Reactor Summary for  Root 😀  0.0.1-SNAPSHOT:
    [INFO]
    [INFO]  Root 😀  .......................................... SUCCESS [  1.234 s]
    [INFO]  ................................................... SKIPPED
    [INFO] im-plugin .......................................... SKIPPED
    [INFO] im-cache ........................................... SKIPPED
    [INFO] im-identity ........................................ SKIPPED
    [INFO] im-excel ........................................... SKIPPED
    [INFO] im-metrics ......................................... SKIPPED
    [INFO] im-tracing ......................................... SKIPPED
    [INFO] im-lock ............................................ SKIPPED
    [INFO] im-datasource ...................................... SKIPPED
    [INFO] im-session ......................................... SKIPPED
    [INFO] im-web ............................................. SKIPPED
    [INFO] im-dubbo ........................................... SKIPPED
    [INFO] im-bolt ............................................ SKIPPED
    [INFO] im-mq-kafka ........................................ SKIPPED
    [INFO] im-gossip .......................................... SKIPPED
    [INFO] im-raft ............................................ SKIPPED
    [INFO] im-nacos ........................................... SKIPPED
    [INFO] im-gateway ......................................... SKIPPED
    [INFO] im-http-gateway .................................... SKIPPED
    [INFO] im-ws-gateway ...................................... SKIPPED
    [INFO] im-ws-gateway-sdk .................................. SKIPPED
    [INFO] im-ws-gateway-server ............................... SKIPPED
    [INFO] im-broker .......................................... SKIPPED
    [INFO] im-broker-sdk ...................................... SKIPPED
    [INFO] im-broker-server ................................... SKIPPED
    [INFO] im-service ......................................... SKIPPED
    [INFO] im-account ......................................... SKIPPED
    [INFO] im-account-facade .................................. SKIPPED
    [INFO] im-account-admin-facade ............................ SKIPPED
    [INFO] im-account-server .................................. SKIPPED
    [INFO] im-social .......................................... SKIPPED
    [INFO] im-social-facade ................................... SKIPPED
    [INFO] im-social-server ................................... SKIPPED
    [INFO] im-message ......................................... SKIPPED
    [INFO] im-message-facade .................................. SKIPPED
    [INFO] im-message-server .................................. SKIPPED
    [INFO] im-management ...................................... SKIPPED
    [INFO] im-iam ............................................. SKIPPED
    [INFO] im-iam-server ...................................... SKIPPED
    [INFO] im-iam-sdk ......................................... SKIPPED
    [INFO] im-admin ........................................... SKIPPED
    [INFO] im-ops ............................................. SKIPPED
    [INFO] im-ops-server ...................................... SKIPPED
    [INFO] im-ops-cli ......................................... SKIPPED
    [INFO] im-audit ........................................... SKIPPED
    [INFO] im-audit-sdk ....................................... SKIPPED
    [INFO] im-audit-server .................................... SKIPPED
    [INFO] im-test ............................................ SKIPPED
    [INFO] im-architecture-test ............................... SKIPPED
    [INFO] im-e2e-test ........................................ SKIPPED
    [INFO] im-load-test ....................................... SKIPPED
    [INFO] im-routing-ha ...................................... SKIPPED
  SUMMARY
  summary_segment = summary_segment.sub("[INFO]\n", "[INFO] \n")
  success_segment = <<~'SUCCESS'
    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time:  2.345 s
    [INFO] Finished at: 2026-11-03T08:30:01Z
    [INFO] ------------------------------------------------------------------------
  SUCCESS
  stdout_fixture = build_order_segment + root_banner_segment + logged_body_segment + summary_segment + success_segment
  read_stdout = lambda do |text, xml = projection_xml, bound = order_binding, raw = body_oracle|
    evidence.read_active_profiles_stdout(text, xml: xml, oracle: raw, binding: bound, settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
  end
  stdout_summary = read_stdout.call(stdout_fixture)
  raise 'complete stdout legal independent baseline' unless stdout_summary.fetch('projectCount') == 53 && stdout_summary.fetch('stdoutSha256') == Digest::SHA256.hexdigest(stdout_fixture)
  raise 'summary pure format literal' unless evidence.format_active_profiles_summary(expected_projection, '1.234 s') == summary_segment
  # 纯格式接缝只有展示输入，无formal接受/provenance含义；mixed预期独立literal。
  mixed_projects = [
    { 'gav' => 'com.co.kc.im:im-chat:0.0.1-SNAPSHOT', 'name' => 'Root', 'version' => '1' },
    { 'gav' => 'com.co.kc.im:child:2', 'name' => 'Child', 'version' => '2' }
  ]
  mixed_summary = <<~'MIXED'
    [INFO] ------------------------------------------------------------------------
    [INFO] Reactor Summary:
    [INFO]
    [INFO] Root 1 ............................................. SUCCESS [  1.234 s]
    [INFO] Child 2 ............................................ SKIPPED
  MIXED
  mixed_summary = mixed_summary.sub("[INFO]\n", "[INFO] \n")
  raise 'mixed pure format literal' unless evidence.format_active_profiles_summary(mixed_projects, '1.234 s') == mixed_summary
  [stdout_fixture + "\n", "\n" + stdout_fixture, stdout_fixture.delete_suffix("\n"),
   stdout_fixture + '[INFO] unknown', stdout_fixture.gsub("\n", "\r\n"), "﻿" + stdout_fixture,
   stdout_fixture.sub('[INFO] BUILD SUCCESS', '[INFO] BUILD FAILURE'),
   stdout_fixture.sub('Total time:  ', 'Total time: '), stdout_fixture.sub('2.345 s', '2.345 s (Wall Clock)'),
   stdout_fixture.sub('im-routing-ha ...................................... SKIPPED', 'im-routing-ha ...................................... SUCCESS [  1.234 s]'),
   stdout_fixture.sub(summary_segment, ''), stdout_fixture.sub(logged_body_segment, ''),
   stdout_fixture.sub(summary_segment, summary_segment + summary_segment),
   build_order_segment + logged_body_segment + root_banner_segment + summary_segment + success_segment,
   stdout_fixture.sub('Reactor Summary for  Root 😀  0.0.1-SNAPSHOT:', 'Reactor Summary:'),
   stdout_fixture.sub('Scanning', "\e[0mScanning"), stdout_fixture + "\0"].each do |text|
    raise 'stdout mutation actually changes input' if text == stdout_fixture
    assert_failure('whole stdout rejects extra/missing/reordered/altered bytes') { read_stdout.call(text) }
  end
  ['0.000 s', '59.999 s', '01:00 min', '59:59 min', '01:00 h', '23:59 h', '1 d 00:00 h', '12 d 23:59 h'].each do |duration|
    summary_text = evidence.format_active_profiles_summary(expected_projection, duration)
    text = stdout_fixture.sub(summary_segment, summary_text).sub('Total time:  2.345 s', 'Total time:  ' + duration)
    raise 'duration branch positive' unless read_stdout.call(text).fetch('projectCount') == 53
  end
  ['-1.000 s','00.000 s','60.000 s','1.00 s','1.0000 s','00:59 min','60:00 min','01:60 min','00:01 h','24:00 h','01:60 h','0 d 00:00 h','01 d 00:00 h','1 d 24:00 h','1 d 00:60 h','١.000 s','1.000 s '].each do |duration|
    assert_failure('duration finite grammar') { read_stdout.call(stdout_fixture.sub('Total time:  2.345 s', 'Total time:  ' + duration)) }
    assert_failure('summary duration finite grammar') { evidence.format_active_profiles_summary(expected_projection, duration) }
  end
  ['2024-02-29T23:59:59+23:59','0001-01-01T00:00:00-00:30','9999-12-31T23:59:59Z'].each do |stamp|
    raise 'timestamp calendar positive' unless read_stdout.call(stdout_fixture.sub('2026-11-03T08:30:01Z',stamp)).fetch('projectCount') == 53
  end
  ['0000-01-01T00:00:00Z','2025-02-29T00:00:00Z','2024-04-31T00:00:00Z','2026-13-01T00:00:00Z','2026-01-00T00:00:00Z','2026-11-03T24:00:00Z','2026-11-03T08:60:00Z','2026-11-03T08:30:60Z','2026-11-03T08:30:01+24:00','2026-11-03T08:30:01+01:60','2026-11-03T08:30:01+0100','2026-11-03T08:30:01.001Z','2026-11-03T08:30:01','2026-11-03T08:30:01Zextra'].each do |stamp|
    assert_failure('timestamp finite calendar/zone grammar') { read_stdout.call(stdout_fixture.sub('2026-11-03T08:30:01Z',stamp)) }
  end
  assert_failure('whole stdout XML digest/source contradiction') { read_stdout.call(stdout_fixture, projection_xml + ' ') }
  assert_failure('whole stdout XML order contradiction') { read_stdout.call(stdout_fixture, reversed_xml, projection_binding.call(reversed_xml)) }
  assert_failure('whole stdout caller raw SHA replacement') { read_stdout.call(stdout_fixture, projection_xml, order_binding, stale_logged_oracle) }
  assert_failure('pure mixed output cannot bypass formal model') { read_stdout.call(stdout_fixture.sub(summary_segment, mixed_summary)) }
  assert_failure('whole stdout settings cannot reuse caller SHA') do
    evidence.read_active_profiles_stdout(stdout_fixture, xml: projection_xml, oracle: body_oracle, binding: order_binding, settings_xml: settings_xml + ' ', settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
  end
  # B2 私有输出仅创建新fixture路径，拒绝预存，绝不chmod既有对象。
  private_parent = root + '/.harness/runtime'
  FileUtils.mkdir_p(private_parent)
  private_output = private_parent + '/b2-private-fixture'
  evidence.create_model_output_directory(private_output)
  raise 'B2 private directory creation mode' unless File.stat(private_output).mode & 0777 == 0700
  assert_failure('B2 never reuse private output directory') { evidence.create_model_output_directory(private_output) }
  raise 'B2 existing directory remains present' unless File.directory?(private_output)
  existing_file = private_parent + '/b2-existing-file'
  File.write(existing_file, 'preserve')
  assert_failure('B2 never overwrite ordinary output file') { evidence.create_model_output_directory(existing_file) }
  raise 'B2 existing file remains unchanged' unless File.read(existing_file) == 'preserve'
  linked_output = private_parent + '/b2-linked-output'
  File.symlink(private_output, linked_output)
  assert_failure('B2 output rejects symlink') { evidence.create_model_output_directory(linked_output) }
  raise 'B2 existing link preserved' unless File.symlink?(linked_output)
  writable_parent = root + '/.harness'
  parent_mode = File.stat(writable_parent).mode & 0777
  begin
    # 仅改变本测试创建的隔离fixture，不修改宿主既有路径。
    File.chmod(0777, writable_parent)
    assert_failure('B2 writable output parent rejected') { evidence.create_model_output_directory(private_parent + '/b2-unsafe-parent') }
    raise 'B2 writable parent creates no output' if File.exist?(private_parent + '/b2-unsafe-parent')
  ensure
    File.chmod(parent_mode, writable_parent)
  end
  outside_output = root + '/outside-private-fixture'
  assert_failure('B2 output confined to runtime') { evidence.create_model_output_directory(outside_output) }
  # B2 已批准公共输出消费接缝：只验证显式命令结果，不执行宿主命令或清理。
  model_result = { 'exitCode' => 0, 'stdout' => '', 'stderr' => '' }
  repo_result = { 'exitCode' => 0, 'stdout' => host_dir + '/repository', 'stderr' => '' }
  active_result = { 'exitCode' => 0, 'stdout' => stdout_fixture, 'stderr' => '' }
  b2_read = lambda do |results|
    evidence.read_model_command_results(results, xml: projection_xml, oracle: body_oracle, binding: order_binding,
      settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
  end
  # repository 必须来自当前binding，不能由stdout独立更换。
  repo_result['stdout'] = order_binding.fetch('inputs').fetch('localRepository').fetch('path')
  FileUtils.mkdir_p(repo_result.fetch('stdout'))
  raise 'B2 three output legal baseline' unless b2_read.call([model_result, repo_result, active_result]).fetch('projectCount') == 53
  ["\n", "\r\n", " ", "\e[0m", "\0"].each do |suffix|
    assert_failure('B2 evaluate exact bytes no strip') { b2_read.call([model_result, repo_result.merge('stdout' => repo_result.fetch('stdout') + suffix), active_result]) }
  end
  [0, 1, 2].each do |index|
    results = [model_result, repo_result, active_result].map(&:dup)
    results[index]['stderr'] = "warning\n"
    assert_failure('B2 any stderr fails closed') { b2_read.call(results) }
    results[index]['stderr'] = ''
    results[index]['exitCode'] = 1
    assert_failure('B2 any nonzero fails closed') { b2_read.call(results) }
  end
  assert_failure('B2 effective stdout must be empty') { b2_read.call([model_result.merge('stdout' => "\n"), repo_result, active_result]) }
  dynamic_model = order_binding.fetch('model').merge('argv' => order_binding.fetch('model').fetch('argv')[0...-1] + ['-Doutput=' + private_output + '/effective-pom.xml'])
  dynamic_inputs = order_binding.fetch('inputs').merge('effectivePom' => order_binding.fetch('inputs').fetch('effectivePom').merge('path' => private_output + '/effective-pom.xml'))
  dynamic_binding = evidence.bind_model_provenance(model: dynamic_model, verify: order_binding.fetch('verify'), inputs: dynamic_inputs)
  raise 'B2 registered private output binding' unless evidence.read_bound_effective_model(projection_xml, body_oracle, dynamic_binding).fetch('displayProjects').size == 53
  assert_failure('B2 no arbitrary dynamic model filename') do
    bad_model = dynamic_model.merge('argv' => dynamic_model.fetch('argv')[0...-1] + ['-Doutput=' + private_output + '/other.xml'])
    evidence.bind_model_provenance(model: bad_model, verify: order_binding.fetch('verify'), inputs: dynamic_inputs)
  end
  # 隔离采集贯通三个固定命令及所有reader；失败绝不触碰生产clear/verify。
  execution_path = [host_layout.fetch('mavenHome') + '/bin', host_layout.fetch('jdk') + '/bin', host_dir + '/node/bin', root + '/scripts/bin', '/usr/bin', '/bin', '/usr/sbin', '/sbin'].join(':')
  execution_env = { 'HOME' => host_layout.fetch('userHome'), 'JAVA_HOME' => host_layout.fetch('jdk'), 'PATH' => execution_path }
  execution_record = evidence.model_environment_record(original: host_env, execution: execution_env, layout: host_layout, node_bin: host_dir + '/node/bin')
  raise 'B2 original PATH kept separately' unless execution_record.fetch('originalPath').fetch('sha256') == Digest::SHA256.hexdigest(host_env.fetch('PATH'))
  raise 'B2 explicit execution PATH' unless execution_record.fetch('execution') == execution_env
  %w[MAVEN_ARGS MAVEN_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_PROJECTBASEDIR MAVEN_CONFIG MAVEN_BASEDIR MAVEN_DEBUG_OPTS _JAVA_OPTIONS].each do |key|
    assert_failure('B2 original injection cannot be hidden by controlled env') { evidence.model_environment_record(original: host_env.merge(key => ''), execution: execution_env, layout: host_layout, node_bin: host_dir + '/node/bin') }
  end
  assert_failure('B2 execution cannot inherit extra env') { evidence.model_environment_record(original: host_env, execution: execution_env.merge('EXTRA' => 'x'), layout: host_layout, node_bin: host_dir + '/node/bin') }
  assert_failure('B2 execution PATH cannot fall back') { evidence.model_environment_record(original: host_env, execution: host_env, layout: host_layout, node_bin: host_dir + '/node/bin') }
  fixture_tree = host_dir + '/source-tree'
  FileUtils.mkdir_p(fixture_tree + '/legal')
  FileUtils.mkdir_p(fixture_tree + '/licenses')
  File.write(fixture_tree + '/licenses/NOTICE', 'fixture-license')
  File.symlink('../licenses/NOTICE', fixture_tree + '/legal/NOTICE')
  fixture_link = { 'legal/NOTICE' => { 'link' => '../licenses/NOTICE', 'target' => 'licenses/NOTICE', 'sha256' => Digest::SHA256.hexdigest('fixture-license') } }
  fixture_members = { 'licenses/NOTICE' => Digest::SHA256.hexdigest('fixture-license') }
  raise 'B2 exact JDK legal links' unless evidence.read_reviewed_tree(fixture_tree, members: fixture_members, links: fixture_link) == fixture_members
  assert_failure('B2 legal link not generic allowance') { evidence.read_reviewed_tree(fixture_tree, members: fixture_members, links: {}) }
  File.write(fixture_tree + '/licenses/NOTICE', 'changed')
  assert_failure('B2 legal target bytes bound') { evidence.read_reviewed_tree(fixture_tree, members: fixture_members, links: fixture_link) }
  File.write(fixture_tree + '/licenses/NOTICE', 'fixture-license')
  File.symlink('licenses', fixture_tree + '/linked-directory')
  assert_failure('B2 directory links forbidden') { evidence.read_reviewed_tree(fixture_tree, members: fixture_members, links: fixture_link) }
  # 生产collector/consumer/prepare/verify贯通；仅合成来源授权，执行/reader/清理不覆写。
  production_copy = root + '/production-helper-copy.rb'
  File.binwrite(production_copy, File.binread(helper))
  raise 'production helper copy differs' unless Digest::SHA256.file(production_copy).hexdigest == Digest::SHA256.file(helper).hexdigest
  eval(File.read(production_copy).split("\nif $PROGRAM_NAME == __FILE__\nbegin\n  command, root").first, TOPLEVEL_BINDING, production_copy)
  class ProductionModelFixture < ReactorEvidence
    attr_accessor :fixture_sources, :fixture_snapshot, :snapshot_hook
    def read_approved_model_sources; fixture_sources; end
    def snapshot_approved_model_inputs
      snapshot_hook.call if snapshot_hook
      Marshal.load(Marshal.dump(fixture_snapshot))
    end
    def approved_settings_identity_sha; Digest::SHA256.hexdigest('fixture-a'); end
    def collect_approved_model(output:)
      super.merge('hostProvenance' => false, 'bootstrapComplete' => false)
    end
    def read_approved_collection(collection)
      raise 'not isolated fixture' unless collection.values_at('hostProvenance', 'bootstrapComplete') == [false, false]
      validate_model_collection(collection)
    end
  end
  source_dir = root + '/synthetic-model-source'
  FileUtils.mkdir_p(source_dir + '/conf')
  FileUtils.mkdir_p(source_dir + '/bin')
  File.binwrite(source_dir + '/conf/settings.xml', settings_xml)
  File.binwrite(source_dir + '/effective.xml', projection_xml)
  File.binwrite(source_dir + '/active.stdout', stdout_fixture)
  File.write(source_dir + '/bin/mvn', <<~SCRIPT)
    #!/usr/bin/ruby
    require 'json'
    source = #{source_dir.inspect}
    File.open(source + '/calls', 'a') { |f| f.puts(JSON.generate(ARGV)) }
    if ARGV == ['-q', 'verify']
      File.open(source + '/verify-count', 'a') { |f| f.puts('verify') }
      File.write(source + '/drift', 'changed') if File.exist?(source + '/drift-after-verify')
    elsif ARGV == ['-o', '-q', '-f', 'pom.xml', 'org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate', '-Dexpression=settings.localRepository', '-DforceStdout']
      STDOUT.write(#{repo_result.fetch('stdout').inspect})
    elsif ARGV == ['-o', '-f', 'pom.xml', 'org.apache.maven.plugins:maven-help-plugin:3.5.1:active-profiles']
      STDOUT.write(File.binread(source + '/active.stdout'))
    elsif ARGV.size == 7 && ARGV[0, 6] == ['-o', '-q', '-f', 'pom.xml', 'org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom', '-Dverbose=false'] && ARGV[6].start_with?('-Doutput=')
      File.binwrite(ARGV[6].delete_prefix('-Doutput='), File.binread(source + '/effective.xml'))
    else
      abort 'unexpected synthetic Maven argv'
    end
  SCRIPT
  File.chmod(0755, source_dir + '/bin/mvn')
  production_fixture = ProductionModelFixture.new(root)
  production_fixture.fixture_sources = { 'mavenJdk' => { 'layout' => { 'mavenHome' => source_dir, 'launcher' => source_dir + '/bin/mvn', 'jdk' => '/fixture/jdk' } } }
  production_fixture.fixture_snapshot = { 'settingsSha256' => settings_sha, 'environment' => { 'execution' => { 'HOME' => root, 'JAVA_HOME' => '/fixture/jdk', 'PATH' => source_dir + '/bin:/usr/bin:/bin' }, 'absentInjection' => provenance_common.fetch('environment') } }
  production_fixture.snapshot_hook = lambda do
    production_fixture.fixture_snapshot['drift'] = 'changed' if File.exist?(source_dir + '/drift')
  end
  collected = production_fixture.collect_approved_model(output: private_parent + '/production-collect')
  raise 'production three commands/readers' unless collected.fetch('projectCount') == 53 && File.readlines(source_dir + '/calls').size == 3
  assert_failure('formal reader rejects isolated collection') { evidence.read_approved_collection(collected) }
  fake_true = collected.merge('hostProvenance' => true, 'bootstrapComplete' => true)
  assert_failure('true labels do not authorize source') { evidence.read_approved_collection(fake_true) }
  collection_file = private_parent + '/production-collect/collection.json'
  File.write(collection_file, JSON.generate(collected))
  cli_stdout, cli_stderr, cli_status = Open3.capture3({'PATH' => '/usr/bin:/bin'}, '/usr/bin/ruby', production_copy, 'model-check', root, collection_file, unsetenv_others: true)
  raise 'formal CLI admitted isolated collection' if cli_status.success?
  raise 'formal CLI did not reject classification' unless cli_stderr.include?('untrusted model collection')
  manifest_file = private_parent + '/production-manifest.json'
  summary_file = private_parent + '/production-summary.json'
  production_targets = production_fixture.selection.values.flatten.map { |entry| production_fixture.report(entry) }.uniq
  jar_file = ReactorEvidence::JARS.first + '/target/fixture-exec.jar'
  preserved = [manifest_file, summary_file, root + '/' + production_targets.first, root + '/' + jar_file]
  reset_old = lambda do
    preserved.each { |file| FileUtils.mkdir_p(File.dirname(file)); File.binwrite(file, 'old:' + file) }
  end
  unchanged = lambda do
    raise 'drift deleted old evidence' unless preserved.all? { |file| File.binread(file) == 'old:' + file }
    raise 'drift reached Maven verify' if File.exist?(source_dir + '/verify-count')
  end
  %w[source output binding consumption clear].each do |mutation|
    reset_old.call
    candidate = Marshal.load(Marshal.dump(collected))
    stream = private_parent + '/production-collect/command-2.stdout'
    original_stream = File.binread(stream)
    hook_calls = 0
    production_fixture.snapshot_hook = lambda do
      hook_calls += 1
      production_fixture.fixture_snapshot['drift'] = 'changed' if mutation == 'source' || (mutation == 'consumption' && hook_calls == 2) || (mutation == 'clear' && hook_calls == 3)
    end
    File.binwrite(stream, original_stream + 'extra') if mutation == 'output'
    if mutation == 'binding'
      candidate['collectionProof']['binding']['verify']['argv'] = ['mvn', 'clean']
      candidate['collectionSha256'] = Digest::SHA256.hexdigest(JSON.generate(candidate['collectionProof']))
    end
    File.write(collection_file, JSON.generate(candidate))
    assert_failure('production prepare rejects ' + mutation) { production_fixture.prepare_approved_model(collection_file, manifest_file, summary_file) }
    unchanged.call
    File.binwrite(stream, original_stream)
    production_fixture.fixture_snapshot.delete('drift')
  end
  production_fixture.snapshot_hook = nil
  File.write(collection_file, JSON.generate(collected))
  reset_old.call
  outside = root + '/module/target/unselected.keep'
  FileUtils.mkdir_p(File.dirname(outside)); File.write(outside, 'keep')
  controlled = %w[classes generated-sources/annotations maven-status/maven-compiler-plugin site/jacoco].map { |suffix| root + '/im-common/target/' + suffix }
  controlled.each do |directory|
    FileUtils.mkdir_p(directory)
    File.write(directory + '/old', 'old')
  end
  controlled << root + '/im-common/target/jacoco.exec'
  File.write(controlled.last, 'old')
  File.symlink(outside, controlled.first + '/link')
  assert_failure('formal late output link preserves old state') { production_fixture.prepare_approved_model(collection_file, manifest_file, summary_file) }
  unchanged.call
  raise 'formal batch deleted earlier output' unless File.read(controlled.first + '/old') == 'old'
  File.unlink(controlled.first + '/link')
  production_fixture.prepare_approved_model(collection_file, manifest_file, summary_file)
  raise 'formal model outputs survived prepare' if controlled.any? { |file| File.exist?(file) }
  raise 'production prepare clear contract' if File.exist?(summary_file) || File.exist?(preserved[2]) || File.exist?(preserved[3])
  raise 'prepare escaped selected targets' unless File.read(outside) == 'keep'
  saved_manifest = File.binread(manifest_file)
  wrong_manifest = JSON.parse(saved_manifest); wrong_manifest['modelCollection']['collectionSha256'] = '0' * 64
  File.write(manifest_file, JSON.generate(wrong_manifest))
  assert_failure('production manifest association') { production_fixture.verify_approved_model(collection_file, manifest_file) }
  File.binwrite(manifest_file, saved_manifest)
  hook_calls = 0
  production_fixture.snapshot_hook = lambda do
    hook_calls += 1
    production_fixture.fixture_snapshot['drift'] = 'changed' if hook_calls == 3
  end
  assert_failure('production verify pre-execution drift') { production_fixture.verify_approved_model(collection_file, manifest_file) }
  raise 'preverify drift ran verify' if File.exist?(source_dir + '/verify-count')
  production_fixture.fixture_snapshot.delete('drift'); production_fixture.snapshot_hook = nil
  production_fixture.verify_approved_model(collection_file, manifest_file)
  raise 'production verify did not execute' unless File.readlines(source_dir + '/verify-count').size == 1
  File.write(source_dir + '/drift-after-verify', 'yes')
  production_fixture.snapshot_hook = lambda { production_fixture.fixture_snapshot['drift'] = 'changed' if File.exist?(source_dir + '/drift') }
  assert_failure('production postverify drift fails after actual clear') { production_fixture.verify_approved_model(collection_file, manifest_file) }
  raise 'postverify must preserve actual clear fact' if File.exist?(preserved[2]) || File.exist?(preserved[3])
  raise 'postverify command not executed' unless File.readlines(source_dir + '/verify-count').size == 2
  # 生产固定工具解析使用合成来源；检查精确SHA、链接/metadata、PCRE及伪环境target不授信。
  tool_root = source_dir + '/tool-fixture'
  FileUtils.mkdir_p(tool_root + '/scripts/bin')
  %w[rg docker docker-credential-desktop].each { |name| File.write(tool_root + '/scripts/bin/' + name, 'fixture-source-' + name) }
  target = tool_root + '/target'
  File.write(target, "#!/bin/sh\nexit 0\n"); File.chmod(0700, target)
  pcre_file = tool_root + '/pcre'
  File.write(pcre_file, 'literal-pcre')
  tool_metadata = lambda do |file|
    stat = File.lstat(file)
    { 'mode' => stat.mode & 0777, 'uid' => stat.uid, 'gid' => stat.gid, 'link' => nil }
  end
  target_record = { 'lexical' => target, 'canonical' => target, 'sha256' => Digest::SHA256.file(target).hexdigest }
  tool_sources = { 'aggregate' => { 'launchers' => %w[rg docker docker-credential-desktop].to_h { |name| [name, Digest::SHA256.file(tool_root + '/scripts/bin/' + name).hexdigest] } }, 'localTools' => {
    'tools' => { 'tools' => %w[rg docker compose docker-credential-desktop].to_h { |name| [name, target_record] }, 'permissionChains' => { target => tool_metadata.call(target) } },
    'pcre' => { 'lexical' => pcre_file, 'canonical' => pcre_file, 'sha256' => Digest::SHA256.file(pcre_file).hexdigest, 'permissionChains' => { pcre_file => tool_metadata.call(pcre_file) } } } }
  tool_class = Class.new(ReactorEvidence) do
    attr_accessor :fixture_sources
    def read_approved_model_sources; fixture_sources; end
  end
  tool_fixture = tool_class.new(tool_root); tool_fixture.fixture_sources = tool_sources
  %w[rg docker compose docker-credential-desktop].each { |name| raise 'wrong approved tool' unless tool_fixture.approved_local_tool(name) == target }
  assert_failure('unknown tool') { tool_fixture.approved_local_tool('other') }
  assert_failure('unknown credential helper') { tool_fixture.approved_local_tool('docker-credential-other') }
  credential_record = target_record.dup
  tool_sources.fetch('localTools').fetch('tools').fetch('tools')['docker-credential-desktop'] = credential_record
  credential_record['canonical'] = tool_root + '/unexpected-target'
  assert_failure('credential helper canonical drift') { tool_fixture.approved_local_tool('docker-credential-desktop') }
  credential_record['canonical'] = target
  credential_link = tool_root + '/credential-link'
  File.symlink(target, credential_link)
  credential_record['lexical'] = credential_link
  tool_sources.fetch('localTools').fetch('tools').fetch('permissionChains')[credential_link] = tool_metadata.call(credential_link).merge('link' => target)
  raise 'credential link rejected' unless tool_fixture.approved_local_tool('docker-credential-desktop') == target
  File.unlink(credential_link)
  File.symlink('./target', credential_link)
  assert_failure('credential link text drift with same target') { tool_fixture.approved_local_tool('docker-credential-desktop') }
  File.unlink(credential_link)
  tool_sources.fetch('localTools').fetch('tools').fetch('permissionChains').delete(credential_link)
  credential_record['lexical'] = target
  File.chmod(0600, target)
  assert_failure('credential helper permission drift') { tool_fixture.approved_local_tool('docker-credential-desktop') }
  File.chmod(0700, target)
  File.write(target, "#!/bin/sh\nexit 1\n")
  assert_failure('credential helper bytes drift') { tool_fixture.approved_local_tool('docker-credential-desktop') }
  File.write(target, "#!/bin/sh\nexit 0\n")
  File.write(tool_root + '/scripts/bin/docker-credential-desktop', 'changed-helper-launcher')
  assert_failure('credential launcher drift') { tool_fixture.approved_local_tool('docker-credential-desktop') }
  File.write(tool_root + '/scripts/bin/docker-credential-desktop', 'fixture-source-docker-credential-desktop')
  File.write(pcre_file, 'changed-pcre')
  assert_failure('PCRE drift') { tool_fixture.approved_local_tool('rg') }
  File.write(pcre_file, 'literal-pcre')
  File.write(target, "#!/bin/sh\nexit 1\n")
  assert_failure('tool bytes drift') { tool_fixture.approved_local_tool('docker') }
  File.write(target, "#!/bin/sh\nexit 0\n")
  File.write(tool_root + '/scripts/bin/rg', 'changed-launcher')
  assert_failure('source launcher drift') { tool_fixture.approved_local_tool('docker') }
  # 逐字节源码launcher以合成授权helper解析target，实际exec保留argv与退出码。
  FileUtils.mkdir_p(tool_root + '/scripts/lib')
  File.write(tool_root + '/scripts/lib/harness-reactor-evidence.rb', "class ReactorEvidence\n def initialize(root); @root=root; end\n def approved_local_tool(name); raise 'unknown' unless %w[rg docker docker-credential-desktop].include?(name); @root+'/exec-target'; end\nend\n")
  File.write(tool_root + '/exec-target', "#!/usr/bin/ruby\nrequire 'json'\nFile.write(File.dirname(__FILE__)+'/exec-argv',JSON.generate(ARGV)); exit 29\n")
  File.chmod(0700, tool_root + '/exec-target')
  %w[rg docker docker-credential-desktop].each do |name|
    launcher_source = File.dirname(helper) + '/../bin/' + name
    FileUtils.cp(launcher_source, tool_root + '/scripts/bin/' + name)
    File.chmod(0755, tool_root + '/scripts/bin/' + name)
    launcher_argv = ['space value', '', '--literal']
    if name == 'docker-credential-desktop'
      launcher_output, launcher_error, launcher_status = Open3.capture3({ 'PATH' => '/nonexistent' }, tool_root + '/scripts/bin/' + name, *launcher_argv)
      raise "launcher stderr not preserved: #{launcher_error.inspect}" unless launcher_error == ''
      raise "launcher stdout not preserved: #{launcher_output.inspect}" unless launcher_output == ''
      raise 'launcher exit not preserved' unless launcher_status.exitstatus == 29
    else
      system('/usr/bin/ruby', tool_root + '/scripts/bin/' + name, *launcher_argv)
      raise 'launcher exit not preserved' unless $?.exitstatus == 29
    end
    raise 'launcher argv not preserved' unless JSON.parse(File.read(tool_root + '/exec-argv')) == ['space value', '', '--literal']
  end
  File.write(tool_root + '/exec-target', "#!/usr/bin/ruby\nraise 'wrong protocol argv' unless ARGV == ['get']\nSTDOUT.write(STDIN.read); STDERR.write('fixture-stderr'); exit 29\n")
  [{ 'PATH' => '/nonexistent' }, { 'PATH' => nil }].each do |environment|
    protocol_output, protocol_error, protocol_status = Open3.capture3(environment, tool_root + '/scripts/bin/docker-credential-desktop', 'get', stdin_data: "fixture-registry\n", unsetenv_others: true)
    raise 'credential protocol streams not preserved' unless protocol_output == "fixture-registry\n" && protocol_error == 'fixture-stderr' && protocol_status.exitstatus == 29
  end
  # 已知共享可写链仅精确metadata接受；未知路径/metadata变化仍拒绝。
  approved_dir = source_dir + '/approved-shared'
  Dir.mkdir(approved_dir, 0775)
  File.chmod(0775, approved_dir)
  approved_stat = File.lstat(approved_dir)
  approved_record = { approved_dir => { 'mode' => 0775, 'uid' => approved_stat.uid, 'gid' => approved_stat.gid, 'link' => nil } }
  raise 'exact approved shared metadata rejected' unless evidence.approved_installation_stat?(approved_dir, approved_stat, approved_record)
  raise 'unknown shared path accepted' if evidence.approved_installation_stat?(approved_dir + '-other', approved_stat, approved_record)
  evidence.verify_local_metadata(approved_record)
  File.chmod(0755, approved_dir)
  assert_failure('approved local permission drift') { evidence.verify_local_metadata(approved_record) }
  # 固定Compose入口使用生产编排，仅来源授权替换为合成executable；不执行宿主工具。
  FileUtils.mkdir_p(root + '/deploy/local')
  %w[compose.yml compose.load-test.yml].each { |file| File.write(root + '/deploy/local/' + file, 'fixture-input') }
  compose_config_dir = root + '/.harness/t11/coverage-b2-approved-sources-v2/docker-config'
  FileUtils.mkdir_p(compose_config_dir, mode: 0700)
  File.chmod(0700, compose_config_dir)
  File.write(compose_config_dir + '/config.json', "{}\n", mode: 'w', perm: 0600)
  compose_fake = source_dir + '/compose-fake'
  File.write(compose_fake, <<~'COMPOSE_FAKE')
    #!/usr/bin/ruby
    require 'json'
    File.write(File.dirname(__FILE__) + '/compose-observed.json', JSON.generate({ 'argv' => ARGV, 'cwd' => Dir.pwd, 'config' => ENV['DOCKER_CONFIG'], 'injected' => ENV.select { |key, _| key.start_with?('COMPOSE_') }, 'path' => ENV['PATH'] }))
    marker = File.dirname(__FILE__) + '/compose-mutation'
    if File.exist?(marker)
      target = JSON.parse(File.read(marker))
      File.write(target.fetch('file'), target.fetch('bytes'))
    end
    exit(File.exist?(File.dirname(__FILE__) + '/compose-fail') ? 23 : 0)
  COMPOSE_FAKE
  File.chmod(0700, compose_fake)
  compose_fixture_class = Class.new(ReactorEvidence) do
    attr_accessor :fixture_compose, :fixture_home, :fixture_sha, :fixture_null_stat
    def approved_compose_yaml
      { 'deploy/local/compose.yml' => Digest::SHA256.hexdigest('fixture-input'),
        'deploy/local/compose.load-test.yml' => Digest::SHA256.hexdigest('fixture-input') }
    end
    def compose_null_stat
      fixture_null_stat || super
    end
    def read_approved_model_sources
      { 'mavenJdk' => { 'layout' => { 'userHome' => fixture_home } } }
    end
    def approved_local_tool(name)
      raise 'unexpected fixture tool' unless name == 'compose'
      raise 'fixture source drift' unless Digest::SHA256.file(fixture_compose).hexdigest == fixture_sha
      fixture_compose
    end
  end
  compose_fixture = compose_fixture_class.new(root)
  compose_fixture.fixture_compose = compose_fake
  compose_fixture.fixture_sha = Digest::SHA256.file(compose_fake).hexdigest
  compose_fixture.fixture_home = ENV.fetch('HOME')
  old_compose_env = %w[COMPOSE_FILE COMPOSE_ENV_FILES COMPOSE_DISABLE_ENV_FILE COMPOSE_PROJECT_NAME].to_h { |key| [key, ENV[key]] }
  old_compose_env.each_key { |key| ENV[key] = 'must-not-inherit' }
  begin
    %w[local load].each do |scope|
      raise 'fixed Compose config failed' unless compose_fixture.compose_config(scope) == 0
      observed = JSON.parse(File.read(source_dir + '/compose-observed.json'))
      files = ['-f', root + '/deploy/local/compose.yml']
      files += ['-f', root + '/deploy/local/compose.load-test.yml'] if scope == 'load'
      profiles = scope == 'local' ? %w[--profile infra --profile full] : %w[--profile full]
      raise 'fixed Compose argv/order changed' unless observed.fetch('argv') == ['--env-file', '/dev/null'] + files + profiles + %w[config --format json]
      raise 'fixed Compose context changed' unless observed.fetch('cwd') == root && observed.fetch('config') == compose_config_dir && observed.fetch('injected').empty? && observed.fetch('path') == '/usr/bin:/bin'
    end
    observed_file = source_dir + '/compose-observed.json'
    reject_before_compose = lambda do |label, expected_error|
      File.delete(observed_file) if File.exist?(observed_file)
      begin
        compose_fixture.compose_config('local')
      rescue StandardError => error
        raise "wrong Compose rejection: #{label}: #{error.message}" unless error.message == expected_error
      else
        raise "Compose accepted #{label}"
      end
      raise "Compose ran before rejection: #{label}" if File.exist?(observed_file)
    end
    yaml_file = root + '/deploy/local/compose.yml'
    external = root + '/external.yml'
    ["include: [../../external.yml]\n", "services: {app: {extends: {file: ../../external.yml, service: base}}}\n", "services: {app: {env_file: ../../external.yml}}\n", 'unknown-input'].each do |bytes|
      File.binwrite(yaml_file, bytes)
      %w[first second].each do |external_bytes|
        File.binwrite(external, external_bytes)
        reject_before_compose.call('unapproved YAML with stable external reference', 'unapproved Compose YAML bytes')
        raise 'main YAML changed in external fixture' unless File.binread(yaml_file) == bytes
      end
    end
    File.binwrite(yaml_file, 'fixture-input')
    File.delete(yaml_file); File.symlink(external, yaml_file)
    reject_before_compose.call('YAML symlink', "symlink not allowed: #{yaml_file}")
    File.delete(yaml_file); File.binwrite(yaml_file, 'fixture-input')
    # 正式类不使用fixture批准表；任意相同fixture字节不能晋级生产准入。
    begin
      ReactorEvidence.new(root).compose_yaml_inputs(['deploy/local/compose.yml'])
      raise 'production admitted fixture YAML'
    rescue StandardError => error
      raise unless error.message == 'unapproved Compose YAML bytes'
    end
    [Struct.new(:chardev?, :symlink?).new(false, false), Struct.new(:chardev?, :symlink?).new(true, true)].each do |bad_stat|
      compose_fixture.fixture_null_stat = bad_stat
      reject_before_compose.call('invalid null device', 'invalid Compose null env-file')
    end
    compose_fixture.fixture_null_stat = nil
    File.write(source_dir + '/compose-fail', 'yes')
    raise 'Compose failure not propagated' unless compose_fixture.compose_config('local') == 23
    assert_failure('unknown Compose scope') { compose_fixture.compose_config('other') }
    File.delete(source_dir + '/compose-fail')
    ENV['HOME'] = compose_fixture.fixture_home + '-wrong'
    assert_failure('caller HOME mismatch') { compose_fixture.compose_config('local') }
    ENV['HOME'] = compose_fixture.fixture_home
    dotenv = root + '/.env'
    project_dotenv = root + '/deploy/local/.env'
    original_fake = File.binread(compose_fake)
    mutations = [
      ['config bytes', compose_config_dir + '/config.json', "{}\n", "{\"changed\":true}\n"],
      ['YAML bytes', root + '/deploy/local/compose.yml', 'fixture-input', 'changed-input'],
      ['source bytes', compose_fake, original_fake, original_fake + "\n#changed\n"],
      ['dotenv appears', dotenv, nil, "KEY=literal\n"],
      ['dotenv changes', dotenv, "KEY=literal\n", "KEY=other\n"],
      ['project dotenv changes', project_dotenv, "KEY=literal\n", "KEY=other\n"]
    ]
    mutations.each do |label, file, before, after|
      before.nil? ? (File.delete(file) if File.exist?(file)) : File.binwrite(file, before)
      File.write(source_dir + '/compose-mutation', JSON.generate({ 'file' => file, 'bytes' => after }))
      assert_failure('production Compose postcheck: ' + label) { compose_fixture.compose_config('local') }
      raise 'Compose mutation child did not run: ' + label unless File.binread(file) == after
      File.delete(source_dir + '/compose-mutation')
      before.nil? ? File.delete(file) : File.binwrite(file, before)
    end
    File.delete(dotenv) if File.exist?(dotenv)
    File.symlink(root + '/deploy/local/compose.yml', dotenv)
    assert_failure('dotenv symlink rejected') { compose_fixture.compose_config('local') }
    File.delete(dotenv)
  ensure
    ENV['HOME'] = compose_fixture.fixture_home

    old_compose_env.each { |key, value| value ? ENV[key] = value : ENV.delete(key) }
  end
  mock_calls = []
  mock_snapshot = lambda { { 'source' => 'fixture-frozen', 'environment' => 'fixture-frozen' } }
  mock_runner = lambda do |argv, cwd|
    raise 'B2 mock cwd' unless cwd == root
    mock_calls << argv
    if argv.any? { |arg| arg.start_with?('-Doutput=') }
      File.binwrite(argv.last.delete_prefix('-Doutput='), projection_xml)
      model_result.dup
    elsif argv.include?('-DforceStdout')
      repo_result.dup
    else
      active_result.dup
    end
  end
  mock_arguments = { output: private_parent + '/b2-collect-positive', snapshot: mock_snapshot, runner: mock_runner,
    model_context: order_binding.fetch('model'), verify_context: order_binding.fetch('verify'),
    settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha }
  mock_collected = evidence.collect_model_fixture(**mock_arguments)
  raise 'B2 mock full readers' unless mock_collected.fetch('projectCount') == 53 && mock_calls.size == 3 && mock_collected.fetch('hostProvenance') == false
  raise 'B2 mock no lifecycle invocation' if mock_calls.flatten.any? { |arg| %w[verify clean prepare].include?(arg) }
  mock_calls.clear
  failed_runner = lambda do |argv, cwd|
    mock_calls << argv
    model_result.merge('exitCode' => 1)
  end
  assert_failure('B2 command failure blocks later commands') { evidence.collect_model_fixture(**mock_arguments.merge(output: private_parent + '/b2-collect-command-failure', runner: failed_runner)) }
  raise 'B2 later command ran after failure' unless mock_calls.size == 1
  mock_calls.clear
  snapshot_reads = 0
  changed_snapshot = lambda { snapshot_reads += 1; { 'source' => snapshot_reads < 3 ? 'before' : 'after' } }
  assert_failure('B2 source change invalidates entire collection') { evidence.collect_model_fixture(**mock_arguments.merge(output: private_parent + '/b2-collect-source-failure', snapshot: changed_snapshot)) }
  raise 'B2 commands ran after source mutation' unless mock_calls.size == 1
  lifecycle_events = []
  evidence.run_model_lifecycle_fixture(collect: lambda { lifecycle_events << 'collect'; mock_collected }, clear: lambda { lifecycle_events << 'clear' }, verify: lambda { lifecycle_events << 'verify' })
  raise 'B2 fixture lifecycle order' unless lifecycle_events == %w[collect clear verify]
  lifecycle_events.clear
  assert_failure('B2 failed collection zero clear zero verify') do
    evidence.run_model_lifecycle_fixture(collect: lambda { raise 'fixture reader failure' }, clear: lambda { lifecycle_events << 'clear' }, verify: lambda { lifecycle_events << 'verify' })
  end
  raise 'B2 failure reached lifecycle' unless lifecycle_events.empty?
  assert_failure('B2 host result forbidden on fixture lifecycle') do
    evidence.run_model_lifecycle_fixture(collect: lambda { mock_collected.merge('hostProvenance' => true) }, clear: lambda { lifecycle_events << 'clear' }, verify: lambda { lifecycle_events << 'verify' })
  end
  raise 'B2 host result reached fixture lifecycle' unless lifecycle_events.empty?
  preflight_events = []
  preflight_source = { 'source' => 'frozen', 'environment' => 'same' }
  preflight_arguments = {
    attest: lambda { preflight_events << 'attest'; preflight_source.dup },
    readiness: lambda { preflight_events << 'readiness' }, clean: lambda { preflight_events << 'clean' },
    selftests: lambda { preflight_events << 'selftests' }, collect: lambda { preflight_events << 'collect'; 'fixture-proof' },
    prepare: lambda { |proof| raise unless proof == 'fixture-proof'; preflight_events << 'prepare' },
    verify: lambda { |proof| raise unless proof == 'fixture-proof'; preflight_events << 'verify' }
  }
  evidence.run_full_preflight_fixture(**preflight_arguments)
  raise 'B2 source-first same-environment fixture order' unless preflight_events == %w[attest readiness attest clean selftests attest collect attest prepare verify]
  preflight_events.clear
  assert_failure('B2 source rejection launches no tools') do
    evidence.run_full_preflight_fixture(**preflight_arguments.merge(attest: lambda { raise 'fixture unapproved source' }))
  end
  raise 'B2 source failure reached tools' unless preflight_events.empty?
  assert_failure('B2 readiness failure zero collect prepare verify') do
    evidence.run_full_preflight_fixture(**preflight_arguments.merge(readiness: lambda { preflight_events << 'readiness'; raise 'fixture readiness failure' }))
  end
  raise 'B2 readiness failure reached later steps' unless preflight_events == %w[attest readiness]
  preflight_events.clear
  assert_failure('B2 source drift after readiness') do
    evidence.run_full_preflight_fixture(**preflight_arguments.merge(readiness: lambda { preflight_events << 'readiness'; preflight_source['source'] = 'changed' }))
  end
  raise 'B2 readiness drift reached collect' unless preflight_events == %w[attest readiness attest]
  preflight_source['source'] = 'frozen'
  # 修改无语义注释也必须被每次入口重读的raw SHA捕获，再恢复本次fixture。
  raw_root_before = File.binread(root + '/pom.xml')
  begin
    File.binwrite(root + '/pom.xml', raw_root_before + '<!-- B1 same identity -->')
    assert_failure('whole stdout rereads raw bytes') { read_stdout.call(stdout_fixture) }
  ensure
    File.binwrite(root + '/pom.xml', raw_root_before)
  end
  raise 'whole stdout raw restored positive' unless read_stdout.call(stdout_fixture).fetch('projectCount') == 53
  long_summary_projects = [
    { 'gav' => 'com.co.kc.im:im-chat:0.0.1-SNAPSHOT', 'name' => '😀' * 26, 'version' => '1' },
    { 'gav' => 'com.co.kc.im:child:1', 'name' => 'x' * 51, 'version' => '1' }
  ]
  long_expected = "[INFO] ------------------------------------------------------------------------\n[INFO] Reactor Summary for " + '😀' * 26 + " 1:\n[INFO] \n[INFO] " + '😀' * 26 + " SUCCESS [1 d 00:00 h]\n[INFO] " + 'x' * 51 + "  SKIPPED\n"
  raise 'summary UTF16 threshold/no truncation/long duration' unless evidence.format_active_profiles_summary(long_summary_projects, '1 d 00:00 h') == long_expected
  reordered_expected = mixed_summary.sub("[INFO] Root 1 ............................................. SUCCESS [  1.234 s]\n[INFO] Child 2 ............................................ SKIPPED\n", "[INFO] Child 2 ............................................ SKIPPED\n[INFO] Root 1 ............................................. SUCCESS [  1.234 s]\n")
  raise 'summary root not first display row' unless evidence.format_active_profiles_summary(mixed_projects.reverse, '1.234 s') == reordered_expected
  body_oracle.each do |entry|
    original = entry.fetch('packaging')
    alternative = original == 'pom' ? 'jar' : 'pom'
    [alternative, 'war'].each do |replacement|
      changed_packaging_oracle = body_oracle.map { |candidate| candidate == entry ? candidate.merge('packaging' => replacement) : candidate }
      group, artifact, version = entry.fetch('gav').split(':')
      old_id = [group, artifact, original, version].join(':')
      new_id = [group, artifact, replacement, version].join(':')
      begin
        evidence.read_active_profiles_body(active_body.sub(old_id, new_id), oracle: changed_packaging_oracle, settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
      rescue StandardError => error
        raise 'unsafe packaging rejection' unless error.message == 'invalid active profiles oracle'
        next
      end
      raise 'body and oracle joint packaging substitution accepted'
    end
  end
  body_summary = read_body.call(active_body)
  raise 'active body positive' unless body_summary.fetch('projectCount') == 53 && body_summary.fetch('bodySha256') == Digest::SHA256.hexdigest(active_body)
  raise 'same-call settings active summary' unless body_summary.fetch('settings').fetch('activeProfileIdentitySha256') == settings_identity_sha && body_summary.fetch('settings').fetch('settingsSha256') == settings_sha
  File.binwrite(root + '/pom.xml', raw_root_bytes.sub('</project>', '<!-- changed after oracle --></project>'))
  begin
    assert_failure('active body rejects raw bytes changed with stale caller SHA') { read_body.call(active_body) }
  ensure
    File.binwrite(root + '/pom.xml', raw_root_bytes)
  end
  reject_body_oracle = lambda do |candidate|
    begin
      evidence.read_active_profiles_body(active_body, oracle: candidate, settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha)
    rescue StandardError => error
      raise 'unsafe raw body rejection' unless error.message == 'invalid active profiles oracle'
      next
    end
    raise 'raw body oracle mutation accepted'
  end
  %w[gav pom basedir packaging modules sha256].each do |key|
    candidate = Marshal.load(Marshal.dump(body_oracle))
    candidate.first[key] = key == 'modules' ? ['foreign'] : 'foreign'
    reject_body_oracle.call(candidate)
  end
  candidate = Marshal.load(Marshal.dump(body_oracle))
  candidate.first.delete('sha256')
  reject_body_oracle.call(candidate)
  File.binwrite(root + '/pom.xml', raw_root_bytes.sub('</project>', '<!-- changed after oracle --></project>'))
  reject_body_oracle.call(body_oracle)
  refreshed = evidence.read_raw_pom_oracle
  raise 'active body refreshed raw positive' unless evidence.read_active_profiles_body(active_body, oracle: refreshed, settings_xml: settings_xml, settings_sha256: settings_sha, settings_identity_sha: settings_identity_sha).fetch('projectCount') == 53
  File.binwrite(root + '/pom.xml', raw_root_bytes.sub('<modules>', '<modules unexpected="x">'))
  reject_body_oracle.call(body_oracle)
  File.binwrite(root + '/pom.xml', raw_root_bytes)
  raise 'active body raw restoration' unless read_body.call(active_body).fetch('projectCount') == 53
  assert_failure('active body wrong source') { read_body.call(active_body.sub('(source: external)', '(source: project)')) }
  assert_failure('active body wrong identity') { read_body.call(active_body.sub(' - fixture-a ', ' - foreign ')) }
  reject_body = lambda do |body|
    begin
      read_body.call(body)
    rescue StandardError => error
      raise 'unsafe active body error' unless %w[body projects source identity].any? { |reason| error.message == 'invalid active profiles ' + reason }
      next
    end
    raise 'invalid active body accepted'
  end
  first_block = "\nActive Profiles for Project 'com.co.kc.im:im-chat:pom:0.0.1-SNAPSHOT':\n\nThe following profiles are active:\n\n - fixture-a (source: external)\n\n\n"
  [active_body.delete_prefix(first_block), active_body + first_block,
   active_body.sub('im-plugin:pom', 'im-chat:pom'), active_body.sub('im-plugin:pom', 'im-plugin:jar'),
   active_body.sub('im-plugin:pom', 'unknown:pom'), active_body.sub(' - fixture-a ', ' - fixture-b '),
   active_body.sub(' - fixture-a ', ' - Fixture-a '), active_body.sub(' - fixture-a ', ' - fixture-a  '),
   active_body.sub(' - fixture-a ', ' -  fixture-a '), active_body.sub(' - fixture-a ', ' -  '),
   active_body.sub('(source: external)', '(source: external)\n - extra (source: project)'),
   active_body.sub('The following profiles are active:', 'There are no active profiles.'),
   active_body.sub('(source: external)', '(source: unknown)'), active_body.sub("\n\n", "\n"),
   "[INFO] " + active_body, active_body + "unknown\n", active_body + "\n",
   "﻿" + active_body, active_body.gsub("\n", "\r\n"), active_body + "\r",
   active_body + "\e[0m", active_body + "\0", active_body + "\t", active_body + "\u0085",
   active_body.b + "\xff".b].each { |body| reject_body.call(body) }
  assert_failure('settings bytes SHA mismatch') { read_body.call(active_body, settings_xml + ' ', settings_sha) }
  switched_settings = settings_xml.sub('<activeProfile>fixture-a</activeProfile>', '<activeProfile>fixture-b</activeProfile>')
  switched_sha = Digest::SHA256.hexdigest(switched_settings)
  second_identity = Digest::SHA256.hexdigest('fixture-b')
  assert_failure('settings active changed under old expected SHA') { read_body.call(active_body, switched_settings, switched_sha) }
  assert_failure('body must follow same-call settings active') { read_body.call(active_body, switched_settings, switched_sha, second_identity) }
  switched = read_body.call(active_body.gsub(' - fixture-a ', ' - fixture-b '), switched_settings, switched_sha, second_identity)
  raise 'same-call active settings summary' unless switched.fetch('settings').fetch('activeProfileIdentitySha256') == second_identity && switched.fetch('settings').fetch('settingsSha256') == switched_sha
  encoded_summary = read_body.call(active_body, encoded_settings, Digest::SHA256.hexdigest(encoded_settings))
  raise 'settings decoded bytes association' unless encoded_summary.fetch('settings').fetch('activeProfileIdentitySha256') == settings_identity_sha
  unicode_settings = settings_xml.gsub('fixture-a', "fixture-é")
  unicode_sha = Digest::SHA256.hexdigest("fixture-é")
  read_body.call(active_body.gsub('fixture-a', "fixture-é"), unicode_settings, Digest::SHA256.hexdigest(unicode_settings), unicode_sha)
  assert_failure('profile identity no Unicode normalization') { read_body.call(active_body.gsub('fixture-a', "fixture-é"), unicode_settings, Digest::SHA256.hexdigest(unicode_settings), unicode_sha) }
  raise 'formal 53 model summary' unless formal_summary.fetch('modules').size == 53
  assert_failure('formal reduced oracle') { evidence.read_effective_model(formal_xml, formal_oracle.first(52), model_context, contract: :formal) }
  expanded_oracle = formal_oracle + [formal_oracle.last.merge('gav' => 'example:z:1')]
  assert_failure('formal expanded oracle') { evidence.read_effective_model(formal_xml, expanded_oracle, model_context, contract: :formal) }
  replaced_oracle = formal_oracle.dup
  replaced_oracle[-1] = formal_oracle.last.merge('gav' => 'example:replacement:1')
  raise 'replacement must preserve cardinality' unless replaced_oracle.size == 53
  assert_failure('formal replaced identity') { evidence.read_effective_model(formal_xml, replaced_oracle, model_context, contract: :formal) }
  duplicate_oracle = formal_oracle.dup
  duplicate_oracle[-1] = formal_oracle.first
  raise 'duplicate must preserve cardinality' unless duplicate_oracle.size == 53
  assert_failure('formal duplicate identity') { evidence.read_effective_model(formal_xml, duplicate_oracle, model_context, contract: :formal) }
  path_oracle = formal_oracle.dup
  path_oracle[0] = path_oracle[0].merge('pom' => root + '/other/pom.xml')
  assert_failure('formal canonical path') { evidence.read_effective_model(formal_xml, path_oracle, model_context, contract: :formal) }
  raise 'model semantic identity summary' unless model_summary.fetch('modules').map { |m| m.fetch('gav') } == ['example:parent:1', 'example:child:1']
  raise 'model semantic path summary' unless model_summary.fetch('modules').last.fetch('basedir') == root + '/child'
  reject_model = lambda do |label, content, expected_reason, oracle = model_oracle, context = model_context|
    begin
      evidence.read_effective_model(content, oracle, context)
    rescue StandardError => error
      raise "wrong model rejection #{label}: #{error.message}" unless error.message.include?(expected_reason)
      next
    end
    raise "expected model rejection: #{label}"
  end
  reject_model.call('wrong output', valid_model.sub(root + '/child/target/classes', root + '/child/target/elsewhere'), 'non-default model outputDirectory')
  reject_model.call('missing module', '<projects>' + parent_model + '</projects>', 'effective module set')
  {
    'extra module' => valid_model.sub('</projects>', child_model.sub('<artifactId>child</artifactId>', '<artifactId>extra</artifactId>') + '</projects>'),
    'identity mismatch' => valid_model.sub('<groupId>example</groupId>', '<groupId>foreign</groupId>')
  }.each { |label, xml| reject_model.call(label, xml, 'unknown model identity') }
  reject_model.call('duplicate module', valid_model.sub('</projects>', child_model + '</projects>'), 'effective module set')
  reject_model.call('illegal wrapper', valid_model.sub('<projects>', '<models>').sub('</projects>', '</models>'), 'effective wrapper')
  reject_model.call('single project', child_model, 'effective wrapper')
  reject_model.call('wrapper member', valid_model.sub('</projects>', '<unknown/></projects>'), 'wrapper children')
  reject_model.call('namespace', valid_model.sub('http://maven.apache.org/POM/4.0.0', 'urn:wrong'), 'POM namespace')
  reject_model.call('packaging', valid_model.sub('<packaging>jar</packaging>', '<packaging>pom</packaging>'), 'packaging mismatch')
  reject_model.call('module path', valid_model.sub('<module>child</module>', '<module>elsewhere</module>'), 'module declaration')
  reject_model.call('duplicate scalar', valid_model.sub('<groupId>example</groupId>', '<groupId>example</groupId><groupId>example</groupId>'), 'unique model field')
  reject_model.call('nested scalar', valid_model.sub('<artifactId>child</artifactId>', '<artifactId><value>child</value></artifactId>'), 'plain model scalar')
  %w[directory sourceDirectory testSourceDirectory outputDirectory testOutputDirectory].each do |field|
    bad = valid_model.sub(/<#{field}>[^<]+<\/#{field}>/, "<#{field}>${unknown}</#{field}>")
    reject_model.call("dynamic #{field}", bad, 'non-default model')
  end
  reject_model.call('DTD', '<!DOCTYPE projects><projects/>', 'DTD not supported')
  %w[compile package verify].each do |phase|
    %w[clean install deploy site site-deploy].each do |from|
      reject_model.call("rebound #{from} #{phase}", valid_model.sub('<phase>' + from + '</phase>', '<phase>' + phase + '</phase>'), 'unapproved lifecycle')
    end
  end
  reject_model.call('unknown goal', valid_model.sub('<goal>compile</goal>', '<goal>other</goal>'), 'unapproved lifecycle')
  reject_model.call('unknown empty phase', valid_model.sub('<phase>compile</phase>', ''), 'unapproved lifecycle')
  reject_model.call('added execution', valid_model.sub('</executions>', '<execution><phase>compile</phase><goals><goal>exec</goal></goals></execution></executions>'), 'configuration differs')
  reject_model.call('unknown plugin', valid_model.sub('maven-clean-plugin', 'build-helper-maven-plugin'), 'unknown effective plugin')
  reject_model.call('wrong compiler version', valid_model.sub('3.14.1', '9.9'), 'unknown effective plugin')
  reject_model.call('processor version', valid_model.sub('1.5.5.Final', '${mapstruct.version}'), 'configuration differs')
  reject_model.call('extra processor', valid_model.sub('</annotationProcessorPaths>', '<path><groupId>third.party</groupId><artifactId>processor</artifactId><version>1</version></path></annotationProcessorPaths>'), 'configuration differs')
  reject_model.call('late token compiler', valid_model.sub('<release>21</release>', '<release>${start-class}</release>'), 'configuration differs')
  reject_model.call('skip', valid_model.sub('<argLine>', '<skipTests>true</skipTests><argLine>'), 'configuration differs')
  reject_model.call('argLine injection', valid_model.sub('</argLine>', ' -javaagent:foreign.jar</argLine>'), 'configuration differs')
  reject_model.call('unresolved Mockito', valid_model.sub('mockito-core/5.17.0', 'mockito-core/${mockito.version}'), 'configuration differs')
  reject_model.call('report filter', valid_model.sub('<id>report</id><phase>verify</phase><goals><goal>report</goal></goals>', '<id>report</id><phase>verify</phase><goals><goal>report</goal></goals><configuration><excludes><exclude>**/*</exclude></excludes></configuration>'), 'configuration differs')
  reject_model.call('agent range', valid_model.sub('com.co.kc.imchat.*', '*'), 'configuration differs')
  %w[clean install deploy site].each { |goal| reject_model.call("invocation #{goal}", valid_model, 'unreviewed invocation', model_oracle, model_context.merge('verifyArgv' => ['mvn', '-q', 'verify', goal])) }
  reject_model.call('unreviewed source', valid_model, 'unreviewed invocation', model_oracle, model_context.merge('sourceReviewed' => false))
  reject_model.call('relative repository', valid_model, 'invalid local repository', model_oracle, model_context.merge('localRepository' => 'repository'))
  reject_model.call('oracle duplicate path', valid_model, 'duplicate oracle pom', model_oracle + [model_oracle.first.merge('gav' => 'example:other:1')])
  reject_model.call('oracle escape', valid_model, 'oracle path mismatch', [model_oracle.first.merge('basedir' => '/outside')])
  FileUtils.mkdir_p(root + '/linked-real')
  File.symlink(root + '/linked-real', root + '/linked')
  reject_model.call('oracle symlink', valid_model, 'symlink not allowed', [model_oracle.first.merge('basedir' => root + '/linked', 'pom' => root + '/linked/pom.xml')])
  File.unlink(root + '/linked')
  # Shade 正例保留全局true、execution false、provided scope、签名资源及Boot plugin依赖。
  filters = '<filters><filter><artifact>*:*</artifact><excludes><exclude>META-INF/*.SF</exclude><exclude>META-INF/*.DSA</exclude><exclude>META-INF/*.RSA</exclude></excludes></filter></filters>'
  shade_base = '<keepDependenciesWithProvidedScope>true</keepDependenciesWithProvidedScope><createDependencyReducedPom>true</createDependencyReducedPom>' + filters
  shade_run = '<createDependencyReducedPom>false</createDependencyReducedPom><transformers combine.self="override"><transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer"><mainClass>com.co.kc.imchat.management.ops.cli.OpsCli</mainClass></transformer></transformers><keepDependenciesWithProvidedScope>true</keepDependenciesWithProvidedScope>' + filters
  shade_deps = '<dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId><version>3.5.14</version><scope>compile</scope></dependency></dependencies>'
  shade = fixture_plugin.call('maven-shade-plugin', '3.6.0', shade_base, [['', 'package', 'shade', shade_run]], nil, shade_deps)
  special_model = lambda do |relative, extra|
    base = root + '/' + relative
    oracle = [{ 'gav' => 'example:special:1', 'pom' => base + '/pom.xml', 'basedir' => base, 'packaging' => 'jar', 'modules' => [] }]
    ['<projects>' + model_project.call('special', base, 'jar', [], extra) + '</projects>', oracle]
  end
  shade_xml, shade_oracle = special_model.call('im-management/im-ops/im-ops-cli', shade)
  raise 'Shade legitimate model missing' unless evidence.read_effective_model(shade_xml, shade_oracle, model_context).fetch('modules').first.fetch('plugins').include?('maven-shade-plugin')
  {
    'missing execution false' => shade_xml.sub('<createDependencyReducedPom>false</createDependencyReducedPom>', ''),
    'provided scope' => shade_xml.sub('<keepDependenciesWithProvidedScope>true', '<keepDependenciesWithProvidedScope>false'),
    'class filter' => shade_xml.sub('META-INF/*.SF', '**/*.class'),
    'wider filter' => shade_xml.sub('META-INF/*.SF', 'META-INF/*'),
    'extra filter' => shade_xml.sub('</excludes>', '<exclude>extra</exclude></excludes>'),
    'relocation' => shade_xml.sub('</transformers>', '</transformers><relocations/>'),
    'minimize' => shade_xml.sub('</transformers>', '</transformers><minimizeJar>true</minimizeJar>'),
    'output' => shade_xml.sub('</transformers>', '</transformers><outputFile>elsewhere.jar</outputFile>'),
    'extra transformer' => shade_xml.sub('</transformers>', '<transformer implementation="other"/></transformers>'),
    'wrong combine' => shade_xml.sub('combine.self="override"', 'combine.children="append"')
  }.each { |label, xml| reject_model.call('Shade ' + label, xml, 'configuration differs', shade_oracle) }
  reject_model.call('Shade Boot dependency', shade_xml.sub('<scope>compile</scope>', '<scope>provided</scope>'), 'unapproved Shade plugin dependency', shade_oracle)
  boot_config = '<classifier>exec</classifier><mainClass>${start-class}</mainClass>'
  boot_plugin = fixture_plugin.call('spring-boot-maven-plugin', '3.5.14', boot_config, [['repackage', '', 'repackage', boot_config]], 'org.springframework.boot')
  boot_xml, boot_oracle = special_model.call('im-broker/im-broker-server', boot_plugin)
  evidence.read_effective_model(boot_xml, boot_oracle, model_context)
  reject_model.call('Boot goal', boot_xml.sub('<goal>repackage</goal>', '<goal>run</goal>'), 'unapproved lifecycle', boot_oracle)
  ui_commands = [['install-admin-ui', 'prepare-package', 'exec', '<workingDirectory>' + root + '/im-management/im-admin/ui</workingDirectory><executable>npm</executable><arguments><argument>ci</argument><argument>--prefer-offline</argument><argument>--no-audit</argument><argument>--no-fund</argument></arguments>'], ['build-admin-ui', 'prepare-package', 'exec', '<workingDirectory>' + root + '/im-management/im-admin/ui</workingDirectory><executable>npm</executable><arguments><argument>run</argument><argument>build</argument></arguments>']]
  ui_xml, ui_oracle = special_model.call('im-management/im-admin', fixture_plugin.call('exec-maven-plugin', '3.6.3', nil, ui_commands, 'org.codehaus.mojo'))
  evidence.read_effective_model(ui_xml, ui_oracle, model_context)
  reject_model.call('UI command', ui_xml.sub('<argument>ci</argument>', '<argument>exec</argument>'), 'configuration differs', ui_oracle)
  reject_model.call('UI directory', ui_xml.sub('/im-admin/ui</workingDirectory>', '/im-admin/elsewhere</workingDirectory>'), 'configuration differs', ui_oracle)
  reject_model.call('UI phase', ui_xml.sub('<phase>prepare-package</phase>', '<phase>compile</phase>'), 'unapproved lifecycle', ui_oracle)
  load_xml, load_oracle = special_model.call('im-test/im-load-test', fixture_plugin.call('exec-maven-plugin', '3.5.0', '<classpathScope>test</classpathScope>', [], 'org.codehaus.mojo'))
  evidence.read_effective_model(load_xml, load_oracle, model_context)
  load_bad = fixture_plugin.call('exec-maven-plugin', '3.5.0', '<classpathScope>test</classpathScope>', [['added', 'compile', 'exec', nil]], 'org.codehaus.mojo')
  load_bad_xml, = special_model.call('im-test/im-load-test', load_bad)
  reject_model.call('load execution', load_bad_xml, 'configuration differs', load_oracle)
  manifest = File.join(root, 'manifest.json')
  class_file = 'im-common/target/classes/com/co/kc/imchat/fixture/Sample.class'
  class_bytes = lambda do |name, extra = ''.b, slots = 0|
    utf8 = ->(text) { [1, text.bytesize].pack('Cn') + text.b }
    [0xcafebabe, 0, 65, 5 + slots].pack('Nnnn') + utf8.call(name) + [7, 1].pack('Cn') +
      utf8.call('java/lang/Object') + [7, 3].pack('Cn') + extra + [0x21, 2, 4, 0, 0, 0, 0].pack('nnnnnnn')
  end
  reject_class = lambda do |label, bytes, diagnostic|
    write.call(class_file, bytes)
    begin
      evidence.class_internal_name(File.join(root, class_file))
    rescue StandardError => error
      raise "wrong class rejection: #{label}: #{error.message}" unless error.message.include?(diagnostic)
    else
      raise "class accepted: #{label}"
    end
  end
  owned_name = 'com/co/kc/imchat/fixture/Sample'
  owned_class = class_bytes.call(owned_name)
  [owned_name, owned_name + '$Nested', owned_name + '$1', 'com/co/kc/imchat/fixture/package-info'].each do |name|
    write.call(class_file, class_bytes.call(name))
    raise 'owned class name changed' unless evidence.class_internal_name(File.join(root, class_file)) == name
  end
  extra_constants = [3, 4, 9, 10, 11, 12, 17, 18].map { |tag| [tag, 0].pack('CN') }.join.b +
    [5, 6].map { |tag| [tag, 0, 0].pack('CNN') }.join.b +
    [8, 16, 19, 20].map { |tag| [tag, 1].pack('Cn') }.join.b + [15, 1, 1].pack('CCn')
  write.call(class_file, class_bytes.call(owned_name, extra_constants, 17))
  raise 'constant pool alignment changed' unless evidence.class_internal_name(File.join(root, class_file)) == owned_name
  reject_class.call('magic', owned_class.sub([0xcafebabe].pack('N'), [0].pack('N')), 'invalid header')
  [[0, 44], [0, 66], [1, 65], [65535, 55]].each do |minor, major|
    bytes = owned_class.dup
    bytes[4, 4] = [minor, major].pack('nn')
    reject_class.call('version', bytes, 'invalid header')
  end
  [0, 9, 10, 11, 12, 14, owned_class.bytesize - 10].each do |length|
    reject_class.call("truncated #{length}", owned_class.byteslice(0, length), 'truncated header')
  end
  reject_class.call('unknown tag', class_bytes.call(owned_name, [2].pack('C'), 1), 'unknown constant tag')
  [5, 6].each do |tag|
    reject_class.call('double slot', class_bytes.call(owned_name, [tag, 0, 0].pack('CNN'), 1), 'invalid double slot')
    reject_class.call('truncated double', class_bytes.call(owned_name, [tag].pack('C'), 2).byteslice(0, owned_class.bytesize - 13), 'truncated header')
  end
  [0, 1, 5, 65535].each do |index|
    bytes = owned_class.dup
    bytes[-12, 2] = [index].pack('n')
    reject_class.call('this_class index', bytes, 'invalid this_class')
  end
  [0, 2, 5, 65535].each do |index|
    bytes = owned_class.dup
    bytes[14 + owned_name.bytesize, 2] = [index].pack('n')
    reject_class.call('name index', bytes, 'invalid this_class')
  end
  ['foreign/Sample', 'com/co/kc/imchatOther/Sample', 'com/co/kc/imchat/../Sample', 'com/co/kc/imchat/module-info', "com/co/kc/imchat/Bad\x00Name"].each do |name|
    reject_class.call('foreign/unsupported name', class_bytes.call(name), 'foreign or unsupported internal name')
  end
  foreign_class = class_bytes.call('foreign/Sample')
  write.call(class_file, foreign_class)
  evidence.prepare(manifest)
  data = JSON.parse(File.read(manifest))
  reports = data['groups'].values.flatten.uniq { |entry| entry['testClass'] }
  fill = lambda do
    reports.each do |entry|
      klass = entry['testClass']
      write.call(evidence.report(entry), "<testsuite name=\"#{klass}\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"#{klass}\" name=\"works\"/><testcase classname=\"#{klass}\" name=\"parameter(String)[1]\"><system-out>中文输出</system-out></testcase></testsuite>")
    end
    optional = reports.find { |entry| entry['testClass'] == 'example.OptionalDatabaseTest' }
    write.call(evidence.report(optional), '<testsuite name="example.OptionalDatabaseTest" tests="2" failures="0" errors="0" skipped="2"><testcase classname="example.OptionalDatabaseTest" name="works"><skipped message="System property [fixture.database] does not exist"/></testcase><testcase classname="example.OptionalDatabaseTest" name="second"><skipped message="System property [fixture.database] does not exist"/></testcase></testsuite>')
    ReactorEvidence::JARS.each { |mod| write.call("#{mod}/target/app-exec.jar", 'new executable') }
  end
  fill.call
  begin
    evidence.verify(manifest)
  rescue StandardError => error
    raise unless error.message.include?('class ownership')
  else
    raise 'foreign class internal name was accepted'
  end
  File.delete(File.join(root, class_file))
  production_source = 'im-common/src/main/java/com/co/kc/imchat/fixture/Sample.java'
  write.call(production_source, 'package com.co.kc.imchat.fixture; public class Sample {}')
  begin
    evidence.production_class_inventory
  rescue StandardError => error
    raise unless error.message.include?('production classes missing')
  else
    raise 'production source without class output was accepted'
  end
  File.delete(File.join(root, production_source))
  package_source = 'im-common/src/main/java/com/co/kc/imchat/fixture/package-info.java'
  write.call(package_source, '/** package documentation */ package com.co.kc.imchat.fixture;')
  evidence.production_class_inventory
  write.call(package_source, '@Deprecated package com.co.kc.imchat.fixture;')
  assert_failure('annotated package requires production class') { evidence.production_class_inventory }
  File.delete(File.join(root, package_source))
  write.call(production_source, 'package com.co.kc.imchat.fixture; public class Sample {}')
  write.call(class_file, owned_class)
  existing = evidence.production_class_inventory.find { |mod| mod.fetch('pom') == 'im-common/pom.xml' }
  raise 'production source inventory missing' unless existing.fetch('sources') == [production_source] && existing.fetch('classes').size == 1
  FileUtils.mv(File.join(root, 'im-common/src/main/java'), File.join(root, 'im-common/src/main/java.saved'))
  write.call('im-common/src/main/java', 'not a source directory')
  assert_failure('production source root is not directory') { evidence.production_class_inventory }
  File.delete(File.join(root, 'im-common/src/main/java'))
  FileUtils.mv(File.join(root, 'im-common/src/main/java.saved'), File.join(root, 'im-common/src/main/java'))
  File.delete(File.join(root, class_file))
  write.call('im-common/target/classes/application.yml', 'fixture: true')
  assert_failure('resource-only output is not production classes') { evidence.production_class_inventory }
  FileUtils.mv(File.join(root, 'im-common/target/classes'), File.join(root, 'im-common/target/classes.saved'))
  assert_failure('missing production output directory') { evidence.production_class_inventory }
  FileUtils.mv(File.join(root, 'im-common/target/classes.saved'), File.join(root, 'im-common/target/classes'))
  write.call(class_file, owned_class)
  Dir.mktmpdir('production-source-external-') do |external|
    sentinel = File.join(external, 'Sample.java')
    File.write(sentinel, 'source sentinel')
    FileUtils.mv(File.join(root, production_source), File.join(root, production_source + '.saved'))
    File.symlink(sentinel, File.join(root, production_source))
    assert_failure('production source symlink') { evidence.production_class_inventory }
    raise 'source external sentinel modified' unless File.read(sentinel) == 'source sentinel'
    File.unlink(File.join(root, production_source))
    FileUtils.mv(File.join(root, production_source + '.saved'), File.join(root, production_source))
  end
  File.delete(File.join(root, production_source))
  File.delete(File.join(root, class_file))
  evidence.prepare(manifest)
  fill.call
  # prepare 必须删除同秒旧报告与 JAR；仅改时间不能替代新执行。
  evidence.prepare(manifest)
  raise 'old XML survived prepare' if reports.any? { |entry| File.exist?(File.join(root, evidence.report(entry))) }
  raise 'old jar survived prepare' if ReactorEvidence::JARS.any? { |mod| File.exist?(File.join(root, mod, 'target/app-exec.jar')) }
  assert_failure('unrefreshed same-second outputs') { evidence.verify(manifest) }
  data = JSON.parse(File.read(manifest))
  fill.call
  write.call(class_file, owned_class)
  mapper_source = 'im-common/src/main/java/com/co/kc/imchat/fixture/SampleMapper.java'
  write.call(mapper_source, "package com.co.kc.imchat.fixture; import org.mapstruct.Mapper; @Mapper public interface SampleMapper {}")
  begin
    evidence.verify(manifest)
  rescue StandardError => error
    raise unless error.message.include?('mapper output missing')
  else
    raise 'missing MapStruct implementation was accepted'
  end
  mapper_generated = 'im-common/target/generated-sources/annotations/com/co/kc/imchat/fixture/SampleMapperImpl.java'
  mapper_class = 'im-common/target/classes/com/co/kc/imchat/fixture/SampleMapperImpl.class'
  mapper_text = 'package com.co.kc.imchat.fixture; public class SampleMapperImpl implements SampleMapper {}'
  write.call(mapper_generated, mapper_text)
  write.call(mapper_class, class_bytes.call('com/co/kc/imchat/fixture/SampleMapperImpl'))
  generated_path = File.join(root, mapper_generated)
  File.utime(Time.at(1), Time.at(1), generated_path)
  begin
    evidence.verify(manifest)
  rescue StandardError => error
    raise unless error.message.include?('stale production output')
  else
    raise 'stale generated source accepted'
  end
  File.utime(Time.now, Time.now, generated_path)
  generated_inventory = evidence.generated_source_inventory(data.fetch('startedAt'))
  raise 'generated source digest missing' unless generated_inventory == [{ 'path' => mapper_generated, 'sha256' => Digest::SHA256.hexdigest(mapper_text) }]
  write.call(mapper_generated, '')
  assert_failure('empty generated source') { evidence.generated_source_inventory(data.fetch('startedAt')) }
  write.call(mapper_generated, mapper_text)
  File.utime(Time.at(Time.now.to_f + 3600), Time.at(Time.now.to_f + 3600), generated_path)
  assert_failure('future generated source') { evidence.generated_source_inventory(data.fetch('startedAt')) }
  File.utime(Time.now, Time.now, generated_path)
  mapper_positive_sources = [
    'import org.mapstruct.Mapper; @Mapper public interface SampleMapper {}',
    '@org.mapstruct.Mapper public interface SampleMapper {}',
    'import org.mapstruct.Mapper; @Mapper(imports = {String.class, Integer.class}, uses = Other.class, unmappedTargetPolicy = ReportingPolicy.IGNORE) public interface SampleMapper {}',
    'import org.mapstruct.Mapper; /* @Mapper(config=Bad.class) */ @Mapper(uses = {Other.class}) public interface SampleMapper { String TEXT = "@Mapper"; }'
  ]
  mapper_positive_sources.each do |body|
    write.call(mapper_source, 'package com.co.kc.imchat.fixture; ' + body)
    records = evidence.generated_mapper_inventory
    raise 'mapper expectation not derived from source' unless records.size == 1 && records.first.fetch('source') == mapper_source && records.first.fetch('generated') == mapper_generated && records.first.fetch('class') == mapper_class
  end
  evidence.verify(manifest)
  evidence.check(manifest)
  locale_stdout, locale_stderr, locale_status = Open3.capture3({ 'HOME' => ENV.fetch('HOME'), 'PATH' => '/usr/bin:/bin', 'LC_ALL' => 'C' }, RbConfig.ruby, helper, 'check', root, manifest, unsetenv_others: true)
  raise "C locale manifest check failed: #{locale_stdout}#{locale_stderr}" unless locale_status.success?
  File.write(File.join(root, mapper_generated), mapper_text + ' ')
  assert_failure('generated bytes changed after verify') { evidence.check(manifest) }
  write.call(mapper_generated, mapper_text)
  FileUtils.mv(File.join(root, mapper_source), File.join(root, mapper_source + '.saved'))
  assert_failure('mapper source removed after verify') { evidence.check(manifest) }
  FileUtils.mv(File.join(root, mapper_source + '.saved'), File.join(root, mapper_source))
  Dir.mktmpdir('mapper-generated-external-') do |external|
    sentinel = File.join(external, 'SampleMapperImpl.java')
    File.write(sentinel, mapper_text)
    FileUtils.mv(File.join(root, mapper_generated), File.join(root, mapper_generated + '.saved'))
    File.symlink(sentinel, File.join(root, mapper_generated))
    assert_failure('generated mapper symlink') { evidence.check(manifest) }
    raise 'generated external sentinel modified' unless File.read(sentinel) == mapper_text
    File.unlink(File.join(root, mapper_generated))
    FileUtils.mv(File.join(root, mapper_generated + '.saved'), File.join(root, mapper_generated))
  end
  File.delete(File.join(root, mapper_class))
  assert_failure('missing mapper class') { evidence.generated_mapper_inventory }
  write.call(mapper_class, class_bytes.call('com/co/kc/imchat/fixture/WrongImpl'))
  assert_failure('wrong mapper class identity') { evidence.generated_mapper_inventory }
  write.call(mapper_class, class_bytes.call('com/co/kc/imchat/fixture/SampleMapperImpl'))
  [mapper_text.sub('SampleMapper {}', 'WrongMapper {}'), mapper_text.sub('imchat.fixture', 'imchatOther.fixture'),
   'package com.co.kc.imchat.fixture; public class SampleMapperImpl {} class Other { class SampleMapperImpl implements SampleMapper {} }'].each do |text|
    write.call(mapper_generated, text)
    assert_failure('wrong generated implementation') { evidence.generated_mapper_inventory }
  end
  write.call(mapper_generated, mapper_text)
  [
    'import org.mapstruct.Mapper; @Mapper(config=Shared.class) public interface SampleMapper {}',
    'import org.mapstruct.Mapper; @Mapper(implementationName="SampleMapperImpl") public interface SampleMapper {}',
    'import org.mapstruct.Mapper; @Mapper(implementationPackage="com.co.kc.imchat.fixture") public interface SampleMapper {}',
    'import org.mapstruct.Mapper; @Mapper(componentModel="spring") public interface SampleMapper {}',
    'import org.mapstruct.*; @Mapper public interface SampleMapper {}',
    'import org.mapstruct.Mapper; import other.Mapper; @Mapper public interface SampleMapper {}',
    'import org.mapstruct.Mapper; class Outer { @Mapper interface SampleMapper {} }',
    'import org.mapstruct.Mapper; @Mapper public abstract class SampleMapper {}',
    'import org.mapstruct.Mapper; @Mapper public interface SampleMapper {} @interface Mapper {}',
    '@org.mapstruct.MapperConfig interface SampleMapper {}',
    'import org.mapstruct.Mapper; @Mapper(uses = {Other.class) public interface SampleMapper {}'
  ].each do |body|
    write.call(mapper_source, 'package com.co.kc.imchat.fixture; ' + body)
    assert_failure('unsupported mapper shape') { evidence.generated_mapper_inventory }
  end
  [
    'import custom.Mapper; @Mapper public interface SampleMapper {}',
    'public interface SampleMapper { String TEXT = "@org.mapstruct.Mapper"; }',
    "/* @org.mapstruct.Mapper */ public interface SampleMapper { String TEXT = \"\"\"\n@org.mapstruct.Mapper\n\"\"\"; }"
  ].each do |body|
    write.call(mapper_source, 'package com.co.kc.imchat.fixture; ' + body)
    raise 'false mapper task' unless evidence.generated_mapper_inventory.empty?
  end
  File.delete(File.join(root, mapper_source))
  File.delete(File.join(root, mapper_generated))
  File.delete(File.join(root, mapper_class))
  evidence.prepare(manifest)
  fill.call
  write.call(class_file, owned_class)
  File.utime(Time.at(1), Time.at(1), File.join(root, class_file))
  begin
    evidence.verify(manifest)
  rescue StandardError => error
    raise unless error.message.include?('stale production output')
  else
    raise 'stale production class accepted'
  end
  File.utime(Time.now, Time.now, File.join(root, class_file))
  evidence.verify(manifest)
  evidence.check(manifest)
  evidence.ha_evidence(data['startedAt'], File.join(root, 'rows.jsonl'), File.join(root, 'topology.json'))
  raise 'scenario count' unless File.readlines(File.join(root, 'rows.jsonl')).size == 25
  verified = JSON.parse(File.read(manifest))
  retained_generated = 'im-common/target/generated-sources/annotations/com/co/kc/imchat/fixture/Retained.java'
  write.call(retained_generated, 'package com.co.kc.imchat.fixture; class Retained {}')
  evidence.prepare(manifest)
  fill.call
  write.call(class_file, owned_class)
  write.call(retained_generated, 'package com.co.kc.imchat.fixture; class Retained {}')
  evidence.verify(manifest)
  write.call(retained_generated, 'package com.co.kc.imchat.fixture; class Retained { int changed; }')
  assert_failure('generated source replaced after verification') { evidence.check(manifest) }
  File.delete(File.join(root, retained_generated))
  assert_failure('generated source missing after verification') { evidence.check(manifest) }
  evidence.prepare(manifest)
  fill.call
  write.call(class_file, owned_class)
  evidence.verify(manifest)
  verified = JSON.parse(File.read(manifest))
  added_generated = 'im-common/target/generated-sources/annotations/com/co/kc/imchat/fixture/Added.java'
  write.call(added_generated, 'package com.co.kc.imchat.fixture; class Added {}')
  assert_failure('generated source added after verification') { evidence.check(manifest) }
  File.delete(File.join(root, added_generated))
  inventory = verified.fetch('summaries').fetch('productionClasses')
  common_classes = inventory.find { |mod| mod.fetch('pom') == 'im-common/pom.xml' }.fetch('classes')
  raise 'class inventory missing' unless inventory.size == 53 && common_classes == [{ 'path' => class_file, 'internalName' => owned_name, 'accessFlags' => 33, 'sha256' => Digest::SHA256.hexdigest(owned_class) }]
  raise 'empty module classes missing' unless inventory.find { |mod| mod.fetch('pom') == 'pom.xml' }.fetch('classes') == []
  raise 'class hash not bound' unless verified.fetch('sha256').fetch(class_file) == Digest::SHA256.hexdigest(owned_class)
  reject_inventory = lambda do |label, diagnostic|
    before = File.read(manifest)
    begin
      evidence.check(manifest)
    rescue StandardError => error
      raise "wrong inventory rejection: #{label}: #{error.message}" unless error.message.include?(diagnostic)
    else
      raise "class inventory accepted: #{label}"
    end
    raise 'check rewrote manifest' unless File.read(manifest) == before
  end
  added_class = 'im-common/target/classes/com/co/kc/imchat/fixture/Added.class'
  write.call(production_source, 'package com.co.kc.imchat.fixture; public class Sample {}')
  reject_inventory.call('added production source', 'reactor output changed')
  File.delete(File.join(root, production_source))
  write.call(added_class, class_bytes.call('com/co/kc/imchat/fixture/Added'))
  reject_inventory.call('added', 'reactor output changed')
  File.delete(File.join(root, added_class))
  File.delete(File.join(root, class_file))
  reject_inventory.call('removed', 'reactor output changed')
  write.call(class_file, owned_class + 'changed body')
  reject_inventory.call('replaced', 'reactor output changed')
  write.call(class_file, class_bytes.call(owned_name + '$Other'))
  reject_inventory.call('path mismatch', 'class ownership: path mismatch')
  write.call(class_file, owned_class)
  duplicate_class = class_file.sub('im-common/', 'im-management/im-ops/im-ops-cli/')
  write.call(duplicate_class, owned_class)
  reject_inventory.call('duplicate', 'class ownership: duplicate internal name')
  File.delete(File.join(root, duplicate_class))
  Dir.mktmpdir('class-external-') do |external|
    sentinel = File.join(external, 'Sample.class')
    File.binwrite(sentinel, owned_class)
    [class_file, File.dirname(class_file), 'im-common/target/classes', 'im-common/target'].each do |linked|
      full = File.join(root, linked)
      FileUtils.mv(full, full + '.saved')
      File.symlink(File.extname(linked) == '.class' ? sentinel : external, full)
      reject_inventory.call('symlink ' + linked, 'symlink not allowed')
      raise 'external class modified' unless File.binread(sentinel) == owned_class
      File.unlink(full)
      FileUtils.mv(full + '.saved', full)
    end
  end
  evidence.check(manifest)
  conditional = verified.fetch('summaries').fetch('conditionalArchitecture')
  coverage_exec = 'im-common/target/jacoco.exec'
  coverage_xml = 'im-common/target/site/jacoco/jacoco.xml'
  coverage_content = '<report><package name="com/co/kc/imchat/fixture"><class name="com/co/kc/imchat/fixture/Sample"><counter type="INSTRUCTION" missed="3" covered="0"/></class></package></report>'
  write.call(coverage_exec, 'same-run fixture exec')
  write.call(coverage_xml, coverage_content)
  synthetic_path = class_file.sub('Sample.class', 'Sample$1.class')
  synthetic_bytes = class_bytes.call(owned_name + '$1')
  synthetic_bytes[-14, 2] = [0x1020].pack('n')
  write.call(synthetic_path, synthetic_bytes)
  data = JSON.parse(File.read(manifest))
  data.delete('sha256')
  data.delete('summaries')
  evidence.save(manifest, data)
  evidence.verify(manifest)
  covered_manifest = JSON.parse(File.read(manifest))
  classified = covered_manifest.fetch('summaries').fetch('coverageArtifacts').first.fetch('filteredClasses')
  raise 'synthetic header not classified' unless classified.size == 1 && classified.first.fetch('path') == synthetic_path && classified.first.fetch('accessFlags') == 0x1020 && classified.first.fetch('sha256') == Digest::SHA256.hexdigest(synthetic_bytes)
  changed_flags = synthetic_bytes.dup
  changed_flags[-14, 2] = [0x20].pack('n')
  write.call(synthetic_path, changed_flags)
  assert_failure('class header flags changed after verify') { evidence.check(manifest) }
  write.call(synthetic_path, synthetic_bytes)
  File.delete(File.join(root, synthetic_path))
  assert_failure('synthetic class missing after verify') { evidence.check(manifest) }
  write.call(synthetic_path, synthetic_bytes)
  raise 'coverage hash not bound' unless covered_manifest.fetch('sha256').fetch(coverage_exec) == Digest::SHA256.hexdigest('same-run fixture exec')
  filtered_manifest = Marshal.load(Marshal.dump(covered_manifest))
  filtered_manifest.fetch('summaries').fetch('coverageArtifacts').first['filteredClasses'] = [{ 'reason' => 'forged' }]
  evidence.save(manifest, filtered_manifest)
  assert_failure('forged manifest classification') { evidence.check(manifest) }
  evidence.save(manifest, covered_manifest)
  write.call(coverage_exec, 'replaced fixture exec')
  assert_failure('exec replaced after verify') { evidence.check(manifest) }
  write.call(coverage_exec, 'same-run fixture exec')
  write.call(coverage_xml, coverage_content.sub('missed="3"', 'missed="4"'))
  assert_failure('report replaced after verify') { evidence.check(manifest) }
  write.call(coverage_xml, coverage_content)
  evidence.check(manifest)
  File.delete(File.join(root, coverage_xml))
  assert_failure('report removed after verify') { evidence.check(manifest) }
  File.delete(File.join(root, coverage_exec))
  File.delete(File.join(root, synthetic_path))
  evidence.save(manifest, verified)
  evidence.check(manifest)
  record = conditional.fetch('records').fetch(0)
  raise 'conditional skip was counted as pass' unless record['status'] == 'conditional-skipped' && record['tests'] == 2 && record['skipped'] == 2 && record['reasons'].size == 2
  raise 'annotation metadata missing' unless record['condition'] == { 'annotation' => 'EnabledIfSystemProperty', 'named' => 'fixture.database', 'matches' => 'true' }
  raise 'mandatory architecture changed' unless verified['summaries']['architecture']['tests'] == 2 && verified['groups']['architecture'].size == 1
  raise 'aggregate lost skipped cases' unless verified['testSummary']['skipped'] == 2
  optional = reports.find { |entry| entry['testClass'] == 'example.OptionalDatabaseTest' }
  optional_file = File.join(root, evidence.report(optional))
  skipped_xml = File.read(optional_file)
  active_xml = '<testsuite name="example.OptionalDatabaseTest" tests="2" failures="0" errors="0" skipped="0"><testcase classname="example.OptionalDatabaseTest" name="works"/><testcase classname="example.OptionalDatabaseTest" name="second"/></testsuite>'
  File.write(optional_file, active_xml)
  _, active_summary = evidence.validate(verified)
  raise 'active conditional not executed' unless active_summary['conditionalArchitecture']['records'][0]['status'] == 'executed'
  {
    'partial skip' => skipped_xml.sub('skipped="2"', 'skipped="1"').sub('<skipped message="System property [fixture.database] does not exist"/>', ''),
    'empty reason' => skipped_xml.gsub('System property [fixture.database] does not exist', ''),
    'inconsistent skip count' => skipped_xml.sub('skipped="2"', 'skipped="0"'),
    'conditional error' => skipped_xml.sub('errors="0"', 'errors="1"'),
    'conditional testcase error' => skipped_xml.sub('<skipped message="System property [fixture.database] does not exist"/>', '<error/>'),
    'conditional failure' => skipped_xml.sub('<skipped message="System property [fixture.database] does not exist"/>', '<failure/>'),
    'conditional wrong identity' => skipped_xml.sub('classname="example.', 'classname="wrong.'),
    'active incomplete methods' => active_xml.sub('name="second"', 'name="unknown"')
  }.each do |label, xml|
    File.write(optional_file, xml)
    assert_failure(label) { evidence.validate(verified) }
  end
  File.write(optional_file, skipped_xml)
  File.utime(Time.at(1), Time.at(1), optional_file)
  assert_failure('conditional stale') { evidence.validate(verified) }
  File.delete(optional_file)
  assert_failure('conditional missing') { evidence.validate(verified) }
  File.write(optional_file, skipped_xml)
  original_source = File.read(File.join(root, conditional_source))
  [original_source.sub(conditional_annotation, '').sub('@Test void works', conditional_annotation + "\n@Test void works"),
   original_source.sub(conditional_annotation, '// ' + conditional_annotation),
   original_source.sub(conditional_annotation, '@EnabledOnOs(OS.LINUX)'),
   original_source.sub(conditional_annotation, conditional_annotation + "\n@EnabledOnOs(OS.LINUX)"),
   original_source.sub('@Test void works', "@EnabledOnOs(OS.LINUX)\n@Test void works")].each do |source_text|
    write.call(conditional_source, source_text)
    fresh = ReactorEvidence.new(root)
    classified = fresh.selection
    raise 'unsupported condition classified optional' if classified.fetch('conditionalArchitecture', []).any? { |entry| entry['testClass'] == optional['testClass'] }
    assert_failure('unsupported/method condition skipped') { fresh.cases(optional, verified['startedAt']) }
  end
  write.call(conditional_source, original_source)
  # 必需组即使带同名类级条件也不能放宽 skipped。
  assert_failure('required skipped') { evidence.cases(optional, verified['startedAt']) }
  pristine_manifest = File.read(manifest)
  [
    ['root mismatch', ->(d) { d['root'] = root + '/other' }],
    ['selection mismatch', ->(d) { d['groups']['startup'] = [] }],
    ['malformed groups', ->(d) { d['groups'] = nil }],
    ['malformed epoch', ->(d) { d['startedAt'] = {} }],
    ['malformed hashes', ->(d) { d['sha256'] = [] }],
    ['malformed version', ->(d) { d['version'] = '1' }]
  ].each do |label, mutate|
    altered = JSON.parse(pristine_manifest)
    mutate.call(altered)
    File.write(manifest, JSON.generate(altered))
    assert_failure(label) { evidence.check(manifest) }
  end
  File.write(manifest, '{malformed')
  assert_failure('malformed JSON') { evidence.check(manifest) }
  File.write(manifest, pristine_manifest)
  # target、报告本身的符号链接均拒绝；整批预检不应先删除其他报告。
  Dir.mktmpdir('reactor-external-') do |external|
    sentinel = File.join(external, 'sentinel.xml')
    File.write(sentinel, 'external sentinel')
    target = File.join(root, reports.last['module'], 'target')
    FileUtils.mv(target, target + '.saved')
    File.symlink(external, target)
    assert_failure('target symlink prepare') { evidence.prepare(manifest) }
    assert_failure('target symlink check') { evidence.check(manifest) }
    raise 'preflight deleted earlier report' unless File.file?(File.join(root, evidence.report(reports.first)))
    File.unlink(target)
    FileUtils.mv(target + '.saved', target)
    report_file = File.join(root, evidence.report(reports.first))
    FileUtils.mv(report_file, report_file + '.saved')
    File.symlink(sentinel, report_file)
    assert_failure('report symlink prepare') { evidence.prepare(manifest) }
    assert_failure('report symlink check') { evidence.check(manifest) }
    assert_failure('startup report symlink clear') { evidence.startup_clear }
    raise 'external file changed' unless File.read(sentinel) == 'external sentinel'
    File.unlink(report_file)
    FileUtils.mv(report_file + '.saved', report_file)
  end
  selected = File.join(root, evidence.report(reports.first))
  original = File.read(selected)
  mutations = {
    'zero' => original.sub('tests="2"', 'tests="0"'),
    'suite failure' => original.sub('failures="0"', 'failures="1"'),
    'skipped method with other passing' => original.sub('name="works"/>', 'name="works"><skipped/></testcase>'),
    'failed testcase' => original.sub('name="works"/>', 'name="works"><failure/></testcase>'),
    'wrong classname' => original.gsub('example.', 'wrong.'),
    'prefix method collision' => original.sub('name="works"', 'name="worksOther"')
  }
  mutations.each do |label, content|
    File.write(selected, content)
    assert_failure(label) { evidence.check(manifest) }
  end
  File.write(selected, original + "\n")
  assert_failure('hash changed') { evidence.check(manifest) }
  File.write(selected, original)
  File.utime(Time.at(1), Time.at(1), selected)
  assert_failure('stale report') { evidence.check(manifest) }
  File.delete(selected)
  assert_failure('missing report') { evidence.check(manifest) }
  fill.call
  jar = File.join(root, ReactorEvidence::JARS.first, 'target/extra-exec.jar')
  File.write(jar, 'extra')
  assert_failure('multiple jars') { evidence.check(manifest) }
  File.delete(jar)
  jar = File.join(root, ReactorEvidence::JARS.first, 'target/app-exec.jar')
  File.write(jar, 'overwritten')
  assert_failure('changed jar') { evidence.check(manifest) }
  %w[0 -1 NaN Infinity].push((Time.now.to_f + 3600).to_s).each { |value| assert_failure("epoch #{value}") { evidence.epoch(value) } }
  source.call('duplicate', 'ScenarioTest')
  # 新 helper 实例重新枚举源码，禁止仅凭 suite 成功跳过歧义。
  assert_failure('ambiguous fresh source') { ReactorEvidence.new(root).ha_classes }
end
puts 'Reactor evidence fixtures passed.'
RUBY
