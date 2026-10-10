package com.co.kc.imchat.broker.model.cqrs.result;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.domain.cluster.model.DecisionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class AddLearnerResultTest {

    @Test
    void conflictRecordsRequestIdentityAndReason() {
        AddLearnerResult result = AddLearnerResult.conflict(new ClusterOperationId("join"));
        assertThat(result.operationId()).isEqualTo("join");
        assertThat(result.status()).isEqualTo(BrokerClusterConsensusStatus.CONFLICT);
        assertThat(result.message()).isEqualTo("expected epoch does not match committed state");
        assertThat(result.leaderEndpoint()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = BrokerClusterDecision.Status.class, names = {"CONFLICT", "REJECTED", "UNAVAILABLE"})
    void failurePreservesDomainStatusAndReason(BrokerClusterDecision.Status status) {
        BrokerClusterDecision decision = new BrokerClusterDecision(status, null, new DecisionReason("not admitted"));
        AddLearnerResult result = AddLearnerResult.failure(new ClusterOperationId("join"), decision);
        assertThat(result.operationId()).isEqualTo("join");
        assertThat(result.status()).isEqualTo(BrokerClusterConsensusStatus.valueOf(status.name()));
        assertThat(result.message()).isEqualTo("not admitted");
        assertThat(result.leaderEndpoint()).isNull();
    }
}
