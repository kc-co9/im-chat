#!/usr/bin/env bash
# 用途：验证受控 bolt-dev 部署拓扑合同及拒绝样本；不启动业务、不将 fixture 当实际隔离证明。
# 输入：无参数。输出/副作用：读取 Compose，临时 JSON 目录由本脚本 trap 清理；不修改 Docker 状态。
# 依赖：Docker Compose、Ruby JSON/UNIXSocket/Timeout。退出码：任一安全断言未满足即非 0。
# Shell fixture 仅 fake Docker；通过 UNIX socket 就绪/释放握手保证并发顺序，各等待最多15秒，不 sleep 轮询。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
file="$ROOT_DIR/deploy/local/compose.bolt-dev.yml"
[[ -f "$file" ]] || { echo "Missing dedicated bolt-dev Compose" >&2; exit 1; }
docker compose -f "$file" config --format json | ruby -rjson -e '
  config = JSON.parse(STDIN.read)
  network = config.fetch("networks").fetch("bolt-dev", {})
  abort "bolt-dev requires actual internal network configuration" unless network["internal"] == true
  abort "bolt-dev requires fixed subnet" unless network.fetch("ipam").fetch("config").map { |c| c["subnet"] } == ["172.30.88.0/24"]
'
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
docker compose -f "$ROOT_DIR/deploy/local/compose.bolt-dev.yml" config --format json > "$tmp/compose.json"
ruby "$ROOT_DIR/scripts/lib/bolt-dev-network.rb" config "$tmp/compose.json"
ruby -rjson -ryaml - "$ROOT_DIR" "$tmp/compose.json" <<'PAIR_RUBY'
root, path = ARGV
config = JSON.parse(File.read(path))
port = YAML.load_file("#{root}/im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/resources/application.yml").fetch('im').fetch('bolt').fetch('server').fetch('port')
valid = lambda do |candidate|
  broker = candidate.fetch('services').fetch('broker').fetch('environment')
  gateway = candidate.fetch('services').fetch('gateway').fetch('environment')
  broker['IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_ADDRESS'] == "#{gateway.fetch('IM_GATEWAY_HOST')}:#{port}" &&
    broker['IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_EXPECTEDPEERIDENTITY'] == 'spiffe://im-chat/gateway/bolt-dev-gateway'
end
abort 'DEV Gateway deployment pair must match actual Gateway host/port and fixed namespace URI' unless valid.call(config)
%w[missing-pair wrong-endpoint].each do |label|
  mutant = Marshal.load(Marshal.dump(config))
  env = mutant['services']['broker']['environment']
  if label == 'missing-pair'
    env.delete('IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_ADDRESS')
    env.delete('IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_EXPECTEDPEERIDENTITY')
  else
    env['IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_ADDRESS'] = '172.30.88.11:18013'
  end
  abort "#{label} incorrectly accepted" if valid.call(mutant)
end
puts 'bolt-dev Gateway pair: actual Compose positive + missing/wrong endpoint rejected'
PAIR_RUBY
# actual JSON 形状用确定性 fixture 检查全部拒绝路径；真实 Docker inspect 由 --check-only 另验。
ruby -rjson - "$ROOT_DIR" "$tmp" <<'RUBY'
root, dir = ARGV
config = JSON.parse(File.read("#{dir}/compose.json"))
network_name = config.fetch('networks').fetch('bolt-dev').fetch('name')
containers = config.fetch('services').map do |name, service|
  {
    'Id' => name, 'State' => {'Running' => false}, 'Mounts' => [],
    'Config' => {'Labels' => {'com.docker.compose.project' => config['name'], 'com.docker.compose.service' => name},
                 'Env' => service.fetch('environment', {}).map { |key, value| "#{key}=#{value}" }},
    'HostConfig' => {'Privileged' => false, 'NetworkMode' => network_name,
                     'PortBindings' => service.fetch('ports', []).to_h { |port| ["#{port['target']}/tcp", [{'HostIp' => port['host_ip'], 'HostPort' => port['published']}]] }},
    'NetworkSettings' => {'Networks' => {network_name => {'IPAMConfig' => {'IPv4Address' => service['networks']['bolt-dev']['ipv4_address']}}}}
  }
