package com.co.kc.imchat.broker.transformer.infrastructure;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerFenceTransformerTest {
    @Test
    void requestPreservesPersistedOperationEpochTermAndReadIndex() {
        RoutingEpochFence fence = RoutingEpochFence.builder()
                .operationId(new ClusterOperationId("fence-1")).epoch(new RoutingEpoch(7L))
                .leaderTerm(term(3L)).quorumReadIndex(readIndex(42L))
                .fencedBrokerIds(List.of(new BrokerId("broker-a"))).build();
        assertThat(BrokerFenceTransformer.INSTANCE.routingFenceParamsFrom(fence))
                .isEqualTo(new RoutingFenceParams("fence-1", 7L, 3L, 42L));
    }

    @Test
    void responsePreservesRemoteIdentityForApplicationValidation() {
        FenceAck ack = BrokerFenceTransformer.INSTANCE.fenceAckFrom(
                new RoutingFenceAckResult("broker-other", 7L, 3L, 9L, "fence-other"));
        assertThat(ack.brokerId()).isEqualTo(new BrokerId("broker-other"));
        assertThat(ack.operationId()).isEqualTo(new ClusterOperationId("fence-other"));
        assertThat(ack.routingEpoch()).isEqualTo(new RoutingEpoch(7L));
        assertThat(ack.leaderTerm()).isEqualTo(term(3L));
        assertThat(ack.fencedLeaseSequence().value()).isEqualTo(9L);
    }
}
