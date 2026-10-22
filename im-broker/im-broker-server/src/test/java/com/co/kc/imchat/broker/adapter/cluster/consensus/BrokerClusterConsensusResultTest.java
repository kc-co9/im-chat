package com.co.kc.imchat.broker.adapter.cluster.consensus;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.RecordComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerClusterConsensusResultTest {

    @Test
    void resultCarriesOutcomeWithoutClusterSnapshot() {
        assertThat(BrokerClusterConsensusResult.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("status", "operation", "leaderEndpoint", "message");
    }

    @Test
    void notLeaderFactoryPreservesReasonAndEndpoint() {
        BrokerClusterConsensusResult result = BrokerClusterConsensusResult.notLeader("127.0.0.1:19001", "learner progress check requires the cluster leader");
        assertThat(result.status()).isEqualTo(Status.NOT_LEADER);
        assertThat(result.leaderEndpoint()).isEqualTo("127.0.0.1:19001");
        assertThat(result.message()).isEqualTo("learner progress check requires the cluster leader");
        assertThat(result.operation()).isNull();
        assertThat(result.accepted()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"learner has not joined the committed consensus membership",
            "learner has not applied the committed Raft read index"})
    void rejectionFactoryPreservesDifferentReasons(String message) {
        BrokerClusterConsensusResult result = BrokerClusterConsensusResult.rejected(message);
        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.operation()).isNull();
        assertThat(result.leaderEndpoint()).isNull();
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void pendingOperationRequiresAcceptedResultAndPendingPhase() {
        BrokerClusterOperation operation = mock(BrokerClusterOperation.class);
        when(operation.phase()).thenReturn(BrokerClusterOperationPhase.PENDING);
        assertThat(result(Status.APPLIED, operation).hasPendingOperation()).isTrue();
        assertThat(result(Status.DUPLICATE, operation).hasPendingOperation()).isTrue();
        assertThat(result(Status.REJECTED, operation).hasPendingOperation()).isFalse();
        assertThat(result(Status.APPLIED, null).hasPendingOperation()).isFalse();
        when(operation.phase()).thenReturn(BrokerClusterOperationPhase.RAFT_COMMITTED);
        assertThat(result(Status.APPLIED, operation).hasPendingOperation()).isFalse();
    }

    @Test
    void operationIsReadyForCompletionOnlyAfterAcceptedRaftCommit() {
        BrokerClusterOperation operation = mock(BrokerClusterOperation.class);
        when(operation.phase()).thenReturn(BrokerClusterOperationPhase.RAFT_COMMITTED);

        assertThat(result(Status.APPLIED, operation)
                .hasCommittedOperationToComplete()).isTrue();
        assertThat(result(Status.DUPLICATE, operation)
                .hasCommittedOperationToComplete()).isTrue();
        assertThat(result(Status.REJECTED, operation)
                .hasCommittedOperationToComplete()).isFalse();
        assertThat(result(Status.APPLIED, null)
                .hasCommittedOperationToComplete()).isFalse();

        when(operation.phase()).thenReturn(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(result(Status.APPLIED, operation)
                .hasCommittedOperationToComplete()).isFalse();
    }

    private BrokerClusterConsensusResult result(Status status,
                                                BrokerClusterOperation operation) {
        return new BrokerClusterConsensusResult(status, operation, null, null);
    }
}