end
network = [{'Name' => network_name, 'Internal' => true, 'Driver' => 'bridge', 'Containers' => {},
            'Labels' => {'com.docker.compose.project' => config['name'], 'com.docker.compose.network' => 'bolt-dev'},
            'IPAM' => {'Config' => [{'Subnet' => '172.30.88.0/24'}]}}]
check = lambda do |label, cfg, net, ctrs, expected|
  [cfg, net, ctrs].zip(%w[config network containers]).each { |data, name| File.write("#{dir}/#{name}.json", JSON.generate(data)) }
  success = system('ruby', "#{root}/scripts/lib/bolt-dev-network.rb", 'actual', "#{dir}/config.json", "#{dir}/network.json", "#{dir}/containers.json", out: File::NULL, err: File::NULL)
  abort "#{label}: expected #{expected}, got #{success}" unless success == expected
  puts "bolt-dev fixture #{label}: passed"
end
check.call('positive', config, network, containers, true)
mutations = {
  'actual-public-network' => ->(c,n,t) { n[0]['Internal'] = false },
  'actual-wrong-subnet' => ->(c,n,t) { n[0]['IPAM']['Config'][0]['Subnet'] = '10.0.0.0/24' },
  'actual-extra-attachment' => ->(c,n,t) { t[0]['NetworkSettings']['Networks']['external'] = {} },
  'actual-host-network' => ->(c,n,t) { t[0]['HostConfig']['NetworkMode'] = 'host' },
  'actual-foreign-member' => ->(c,n,t) { n[0]['Containers']['foreign'] = {} },
  'actual-nonloopback-bolt-port' => ->(c,n,t) { t[0]['HostConfig']['PortBindings']['18020/tcp'] = [{'HostIp' => '0.0.0.0', 'HostPort' => '18020'}] },
  'actual-unexpected-loopback-port' => ->(c,n,t) { t[0]['HostConfig']['PortBindings']['18020/tcp'] = [{'HostIp' => '127.0.0.1', 'HostPort' => '18020'}] },
  'actual-synthetic-mismatch' => ->(c,n,t) { t[0]['Config']['Env'] << 'IM_BOLT_SYNTHETIC_DATA=false' },
  'config-production-profile' => ->(c,n,t) { c['services']['broker']['environment']['SPRING_PROFILES_ACTIVE'] = 'production,bolt-dev' },
  'config-missing-synthetic' => ->(c,n,t) { c['services']['broker']['environment'].delete('IM_BOLT_SYNTHETIC_DATA') },
  'config-missing-declaration' => ->(c,n,t) { c['services']['broker']['environment'].delete('IM_BOLT_ISOLATED_CONTAINER_NETWORK') },
  'config-extra-network' => ->(c,n,t) { c['services']['broker']['networks']['external'] = {} },
  'actual-already-running' => ->(c,n,t) { t[0]['State']['Running'] = true }
}
mutations.each do |label, mutate|
  c,n,t = Marshal.load(Marshal.dump([config, network, containers]))
  mutate.call(c,n,t)
  check.call(label, c,n,t, false)
end
RUBY
# 使用本脚本已生成的独立 JSON 构造缺失/错误类型反例，不接受缺省值替代 Docker 事实。
ruby -rjson - "$ROOT_DIR" "$tmp" <<'RUBY'
root, dir = ARGV
# 前一组末次 mutant 的文件不复用，重新从正式config生成最小完整actual形状。
config = JSON.parse(`docker compose -f #{root}/deploy/local/compose.bolt-dev.yml config --format json`)
name = config.fetch('networks').fetch('bolt-dev').fetch('name')
containers = config.fetch('services').map do |service, value|
  {'Id'=>service, 'State'=>{'Running'=>false}, 'Mounts'=>[],
   'Config'=>{'Labels'=>{'com.docker.compose.project'=>config['name'], 'com.docker.compose.service'=>service}, 'Env'=>value.fetch('environment', {}).map{|k,v| "#{k}=#{v}"}},
   'HostConfig'=>{'Privileged'=>false, 'NetworkMode'=>name, 'PortBindings'=>value.fetch('ports', []).to_h{|p| ["#{p['target']}/tcp", [{'HostIp'=>p['host_ip'], 'HostPort'=>p['published']}]]}},
   'NetworkSettings'=>{'Networks'=>{name=>{'IPAMConfig'=>{'IPv4Address'=>value['networks']['bolt-dev']['ipv4_address']}}}}}
