#!/usr/bin/env ruby
# 用途：校验 bolt-dev 的 Compose 配置或 create 后的实际 Docker 拓扑，不生成 JVM 准入文件。
# 输入：config <compose.json>；actual <compose.json> <network-inspect.json> <container-inspect.json>。
# 输出：成功无输出，失败 stderr 原因及非 0。只读 JSON，不执行 Docker、不拥有容器清理。
require 'json'

def require_safe(condition, message)
  abort "bolt-dev: #{message}" unless condition
end

mode, config_path, network_path, containers_path = ARGV
abort 'Usage: bolt-dev-network.rb config|actual COMPOSE_JSON [NETWORK_JSON CONTAINERS_JSON]' unless %w[config actual].include?(mode)
config = JSON.parse(File.read(config_path))
networks = config.fetch('networks')
require_safe(networks.keys == ['bolt-dev'], 'exactly one dedicated network is required')
network = networks.fetch('bolt-dev')
require_safe(network['internal'] == true && !network['external'], 'network must be internal and owned')
require_safe(network.fetch('ipam').fetch('config').map { |entry| entry['subnet'] } == ['172.30.88.0/24'], 'fixed subnet required')
services = config.fetch('services')
require_safe(services.keys.sort == %w[broker gateway nacos redis], 'dedicated topology services required')
services.each do |name, service|
  require_safe(service.fetch('networks').keys == ['bolt-dev'] && !service['network_mode'], "#{name}: extra network attachment")
  require_safe(!service['privileged'] && !service['pid'] && !service['ipc'], "#{name}: host namespace or privileged mode")
  require_safe(service.fetch('tmpfs', []) == (name == 'redis' ? ['/data'] : []), "#{name}: only synthetic Redis tmpfs is allowed")
  require_safe(service.fetch('volumes', []).empty?, "#{name}: mounts are not allowed in this synthetic topology")
  service.fetch('ports', []).each do |port|
    require_safe(port['host_ip'] == '127.0.0.1' || port['host_ip'] == '::1', "#{name}: non-loopback publication")
  end
  next unless %w[broker gateway].include?(name)
  environment = service.fetch('environment')
  require_safe(environment['SPRING_PROFILES_ACTIVE'] == 'bolt-dev', "#{name}: exact bolt-dev profile required")
  require_safe(environment['IM_BOLT_SYNTHETIC_DATA'] == 'true' && environment['IM_BOLT_ISOLATED_CONTAINER_NETWORK'] == 'true', "#{name}: explicit synthetic/declaration required")
  address = service.fetch('networks').fetch('bolt-dev').fetch('ipv4_address')
  require_safe(environment['IM_BOLT_SERVER_HOST'] == address, "#{name}: bind must match dedicated attachment")
end
exit 0 if mode == 'config'

actual_networks = JSON.parse(File.read(network_path))
require_safe(actual_networks.is_a?(Array) && actual_networks.length == 1, 'one actual network required')
actual = actual_networks.first
labels = actual.fetch('Labels')
require_safe(labels.is_a?(Hash) && labels['com.docker.compose.project'] == config.fetch('name') && labels['com.docker.compose.network'] == 'bolt-dev', 'actual network ownership labels required')
require_safe(actual['Name'] == network.fetch('name') && actual['Internal'] == true && actual['Driver'] == 'bridge', 'actual network must be owned internal bridge')
require_safe(actual.fetch('IPAM').fetch('Config').map { |entry| entry['Subnet'] } == ['172.30.88.0/24'], 'actual subnet mismatch')
containers = JSON.parse(File.read(containers_path))
require_safe(containers.length == services.length, 'actual container count mismatch')
seen = []
containers.each do |container|
  labels = container.fetch('Config').fetch('Labels')
  name = labels.fetch('com.docker.compose.service')
  require_safe(labels['com.docker.compose.project'] == config.fetch('name') && services.key?(name) && !seen.include?(name), 'foreign or duplicate actual container')
  seen << name
  require_safe(container.fetch('State').fetch('Running') == false, "#{name}: must inspect before first start")
  host = container.fetch('HostConfig')
  require_safe(host.fetch('Privileged') == false && host.fetch('NetworkMode') == actual.fetch('Name'), "#{name}: unsafe actual host configuration")
  tmpfs = services.fetch(name).fetch('tmpfs', [])
  mounts = container.fetch('Mounts')
  require_safe(mounts.is_a?(Array), "#{name}: actual Mounts array required")
  require_safe(mounts.all? { |mount| mount['Type'] == 'tmpfs' && tmpfs.include?(mount['Destination']) }, "#{name}: unexpected actual mount")
  attachments = container.fetch('NetworkSettings').fetch('Networks')
  require_safe(attachments.keys == [actual.fetch('Name')], "#{name}: extra actual attachment")
  endpoint = attachments.values.first
  desired = services.fetch(name).fetch('networks').fetch('bolt-dev').fetch('ipv4_address')
  require_safe(endpoint.fetch('IPAMConfig').fetch('IPv4Address') == desired, "#{name}: actual address differs")
  declared_environment = services.fetch(name).fetch('environment', {})
  actual_environment = container.fetch('Config').fetch('Env').to_h { |entry| entry.split('=', 2) }
  require_safe(declared_environment.all? { |key, value| actual_environment[key] == value.to_s }, "#{name}: actual environment differs")
  expected_ports = services.fetch(name).fetch('ports', []).map { |port| ["#{port.fetch('target')}/#{port.fetch('protocol', 'tcp')}", port.fetch('host_ip'), port.fetch('published').to_s] }.sort
  bindings = host.fetch('PortBindings')
  # Docker create 无发布时 PortBindings 可为 null；字段缺失或非 Hash/null 不是无端口证明。
  require_safe(bindings.nil? || bindings.is_a?(Hash), "#{name}: actual PortBindings must be Hash or null")
  bindings = {} if bindings.nil?
  actual_ports = bindings.flat_map { |port, bindings| bindings.map { |binding| [port, binding.fetch('HostIp'), binding.fetch('HostPort')] } }.sort
  require_safe(actual_ports == expected_ports, "#{name}: unexpected actual port binding")
end
# create 后 Docker 尚未一定将 endpoint 登入 network inspect；已有成员只能属于本批容器。
ids = containers.map { |container| container.fetch('Id') }
members = actual.fetch('Containers')
require_safe(members.is_a?(Hash), 'actual network Containers hash required')
require_safe(members.keys.all? { |id| ids.include?(id) }, 'foreign container on actual network')
