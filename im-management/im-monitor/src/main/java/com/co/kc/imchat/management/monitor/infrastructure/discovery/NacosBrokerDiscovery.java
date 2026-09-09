package com.co.kc.imchat.management.monitor.infrastructure.discovery;

import com.co.kc.imchat.management.monitor.infrastructure.config.properties.MonitorBrokerProperties;
import com.co.kc.imchat.management.monitor.domain.model.BrokerManagementEndpoint;
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

    private final DiscoveryClient discoveryClient;
    private final MonitorBrokerProperties properties;

    @Override
    public List<BrokerManagementEndpoint> discover() {
        Map<String, BrokerManagementEndpoint> endpoints = new LinkedHashMap<>();
        for (ServiceInstance instance : discoveryClient.getInstances(properties.getServiceName())) {
            String brokerId = brokerId(instance);
            endpoints.putIfAbsent(brokerId, endpoint(instance, brokerId));
        }
        return List.copyOf(endpoints.values());
    }

    private BrokerManagementEndpoint endpoint(ServiceInstance instance, String brokerId) {
        String host = instance.getMetadata().get(MANAGEMENT_HOST);
        String portValue = instance.getMetadata().get(MANAGEMENT_PORT);
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