end
network = [{'Name'=>name, 'Internal'=>true, 'Driver'=>'bridge', 'Containers'=>{},
            'Labels'=>{'com.docker.compose.project'=>config['name'], 'com.docker.compose.network'=>'bolt-dev'},
            'IPAM'=>{'Config'=>[{'Subnet'=>'172.30.88.0/24'}]}}]
File.write("#{dir}/schema-config.json", JSON.generate(config))
mutations = {
  'missing-privileged'=>->(n,t){t[0]['HostConfig'].delete('Privileged')},
  'null-privileged'=>->(n,t){t[0]['HostConfig']['Privileged']=nil},
  'missing-running'=>->(n,t){t[0]['State'].delete('Running')},
  'string-running'=>->(n,t){t[0]['State']['Running']='false'},
  'string-privileged'=>->(n,t){t[0]['HostConfig']['Privileged']='false'},
  'invalid-network-containers'=>->(n,t){n[0]['Containers']=[]},
  'null-running'=>->(n,t){t[0]['State']['Running']=nil},
  'missing-mounts'=>->(n,t){t[0].delete('Mounts')},
  'null-mounts'=>->(n,t){t[0]['Mounts']=nil},
  'missing-port-bindings'=>->(n,t){t[0]['HostConfig'].delete('PortBindings')},
  'invalid-port-bindings'=>->(n,t){t[0]['HostConfig']['PortBindings']=[]},
  'missing-network-containers'=>->(n,t){n[0].delete('Containers')},
  'missing-network-labels'=>->(n,t){n[0].delete('Labels')},
  'foreign-network-project'=>->(n,t){n[0]['Labels']['com.docker.compose.project']='other'}
}
# Docker nullable字段显式正例；有发布的Gateway不能null，否则精确port比较失败。
nullable = Marshal.load(Marshal.dump(containers))
nullable[0]['HostConfig']['PortBindings'] = nil
File.write("#{dir}/schema-network.json", JSON.generate(network))
File.write("#{dir}/schema-containers.json", JSON.generate(nullable))
abort 'explicit null PortBindings with no publication rejected' unless system('ruby', "#{root}/scripts/lib/bolt-dev-network.rb", 'actual', "#{dir}/schema-config.json", "#{dir}/schema-network.json", "#{dir}/schema-containers.json", out: File::NULL, err: File::NULL)
failures = []
mutations.each do |label, mutate|
  n,t = Marshal.load(Marshal.dump([network,containers]));mutate.call(n,t)
  File.write("#{dir}/schema-network.json", JSON.generate(n)); File.write("#{dir}/schema-containers.json", JSON.generate(t))
  success = system('ruby', "#{root}/scripts/lib/bolt-dev-network.rb", 'actual', "#{dir}/schema-config.json", "#{dir}/schema-network.json", "#{dir}/schema-containers.json", out: File::NULL, err: File::NULL)
  failures << label if success
end
abort "missing/malformed facts incorrectly accepted: #{failures.join(', ')}" unless failures.empty?
puts "bolt-dev malformed facts: #{mutations.length} rejected"
RUBY
# Shell entry 的并发/探测失败反例：fake Docker 只记录命令；UNIX socket 作为有界握手，不轮询 sleep。
ruby -rjson -rsocket -rtimeout - "$ROOT_DIR" "$tmp" <<'RUBY'
root, dir = ARGV
fake = "#{dir}/docker-fake"
File.write(fake, <<'FAKE')
#!/usr/bin/env ruby
require 'socket'
File.open(ENV.fetch('FAKE_LOG'), 'a') { |f| f.puts(ARGV.join(' ')) }
if ARGV[0] == 'context'
  exit 44 if ENV['CASE'] == 'CONTEXT_ERROR'
  puts(ARGV[1] == 'show' ? 'fixture' : 'unix:///fixture/bolt-dev-test.sock'); exit
end
if ARGV[0] == 'ps'
  exit 42 if ENV['CASE'] == 'PS_ERROR'
  if ENV['ROLE'] == 'first'
    socket = UNIXSocket.new(ENV.fetch('SYNC_SOCKET')); socket.puts('ready'); socket.gets; socket.close
    puts 'existing-first'; exit
  end
  exit
