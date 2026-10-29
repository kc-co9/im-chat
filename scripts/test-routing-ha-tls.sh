#!/usr/bin/env bash
# 用途：离线验证真实HA材料与部署配对合同，不运行Maven、Docker或在线签发。
# 输出：仅通过标记；材料位于新建0700临时目录，退出时删除，任一正反断言失败即非零。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ruby - "$ROOT_DIR" <<'RUBY'
require 'tmpdir'
require 'fileutils'
root = ARGV.fetch(0)
helper = File.join(root, 'scripts/lib/routing-ha-tls.rb')
compose = File.join(root, 'deploy/local/compose.routing-ha.yml')
abort 'HA TLS fixture helper missing' unless File.file?(helper)
Dir.mktmpdir('routing-ha-tls-contract-') do |dir|
  material = File.join(dir,'material'); Dir.mkdir(material,0700)
  system('ruby',helper,'create',material,compose) or abort 'material generation failed'
  system('ruby',helper,'check',material,compose) or abort 'complete material rejected'
  abort 'CA private key retained after issuance' if File.exist?(File.join(material,'ca.p12'))
  require 'yaml'
  require 'openssl'
  stores = Dir.glob(File.join(material,'*','node.p12'))
  abort 'expected five independent private keys' unless stores.size == 5
  keys = stores.map do |store|
    config = YAML.load_file(File.join(File.dirname(store),'application.yml'))
    passphrase = config.fetch('im').fetch('bolt').fetch('client').fetch('tls').fetch('key-store-password')
    OpenSSL::PKCS12.new(File.binread(store),passphrase).key.public_key.to_der
  end
  abort 'node private key reused' unless keys.uniq.size == 5
  # 密码不得进入helper诊断；配对/证书的实际内容由只读check逐一核对。

  # 不接受缺失或损坏的挂载输入；check不得补发或改写。
  store = Dir.glob(File.join(material,'*','node.p12')).first
  original = File.binread(store)
  File.unlink(store)
  abort 'missing store accepted' if system('ruby',helper,'check',material,compose,out:File::NULL,err:File::NULL)
  File.binwrite(store,'invalid'); File.chmod(0600,store)
  abort 'corrupt store accepted' if system('ruby',helper,'check',material,compose,out:File::NULL,err:File::NULL)
  File.binwrite(store,original)
  File.chmod(0644,store)
  abort 'public store accepted' if system('ruby',helper,'check',material,compose,out:File::NULL,err:File::NULL)
  File.chmod(0600,store)
  config = Dir.glob(File.join(material,'*','application.yml')).first
  contents = File.read(config)
  File.write(config,contents.sub('spiffe://im-chat/broker/','spiffe://untrusted/broker/'))
  abort 'changed identity config accepted' if system('ruby',helper,'check',material,compose,out:File::NULL,err:File::NULL)
  File.write(config,contents)
  system('ruby',helper,'check',material,compose) or abort 'restored material rejected'
  # 生成不覆盖既有目录；失败只留在owned父目录，不向外写入。
  abort 'existing material overwritten' if system('ruby',helper,'create',material,compose,out:File::NULL,err:File::NULL)
  failed = File.join(dir,'failed'); Dir.mkdir(failed,0700)
  bin = File.join(dir,'bin'); Dir.mkdir(bin)
  File.write(File.join(bin,'keytool'),"#!/bin/sh\nexit 23\n"); File.chmod(0700,File.join(bin,'keytool'))
  # 用真实exit23和wait见证回收；拦截已回收PID上的signal，不依赖系统实际复用PID。
  guard = File.join(dir,'reap-guard.rb')
  File.write(guard, <<~'CODE')
    module HaKeytoolReapGuard
      def wait(pid = -1, flags = 0)
        result = super
        if $?.exitstatus == 23
          @ha_reaped_pid = result
          File.write(ENV.fetch('HA_REAP_WITNESS'), 'exit23 reaped')
        end
        result
      end
      def kill(signal, *pids)
        if @ha_reaped_pid && pids.include?(@ha_reaped_pid)
          File.write(ENV.fetch('HA_SIGNAL_AFTER_REAP'), signal.to_s)
          raise Errno::ESRCH # 测试不向失去所有权的PID发送真实信号。
        end
        super
      end
    end
    Process.singleton_class.prepend(HaKeytoolReapGuard)
  CODE
  witness = File.join(dir,'reaped')
  forbidden = File.join(dir,'signal-after-reap')
  failure_env = {'PATH'=>"#{bin}:#{ENV['PATH']}", 'RUBYOPT'=>"-r#{guard}",
                 'HA_REAP_WITNESS'=>witness, 'HA_SIGNAL_AFTER_REAP'=>forbidden}
  abort 'generation failure accepted' if system(failure_env,'ruby',helper,'create',failed,compose,out:File::NULL,err:File::NULL)
  abort 'exit23 was not reaped' unless File.file?(witness)
  abort 'keytool signalled after reap' if File.exist?(forbidden)
  abort 'partial material accepted' if system('ruby',helper,'check',failed,compose,out:File::NULL,err:File::NULL)
end
puts 'Routing HA TLS fixture contract passed.'
RUBY
