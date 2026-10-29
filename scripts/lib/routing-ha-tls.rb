#!/usr/bin/env ruby
# 用途：为固定HA Compose签发一次性测试CA和五个独立节点材料；不是生产签发或D13运行期副本。
# 参数：create/check、调用者刚创建的0700空目录、compose.routing-ha.yml；check只读且不修补材料。
# 输出：每节点node.p12/application.yml及公开CA/叶证书；密码只写0600配置，通过环境传给keytool。
# 隔离/失败：拒绝符号链接、非本UID/私有目录和覆盖；失败保留部分材料交runner owned cleanup。
# 每次keytool最多20秒，失败只输出步骤名不输出argv/密码；父runner监督整个进程组。
require 'yaml'
require 'json'
require 'openssl'
require 'securerandom'
require 'timeout'
require 'fileutils'

module RoutingHaTls
  module_function
  def private_path(path, directory: false)
    stat = File.lstat(path)
    expected = directory ? 0700 : 0600
    raise 'unsafe HA TLS path' unless !stat.symlink? && stat.uid == Process.uid &&
      (stat.mode & 0777) == expected && (directory ? stat.directory? : stat.file?)
  end

  def nodes(compose)
    # 名称、端口和稳定Raft身份以实际部署声明为准，不从运行时metadata学习。
    document = YAML.respond_to?(:unsafe_load_file) ? YAML.unsafe_load_file(compose) : YAML.load_file(compose)
    document.fetch('services').map do |name, service|
      env = service.fetch('environment', {})
      if env.key?('IM_BROKER_HOST')
        raft = JSON.parse(env.fetch('SPRING_APPLICATION_JSON')).fetch('im').fetch('broker').fetch('raft')
        raise 'HA Broker identity/endpoint mismatch' unless raft.fetch('node-id') == name &&
          env.fetch('IM_BROKER_HOST') == name && raft.fetch('endpoint') == "#{name}:18120"
        [name, 'broker', "spiffe://im-chat/broker/#{raft.fetch('node-id')}"]
      elsif env.key?('IM_GATEWAY_HOST')
        raise 'HA Gateway host mismatch' unless env.fetch('IM_GATEWAY_HOST') == name
        [name, 'gateway', "spiffe://im-chat/gateway/#{name}"]
      end
    end.compact.tap do |entries|
      raise 'expected three Brokers and two Gateways' unless entries.count { |n| n[1] == 'broker' } == 3 &&
        entries.count { |n| n[1] == 'gateway' } == 2
      raise 'unsafe HA node name' unless entries.all? { |n| n[0].match?(/\A[a-z0-9][a-z0-9-]*\z/) }
    end
  end

  def configuration(node, entries, password)
    name, role, = node
    brokers = entries.select { |n| n[1] == 'broker' }
    targets = brokers.map { |n| {'address'=>"#{n[0]}:18020", 'expected-peer-identity'=>n[2]} }
    tls = {'enabled'=>true, 'key-store'=>"/run/routing-ha-tls/node.p12",
           'key-store-password'=>password, 'key-store-type'=>'PKCS12'}
    bolt = {'client'=>{'enabled'=>true, 'mode'=>'MTLS', 'tls'=>tls,
             'allowed-peer-uri-prefixes'=> role == 'broker' ? ['spiffe://im-chat/broker/', 'spiffe://im-chat/gateway/'] : ['spiffe://im-chat/broker/']},
            'server'=>{'enabled'=>true, 'mode'=>'MTLS', 'tls'=>tls.merge('need-client-auth'=>true)}}
    im = {'bolt'=>bolt}
    if role == 'broker'
      im['broker'] = {'cluster'=>{'seed-addresses'=>targets.map { |t| t['address'] },
        'trusted-peers'=>brokers.zip(targets).map { |n,t|
          {'observation-id'=>"broker-#{n[0]}-18020", 'target'=>t}
        }}}
    else
      im['gateway'] = {'ws'=>{'broker'=>{'bootstrap-targets'=>targets}}}
    end
    # Nacos候选必须保留与部署配对相同的DNS字面地址；不能从发现metadata补授权。
    {'spring'=>{'cloud'=>{'nacos'=>{'discovery'=>{'ip'=>name}}}}, 'im'=>im}
  end

  def keytool(directory, password, *arguments)
    log = File.join(directory, '.keytool.log')
    pid = nil
    File.open(log, File::WRONLY | File::CREAT | File::TRUNC, 0600) do |output|
      pid = Process.spawn({'HA_TLS_PASSWORD'=>password}, 'keytool', *arguments,
                          '-storepass:env', 'HA_TLS_PASSWORD', out:output, err:[:child,:out])
      status = Timeout.timeout(20) do
        Process.wait(pid)
        pid = nil
        $?
      end
      raise 'HA keytool step failed' unless status.success?
    end
  ensure
    if pid
      Process.kill('KILL', pid) rescue Errno::ESRCH
      Process.wait(pid) rescue Errno::ECHILD
    end
    File.unlink(log) if File.file?(log)
  end

  def write(path, content)
    File.open(path, File::WRONLY | File::CREAT | File::EXCL, 0600) { |f| f.write(content) }
  end

  def create(directory, compose)
    private_path(directory, directory:true)
    raise 'HA TLS create requires empty owned directory' unless Dir.empty?(directory)
    entries = nodes(compose)
    password = SecureRandom.hex(24)
    root = File.join(directory,'ca.p12')
    keytool(directory,password,'-genkeypair','-alias','ha-ca','-keyalg','RSA','-keysize','2048',
      '-dname','CN=IM Chat ephemeral HA test CA','-startdate','-1d','-validity','3',
      '-ext','bc:c=ca:true','-ext','ku=keyCertSign,cRLSign','-storetype','PKCS12','-keystore',root)
    keytool(directory,password,'-exportcert','-rfc','-alias','ha-ca','-keystore',root,'-file',File.join(directory,'ca.pem'))
    entries.each do |node|
      name, _, identity = node
      path = File.join(directory,name); Dir.mkdir(path,0700)
      store = File.join(path,'node.p12')
      keytool(directory,password,'-genkeypair','-alias','node','-keyalg','RSA','-keysize','2048',
        '-dname',"CN=#{name}",'-startdate','-1d','-validity','2','-storetype','PKCS12','-keystore',store)
      request = File.join(path,'node.csr'); certificate = File.join(path,'node.pem')
      keytool(directory,password,'-certreq','-alias','node','-keystore',store,'-file',request)
      keytool(directory,password,'-gencert','-alias','ha-ca','-keystore',root,'-infile',request,
        '-outfile',certificate,'-rfc','-startdate','-1d','-validity','2',
        '-ext',"SAN=URI:#{identity},DNS:#{name}",'-ext','ku=digitalSignature,keyEncipherment','-ext','eku=serverAuth,clientAuth')
      keytool(directory,password,'-importcert','-alias','ha-ca','-keystore',store,'-file',File.join(directory,'ca.pem'),'-noprompt')
      keytool(directory,password,'-importcert','-alias','node','-keystore',store,'-file',certificate,'-noprompt')
      File.unlink(request)
      write(File.join(path,'application.yml'), configuration(node,entries,password).to_yaml)
    end
    # 只保留运行所需叶私钥，不把CA私钥挂进容器；无在线签发/运行期复制。
    File.unlink(root)
    check(directory,compose)
  end

  def check(directory, compose)
    private_path(directory, directory:true)
    entries = nodes(compose)
    private_path(File.join(directory,'ca.pem'))
    root = OpenSSL::X509::Certificate.new(File.read(File.join(directory,'ca.pem')))
    now = Time.now
    raise 'invalid HA CA' unless root.verify(root.public_key) && root.not_before <= now && now < root.not_after
    entries.each do |node|
      path = File.join(directory,node[0]); private_path(path,directory:true)
      %w[node.p12 application.yml node.pem].each { |file| private_path(File.join(path,file)) }
      config = YAML.load_file(File.join(path,'application.yml'))
      password = config.fetch('im').fetch('bolt').fetch('client').fetch('tls').fetch('key-store-password')
      raise 'HA TLS configuration mismatch' unless config == configuration(node,entries,password)
      store = OpenSSL::PKCS12.new(File.binread(File.join(path,'node.p12')),password)
      leaf = store.certificate
      raise 'HA TLS key/leaf mismatch' unless leaf && store.key && leaf.check_private_key(store.key) &&
        leaf.to_der == OpenSSL::X509::Certificate.new(File.read(File.join(path,'node.pem'))).to_der
      chain = [leaf, *store.ca_certs]
      raise 'HA TLS presented certificate interval invalid' unless chain.all? { |cert| cert.not_before <= now && now < cert.not_after }
      raise 'HA TLS root mismatch' unless store.ca_certs.any? { |cert| cert.to_der == root.to_der } &&
        store.ca_certs.all? { |cert| cert.to_der == root.to_der }
      trust = OpenSSL::X509::Store.new; trust.add_cert(root)
      raise 'HA TLS certificate not trusted' unless trust.verify(leaf) && leaf.verify(root.public_key)
      san = leaf.extensions.select { |ext| ext.oid == 'subjectAltName' }.map(&:value)
      raise 'HA TLS leaf URI/DNS mismatch' unless san == ["URI:#{node[2]}, DNS:#{node[0]}"]
    end
  end
end

if $PROGRAM_NAME == __FILE__
  begin
    operation, directory, compose = ARGV
    raise 'expected create/check directory compose' unless ARGV.size == 3 && %w[create check].include?(operation)
    File.umask(0077)
    RoutingHaTls.public_send(operation, directory, compose)
  rescue StandardError => failure
    # 不输出底层异常消息，避免密码或配置内容进入诊断日志。
    warn "HA TLS fixture #{operation || 'operation'} failed (#{failure.class}); material/configuration rejected"
    exit 1
  end
end