end
if ARGV[0,2] == ['network','ls']
  exit(ENV['CASE'] == 'NETWORK_ERROR' ? 43 : 0)
end
if ARGV[0] == 'compose'
  if ARGV.include?('config'); print File.read(ENV.fetch('FAKE_CONFIG')); exit; end
  exit 77 if ARGV.include?('create')
  exit
end
abort "unexpected fake command #{ARGV.inspect}"
FAKE
File.chmod(0755, fake)
# 独立记录无任何真实Docker副作用；所有child在ensure中有界回收。
base = {'DOCKER_BIN'=>fake, 'FAKE_CONFIG'=>"#{dir}/compose.json", 'DOCKER_HOST'=>'unix:///fixture/bolt-dev-test.sock', 'DOCKER_CONTEXT'=>''}
script = "#{root}/scripts/bolt-dev-up.sh"
failures=[]
%w[PS_ERROR NETWORK_ERROR CONTEXT_ERROR].each do |test|
  log="#{dir}/#{test}.log"
  env=base.merge('DOCKER_HOST'=>(test == 'CONTEXT_ERROR' ? '' : base['DOCKER_HOST']), 'CASE'=>test,'FAKE_LOG'=>log,'BOLT_DEV_PROJECT'=>"bolt-dev-fixture-#{Process.pid}-#{test.downcase.tr('_','-')}")
  status=system(env, 'bash',script,'--check-only',out:File::NULL,err:File::NULL)
  text=File.read(log)
  failures << test if status || text.match?(/ (create|start|down)( |$)/)
end
server=UNIXServer.new("#{dir}/sync.sock")
first=nil; second=nil; connection=nil
begin
  env=base.merge('BOLT_DEV_PROJECT'=>"bolt-dev-fixture-parallel-#{Process.pid}", 'SYNC_SOCKET'=>"#{dir}/sync.sock")
  first=Process.spawn(env.merge('ROLE'=>'first','FAKE_LOG'=>"#{dir}/first.log"), 'bash',script,'--check-only',out:File::NULL,err:File::NULL)
  connection=Timeout.timeout(15){server.accept}
  abort 'missing fixture readiness' unless Timeout.timeout(5){connection.gets} == "ready\n"
  second=Process.spawn(env.merge('ROLE'=>'second','FAKE_LOG'=>"#{dir}/second.log"),'bash',script,'--check-only',out:File::NULL,err:File::NULL)
  Timeout.timeout(15){Process.wait(second)}
  status=$?;second=nil
  text=File.exist?("#{dir}/second.log") ? File.read("#{dir}/second.log") : ''
  failures << 'PARALLEL_LOCK' if status.success? || text.match?(/ (create|start|down)( |$)/)
  connection.puts('release')
  Timeout.timeout(15){Process.wait(first)};first=nil
ensure
  connection.close if connection
  server.close
  [first,second].compact.each { |pid| Process.kill('KILL',pid) rescue nil; Process.wait(pid) rescue nil }
end
abort "unsafe shell entry cases: #{failures.join(', ')}" unless failures.empty?
puts 'bolt-dev shell entry: probe errors 2 + endpoint resolution error 1 + exclusive concurrency 1 passed'
RUBY
# 取消 create 及跨 TMPDIR 并发：socket 逐阶段同步，并检查实际 create 子进程终止先于 down。
ruby -rsocket -rtimeout - "$ROOT_DIR" "$tmp" <<'RUBY'
root, dir = ARGV
fake="#{dir}/docker-cancel"
File.write(fake, <<'FAKE')
#!/usr/bin/env ruby
require 'socket'
File.open(ENV.fetch('FAKE_LOG'),'a'){|f| f.puts(ARGV.join(' '))}
if ARGV[0]=='compose'
  if ARGV.include?('config'); print File.read(ENV.fetch('FAKE_CONFIG')); exit; end
  if ARGV.include?('create')
    exit 77 if ENV['ROLE']=='later'
    s=UNIXSocket.new(ENV.fetch('SYNC_SOCKET')); s.puts("create #{Process.pid}"); s.gets
    exit 77
  end
  if ARGV.include?('down')
    exit if ENV['ROLE']=='later'
    s=UNIXSocket.new(ENV.fetch('SYNC_SOCKET')); s.puts('down'); s.gets
  end
