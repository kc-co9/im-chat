package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrokerClusterLeaseInstallationTest {
    private BrokerPeerAdapter peerAdapter;
    @ParameterizedTest
    @ValueSource(strings = {"staleEpoch", "inactive", "fenced", "missingBroker"})
    void admissionFailureDoesNotStartPrepareOrQuorum(String condition) {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
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
        when(adapter.getClusterSnapshot()).thenReturn(
                BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(state, List.of(), fence, 10));
        ActivateRoutingLeaseCommand command = new ActivateRoutingLeaseCommand("install",
                condition.equals("missingBroker") ? "absent" : "broker-a",
                condition.equals("staleEpoch") ? 6L : 7L);

        assertThat(service(adapter).activateLease(command).toCompletableFuture().join()).isFalse();

        verify(peerAdapter, never()).prepareLease(any(), any(), any());
        verify(adapter, never()).confirmLeadership(any());
        verify(peerAdapter, never()).installLease(any(), any());
    }

    @Test
    void prepareMustCompleteBeforeQuorumAndInstall() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerClusterState state = new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode(
                "broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        when(adapter.confirmLeadership(any())).thenReturn(
                CompletableFuture.completedFuture(new QuorumRead(term(3L), readIndex(42L))));
        CompletableFuture<LeaseNonce> prepared = new CompletableFuture<>();
        BrokerClusterAppService service = service(adapter);
        when(peerAdapter.installLease(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(peerAdapter.prepareLease(any(), any(), any())).thenReturn(prepared);
        BrokerCluster cluster =
                BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(state, List.of(), null, 10);
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        CompletableFuture<Boolean> result = service.activateLease(new ActivateRoutingLeaseCommand("install", "broker-a", 7L))
                .toCompletableFuture();
        assertThat(result).isNotDone();
        verify(adapter, never()).confirmLeadership(any());
        verify(peerAdapter, never()).installLease(any(), any());
        prepared.complete(new LeaseNonce("nonce-1"));
        assertThat(result.join()).isTrue();
        InOrder order = inOrder(adapter, peerAdapter);
        order.verify(peerAdapter).prepareLease(any(), any(), any());
        order.verify(adapter).confirmLeadership(new QuorumReadNonce("nonce-1"));
        order.verify(peerAdapter).installLease(any(), argThat(lease ->
                lease.leaseNonce().equals(new LeaseNonce("nonce-1"))
                        && lease.quorumReadIndex().equals(readIndex(42L))));
    }

    @Test
    void failedPrepareNeverIssuesOrInstallsLease() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerClusterState state = new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode(
                "broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        BrokerCluster cluster =
                BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(state, List.of(), null, 10);
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        BrokerClusterAppService service = service(adapter);
        when(peerAdapter.prepareLease(any(), any(), any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("wrong peer identity")));
        assertThat(service.activateLease(new ActivateRoutingLeaseCommand("install", "broker-a", 7L))
                .toCompletableFuture().join()).isFalse();
        verify(adapter, never()).confirmLeadership(any());
        verify(peerAdapter, never()).installLease(any(), any());
    }

    private BrokerClusterAppService service(BrokerRaftAdapter adapter) {
        peerAdapter = mock(BrokerPeerAdapter.class);
        RoutingLeaseGrantService grantService = new RoutingLeaseGrantService(Duration.ofNanos(100));
        when(adapter.grantLease(any(), any())).thenAnswer(invocation -> {
            LeaseGrantRequest request = invocation.getArgument(0);
            return adapter.getClusterSnapshot().checkLeaseGrant(request.brokerId(), request.routingEpoch())
                    .orElseGet(() -> grantService.grant(request, invocation.getArgument(1)));
        });
        return new BrokerClusterAppService(adapter, peerAdapter, mock(GatewayRegistry.class),
                Duration.ofMillis(1), Duration.ofSeconds(1), mock(ScheduledExecutorService.class),
                Duration.ofSeconds(10), null, null, Duration.ofSeconds(60));
    }
}
