package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.interfaces.scheduler.RoutingLeaseScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingLeaseSchedulerTest {

    @Test
    void leaderRenewsEveryActiveBrokerWithoutOverlappingRounds() {
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        BrokerClusterAppService activator = mock(BrokerClusterAppService.class);
        BrokerNode brokerA = activeBroker("broker-a");
        BrokerNode brokerB = activeBroker("broker-b");
        BrokerCluster state = state(brokerA, brokerB);
        CompletableFuture<Boolean> firstActivation = new CompletableFuture<>();
        when(BrokerRaftTestBindings.bindLocalBroker(repository).isLeader()).thenReturn(true);
        when(repository.getClusterSnapshot()).thenReturn(state);
        when(activator.activateLease(argThat(command -> command != null && command.brokerId().equals("broker-a")))).thenReturn(firstActivation);
        when(activator.activateLease(argThat(command -> command != null && command.brokerId().equals("broker-b"))))
                .thenReturn(CompletableFuture.completedFuture(true));
        RoutingLeaseScheduler lifecycle = new RoutingLeaseScheduler(repository, activator);

        lifecycle.renew();
        lifecycle.renew();

        verify(activator).activateLease(argThat(command -> command != null && command.brokerId().equals("broker-a")
                && command.routingEpoch().equals(2L)));
        verify(activator).activateLease(argThat(command -> command != null && command.brokerId().equals("broker-b")
                && command.routingEpoch().equals(2L)));
        assertThat(lifecycle.isRenewalInProgress()).isTrue();

        firstActivation.complete(true);
        assertThat(lifecycle.isRenewalInProgress()).isFalse();
    }

    @Test
    void followerDoesNotIssueLeases() {
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        BrokerClusterAppService activator = mock(BrokerClusterAppService.class);
        when(BrokerRaftTestBindings.bindLocalBroker(repository).isLeader()).thenReturn(false);
        RoutingLeaseScheduler lifecycle = new RoutingLeaseScheduler(repository, activator);

        lifecycle.renew();

        verify(repository, never()).getClusterSnapshot();
        verify(activator, never()).activateLease(any());
    }

    private BrokerCluster state(BrokerNode... brokers) {
        return BrokerCluster.builder()
                .revision(new ClusterRevision(3L)).epoch(new RoutingEpoch(2L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH).brokers(List.of(brokers))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(10)).build();
    }

    private BrokerNode activeBroker(String brokerId) {
        return new BrokerNode(new BrokerId(brokerId), new BrokerRouteEndpoint(brokerId + ":18020"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE);
    }
}
