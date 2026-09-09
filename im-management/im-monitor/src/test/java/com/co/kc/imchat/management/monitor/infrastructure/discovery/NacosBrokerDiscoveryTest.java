package com.co.kc.imchat.management.monitor.infrastructure.discovery;

import com.co.kc.imchat.management.monitor.infrastructure.config.properties.MonitorBrokerProperties;
import com.co.kc.imchat.management.monitor.domain.model.BrokerManagementEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosBrokerDiscoveryTest {

    @Test
    void parsesExplicitMetadataAndKeepsUnsupportedBrokersVisible() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance valid = instance("broker-1", "10.0.0.1", 12200);
        valid.getMetadata().put("management-host", "10.0.1.1");
        valid.getMetadata().put("management-port", "12201");
        DefaultServiceInstance duplicate = instance("broker-1", "10.0.0.1", 12200);
        duplicate.getMetadata().putAll(valid.getMetadata());
        DefaultServiceInstance missing = instance("broker-2", "10.0.0.2", 12200);
        DefaultServiceInstance invalid = instance("broker-3", "10.0.0.3", 12200);
        invalid.getMetadata().put("management-host", "10.0.1.3");
        invalid.getMetadata().put("management-port", "not-a-port");
        when(discoveryClient.getInstances("im-broker"))
                .thenReturn(List.of(valid, duplicate, missing, invalid));
        MonitorBrokerProperties properties = new MonitorBrokerProperties();

        List<BrokerManagementEndpoint> endpoints =
                new NacosBrokerDiscovery(discoveryClient, properties).discover();

        assertThat(endpoints).hasSize(3);
        assertThat(endpoints.getFirst().brokerId()).isEqualTo("broker-1");
        assertThat(endpoints.getFirst().baseUri().toString()).isEqualTo("http://10.0.1.1:12201");
        assertThat(endpoints).filteredOn(endpoint -> !endpoint.isSupported()).hasSize(2);
        assertThat(endpoints).extracting(BrokerManagementEndpoint::brokerId)
                .containsExactly("broker-1", "broker-2", "broker-3");
    }

    @Test
    void usesStableNetworkIdentityWhenDiscoveryInstanceIdIsMissing() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance instance = instance(null, "10.0.0.4", 12200);
        instance.getMetadata().put("management-host", "10.0.1.4");
        instance.getMetadata().put("management-port", "12201");
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(instance));

        assertThat(new NacosBrokerDiscovery(discoveryClient, new MonitorBrokerProperties()).discover())
                .extracting(BrokerManagementEndpoint::brokerId)
                .containsExactly("10.0.0.4:12200");
    }

    @Test
    void buildsValidManagementUriForIpv6Metadata() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance instance = instance("broker-v6", "::1", 12200);
        instance.getMetadata().put("management-host", "::1");
        instance.getMetadata().put("management-port", "12201");
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(instance));

        assertThat(new NacosBrokerDiscovery(discoveryClient, new MonitorBrokerProperties()).discover())
                .singleElement()
                .extracting(endpoint -> endpoint.baseUri().toString())
                .isEqualTo("http://[::1]:12201");
    }

    private DefaultServiceInstance instance(String id, String host, int port) {
        return new DefaultServiceInstance(id, "im-broker", host, port, false);
    }
}
