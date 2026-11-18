package com.co.kc.imchat.management.ops.adapter;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.infrastructure.discovery.BrokerDiscovery;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BrokerClusterOperationAdapterTest {

    @Test
    void routesOfflineTargetThroughFirstSupportedDiscoveredReceiver() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint unsupported = new BrokerManagementEndpoint(
                "broker-legacy", null, "Management metadata unavailable");
        BrokerManagementEndpoint receiver = new BrokerManagementEndpoint(
                "broker-receiver", URI.create("http://127.0.0.1:12201"), null);
        BrokerManagementEndpoint laterReceiver = new BrokerManagementEndpoint(
                "broker-later", URI.create("http://127.0.0.1:12202"), null);
        when(discovery.discover()).thenReturn(List.of(unsupported, receiver, laterReceiver));
        String scope = "ops:broker:raft:write";
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-offline", "add-learner", "offline-target-operation", 7L,
                "127.0.0.1:12210", "Add discovered learner", scope);
        when(client.operate(eq(receiver), same(command), eq(scope)))
                .thenReturn(new BrokerOperationPayload(
                        "offline-target-operation", "APPLIED", 7L, 8L, null, "Learner added"));

        BrokerOperationDTO result = new BrokerClusterOperationAdapter(discovery, client).operate(command);

        assertThat(result).isEqualTo(new BrokerOperationDTO(
                "offline-target-operation", "APPLIED", 7L, 8L, null, "Learner added"));
        verify(discovery).discover();
        verify(client).operate(eq(receiver), same(command), eq(scope));
        verifyNoMoreInteractions(discovery, client);
    }

    @Test
    void firstReceiverFailurePropagatesWithoutTryingAnotherReceiver() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint firstReceiver = new BrokerManagementEndpoint(
                "broker-first", URI.create("http://127.0.0.1:12201"), null);
        BrokerManagementEndpoint secondReceiver = new BrokerManagementEndpoint(
                "broker-second", URI.create("http://127.0.0.1:12202"), null);
        when(discovery.discover()).thenReturn(List.of(firstReceiver, secondReceiver));
        String scope = "ops:broker:raft:write";
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-offline", "add-learner", "receiver-failure-operation", 7L,
                "127.0.0.1:12210", "Add discovered learner", scope);
        IllegalStateException failure = new IllegalStateException("Management transport unavailable");
        when(client.operate(eq(firstReceiver), same(command), eq(scope))).thenThrow(failure);
        BrokerClusterOperationAdapter adapter = new BrokerClusterOperationAdapter(discovery, client);

        assertThatThrownBy(() -> adapter.operate(command)).isSameAs(failure);

        verify(client).operate(eq(firstReceiver), same(command), eq(scope));
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void noSupportedReceiverRejectsOperationWithoutSendingWrite(boolean hasUnsupportedEndpoint) {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        List<BrokerManagementEndpoint> endpoints = hasUnsupportedEndpoint
                ? List.of(new BrokerManagementEndpoint(
                        "broker-offline", null, "Management metadata unavailable"))
                : List.of();
        when(discovery.discover()).thenReturn(endpoints);
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-offline", "add-learner", "no-receiver-operation", 7L,
                "127.0.0.1:12210", "Add discovered learner", "ops:broker:raft:write");
        BrokerClusterOperationAdapter adapter = new BrokerClusterOperationAdapter(discovery, client);

        assertThatThrownBy(() -> adapter.operate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker not found");

        verifyNoInteractions(client);
    }

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
