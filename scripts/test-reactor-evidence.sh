#!/usr/bin/env bash
# 用途：在临时源码/XML/JAR 中验证同轮证明，拒绝缺失、旧产物、必需项跳过与摘要覆盖；单列类级条件跳过。
# 输入：无。输出：契约通过消息；仅创建自动清理的临时目录，不运行 Maven/容器。
# 依赖：Ruby 标准库；任一正反例不符时非零退出。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ruby - "$ROOT_DIR/scripts/lib/harness-reactor-evidence.rb" <<'RUBY'
require 'tmpdir'
require 'fileutils'
require 'json'
helper = ARGV.fetch(0)
# 加载定义而不运行 CLI。
eval(File.read(helper).split("\nbegin\n  command, root").first, TOPLEVEL_BINDING, helper)
def assert_failure(label)
  begin
    yield
  rescue StandardError
    return
  end
  raise "expected rejection: #{label}"
end
Dir.mktmpdir('reactor-evidence-') do |root|
  write = lambda do |file, content|
    FileUtils.mkdir_p(File.dirname(File.join(root, file)))
    File.write(File.join(root, file), content)
  end
  source = lambda do |mod, klass, tag = nil|
    write.call("#{mod}/src/test/java/example/#{klass}.java", "package example;\n#{tag ? '@Tag("' + tag + '")' : ''}\nclass #{klass} {\n@Test void works() {}\n@ParameterizedTest void parameter(String value) {}\n}\n")
  end
  write.call('app/src/main/java/example/DemoApplication.java', '@SpringBootApplication public class DemoApplication {}')
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
  write.call('im-test/im-routing-ha/src/test/resources/routing-ha-scenarios.tsv', (1..25).map { |i| "RH-#{i}\tcase\tunit-test\tScenarioTest#parameter\tassert" }.join("\n"))
  evidence = ReactorEvidence.new(root)
  manifest = File.join(root, 'manifest.json')
  evidence.prepare(manifest)
  data = JSON.parse(File.read(manifest))
  reports = data['groups'].values.flatten.uniq { |entry| entry['testClass'] }
  fill = lambda do
    reports.each do |entry|
      klass = entry['testClass']
      write.call(evidence.report(entry), "<testsuite name=\"#{klass}\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"#{klass}\" name=\"works\"/><testcase classname=\"#{klass}\" name=\"parameter(String)[1]\"/></testsuite>")
    end
    optional = reports.find { |entry| entry['testClass'] == 'example.OptionalDatabaseTest' }
    write.call(evidence.report(optional), '<testsuite name="example.OptionalDatabaseTest" tests="2" failures="0" errors="0" skipped="2"><testcase classname="example.OptionalDatabaseTest" name="works"><skipped message="System property [fixture.database] does not exist"/></testcase><testcase classname="example.OptionalDatabaseTest" name="second"><skipped message="System property [fixture.database] does not exist"/></testcase></testsuite>')
    ReactorEvidence::JARS.each { |mod| write.call("#{mod}/target/app-exec.jar", 'new executable') }
  end
  fill.call
  # prepare 必须删除同秒旧报告与 JAR；仅改时间不能替代新执行。
  evidence.prepare(manifest)
  raise 'old XML survived prepare' if reports.any? { |entry| File.exist?(File.join(root, evidence.report(entry))) }
  raise 'old jar survived prepare' if ReactorEvidence::JARS.any? { |mod| File.exist?(File.join(root, mod, 'target/app-exec.jar')) }
  assert_failure('unrefreshed same-second outputs') { evidence.verify(manifest) }
  data = JSON.parse(File.read(manifest))
  fill.call
  evidence.verify(manifest)
  evidence.check(manifest)
  evidence.ha_evidence(data['startedAt'], File.join(root, 'rows.jsonl'), File.join(root, 'topology.json'))
  raise 'scenario count' unless File.readlines(File.join(root, 'rows.jsonl')).size == 25
  verified = JSON.parse(File.read(manifest))
  conditional = verified.fetch('summaries').fetch('conditionalArchitecture')
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
