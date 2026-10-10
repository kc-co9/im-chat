package com.co.kc.imchat.management.ops.adapter;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.infrastructure.discovery.BrokerDiscovery;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerClusterOperationAdapterTest {

    @Test
    void mapsClusterStateWithoutBusinessClusterIdentity() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:12201"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        when(client.clusterState(endpoint)).thenReturn(new BrokerClusterStatePayload(
                11L, 7L, "ROUND_ROBIN", List.of(new BrokerClusterNodePayload(
                        "broker-1", "127.0.0.1:12200", "VOTER", "ACTIVE"))));

        BrokerClusterStateDTO state = new BrokerClusterOperationAdapter(discovery, client).clusterState();

        assertThat(state.revision()).isEqualTo(11L);
        assertThat(state.epoch()).isEqualTo(7L);
        assertThat(state.algorithm()).isEqualTo("ROUND_ROBIN");
        assertThat(state.brokers()).singleElement().satisfies(node -> {
            assertThat(node.brokerId()).isEqualTo("broker-1");
            assertThat(node.routeEndpoint()).isEqualTo("127.0.0.1:12200");
            assertThat(node.membershipRole()).isEqualTo("VOTER");
            assertThat(node.routingState()).isEqualTo("ACTIVE");
        });
    }
}
