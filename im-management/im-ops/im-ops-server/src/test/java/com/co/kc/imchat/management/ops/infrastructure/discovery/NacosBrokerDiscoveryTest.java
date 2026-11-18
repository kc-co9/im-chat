package com.co.kc.imchat.management.ops.infrastructure.discovery;

import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

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
        OpsBrokerProperties properties = new OpsBrokerProperties();

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

        assertThat(new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties()).discover())
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

        assertThat(new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties()).discover())
                .singleElement()
                .extracting(endpoint -> endpoint.baseUri().toString())
                .isEqualTo("http://[::1]:12201");
    }

    @Test
    void differentPriorityTextRejectsLeaderPair() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance leader = instance("broker-2", "127.0.0.1", 18021);
        leader.getMetadata().put("management-host", "127.0.0.1");
        leader.getMetadata().put("management-port", "19021");
        leader.getMetadata().put("raft-peer-id", "127.0.0.1:18121:2:10");
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(leader));

        assertThatThrownBy(() -> new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties())
                .resolveLeader("127.0.0.1:18121:2:20"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker returned an untrusted Leader hint");
    }

    @Test
    void equivalentIpv6AddressWithDifferentTextRejectsLeaderPair() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance leader = instance("broker-v6", "::1", 18021);
        leader.getMetadata().put("management-host", "::1");
        leader.getMetadata().put("management-port", "19021");
        leader.getMetadata().put("raft-peer-id", "[::1]:18121:2");
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(leader));

        assertThatThrownBy(() -> new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties())
                .resolveLeader("[0:0:0:0:0:0:0:1]:18121:2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker returned an untrusted Leader hint");
    }

    @ParameterizedTest
    @ValueSource(strings = {" 127.0.0.1:18121:2", "127.0.0.1:18121:2 ",
            "127.0.0.1:\t18121:2", "127.0.0.1:18121:2\n", "127.0.0.1:18121:2\u0001"})
    void whitespaceOrControlHintRejectsBeforeDiscoveryEvenWhenMetadataMatches(String hint) {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance leader = instance("broker-2", "127.0.0.1", 18021);
        leader.getMetadata().put("management-host", "127.0.0.1");
        leader.getMetadata().put("management-port", "19021");
        leader.getMetadata().put("raft-peer-id", hint);
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(leader));

        assertThatThrownBy(() -> new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties())
                .resolveLeader(hint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker returned an invalid Leader hint");
        verifyNoInteractions(discoveryClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-port", "0", "65536", ""})
    void matchingPeerIdWithInvalidManagementPortRejectsLeaderPair(String port) {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance leader = instance("broker-2", "127.0.0.1", 18021);
        leader.getMetadata().put("management-host", "127.0.0.1");
        leader.getMetadata().put("management-port", port);
        leader.getMetadata().put("raft-peer-id", "127.0.0.1:18121:2");
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(leader));

        assertThatThrownBy(() -> new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties())
                .resolveLeader("127.0.0.1:18121:2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker returned an untrusted Leader hint");
    }

    @Test
    void unrelatedMissingMetadataDoesNotBlockUniqueLeaderPair() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance unrelated = instance("broker-legacy", "127.0.0.1", 18020);
        DefaultServiceInstance leader = instance("broker-2", "127.0.0.1", 18021);
        leader.getMetadata().put("management-host", "127.0.0.1");
        leader.getMetadata().put("management-port", "19021");
        leader.getMetadata().put("raft-peer-id", "127.0.0.1:18121:2");
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(unrelated, leader));

        assertThat(new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties())
                .resolveLeader("127.0.0.1:18121:2"))
                .isEqualTo(new BrokerManagementEndpoint(
                        "broker-2", java.net.URI.create("http://127.0.0.1:19021"), null));
    }

    @Test
    void trustedHttpOriginResolvesWithoutPeerIdMetadata() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance leader = instance("broker-2", "127.0.0.1", 18021);
        leader.getMetadata().put("management-host", "127.0.0.1");
        leader.getMetadata().put("management-port", "19021");
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(leader));

        assertThat(new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties())
                .resolveLeader("http://127.0.0.1:19021"))
                .isEqualTo(new BrokerManagementEndpoint(
                        "broker-2", java.net.URI.create("http://127.0.0.1:19021"), null));
    }

    @Test
    void unknownHttpOriginDoesNotFallBackToMatchingPeerId() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        String hint = "http://untrusted.example:19020";
        DefaultServiceInstance leader = instance("broker-2", "127.0.0.1", 18021);
        leader.getMetadata().put("management-host", "127.0.0.1");
        leader.getMetadata().put("management-port", "19021");
        leader.getMetadata().put("raft-peer-id", hint);
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(leader));

        assertThatThrownBy(() -> new NacosBrokerDiscovery(discoveryClient, new OpsBrokerProperties())
                .resolveLeader(hint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker returned an untrusted Leader hint");
    }

    private DefaultServiceInstance instance(String id, String host, int port) {
        return new DefaultServiceInstance(id, "im-broker", host, port, false);
    }
}
