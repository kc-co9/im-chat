package com.co.kc.imchat.management.ops.infrastructure.discovery;

import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 Nacos 服务实例 metadata 解析 Broker 管理端点。 */
@Component
@RequiredArgsConstructor
public class NacosBrokerDiscovery implements BrokerDiscovery {
    private static final String MANAGEMENT_HOST = "management-host";
    private static final String MANAGEMENT_PORT = "management-port";
    /** Nacos metadata 中用于解析 Raft Leader 提示的稳定 PeerId 键。 */
    private static final String RAFT_PEER_ID = "raft-peer-id";

    private final DiscoveryClient discoveryClient;
    private final OpsBrokerProperties properties;

    @Override
    public List<BrokerManagementEndpoint> discover() {
        Map<String, BrokerManagementEndpoint> endpoints = new LinkedHashMap<>();
        for (ServiceInstance instance : discoveryClient.getInstances(properties.getServiceName())) {
            String brokerId = brokerId(instance);
            endpoints.putIfAbsent(brokerId, endpoint(instance, brokerId));
        }
        return List.copyOf(endpoints.values());
    }

    @Override
    /** 将 Broker 返回的 URI 或 PeerId hint 解析为唯一可信管理端点。 */
    public BrokerManagementEndpoint resolveLeader(String leaderHint) {
        if (leaderHint == null || leaderHint.isBlank()
                || leaderHint.codePoints().anyMatch(character ->
                        Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException("Broker returned an invalid Leader hint");
        }
        URI leaderUri = null;
        try {
            leaderUri = URI.create(leaderHint);
        } catch (IllegalArgumentException exception) {
            // PeerId 是不透明字符串，不要求符合 URI 语法。
        }
        if (leaderUri != null && ("http".equals(leaderUri.getScheme())
                || "https".equals(leaderUri.getScheme()))) {
            URI trustedUri = leaderUri;
            return discover().stream()
                    .filter(BrokerManagementEndpoint::isSupported)
                    .filter(candidate -> trustedUri.equals(candidate.baseUri()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Broker returned an untrusted Leader hint"));
        }
        if (leaderHint.contains("://")) {
            throw new IllegalArgumentException("Broker returned an invalid Leader hint");
        }
        // 先复制原始观察，再判歧义；普通 discover 的 brokerId 去重不能用于此路径。
        List<DiscoveredPair> snapshot = List.copyOf(
                discoveryClient.getInstances(properties.getServiceName())).stream().map(instance -> {
                    Map<String, String> metadata = Map.copyOf(instance.getMetadata());
                    return new DiscoveredPair(endpoint(metadata, brokerId(instance)), metadata.get(RAFT_PEER_ID));
                }).toList();
        List<DiscoveredPair> matches = snapshot.stream()
                .filter(pair -> leaderHint.equals(pair.peerId())).toList();
        if (matches.size() != 1 || !matches.getFirst().endpoint().isSupported()) {
            throw new IllegalArgumentException("Broker returned an untrusted Leader hint");
        }
        BrokerManagementEndpoint candidate = matches.getFirst().endpoint();
        long brokerMatches = snapshot.stream()
                .filter(pair -> candidate.brokerId().equals(pair.endpoint().brokerId())).count();
        long originMatches = snapshot.stream()
                .filter(pair -> candidate.baseUri().equals(pair.endpoint().baseUri())).count();
        if (brokerMatches != 1 || originMatches != 1) {
            throw new IllegalArgumentException("Broker returned an untrusted Leader hint");
        }
        return candidate;
    }

    private record DiscoveredPair(BrokerManagementEndpoint endpoint, String peerId) {
    }

    private BrokerManagementEndpoint endpoint(ServiceInstance instance, String brokerId) {
        return endpoint(instance.getMetadata(), brokerId);
    }

    /** 从单个 Nacos metadata 解析管理 URI，格式错误转为不支持端点。 */
    private BrokerManagementEndpoint endpoint(Map<String, String> metadata, String brokerId) {
        String host = metadata.get(MANAGEMENT_HOST);
        String portValue = metadata.get(MANAGEMENT_PORT);
        if (host == null || host.isBlank() || portValue == null || portValue.isBlank()) {
            return new BrokerManagementEndpoint(brokerId, null, "Broker management metadata is missing");
        }
        try {
            int port = Integer.parseInt(portValue);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("port out of range");
            }
            URI managementUri = new URI("http", null, host, port, null, null, null);
            return new BrokerManagementEndpoint(brokerId, managementUri, null);
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return new BrokerManagementEndpoint(brokerId, null, "Broker management metadata is invalid");
        }
    }

    private String brokerId(ServiceInstance instance) {
        String instanceId = instance.getInstanceId();
        if (instanceId != null && !instanceId.isBlank()) {
            return instanceId;
        }
        return instance.getHost() + ":" + instance.getPort();
    }
}
