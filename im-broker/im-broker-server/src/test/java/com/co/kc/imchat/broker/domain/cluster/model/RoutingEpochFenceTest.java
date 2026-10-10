package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingEpochFenceTest {
    @Test
    void retryMustMatchBothOperationAndEpoch() {
        RoutingEpochFence fence = fence();
        assertThat(fence.belongsTo(new ClusterOperationId("operation-1"), new RoutingEpoch(7L))).isTrue();
        assertThat(fence.belongsTo(new ClusterOperationId("other"), new RoutingEpoch(7L))).isFalse();
        assertThat(fence.belongsTo(new ClusterOperationId("operation-1"), new RoutingEpoch(8L))).isFalse();
    }

    @Test
    void sameTermReusesPersistedFactAndOldTermIsRejected() {
        RoutingEpochFence fence = fence();
        assertThat(fence.resume(new QuorumRead(term(3L), readIndex(50L)))).isSameAs(fence);
        assertThatThrownBy(() -> fence.resume(new QuorumRead(term(2L), readIndex(50L))))
                .isInstanceOf(IllegalStateException.class).hasMessage("fence leader term regressed");
        assertThat(fence.quorumReadIndex()).isEqualTo(readIndex(42L));
    }

    @Test
    void newTermPreservesFrozenIdentityWithoutMutatingPreviousFact() {
        RoutingEpochFence fence = fence();
        RoutingEpochFence resumed = fence.resume(new QuorumRead(term(4L), readIndex(50L)));
        assertThat(resumed).isNotSameAs(fence);
        assertThat(resumed.operationId()).isEqualTo(fence.operationId());
        assertThat(resumed.epoch()).isEqualTo(fence.epoch());
        assertThat(resumed.fencedBrokerIds()).isEqualTo(fence.fencedBrokerIds());
        assertThat(resumed.leaderTerm()).isEqualTo(term(4L));
        assertThat(resumed.quorumReadIndex()).isEqualTo(readIndex(50L));
        assertThat(fence.leaderTerm()).isEqualTo(term(3L));
    }

    private RoutingEpochFence fence() {
        return RoutingEpochFence.builder().operationId(new ClusterOperationId("operation-1"))
                .epoch(new RoutingEpoch(7L)).leaderTerm(term(3L)).quorumReadIndex(readIndex(42L))
                .fencedBrokerIds(List.of(new BrokerId("broker-a"))).build();
    }
}
