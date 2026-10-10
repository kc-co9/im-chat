package com.co.kc.imchat.broker.application.cluster;



import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.transformer.domain.BrokerClusterDomainTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrokerClusterLeaseInstallationTest {
    @ParameterizedTest
    @ValueSource(strings = {"staleEpoch", "inactive", "fenced", "missingBroker"})
    void admissionFailureDoesNotStartPrepareOrQuorum(String condition) {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        BrokerClusterState state = new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(
                new BrokerNode("broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER,
                        condition.equals("inactive") ? BrokerRoutingState.READY : BrokerRoutingState.ACTIVE),
                new BrokerNode("broker-b", "10.0.0.2:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        RoutingEpochFence fence = null;
        if (condition.equals("fenced")) {
            fence = RoutingEpochFence.builder()
                    .operationId(new ClusterOperationId("close-7"))
                    .epoch(new RoutingEpoch(7L)).leaderTerm(term(3L)).quorumReadIndex(readIndex(42L))
                    .fencedBrokerIds(List.of(new BrokerId("broker-a"), new BrokerId("broker-b")))
                    .build();
        }
        when(adapter.getCluster()).thenReturn(
                BrokerClusterDomainTransformer.INSTANCE.restoreAggregate(state, List.of(), fence, 10));
        ActivateRoutingLeaseCommand command = new ActivateRoutingLeaseCommand("install",
                condition.equals("missingBroker") ? "absent" : "broker-a",
                condition.equals("staleEpoch") ? 6L : 7L);

        assertThat(service(adapter).activateLease(command).toCompletableFuture().join()).isFalse();

        verify(adapter, never()).prepareLease(any(), any(), any());
        verify(adapter, never()).confirmLeadership(any());
        verify(adapter, never()).installLease(any(), any());
    }

    @Test
    void prepareMustCompleteBeforeQuorumAndInstall() {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        BrokerClusterState state = new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode(
                "broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        when(adapter.confirmLeadership(any())).thenReturn(
                CompletableFuture.completedFuture(new QuorumRead(term(3L), readIndex(42L))));
        when(adapter.installLease(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        CompletableFuture<LeaseNonce> prepared = new CompletableFuture<>();
        when(adapter.prepareLease(any(), any(), any())).thenReturn(prepared);
        BrokerClusterAppService service = service(adapter);
        com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster cluster =
                BrokerClusterDomainTransformer.INSTANCE.restoreAggregate(state, List.of(), null, 10);
        when(adapter.getCluster()).thenReturn(cluster);
        CompletableFuture<Boolean> result = service.activateLease(new ActivateRoutingLeaseCommand("install", "broker-a", 7L))
                .toCompletableFuture();
        assertThat(result).isNotDone();
        verify(adapter, never()).confirmLeadership(any());
        verify(adapter, never()).installLease(any(), any());
        prepared.complete(new LeaseNonce("nonce-1"));
        assertThat(result.join()).isTrue();
        InOrder order = inOrder(adapter);
        order.verify(adapter).prepareLease(any(), any(), any());
        order.verify(adapter).confirmLeadership(new QuorumReadNonce("nonce-1"));
        order.verify(adapter).installLease(any(), argThat(lease ->
                lease.leaseNonce().equals(new LeaseNonce("nonce-1"))
                        && lease.quorumReadIndex().equals(readIndex(42L))));
    }

    @Test
    void failedPrepareNeverIssuesOrInstallsLease() {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        when(adapter.prepareLease(any(), any(), any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("wrong peer identity")));
        BrokerClusterState state = new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode(
                "broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster cluster =
                BrokerClusterDomainTransformer.INSTANCE.restoreAggregate(state, List.of(), null, 10);
        when(adapter.getCluster()).thenReturn(cluster);
        assertThat(service(adapter).activateLease(new ActivateRoutingLeaseCommand("install", "broker-a", 7L))
                .toCompletableFuture().join()).isFalse();
        verify(adapter, never()).confirmLeadership(any());
        verify(adapter, never()).installLease(any(), any());
    }

    private BrokerClusterAppService service(BrokerClusterAdapter adapter) {
        RoutingLeaseGrantService grantService = new RoutingLeaseGrantService(Duration.ofNanos(100));
        when(adapter.grantLease(any(), any())).thenAnswer(invocation -> {
            LeaseGrantRequest request = invocation.getArgument(0);
            return adapter.getCluster().checkLeaseGrant(request.brokerId(), request.routingEpoch())
                    .orElseGet(() -> grantService.grant(request, invocation.getArgument(1)));
        });
        return new BrokerClusterAppService(adapter, mock(GatewayRegistry.class), Duration.ofMillis(1), Duration.ofSeconds(1), mock(ScheduledExecutorService.class), java.time.Duration.ofSeconds(10));
    }
}
