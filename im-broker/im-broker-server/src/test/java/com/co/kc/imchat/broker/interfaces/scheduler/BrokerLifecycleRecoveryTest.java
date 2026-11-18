package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureRemovalIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.model.cqrs.result.BrokerFailureRecoveryResult;
import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerLifecycleRecoveryTest {

    @Test
    void drainRetriesAfterCommittedStateReadFails() {
        BrokerRaftAdapter repository = failingLeaderRepository();
        BrokerDrainScheduler lifecycle = new BrokerDrainScheduler(repository, mock(BrokerClusterAppService.class));

        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);

        verify(repository, times(2)).getClusterSnapshot();
    }

    @Test
    void failureRecoveryRetriesAfterCommittedStateReadFails() {
        BrokerRaftAdapter repository = failingLeaderRepository();
        BrokerFailureRecoveryScheduler lifecycle = new BrokerFailureRecoveryScheduler(
                repository, mock(BrokerClusterAppService.class));

        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);

        verify(repository, times(2)).getClusterSnapshot();
    }

    @Test
    void findsFencingFailureBeforeResultEpochBecomesCurrentEpoch() {
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        when(BrokerRaftTestBindings.bindLocalBroker(repository).isLeader()).thenReturn(true);
        BrokerCluster cluster =
                BrokerCluster.builder()
                        .revision(new ClusterRevision(0L))
                        .epoch(new RoutingEpoch(4L))
                        .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                        .brokers(List.of(new BrokerNode(
                                new BrokerId("broker-a"),
                                new BrokerRouteEndpoint("a:18020"),
                                BrokerMembershipRole.VOTER,
                                BrokerTrafficState.ACTIVE),
                                new BrokerNode(
                                        new BrokerId("broker-b"),
                                        new BrokerRouteEndpoint("b:18020"),
                                        BrokerMembershipRole.VOTER,
                                        BrokerTrafficState.ACTIVE)))
                        .operations(List.of())
                        .operationHistoryCapacity(new OperationHistoryCapacity(100))
                        .build();
        BrokerClusterDecision decision = cluster.removeFailedBroker(
                BrokerFailureRemovalIntent.builder()
                        .operationId(new ClusterOperationId("failure-before-commit"))
                        .commandDigest(new CommandDigest("automatic-digest"))
                        .expectedRevision(cluster.revision())
                        .brokerId(new BrokerId("broker-a"))
                        .progress(new DrainProgress(
                                List.of(), Instant.EPOCH, null))
                        .changedAt(Instant.EPOCH).build());
        Assertions.assertThat(decision.accepted()).isTrue();
        Assertions.assertThat(cluster.epoch().value()).isEqualTo(4L);
        Assertions.assertThat(decision.operation().resultEpoch().value()).isEqualTo(5L);
        when(repository.getClusterSnapshot()).thenReturn(cluster);
        when(repository.findActiveRouteRecoveryOperation(cluster.epoch())).thenReturn(Optional.empty());
        BrokerClusterAppService service = mock(BrokerClusterAppService.class);
        when(service.recoverRoutes(ArgumentMatchers.any())).thenReturn(
                CompletableFuture.completedFuture(
                        BrokerFailureRecoveryResult.waitingForGateways(null)));

        new BrokerFailureRecoveryScheduler(repository, service).reconcile();

        verify(service).recoverRoutes(new BrokerOperationReconcileCommand(
                "failure-before-commit"));
    }

    @Test
    void leaseRenewalRetriesAfterCommittedStateReadFails() {
        BrokerRaftAdapter repository = failingLeaderRepository();
        RoutingLeaseScheduler lifecycle = new RoutingLeaseScheduler(repository, mock(BrokerClusterAppService.class));

        assertThatThrownBy(lifecycle::renew).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::renew).isInstanceOf(IllegalStateException.class);

        verify(repository, times(2)).getClusterSnapshot();
    }

    @Test
    void membershipRetriesAfterCommittedStateReadFails() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        when(BrokerRaftTestBindings.bindLocalBroker(adapter).isLeader()).thenReturn(true);
        when(adapter.getClusterSnapshot()).thenThrow(new IllegalStateException("committed state unavailable"));
        BrokerMembershipScheduler lifecycle = new BrokerMembershipScheduler(
                adapter, mock(BrokerMembershipAppService.class), Duration.ofSeconds(1));

        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);

        verify(adapter, times(2)).getClusterSnapshot();
    }

    private BrokerRaftAdapter failingLeaderRepository() {
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        when(BrokerRaftTestBindings.bindLocalBroker(repository).isLeader()).thenReturn(true);
        when(repository.getClusterSnapshot()).thenThrow(new IllegalStateException("committed state unavailable"));
        return repository;
    }
}