end
FAKE
File.chmod(0755,fake)
script="#{root}/scripts/bolt-dev-up.sh"
["#{dir}/tmp-one","#{dir}/tmp-two"].each{|p|Dir.mkdir(p)}
server=UNIXServer.new("#{dir}/cancel.sock")
children=[]; sockets=[]; create_pid=nil; failures=[]
begin
  base={'DOCKER_BIN'=>fake,'DOCKER_HOST'=>'unix:///fixture/cancel.sock','DOCKER_CONTEXT'=>'', 'FAKE_CONFIG'=>"#{dir}/compose.json",'SYNC_SOCKET'=>"#{dir}/cancel.sock",'BOLT_DEV_PROJECT'=>"bolt-dev-cancel-#{Process.pid}"}
  first=Process.spawn(base.merge('FAKE_LOG'=>"#{dir}/cancel-first.log",'TMPDIR'=>"#{dir}/tmp-one"),'bash',script,'--check-only',out:File::NULL,err:File::NULL);children << first
  create=Timeout.timeout(15){server.accept};sockets<<create
  create_pid=Integer(Timeout.timeout(5){create.gets}.split.last)
  # 使用不同TMPDIR，第二调用仍必须在同一个锁上拒绝，不能进入create/down。
  second=Process.spawn(base.merge('ROLE'=>'later','FAKE_LOG'=>"#{dir}/cancel-second.log",'TMPDIR'=>"#{dir}/tmp-two"),'bash',script,'--check-only',out:File::NULL,err:File::NULL);children << second
  Timeout.timeout(15){Process.wait(second)};children.delete(second)
  failures << 'TMPDIR_BYPASS' if (File.exist?("#{dir}/cancel-second.log") ? File.read("#{dir}/cancel-second.log") : '').match?(/ (create|start|down)( |$)/)
  Process.kill('TERM',first)
  # 旧脚本等待foreground Ruby，先解除create使其可处理trap；修后create socket会EOF且child已退出。
  eof=IO.select([create],nil,nil,3)
  if !eof
    failures << 'CREATE_NOT_CANCELLED'
    create.puts('release')
  end
  if IO.select([server],nil,nil,3)
    down=server.accept;sockets<<down
    event=Timeout.timeout(5){down.gets}
    alive = begin
      Process.kill(0,create_pid)
      true
    rescue Errno::ESRCH
      false
    end
    failures << 'DOWN_BEFORE_CREATE_REAPED' if alive
    failures << 'WRONG_CLEANUP_EVENT' unless event=="down\n"
    # cleanup仍持锁，第三调用不能create/down。
    third=Process.spawn(base.merge('ROLE'=>'later','FAKE_LOG'=>"#{dir}/cancel-third.log",'TMPDIR'=>"#{dir}/tmp-two"),'bash',script,'--check-only',out:File::NULL,err:File::NULL);children << third
    Timeout.timeout(15){Process.wait(third)};children.delete(third)
    failures << 'CLEANUP_LOCK_RELEASED' if (File.exist?("#{dir}/cancel-third.log") ? File.read("#{dir}/cancel-third.log") : '').match?(/ (create|start|down)( |$)/)
    down.puts('release')
  else failures << 'MISSING_DOWN';end
  Timeout.timeout(15){Process.wait(first)};children.delete(first)
  count=File.readlines("#{dir}/cancel-first.log").count{|l|l.match?(/ down( |$)/)}
  failures << 'DOWN_COUNT' unless count==1
ensure
  sockets.each{|s|s.close rescue nil};server.close
  children.each{|pid|Process.kill('KILL',pid) rescue nil;Process.wait(pid) rescue nil}
  if create_pid;Process.kill('KILL',create_pid) rescue nil;end
