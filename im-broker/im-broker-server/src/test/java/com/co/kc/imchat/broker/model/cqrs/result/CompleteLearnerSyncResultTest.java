package com.co.kc.imchat.broker.model.cqrs.result;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.DecisionReason;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CompleteLearnerSyncResultTest {

    @Test
    void conflictOwnsStableEpochMismatchReason() {
        CompleteLearnerSyncResult result = CompleteLearnerSyncResult.conflict(
                new ClusterOperationId("sync"), new RoutingEpoch(7L));
        assertThat(result.operationId()).isEqualTo("sync");
        assertThat(result.status()).isEqualTo(BrokerClusterConsensusStatus.CONFLICT);
        assertThat(result.acceptedEpoch()).isEqualTo(7L);
        assertThat(result.message()).isEqualTo("expected epoch does not match committed state");
        assertThat(result.targetEpoch()).isNull();
        assertThat(result.leaderEndpoint()).isNull();
    }

    @Test
    void rejectionPreservesReasonAndObservedEpoch() {
        CompleteLearnerSyncResult result = CompleteLearnerSyncResult.rejected(
                new ClusterOperationId("sync"), new RoutingEpoch(7L));
        assertThat(result.status()).isEqualTo(BrokerClusterConsensusStatus.REJECTED);
        assertThat(result.acceptedEpoch()).isEqualTo(7L);
        assertThat(result.message()).isEqualTo("broker membership endpoint does not match committed binding");
        assertThat(result.targetEpoch()).isNull();
        assertThat(result.leaderEndpoint()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = BrokerClusterDecision.Status.class, names = {"CONFLICT", "REJECTED", "UNAVAILABLE"})
    void failureWithoutAssociatedOperationUsesObservedEpoch(BrokerClusterDecision.Status status) {
        BrokerClusterDecision decision = new BrokerClusterDecision(status, null, new DecisionReason("not admitted"));
        CompleteLearnerSyncResult result = CompleteLearnerSyncResult.failure(
                new ClusterOperationId("sync"), new RoutingEpoch(7L), decision);
        assertThat(result.operationId()).isEqualTo("sync");
        assertThat(result.status()).isEqualTo(BrokerClusterConsensusStatus.valueOf(status.name()));
        assertThat(result.acceptedEpoch()).isEqualTo(7L);
        assertThat(result.message()).isEqualTo("not admitted");
        assertThat(result.targetEpoch()).isNull();
        assertThat(result.leaderEndpoint()).isNull();
    }
}
