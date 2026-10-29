#!/usr/bin/env bash
# 用途：纯 stub 验证命令组期限、取消及实际 runner 的前检/清理；不启动 Maven 或容器。
# 输出：临时目录退出时删除；任何退出码、时间边界或进程所有权断言不符即失败。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ruby - "$ROOT_DIR" <<'RUBY'
require 'tmpdir'
require 'fileutils'
require 'json'
require 'timeout'
root = ARGV.fetch(0)
helper = File.join(root, 'scripts/lib/routing-ha-process.rb')
def assert(value, message)
  abort message unless value
end
def wait_file(path)
  Timeout.timeout(5) { sleep 0.02 until File.exist?(path) }
end
Dir.mktmpdir('ha-process') do |dir|
  env = {'ROUTING_HA_STATE_DIR'=>dir, 'ROUTING_HA_TERMINATE_SECONDS'=>'0.15'}
  # 注入 Ruby 标准库包装：生产实现只允许 WNOHANG，禁止末尾阻塞回收。
  guard = File.join(dir,'wait-guard.rb')
  File.write(guard, <<~CODE)
    module WaitGuard
      def waitpid2(pid = -1, flags = 0)
        abort 'blocking waitpid2 is forbidden' unless flags == Process::WNOHANG
        super
      end
    end
    Process.singleton_class.prepend(WaitGuard)
  CODE
  env['RUBYOPT'] = "-r#{guard}"
  # 历史记录即便指向仍存活的外来组，也不能被新监督者采用或发送信号。
  foreign = Process.spawn('ruby','-e','trap("TERM") {}; sleep 20',pgroup:true)
  stale = File.join(dir,'stale')
  FileUtils.mkdir_p(File.join(stale,'command-groups'),mode:0700)
  File.write(File.join(stale,'command-groups',"999-#{foreign}.pid"),foreign.to_s)
  marker = File.join(dir,'must-not-launch')
  launch = File.join(dir,'launch.sh')
  File.write(launch,"#!/bin/sh\ntouch '#{marker}'\n")
  FileUtils.chmod(0755,launch)
  begin
    child = Process.spawn(env.merge('ROUTING_HA_STATE_DIR'=>stale),'ruby',helper,'supervise','0.3',launch)
    Timeout.timeout(3) { Process.wait(child) }
    assert($?.exitstatus != 0 && !File.exist?(marker),'historical registry must refuse before launch')
    assert(Process.kill(0,foreign) == 1,'foreign group was touched')
  ensure
    Process.kill('KILL',-foreign) rescue nil
    Process.wait(foreign) rescue nil
  end
  started = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  pid = Process.spawn(env, 'ruby', helper, 'run', 'hang', '0.6', 'ruby', '-e', 'trap("TERM") {}; sleep 20')
  Process.wait(pid)
  assert($?.exitstatus == 124, 'bounded helper must return 124')
  assert(Process.clock_gettime(Process::CLOCK_MONOTONIC) - started < 3, 'helper exceeded deadline')
  events = File.readlines(File.join(dir,'diagnostics/steps.jsonl')).map { |l| JSON.parse(l) }
  assert(events.map { |e| e['event'] } == %w[started finished], 'missing step boundaries')
  assert(events.last['exitCode'] == 124 && events.last.key?('rawExitCode'), 'missing raw status')
  handshake = File.join(dir, 'ready')
  code = 'trap("TERM") { exit 0 }; File.write(ARGV[0], Process.pid); sleep 20'
  pid = Process.spawn(env, 'ruby', helper, 'run', 'cancel', '', 'ruby', '-e', code, handshake)
  wait_file(handshake)
  Process.kill('TERM', pid)
  Process.wait(pid)
  assert($?.exitstatus == 143, 'helper cancellation must preserve TERM')
  begin
    Process.kill(0, Integer(File.read(handshake)))
    abort 'command survived cancellation'
  rescue Errno::ESRCH
  end
  # 直接子进程仍存活并回收其孙进程；避免把 OS 孤儿 zombie 当成测试通过。
  grand = File.join(dir,'grand')
  code = 'child = spawn("ruby", "-e", "trap(\"TERM\") {}; sleep 20"); trap("TERM") { Process.kill("KILL", child) rescue nil; Process.wait(child); exit }; File.write(ARGV[0], child); sleep 20'
  pid = Process.spawn(env, 'ruby', helper, 'run', 'grandchild', '0.8', 'ruby', '-e', code, grand)
  wait_file(grand)
  Process.wait(pid)
  assert($?.exitstatus == 124, 'grandchild deadline status')
  begin
    Process.kill(0,Integer(File.read(grand)))
    abort 'grandchild survived'
  rescue Errno::ESRCH
  end
  # 已退出直接子进程的同一组仍有其他成员；helper 不能仅凭 waitpid 判成功。
  owned_child = File.join(dir,'owned-grand')
  code = 'fork { trap("TERM") {}; File.write(ARGV[0],Process.pid); sleep 20 }; sleep 0.01 until File.exist?(ARGV[0]); exit'
  orphan_state = File.join(dir,'orphan-state')
  child = Process.spawn(env.merge('ROUTING_HA_STATE_DIR'=>orphan_state),'ruby',helper,'run','orphan','0.7','ruby','-e',code,owned_child)
  Timeout.timeout(3) { Process.wait(child) }
  assert($?.exitstatus != 0,'direct child exit hid grandchild')
  assert(File.exist?(owned_child),'grandchild trap-ready handshake missing')
  begin
    grand_pid = Integer(File.read(owned_child))
    # ps 仅用于 fixture 分类 zombie；生产仍以组不存在为唯一清理证明。
    process_state = IO.popen(['ps','-o','stat=','-p',grand_pid.to_s], &:read).strip
    assert(process_state.empty? || process_state.start_with?('Z'),'grandchild remains running')
    assert(!Dir.glob(File.join(orphan_state,'command-groups','*','*.pid')).empty?,'uncertain orphan registration removed') unless process_state.empty?
  end
  # 仅 fixture Ruby load 包装目标 supervisor，在枚举后确定性撤销登记。
  retire_guard = File.join(dir,'retire-guard.rb')
  File.write(retire_guard, <<~'CODE')
    if ARGV.first == 'supervise'
      module RetireFixture
        GROUP = 2_000_000_000
        def glob(pattern, *args)
          entries = super
          return entries unless pattern.end_with?('/*.pid') && $fixture_scene
          mode = ENV.fetch('RETIRE_MODE')
          unless $fixture_entry
            $fixture_entry = File.join(File.dirname(pattern),"#{$fixture_scene}-#{GROUP}.pid")
            File.write($fixture_entry,JSON.generate(token: File.basename(File.dirname(pattern)),helper: $fixture_scene,group: GROUP))
            entries << $fixture_entry
            File.unlink($fixture_entry) if mode == 'read'
          end
          if mode == 'known' && $fixture_probes.to_i >= 1 && File.exist?($fixture_entry)
            File.unlink($fixture_entry)
          end
          entries
        end
      end
      module RetireProcessFixture
        def spawn(*args, **options)
          child = super
          $fixture_scene = child if args.first.is_a?(Hash) && args.first.key?('ROUTING_HA_PROCESS_SESSION')
          child
        end
        def getpgid(pid)
          if pid == $fixture_scene && %w[identity unowned].include?(ENV['RETIRE_MODE'])
            File.unlink($fixture_entry) if ENV['RETIRE_MODE'] == 'identity' && File.exist?($fixture_entry)
            raise Errno::ESRCH
          end
          super
        end
        def kill(signal, *pids)
          return super unless pids == [-RetireFixture::GROUP]
          abort 'fixture synthetic group signalled' unless signal == 0
          $fixture_probes = $fixture_probes.to_i + 1
          File.write(File.join(ENV.fetch('ROUTING_HA_STATE_DIR'),'probes'),$fixture_probes)
          raise Errno::ESRCH if ENV['RETIRE_MODE'] != 'known' || $fixture_probes >= 5
          1
        end
      end
      Dir.singleton_class.prepend(RetireFixture)
      Process.singleton_class.prepend(RetireProcessFixture)
    end
  CODE
  retire_scene = File.join(dir,'retire-scene')
  File.write(retire_scene,"#!/usr/bin/env ruby\nsleep 0.2\n")
  FileUtils.chmod(0755,retire_scene)
  retirement_failures = []
  %w[read identity known unowned].each do |mode|
    state = File.join(dir,"retire-#{mode}")
    child = Process.spawn(env.merge('ROUTING_HA_STATE_DIR'=>state,'RETIRE_MODE'=>mode,
      'ROUTING_HA_LOGS_TIMEOUT_SECONDS'=>'0.3','ROUTING_HA_DOWN_TIMEOUT_SECONDS'=>'0.3',
      'ROUTING_HA_SUPERVISOR_OVERHEAD_SECONDS'=>'0.15','ROUTING_HA_CLEANUP_GRACE_SECONDS'=>'1',
      'RUBYOPT'=>"-r#{guard} -r#{retire_guard}"),'ruby',helper,'supervise','2',retire_scene,
      out:File.join(dir,"retire-#{mode}.log"),err:[:child,:out])
    Timeout.timeout(4) { Process.wait(child) }
    retirement_failures << "#{mode} healthy retirement returned #{$?.exitstatus}" unless $?.exitstatus == (mode == 'unowned' ? 1 : 0)
    if mode == 'known'
      probes = File.join(state,'probes')
      retirement_failures << 'known live group forgotten after unlink' unless File.exist?(probes) && Integer(File.read(probes)) >= 5
    end
  end
  assert(retirement_failures.empty?,retirement_failures.join("; "))
  fixture = File.join(dir,'repo')
  FileUtils.mkdir_p(File.join(fixture,'scripts/lib'))
  FileUtils.cp(File.join(root,'scripts/run-routing-ha-scenarios.sh'),File.join(fixture,'scripts'))
  FileUtils.cp(helper,File.join(fixture,'scripts/lib'))
  # 本组只验证runner顺序与失败传播，真实离线材料由test-routing-ha-tls保护。
  File.write(File.join(fixture,'scripts/lib/routing-ha-tls.rb'), <<~CODE)
    File.open(ENV.fetch('TRACE'),'a') { |f| f.puts("tls-" + ARGV.first) }
    File.write(File.join(ENV.fetch('ROUTING_HA_STATE_DIR'),'tls-path'),ARGV[1])
    exit 27 if ENV['MODE'] == 'tls' && ARGV.first == 'check'
  CODE

  FileUtils.cp(File.join(root,'scripts/routing_ha.sh'),File.join(fixture,'scripts'))
  # 本fixture只验证进程/清理；同轮报告校验由test-reactor-evidence单独保护。
  File.write(File.join(fixture,'scripts/lib/harness-reactor-evidence.rb'), <<~CODE)
    abort 'unexpected evidence operation' unless ARGV.first == 'ha-check'
  CODE
  FileUtils.cp(File.join(root,'scripts/lib/harness-evidence.sh'),File.join(fixture,'scripts/lib'))
  File.write(File.join(fixture,'.gitignore'), "state*/\n")
  system('git','-C',fixture,'init','--quiet') or abort 'process fixture Git init failed'
  system('git','-C',fixture,'add','scripts','.gitignore') or abort 'process fixture Git add failed'
  system('git','-C',fixture,'-c','user.name=Harness','-c','user.email=harness@example.invalid','commit','-qm','Initialize process fixture') or abort 'process fixture Git commit failed'
  bin = File.join(dir,'bin'); FileUtils.mkdir_p(bin)
  trace = File.join(dir,'calls')
  File.write(File.join(bin,'docker'), <<~SH)
    #!/usr/bin/env ruby
    File.open(ENV.fetch('TRACE'),'a') { |f| f.puts(ARGV.join(' ')) }
    if ARGV == ['info']
      if ENV['MODE'] == 'engine'
        trap('TERM') {}
        File.write(ENV.fetch('ENGINE_READY'), Process.pid)
        reader, writer = IO.pipe
        reader.read(1) # 写端保持打开：只由 owned group 的终止信号结束，不按 sleep 时长推进。
      end
      File.open(ENV.fetch('TRACE'),'a') { |f| f.puts('engine-info-ok') }
      exit 0
    end
    if ARGV.include?('logs')
      trap('TERM') {}; sleep 20
    end
    exit 29 if ARGV.include?('up')
    exit(ENV['MODE'] == 'down' ? 31 : 0) if ARGV.include?('down')
  SH
  File.write(File.join(bin,'mvn'), "#!/usr/bin/env ruby\nFile.open(ENV.fetch('TRACE'),'a') { |f| f.puts('mvn') }; exit(ENV['MODE'] == 'build' ? 23 : 0)\n")
  FileUtils.chmod(0755,Dir.glob(File.join(bin,'*')))
  %w[engine build tls cleanup down].each do |mode|
    state = File.join(dir,mode)
    File.write(trace,'')
    engine_ready = File.join(dir,'engine-ready')
    # 正例预算只防挂，不要求 Ruby/runner 在亚秒内启动；负例独立保留短期限。
    e = env.merge('PATH'=>"#{bin}:#{ENV['PATH']}", 'TRACE'=>trace, 'MODE'=>mode,
      'ENGINE_READY'=>engine_ready, 'ROUTING_HA_STATE_DIR'=>state,
      'ROUTING_HA_ENGINE_TIMEOUT_SECONDS'=>mode == 'engine' ? '2' : '15',
      'ROUTING_HA_LOGS_TIMEOUT_SECONDS'=>'2', 'ROUTING_HA_DOWN_TIMEOUT_SECONDS'=>'15')
    began = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    pid = Process.spawn(e,'bash',File.join(fixture,'scripts/run-routing-ha-scenarios.sh'),out: File.join(dir,"runner.log"),err: [:child, :out])
    Timeout.timeout(mode == 'engine' ? 6 : 45) { Process.wait(pid) }
    status = $?.exitstatus
    calls = File.read(trace)
    material_path = File.join(state,'tls-path')
    if File.exist?(material_path)
      material = File.read(material_path)
      assert(File.directory?(material) == (mode == 'down'), 'material cleanup ownership/order mismatch')
      # down失败应保留私有材料；本测试只删除自身记录的新mktemp目录。
      FileUtils.remove_entry_secure(material) if mode == 'down'
    end
    steps = File.readlines(File.join(state,'diagnostics/steps.jsonl')).map { |line| JSON.parse(line) }
    engine = steps.select { |event| event['step'] == 'engine-info' && event['event'] == 'finished' }
    assert(engine.size == 1 && engine.first['groupGone'], "runner #{mode} engine group unresolved")
    assert(engine.first['exitCode'] == (mode == 'engine' ? 124 : 0), "runner #{mode} engine outcome")
    assert(status == (mode == 'engine' ? 124 : mode == 'build' ? 23 : mode == 'tls' ? 27 : 29), "runner #{mode} status #{status}: #{File.read(File.join(dir,'runner.log'))}")
    if %w[engine build tls].include?(mode)
      assert(!calls.include?('down') && !calls.include?('up'), 'preflight/build failure touched resources')
      if mode == 'engine'
        assert(File.exist?(engine_ready), 'engine hang readiness missing')
        assert(Process.clock_gettime(Process::CLOCK_MONOTONIC) - began < 6, 'engine hang exceeded fixture bound')
        assert(!calls.include?('engine-info-ok') && !calls.include?('mvn'), 'engine failure invoked Maven or completed')
        begin
          Process.kill(0,Integer(File.read(engine_ready)))
          abort 'engine hang process survived'
        rescue Errno::ESRCH
        end
      elsif mode == 'tls'
        assert(calls.include?('tls-check') && !calls.include?(' up '), 'TLS precheck did not fail before resources')
      else
        assert(calls.lines.index("engine-info-ok\n") < calls.lines.index("mvn\n"), 'build did not follow successful engine info')
        package = steps.select { |event| event['step'] == 'package' && event['event'] == 'finished' }
        assert(package.size == 1 && package.first['exitCode'] == 23 && package.first['groupGone'], 'Maven failure lost status or owned group')
      end
    else
      assert(calls.lines.index("tls-check\n") < calls.lines.index { |line| line.include?(' up ') }, 'Compose up preceded TLS material validation')
      assert(calls.scan(/ down /).size == 1, 'logs timeout must still down exactly once')
      assert(File.size(File.join(state,'resources.jsonl')).zero? == (mode != 'down'), "ledger #{mode}: #{File.read(File.join(state,'resources.jsonl'))} steps #{File.read(File.join(state,'diagnostics/steps.jsonl'))}")
    end
  end
  # fixture 专用 spawn 见证：仅记录本轮实际创建的独立组及内核启动身份，不采用历史 PID。
  spawn_guard = File.join(dir,'cleanup-spawn-guard.rb')
  File.write(spawn_guard, <<~'CODE')
    require 'json'
    module CleanupSpawnWitness
      def spawn(*args, **options)
        child = super
        if options[:pgroup] == true
          identity = IO.popen(['ps','-o','lstart=','-o','command=','-p',child.to_s], &:read).strip
          record = {pid: child, identity: identity}
          path = File.join(ENV.fetch('CLEANUP_OWNERS'), "#{child}.json")
          File.write("#{path}.tmp",JSON.generate(record))
          File.rename("#{path}.tmp",path)
        end
        child
      end
    end
    # 仅 fixture：handler 只置位。上一入口快照须经过下一轮真实取消判断，才可发布。
    helper_lines = File.basename($PROGRAM_NAME) == 'routing-ha-process.rb' ? File.readlines($PROGRAM_NAME) : []
    check_line = helper_lines.index { |line| line.strip == 'result ||= cancelled' }
    raise 'cleanup cancellation check must be unique' unless helper_lines.empty? || helper_lines.count { |line| line.strip == 'result ||= cancelled' } == 1
    run_sleep = check_line && (check_line...helper_lines.size).find { |index| helper_lines[index].strip == 'sleep 0.01' }
    CLEANUP_RUN_SLEEP = run_sleep && [$PROGRAM_NAME, run_sleep + 1].freeze
    module CleanupSignalWitness
      def trap(name, handler = nil, &block)
        unless ARGV[0] == 'cleanup-logs' && %w[TERM INT].include?(name.to_s)
          return block ? super(name, &block) : super(name, handler)
        end
        original = block || handler
        super(name) do |signal|
          ($cleanup_signals ||= {})[name.to_s] = true
          original.call(signal) if original.respond_to?(:call)
        end
      end
    end
    module CleanupLoopWitness
      def sleep(*args)
        location = caller_locations(1,1).first
        if ARGV[0] == 'cleanup-logs' && [location.path,location.lineno] == CLEANUP_RUN_SLEEP
          previous = $cleanup_previous_signals || {}
          $cleanup_previous_signals = ($cleanup_signals || {}).dup
          previous.each_key do |name|
            File.write(File.join(ENV.fetch('ROUTING_HA_STATE_DIR'), "observed-#{name}"),'observed')
          end
        end
        super
      end
    end
    Signal.singleton_class.prepend(CleanupSignalWitness)
    Object.prepend(CleanupLoopWitness)
    Process.singleton_class.prepend(CleanupSpawnWitness)
  CODE
  # 两个 fixture 的异常路径共用：先释放 FIFO、请求 parent 正常收尾，再清理仍匹配身份的独立组。
  # 回收 parent 不代表其分离组已退出；每次发信号前重新验证 identity/PGID，消失即撤销本轮所有权。
  finish_cleanup_fixture = lambda do |pid, poll, release, owned|
    primary = $!
    errors = []
    attempt = lambda do |label, &action|
      begin
        action.call
      rescue StandardError => error
        errors << "#{label}: #{error.class}: #{error.message}"
        nil
      end
    end
    attempt.call('release') { File.open(release,File::RDWR | File::NONBLOCK) { |pipe| pipe.write('.') } }
    attempt.call('parent TERM') { Process.kill('TERM',pid) unless poll.call }
    attempt.call('parent grace') do
      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 40
      sleep 0.02 until poll.call || Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline
    end
    %w[TERM KILL].each do |signal|
      paths = attempt.call('owned records') { Dir.glob(File.join(owned,'*.json')) } || []
      paths.each do |path|
        attempt.call("#{signal} #{File.basename(path)}") do
          record = JSON.parse(File.read(path))
          group = record.fetch('pid')
          begin
            reaped = Process.waitpid2(group,Process::WNOHANG)
          rescue Errno::ECHILD
            reaped = nil
          end
          if reaped
            File.unlink(path)
            next
          end
          identity = IO.popen(['ps','-o','lstart=','-o','command=','-p',group.to_s], &:read).strip
          if identity.empty? || identity != record.fetch('identity')
            File.unlink(path)
            next
          end
          begin
            raise "fixture group identity changed: #{group}" unless Process.getpgid(group) == group
            Process.kill(signal,-group)
          rescue Errno::ESRCH
            File.unlink(path)
          end
        end
      end
      # 每项独立失败，不能中断其他组和 parent 的最终回收；明确 wait 成功优先撤销所有权。
      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 2
      loop do
        attempt.call('parent reap') { poll.call }
        live = false
        (attempt.call('remaining records') { Dir.glob(File.join(owned,'*.json')) } || []).each do |path|
          attempt.call("reap #{File.basename(path)}") do
            record = JSON.parse(File.read(path))
            group = record.fetch('pid')
            begin
              reaped = Process.waitpid2(group,Process::WNOHANG)
            rescue Errno::ECHILD
              reaped = nil
            end
            if reaped
              File.unlink(path)
              next
            end
            identity = IO.popen(['ps','-o','lstart=','-o','command=','-p',group.to_s], &:read).strip
            if identity.empty? || identity != record.fetch('identity')
              File.unlink(path)
            else
              live = true
            end
          end
        end
        break unless live && Process.clock_gettime(Process::CLOCK_MONOTONIC) < deadline
        sleep 0.02
      end
    end
    attempt.call('parent final reap') do
      unless poll.call
        Process.kill('KILL',-pid)
        Timeout.timeout(5) { sleep 0.02 until poll.call }
      end
    end
    attempt.call('owned complete') do
      raise 'fixture independent group not reaped' unless Dir.glob(File.join(owned,'*.json')).empty?
    end
    unless errors.empty?
      message = "fixture cleanup errors: #{errors.uniq.join('; ')}"
      primary ? warn("#{message}; primary=#{primary.class}: #{primary.message}") : raise(message)
    end
  end

  # 确定性异常见证：parent 已退出 / 外层 guard 失败；分离 child 不能随 parent 状态被跳过。
  %w[parent-exited outer-guard cleanup-error].each do |failure|
    owned = File.join(dir,"witness-#{failure}"); Dir.mkdir(owned,0700)
    release = File.join(owned,'release'); system('mkfifo',release) or abort 'witness FIFO failed'
    ready = File.join(owned,'ready')
    orphan = Process.spawn('ruby','-e', 'trap("TERM", "IGNORE"); File.write(ARGV[0],Process.pid); reader,writer=IO.pipe; reader.read(1)',ready,pgroup:true)
    parent = Process.spawn('ruby','-e',failure == 'parent-exited' ? 'exit 0' : 'sleep 20',pgroup:true)
    status = nil
    poll = lambda { status ||= Process.waitpid2(parent,Process::WNOHANG)&.last }
    begin
      wait_file(ready)
      identity = IO.popen(['ps','-o','lstart=','-o','command=','-p',orphan.to_s], &:read).strip
      File.write(File.join(owned,"#{orphan}.json"),JSON.generate(pid:orphan,identity:identity))
      Timeout.timeout(5) { sleep 0.02 until poll.call } if failure == 'parent-exited'
      File.write(File.join(owned,'broken.json'),'invalid') if failure == 'cleanup-error'
      begin
        raise Timeout::Error, "#{failure} witness"
      ensure
        finish_cleanup_fixture.call(parent,poll,release,owned)
      end
    rescue Timeout::Error => error
      assert(error.message == "#{failure} witness",'cleanup replaced primary failure')
      File.unlink(File.join(owned,'broken.json')) if failure == 'cleanup-error'
      assert(Dir.glob(File.join(owned,'*.json')).empty?,"#{failure} leaked independent group")
      begin
        status = Process.waitpid2(orphan,Process::WNOHANG)
        assert(status && status.last.signaled?,'independent child not terminated')
      rescue Errno::ECHILD
        # 已由 cleanup 的非阻塞 wait 回收；不再探测或发送旧 PID。
      end
    ensure
      # 反证实验即使旧 cleanup 漏杀也由本层直接 spawn owner 最终回收，不留下未知进程。
      begin
        unless Process.waitpid2(orphan,Process::WNOHANG)
          Process.kill('KILL',-orphan)
          Timeout.timeout(5) { Process.wait(orphan) }
        end
      rescue Errno::ECHILD
      end
      unless poll.call
        Process.kill('KILL',-parent)
        Timeout.timeout(5) { sleep 0.02 until poll.call }
      end
    end
  end
  # 重复取消与 cleanup 自身超时分开验证；握手预算只防挂，不是启动性能要求。
  cleanup_child = File.join(dir,'cleanup-child.rb')
  File.write(cleanup_child, <<~'CODE')
    trap('TERM', 'IGNORE')
    reader = File.open(ARGV.fetch(1), 'r+')
    File.write(ARGV.fetch(0), Process.pid)
    reader.read(1)
  CODE
  scenario = File.join(dir,'scenario.sh')
  File.write(scenario, <<~'SH')
    #!/usr/bin/env bash
    set -euo pipefail
    cleanup() {
      status=$?
      trap - EXIT
      trap '' TERM INT
      printf cleanup >> "$ROUTING_HA_STATE_DIR/cleanup-count"
      ruby "$HELPER" run cleanup-logs 15 ruby "$CLEANUP_CHILD" "$ROUTING_HA_STATE_DIR/cleanup-ready" "$ROUTING_HA_STATE_DIR/release"
      exit "$status"
    }
    trap cleanup EXIT
    trap 'exit 143' TERM
    trap 'exit 130' INT
    printf '%s' "$$" > "$ROUTING_HA_STATE_DIR/scene-owner"
    ruby "$HELPER" run active "" ruby -e 'trap("TERM") { exit }; File.write(ARGV[0],Process.pid); sleep 20' "$ROUTING_HA_STATE_DIR/active-ready"
    printf forbidden > "$ROUTING_HA_STATE_DIR/continued"
  SH
  FileUtils.chmod(0755,scenario)
  %w[timeout TERM INT].each do |kind|
    state = File.join(dir,"parent-#{kind}")
    FileUtils.mkdir_p(state)
    release = File.join(state,'release')
    system('mkfifo',release) or abort 'cleanup FIFO creation failed'
    owned = File.join(state,'owned-spawns'); Dir.mkdir(owned,0700)
    log = File.join(dir,"parent-#{kind}.log")
    e = env.merge('ROUTING_HA_STATE_DIR'=>state, 'HELPER'=>helper, 'CLEANUP_CHILD'=>cleanup_child, 'CLEANUP_OWNERS'=>owned,
      'RUBYOPT'=>"#{env['RUBYOPT']} -r#{spawn_guard}",
      'ROUTING_HA_SCENARIO_COMMAND'=>scenario, 'ROUTING_HA_DETAIL_PATH'=>File.join(state,'detail.json'),
      'ROUTING_HA_TIMEOUT_SECONDS'=>kind == 'timeout' ? '10' : '30',
      'ROUTING_HA_LOGS_TIMEOUT_SECONDS'=>'15', 'ROUTING_HA_DOWN_TIMEOUT_SECONDS'=>'15',
      'ROUTING_HA_CLEANUP_GRACE_SECONDS'=>'35')
    pid = Process.spawn(e,'bash',File.join(fixture,'scripts/routing_ha.sh'),'verify',pgroup:true,out:log,err:[:child,:out])
    parent_status = nil
    # 仅观察本次 spawn 的 parent；缓存非阻塞回收状态，禁止退出后再用 PID 发信号。
    poll_parent = lambda { parent_status ||= Process.waitpid2(pid,Process::WNOHANG)&.last }
    diagnostics = lambda do
      paths = [log,File.join(state,'diagnostics/steps.jsonl')]
      tails = paths.map { |path| "#{File.basename(path)}: #{File.exist?(path) ? File.readlines(path).last(12).join : '(missing)'}" }
      "parent #{kind} status=#{parent_status.inspect}\n#{tails.join("\n")}"
    end
    wait_marker = lambda do |name|
      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 12
      loop do
        poll_parent.call
        if name.start_with?('observed-')
          steps_path = File.join(state,'diagnostics/steps.jsonl')
          if File.exist?(steps_path)
            finished = File.readlines(steps_path).map do |line|
              event = JSON.parse(line) rescue nil
              event if event && event['step'] == 'cleanup-logs' && event['event'] == 'finished'
            end
            finished.compact!
            raise "cleanup cancelled: #{diagnostics.call}" if finished.any? { |event| event['exitCode'] != 0 }
          end
        end
        raise "exited before #{name}: #{diagnostics.call}" if parent_status
        break if File.exist?(File.join(state,name))
        raise "waiting for #{name}: #{diagnostics.call}" if Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline
        sleep 0.02
      end
    end
    begin
      wait_marker.call('active-ready')
      Process.kill(kind,pid) unless kind == 'timeout'
      wait_marker.call('cleanup-ready')
      # scene-owner 由当前场景自身发布，且 parent 尚未回收；不采用历史登记或未知 PID。
      scene_group = Integer(File.read(File.join(state,'scene-owner')))
      assert(Process.getpgid(scene_group) == scene_group,'scene owner is not its group leader')
      Process.kill('TERM',-scene_group)
      Process.kill('INT',-scene_group)
      wait_marker.call('observed-TERM')
      wait_marker.call('observed-INT')
      File.open(release,File::RDWR | File::NONBLOCK) { |pipe| pipe.write('.') }
      Timeout.timeout(40) { sleep 0.02 until poll_parent.call }
      expected = kind == 'timeout' ? 124 : kind == 'TERM' ? 143 : 130
      assert(parent_status.exitstatus == expected,diagnostics.call)
      assert(File.read(File.join(state,'cleanup-count')) == 'cleanup','cleanup repeated')
      assert(!File.exist?(File.join(state,'continued')) && !File.exist?(File.join(state,'detail.json')),'interrupted scenario continued or passed')
      steps = File.readlines(File.join(state,'diagnostics/steps.jsonl')).map { |line| JSON.parse(line) }
      cleanup = steps.select { |event| event['step'] == 'cleanup-logs' && event['event'] == 'finished' }
      assert(cleanup.size == 1 && cleanup.first['exitCode'] == 0 && cleanup.first['groupGone'],"cleanup cancelled: #{diagnostics.call}")
      assert(Dir.glob(File.join(state,'command-groups','*','*.pid')).empty?, 'owned command was not reaped')
      owners = Dir.glob(File.join(state,'command-groups','*','owner.json'))
      assert(owners.size == 1 && owners.all? { |path| !JSON.parse(File.read(path))['active'] && File.file?(File.join(File.dirname(path),'done')) },'supervisor ownership unresolved')
      %w[active-ready cleanup-ready].each do |name|
        begin
          Process.kill(0,Integer(File.read(File.join(state,name))))
          abort "#{name} process survived"
        rescue Errno::ESRCH
        end
      end
    ensure
      finish_cleanup_fixture.call(pid,poll_parent,release,owned)
    end
  end
  # 独立负例：真实 helper 必须对不释放的 cleanup 使用期限内 KILL，并回收自有组。
  state = File.join(dir,'cleanup-timeout')
  FileUtils.mkdir_p(state)
  release = File.join(state,'release')
  system('mkfifo',release) or abort 'timeout FIFO creation failed'
  owned = File.join(state,'owned-spawns'); Dir.mkdir(owned,0700)
  child = Process.spawn(env.merge('ROUTING_HA_STATE_DIR'=>state,'CLEANUP_OWNERS'=>owned,
    'RUBYOPT'=>"#{env['RUBYOPT']} -r#{spawn_guard}"),'ruby',helper,'run','cleanup-logs','2',
    'ruby',cleanup_child,File.join(state,'cleanup-ready'),release,pgroup:true,out:File.join(dir,'cleanup-timeout.log'),err:[:child,:out])
  child_status = nil
  poll_child = lambda { child_status ||= Process.waitpid2(child,Process::WNOHANG)&.last }
  begin
    Timeout.timeout(8) { sleep 0.02 until poll_child.call }
    assert(child_status.exitstatus == 124,'cleanup timeout lost status')
  assert(File.exist?(File.join(state,'cleanup-ready')),'cleanup timeout child never reached handshake')
  steps = File.readlines(File.join(state,'diagnostics/steps.jsonl')).map { |line| JSON.parse(line) }
  assert(steps.size == 2 && steps.last['exitCode'] == 124 && steps.last['rawExitCode'] == 137 && steps.last['groupGone'],'cleanup timeout did not KILL/reap owned group')
    assert(Dir.glob(File.join(state,'command-groups','*','*.pid')).empty?,'cleanup timeout retained owned group')
  ensure
    finish_cleanup_fixture.call(child,poll_child,release,owned)
  end

  # 只加载实际模块正文；CLI 标记必须唯一，隔离命名空间避免污染全局常量。
  source = File.read(helper)
  cli_marker = "begin\n  exit RoutingHaProcess.execute(ARGV.shift, ARGV)"
  assert(source.scan(cli_marker).size == 1, 'helper CLI marker must be unique')
  module_source = source.split(cli_marker, 2).first
  fixture_error = Class.new(StandardError)
  check = lambda { |value, message| raise fixture_error, message unless value }
  # 保留真实 finish_owned/reap；仅时钟、信号及内核 wait 返回值由 fixture 控制。
  budget_fixture = lambda do |body|
    namespace = Module.new
    namespace.module_eval(body, helper, 1)
    implementation = namespace.const_get(:RoutingHaProcess, false)
    clock = 0.0
    reads = 0
    signals = []
    waits = []
    implementation.define_singleton_method(:now) do
      reads += 1
      # 期限后若不再 sleep，有限读次数也能确定性拒绝空转，不依赖宿主调度。
      check.call(reads <= 1000, 'virtual deadline exceeded')
      clock
    end
    implementation.define_singleton_method(:sleep) { |seconds| clock += seconds }
    implementation.define_singleton_method(:alive) { |_pid| true }
    implementation.define_singleton_method(:signal) { |kind, pid| signals << [kind, pid, clock] }
    original_wait = Process.method(:waitpid2)
    begin
      Process.define_singleton_method(:waitpid2) do |pid = -1, flags = 0|
        check.call(flags == Process::WNOHANG, 'blocking waitpid2 is forbidden')
        waits << [pid, flags]
        nil
      end
      result = implementation.finish_owned(123, nil, 0.6, 0.15)
      check.call(result == [nil, false], 'unknown reap must remain unresolved')
      check.call(clock <= 0.6 + 1e-9, 'virtual deadline exceeded')
      check.call(!waits.empty?, 'actual reap was not exercised')
      check.call(signals.first == ['TERM', 123, 0.0], 'first signal must be TERM')
      check.call(signals.any? { |kind, _, time| kind == 'KILL' && time <= 0.6 }, 'KILL missing within deadline')
      check.call(signals.all? { |_, pid, time| pid == 123 && time >= 0 && time <= 0.6 + 1e-9 }, 'signal exceeded virtual deadline')
    ensure
      Process.define_singleton_method(:waitpid2, original_wait)
    end
  end
  budget_fixture.call(module_source)
  # 反证仅作用于复制的模块；限定异常类型和消息，不能把加载错误等偶然失败当 RED。
  red_log = []
  {
    'break if now >= deadline' => ['break if now >= deadline + 0.2', 'virtual deadline exceeded'],
    'Process.waitpid2(pid, Process::WNOHANG)' => ['Process.waitpid2(pid, 0)', 'blocking waitpid2 is forbidden']
  }.each do |original, (mutation, expected)|
    assert(module_source.scan(original).size == 1, "mutation target must be unique: #{original}")
    begin
      budget_fixture.call(module_source.sub(original, mutation))
    rescue fixture_error => error
      assert(error.message == expected, "unexpected mutation failure: #{error.message}")
      red_log << "RED #{original} -> #{mutation}: #{error.message}"
    else
      abort "mutation survived: #{original}"
    end
  end
  File.write('/tmp/ha-reap-budget-red.log', red_log.join("\n") + "\n")
  # 模拟内核在 KILL 后仍未返回 wait 状态：不得转为阻塞 wait 或清空登记。
  unreaped = File.join(dir,'unreaped-guard.rb')
  File.write(unreaped, <<~CODE)
    module UnreapedGuard
      def waitpid2(pid = -1, flags = 0)
        abort 'blocking final reap' unless flags == Process::WNOHANG
        super
        nil
      end
    end
    Process.singleton_class.prepend(UnreapedGuard)
  CODE
  state = File.join(dir,'unreaped')
  child = Process.spawn(env.merge('ROUTING_HA_STATE_DIR'=>state,'RUBYOPT'=>"-r#{guard} -r#{unreaped}"),
    'ruby',helper,'run','unreaped','0.6','ruby','-e','trap("TERM") {}; sleep 20',
    out:File.join(dir,'unreaped.log'),err:[:child,:out])
  # 外层 8 秒只防止 fixture 挂死；生产 0.6 秒期限由上面的虚拟时钟断言。
  Timeout.timeout(8) { Process.wait(child) }
  assert($?.exitstatus == 124,'unknown reap lost timeout status')
  assert(!Dir.glob(File.join(state,'command-groups','*','*.pid')).empty?,'unknown reap cleared registry')
  # 登记发布异常发生在 spawn 之后，仍必须终止直接拥有的子进程。
  publish_guard = File.join(dir,'publish-guard.rb')
  File.write(publish_guard, <<~CODE)
    module PublishGuard
      def rename(source, target)
        if target.end_with?('.pid')
          Timeout.timeout(5) { sleep 0.01 until File.exist?(ENV.fetch('PUBLISH_READY')) }
          raise IOError, 'fixture publication failure'
        end
        super
      end
    end
    File.singleton_class.prepend(PublishGuard)
  CODE
  state = File.join(dir,'publish-error')
  ready = File.join(dir,'publish-ready')
  child = Process.spawn(env.merge('ROUTING_HA_STATE_DIR'=>state,'PUBLISH_READY'=>ready,'RUBYOPT'=>"-rtimeout -r#{guard} -r#{publish_guard}"),
    'ruby',helper,'run','publish','','ruby','-e','trap("TERM") { exit }; File.write(ARGV[0],Process.pid); sleep 20',ready,
    out:File.join(dir,'publish.log'),err:[:child,:out])
  Timeout.timeout(8) { Process.wait(child) }
  assert($?.exitstatus != 0,'publication error passed')
  wait_file(ready)
  begin
    Process.kill(0,Integer(File.read(ready)))
    abort 'publication failure abandoned owned child'
  rescue Errno::ESRCH
  end
  # 当前会话出现损坏登记不能从 supervisor 抛出并遗弃场景。
  corrupt = File.join(dir,'corrupt.sh')
  File.write(corrupt, <<~SH)
    #!/usr/bin/env bash
    trap 'exit 143' TERM
    printf ready > "$ROUTING_HA_STATE_DIR/corrupt-ready"
    printf invalid > "$ROUTING_HA_STATE_DIR/command-groups/$ROUTING_HA_PROCESS_SESSION/corrupt.pid"
    exec ruby -e 'trap("TERM") { exit }; sleep 20'
  SH
  FileUtils.chmod(0755,corrupt)
  state = File.join(dir,'corrupt')
  e = env.merge('ROUTING_HA_STATE_DIR'=>state,'ROUTING_HA_LOGS_TIMEOUT_SECONDS'=>'0.3',
    'ROUTING_HA_DOWN_TIMEOUT_SECONDS'=>'0.3','ROUTING_HA_SUPERVISOR_OVERHEAD_SECONDS'=>'0.15',
    'ROUTING_HA_CLEANUP_GRACE_SECONDS'=>'1')
  began = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  child = Process.spawn(e,'ruby',helper,'supervise','10',corrupt,out:File.join(dir,'corrupt.log'),err:[:child,:out])
  Timeout.timeout(3) { Process.wait(child) }
  assert($?.exitstatus == 1,'registry parse failure did not preserve failure')
  assert(Process.clock_gettime(Process::CLOCK_MONOTONIC)-began < 2,'registry parse error blocked supervisor')
  assert(!Dir.glob(File.join(state,'command-groups','*','corrupt.pid')).empty?,'corrupt registry history lost')
  # 宽限耗尽仍须有界退出：场景忽略 TERM，注册命令也忽略 TERM。
  stubborn = File.join(dir,'stubborn.sh')
  File.write(stubborn, <<~SH)
    #!/usr/bin/env bash
    trap '' TERM INT
    ruby "$HELPER" run stubborn "" ruby -e 'trap("TERM") {}; File.write(ARGV[0],Process.pid); sleep 20' "$ROUTING_HA_STATE_DIR/stubborn-ready"
    ruby -e 'trap("TERM") {}; sleep 20'
  SH
  FileUtils.chmod(0755,stubborn)
  state = File.join(dir,'grace')
  e = env.merge('ROUTING_HA_STATE_DIR'=>state,'HELPER'=>helper,
    'ROUTING_HA_LOGS_TIMEOUT_SECONDS'=>'0.3','ROUTING_HA_DOWN_TIMEOUT_SECONDS'=>'0.3',
    'ROUTING_HA_SUPERVISOR_OVERHEAD_SECONDS'=>'0.15','ROUTING_HA_CLEANUP_GRACE_SECONDS'=>'1')
  began = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  child = Process.spawn(e,'ruby',helper,'supervise','0.5',stubborn,out:File.join(dir,'grace.log'),err:[:child,:out])
  Timeout.timeout(3) { Process.wait(child) }
  assert($?.exitstatus == 124,'grace exhausted changed timeout status')
  assert(Process.clock_gettime(Process::CLOCK_MONOTONIC)-began < 2.1,'grace exhaustion blocked')
  assert(JSON.parse(File.read(File.join(state,'diagnostics/interruption.json')))['reason'] == 'timeout','wrong timeout reason')

end
puts 'routing-ha process self-test passed.'
RUBY