end
abort "create cancellation failures: #{failures.join(', ')}" unless failures.empty?
puts 'bolt-dev create cancellation + cross-TMPDIR + cleanup lock: passed'
RUBY
# 启动handoff精确反例：仅测试进程的BASH_ENV DEBUG/RUBYOPT TracePoint在真实语句边界送信号，生产无测试开关。
ruby -rtimeout - "$ROOT_DIR" "$tmp" <<'RUBY'
root,dir=ARGV
script="#{root}/scripts/bolt-dev-up.sh"
source=File.read(script)
ruby_code=source.split("ruby -rtimeout -e '\n",2).last.split("\n  ' \"$DOCKER_BIN\"",2).first
# 旧实现在第二个raise trap安装前已经把第一个trap改为raise且不在begin内；新实现选PID发布后的waiting语句。
line=ruby_code.lines.index{|l|l.include?('trap("INT") { raise Interrupt }')} || ruby_code.lines.index{|l|l.include?('waiting = true')}
abort 'missing exact supervisor handoff statement' unless line
File.write("#{dir}/handoff-trace.rb", <<~TRACE)
  if ARGV.include?('create')
    fired=false
    TracePoint.new(:line) do |trace|
      if !fired && trace.path=='-e' && trace.lineno==#{line+2}
        fired=true
        File.write(ENV.fetch('HANDOFF_MARKER'), trace.binding.local_variable_get(:pid).to_s)
        Process.kill('TERM',Process.pid)
      end
    end.enable
  end
TRACE
File.write("#{dir}/handoff-bash", <<'BASH')
set -T
trap 'if [[ "$BASH_COMMAND" == '\''operation_pid=$!'\'' && "$*" == *create* && ! -f "$HANDOFF_MARKER" ]]; then echo "$!" > "$HANDOFF_MARKER"; kill -TERM "$$"; fi' DEBUG
BASH
fake="#{dir}/docker-handoff"
File.write(fake, <<'FAKE')
#!/usr/bin/env ruby
File.open(ENV.fetch('FAKE_LOG'),'a'){|f|f.puts(ARGV.join(' '))}
if ARGV.include?('config');print File.read(ENV.fetch('FAKE_CONFIG'));exit;end
if ARGV.include?('create')
  File.write(ENV.fetch('CHILD_PID'),Process.pid.to_s)
  reader,writer=IO.pipe
  IO.select([reader],nil,nil,10)
  exit 77
end
FAKE
File.chmod(0755,fake)
failures=[]
%w[bash ruby].each do |mode|
  log="#{dir}/handoff-#{mode}.log";marker="#{dir}/handoff-#{mode}.marker";pidfile="#{dir}/handoff-#{mode}.pid"
  env={'DOCKER_BIN'=>fake,'DOCKER_HOST'=>'unix:///fixture/handoff.sock','DOCKER_CONTEXT'=>'', 'FAKE_LOG'=>log,'FAKE_CONFIG'=>"#{dir}/compose.json",'HANDOFF_MARKER'=>marker,'CHILD_PID'=>pidfile,'BOLT_DEV_PROJECT'=>"bolt-dev-handoff-#{mode}-#{Process.pid}"}
  if mode=='bash';env['BASH_ENV']="#{dir}/handoff-bash";else env['RUBYOPT']="-r#{dir}/handoff-trace.rb";end
  pid=Process.spawn(env,'bash',script,'--check-only',out:File::NULL,err:File::NULL)
  begin
    Timeout.timeout(15){Process.wait(pid)}
    failures<<"#{mode}:signal-not-triggered" unless File.exist?(marker)
    text=File.read(log)
    failures<<"#{mode}:cleanup-not-once" unless text.lines.count{|l|l.match?(/ down( |$)/)}==1
    recorded = File.exist?(marker) ? File.read(marker).strip : ''
    # Ruby marker为已spawn Docker PID；Bash marker为尚未发布到operation_pid的supervisor PID。
    if recorded.match?(/^[0-9]+$/)
      target=Integer(recorded)
      begin
        Process.kill(0,target)
        failures<<"#{mode}:handoff-owned-process-alive"
        Process.kill('KILL',target)
      rescue Errno::ESRCH
      end
    end
    if File.exist?(pidfile)
      child=Integer(File.read(pidfile))
      begin
        Process.kill(0,child)
        failures<<"#{mode}:orphan-child"
        Process.kill('KILL',child)
      rescue Errno::ESRCH
      end
    end
  ensure
    Process.kill('KILL',pid) rescue nil
    Process.wait(pid) rescue nil
  end
end
abort "handoff cancellation failures: #{failures.join(', ')}" unless failures.empty?
puts 'bolt-dev Bash PID publication + Ruby spawn handoff cancellation: passed'
RUBY
