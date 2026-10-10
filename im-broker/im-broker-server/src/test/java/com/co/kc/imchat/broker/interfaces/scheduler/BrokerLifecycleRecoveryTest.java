package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.adapter.cluster.LocalBroker;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerLifecycleRecoveryTest {

    @Test
    void drainRetriesAfterCommittedStateReadFails() {
        BrokerClusterConsensus repository = failingLeaderRepository();
        BrokerDrainScheduler lifecycle = new BrokerDrainScheduler(repository, mock(BrokerClusterAppService.class));

        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);

        verify(repository, times(2)).getCluster();
    }

    @Test
    void failureRecoveryRetriesAfterCommittedStateReadFails() {
        BrokerClusterConsensus repository = failingLeaderRepository();
        BrokerFailureRecoveryScheduler lifecycle = new BrokerFailureRecoveryScheduler(
                repository, mock(BrokerClusterAppService.class));

        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);

        verify(repository, times(2)).getCluster();
    }

    @Test
    void leaseRenewalRetriesAfterCommittedStateReadFails() {
        BrokerClusterConsensus repository = failingLeaderRepository();
        RoutingLeaseScheduler lifecycle = new RoutingLeaseScheduler(repository, mock(BrokerClusterAppService.class));

        assertThatThrownBy(lifecycle::renew).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::renew).isInstanceOf(IllegalStateException.class);

        verify(repository, times(2)).getCluster();
    }

    @Test
    void membershipRetriesAfterCommittedStateReadFails() {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        LocalBroker localBroker = mock(LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.isLeader()).thenReturn(true);
        when(adapter.getCluster()).thenThrow(new IllegalStateException("committed state unavailable"));
        BrokerMembershipScheduler lifecycle = new BrokerMembershipScheduler(
                adapter, mock(BrokerMembershipAppService.class), Duration.ofSeconds(1));

        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(lifecycle::reconcile).isInstanceOf(IllegalStateException.class);

        verify(adapter, times(2)).getCluster();
    }

    private BrokerClusterConsensus failingLeaderRepository() {
        BrokerClusterConsensus repository = mock(BrokerClusterConsensus.class);
        LocalBroker localBroker = mock(LocalBroker.class);
        when(repository.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.isLeader()).thenReturn(true);
        when(repository.getCluster()).thenThrow(new IllegalStateException("committed state unavailable"));
        return repository;
    }
}
