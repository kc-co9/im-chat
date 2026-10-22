#!/usr/bin/env ruby
# 用途：run 监督固定 argv 命令组，supervise 监督整个场景；不解释 shell 文本。
# 输入：操作、固定步骤名/主期限、命令期限/入口、argv；空命令期限仍受父监督约束。
# 输出：步骤 UTC 边界（不打印 argv），私有 command-groups 登记；取消原因写 diagnostics。
# 算法：单调期限内预留 TERM 窗口，随后 KILL；直接子进程退出后仍检查拥有组。
# 失败：原码优先；无法证明组消失则保留登记并失败，父宽限只用于终止和清理。
require 'json'
require 'time'
require 'fileutils'
require 'securerandom'

module RoutingHaProcess
  module_function
  def now
    Process.clock_gettime(Process::CLOCK_MONOTONIC)
  end
  def positive(name, default)
    value = Float(ENV.fetch(name, default.to_s))
    raise ArgumentError, "#{name} must be positive and finite" unless value.finite? && value > 0
    value
  end
  def config
    terminate = positive('ROUTING_HA_TERMINATE_SECONDS', 1)
    limits = %w[ENGINE LOGS DOWN].zip([8, 5, 30]).map do |name, default|
      limit = positive("ROUTING_HA_#{name}_TIMEOUT_SECONDS", default)
      raise ArgumentError, "#{name} timeout must exceed termination window" unless limit > terminate
      limit
    end
    grace = positive('ROUTING_HA_CLEANUP_GRACE_SECONDS', 45)
    overhead = positive('ROUTING_HA_SUPERVISOR_OVERHEAD_SECONDS', 2)
    raise ArgumentError, 'cleanup grace cannot cover termination, logs, down and overhead' if grace < terminate + limits[1] + limits[2] + overhead
    [terminate, grace]
  end
  def alive(group)
    Process.kill(0, -group)
    true
  rescue Errno::ESRCH
    false
  rescue Errno::EPERM
    true
  end
  def signal(kind, group)
    Process.kill(kind, -group)
  rescue Errno::ESRCH, Errno::EPERM
    # 无权限不能证明终止；调用方继续保留登记并报告失败。
  end
  def registry(state)
    path = File.join(state, 'command-groups')
    raise ArgumentError, 'symlink registry/state forbidden' if File.symlink?(state) || File.symlink?(path)
    FileUtils.mkdir_p(path, mode: 0700)
    raise ArgumentError, 'registry must be private and owned' unless File.stat(path).uid == Process.uid && (File.stat(path).mode & 0077).zero?
    path
  end
  def atomic(path, contents)
    temporary = "#{path}.#{SecureRandom.hex(8)}.tmp"
    File.open(temporary, File::WRONLY | File::CREAT | File::EXCL, 0600) { |file| file.write(contents) }
    File.rename(temporary, path)
  ensure
    File.unlink(temporary) if temporary && File.exist?(temporary)
  end
  def session(root, operation)
    token = ENV['ROUTING_HA_PROCESS_SESSION']
    if token && operation == 'run'
      raise ArgumentError, 'invalid session token' unless token.match?(/\A[0-9a-f]{48}\z/)
      path = File.join(root,token)
      raise ArgumentError, 'invalid session directory' if File.symlink?(path) || !File.directory?(path)
      owner_file = File.join(path,'owner.json')
      raise ArgumentError, 'unsafe session ownership' if File.symlink?(owner_file) || File.stat(path).uid != Process.uid || (File.stat(path).mode & 0077) != 0
      owner = JSON.parse(File.read(owner_file))
      raise ArgumentError, 'inactive session' unless owner['token'] == token && owner['active']
      Process.kill(0,owner.fetch('pid'))
      [path, owner, false]
    else
      # 历史未决登记只阻止新运行，绝不解释其中的 PID 或发送信号。
      Dir.children(root).each do |name|
        old = File.join(root,name)
        raise ArgumentError, 'unresolved historical registry' unless File.directory?(old) && !File.symlink?(old) &&
          Dir.glob(File.join(old,'*.pid')).empty? && File.file?(File.join(old,'done'))
      end
      token = SecureRandom.hex(24)
      path = File.join(root,token)
      Dir.mkdir(path,0700)
      owner = {'token'=>token,'pid'=>Process.pid,'active'=>true}
      atomic(File.join(path,'owner.json'),JSON.generate(owner))
      [path,owner,true]
    end
  end
  def reap(pid, status)
    status || Process.waitpid2(pid, Process::WNOHANG)&.last
  rescue Errno::ECHILD
    status
  end
  def raw(status)
    status && (status.exitstatus || 128 + status.termsig)
  end
  # 所有取消尾程共用同一绝对期限；KILL 后只做非阻塞回收。
  def finish_owned(pid, status, deadline, terminate)
    signal('TERM',pid)
    kill_at = [now + terminate, deadline - 0.04].min
    loop do
      status = reap(pid,status)
      break if status && !alive(pid)
      signal('KILL',pid) if now >= kill_at
      break if now >= deadline
      sleep [0.01,deadline-now].min if deadline > now
    end
    [reap(pid,status), !alive(pid)]
  end
  def emit(state, step, event, fields = {})
    record = {step: step, event: event, timestamp: Time.now.utc.iso8601(6)}.merge(fields)
    line = JSON.generate(record)
    puts line
    STDOUT.flush
    File.open(File.join(state, 'diagnostics/steps.jsonl'), 'a', 0600) { |file| file.puts(line) }
  end
  def execute(operation, args)
    terminate, grace = config
    state = ENV.fetch('ROUTING_HA_STATE_DIR')
    FileUtils.mkdir_p(File.join(state, 'diagnostics'))
    owned, owner, created = session(registry(state), operation)
    pid = nil
    status = nil
    entry = nil
    deadline = nil
    cancelled = nil
    %w[TERM INT].each { |name| Signal.trap(name) { cancelled ||= name == 'INT' ? 130 : 143 } }
    if operation == 'run'
      step, timeout, *command = args
      # cleanup 不再次响应场景组重复取消；父监督在宽限尾部仍可 KILL。
      %w[TERM INT].each { |name| Signal.trap(name, 'IGNORE') } if %w[cleanup-logs cleanup-down].include?(step)
      raise ArgumentError, 'invalid fixed step name' unless step && step.match?(/\A[a-z][a-z0-9-]*\z/)
      limit = timeout == '' ? nil : Float(timeout)
      raise ArgumentError, 'deadline must exceed termination window' if limit && (!limit.finite? || limit <= terminate)
      raise ArgumentError, 'missing command' if command.empty?
      emit(state, step, 'started')
      output = ENV['ROUTING_HA_STEP_OUTPUT']
      options = {pgroup: true}
      options.merge!(out: output, err: [:child, :out]) if output
      deadline = limit && now + limit
      pid = Process.spawn(*command, **options)
      entry = File.join(owned, "#{Process.pid}-#{pid}.pid")
      atomic(entry, JSON.generate({token: owner.fetch('token'), helper: Process.pid, group: pid}))
      result = nil
      exited_at = nil
      loop do
        status = reap(pid,status)
        break if status && !alive(pid)
        result ||= cancelled
        result ||= 124 if deadline && now >= deadline - terminate
        exited_at ||= now if status
        result ||= 1 if status && alive(pid) && now - exited_at >= 0.1
        if result
          status, = finish_owned(pid,status,deadline || now + terminate,terminate)
          break
        end
        sleep 0.01
      end
      gone = status && !alive(pid)
      File.unlink(entry) if gone
      result ||= gone ? raw(status) : 1
      emit(state, step, 'finished', exitCode: result, rawExitCode: raw(status), groupGone: !!gone)
      result
    elsif operation == 'supervise'
      timeout, command = args
      budget = Float(timeout)
      raise ArgumentError, 'invalid execution budget' unless budget.finite? && budget > 0
      deadline = now + budget
      pid = Process.spawn({'ROUTING_HA_PROCESS_SESSION'=>owner.fetch('token')},command,pgroup:true)
      ending = nil
      result = nil
      known = {}
      retired = {}
      loop do
        status = reap(pid,status)
        begin
          current = Dir.glob(File.join(owned,'*.pid'))
          current.each do |file|
            next if known.key?(file) || retired.key?(file)
            raise ArgumentError, 'symlink registration' if File.symlink?(file)
            begin
              record = JSON.parse(File.read(file))
              helper = record.fetch('helper')
              group = record.fetch('group')
              raise ArgumentError, 'unowned registration' unless record['token'] == owner['token'] &&
                File.basename(file) == "#{helper}-#{group}.pid" && helper.is_a?(Integer) && group.is_a?(Integer) && group > 1 &&
                Process.getpgid(helper) == pid
              known[file] = group
            rescue Errno::ENOENT, Errno::ESRCH
              # helper 正常撤销登记可与枚举/身份检查交错；仍在的登记不得放行。
              raise if File.exist?(file) || File.symlink?(file)
            end
          end
          known.delete_if do |file, group|
            gone = !alive(group)
            retired[file] = true if gone
            gone
          end
        rescue StandardError => error
          warn "registry failure: #{error.class}"
          result ||= 1
        end
        break if status && known.empty? && Dir.glob(File.join(owned,'*.pid')).empty? && !alive(pid)
        result = cancelled if cancelled && !ending
        result = 124 if !ending && now >= deadline
        result ||= raw(status) || 1 if status
        if result && !ending
          reason = result == 124 ? 'timeout' : cancelled ? 'signal' : 'owned-process-residue'
          atomic(File.join(state,'diagnostics/interruption.json'),JSON.generate({reason:reason,exitCode:result,timestamp:Time.now.utc.iso8601}))
          signal('TERM',pid)
          ending = now + grace
        end
        if ending && now >= ending - 0.04
          known.each_value { |group| signal('KILL',group) }
          signal('KILL',pid)
        end
        break if ending && now >= ending
        sleep 0.01
      end
      status = reap(pid,status)
      result || raw(status) || 1
    else
      raise ArgumentError, 'expected run or supervise'
    end
  rescue StandardError => error
    warn "process supervision failed: #{error.class}: #{error.message}"
    status, gone = finish_owned(pid,status,[deadline || now + terminate, now + terminate].min,terminate) if pid
    File.unlink(entry) if entry && gone && File.exist?(entry)
    cancelled || (deadline && now >= deadline ? 124 : 1)
  ensure
    if created && owned
      clean = Dir.glob(File.join(owned,'*.pid')).empty? && (!pid || (status && !alive(pid)))
      atomic(File.join(owned,'done'),'inactive') if clean
      owner['active'] = false
      atomic(File.join(owned,'owner.json'),JSON.generate(owner))
    end
  end
end
begin
  exit RoutingHaProcess.execute(ARGV.shift, ARGV)
rescue ArgumentError, KeyError => error
  warn error.message
  exit 2
end
