package com.co.kc.imchat.broker.sdk.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterStateCacheTest {

    @Test
    void atomicallyInstallsOnlyNewerCommittedState() {
        BrokerClusterStateCache cache = new BrokerClusterStateCache();
        BrokerClusterState revisionTwo = state(2L, 1L, "broker-a");

        assertThat(cache.current()).isEmpty();
        assertThat(cache.install(revisionTwo))
                .isEqualTo(BrokerClusterStateCache.InstallResult.INSTALLED);
        assertThat(cache.install(state(1L, 0L, "broker-a")))
                .isEqualTo(BrokerClusterStateCache.InstallResult.STALE);
        assertThat(cache.install(revisionTwo))
                .isEqualTo(BrokerClusterStateCache.InstallResult.UNCHANGED);
        assertThat(cache.current()).contains(revisionTwo);
    }

    @Test
    void rejectsDifferentContentForSameRevision() {
        BrokerClusterStateCache cache = new BrokerClusterStateCache();
        cache.install(state(2L, 1L, "broker-a"));

        assertThatThrownBy(() -> cache.install(state(2L, 1L, "broker-b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revision");
    }

    @Test
    void rejectsAlgorithmReplacementAfterBootstrap() {
        BrokerClusterStateCache cache = new BrokerClusterStateCache();
        cache.install(state(2L, 1L, "broker-a"));
        BrokerClusterState anotherAlgorithm = new BrokerClusterState(
                3L, 2L, BrokerSelectionAlgorithm.MODULO_HASH,
                List.of(active("broker-b")));

        assertThatThrownBy(() -> cache.install(anotherAlgorithm))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("algorithm");
    }

    private BrokerClusterState state(long revision, long epoch, String brokerId) {
        return new BrokerClusterState(revision, epoch,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(active(brokerId)));
    }

    private BrokerNode active(String brokerId) {
        return new BrokerNode(brokerId, brokerId + ".example:18020",
                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }
}
