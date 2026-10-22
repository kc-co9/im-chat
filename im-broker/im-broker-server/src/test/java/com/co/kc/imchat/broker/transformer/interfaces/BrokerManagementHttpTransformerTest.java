package com.co.kc.imchat.broker.transformer.interfaces;

import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status;
import com.co.kc.imchat.broker.model.io.BrokerManagementOperationResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerManagementHttpTransformerTest {

    @org.junit.jupiter.api.Test
    void mapsMillisecondsAndNullableDiagnostics() {
        java.time.Instant at = java.time.Instant.ofEpochMilli(1787446800123L);
        com.co.kc.imchat.broker.model.io.BrokerInstanceResponse instance =
                BrokerManagementHttpTransformer.INSTANCE.brokerInstanceFrom(
                        new com.co.kc.imchat.broker.model.cqrs.dto.BrokerInstanceDTO(
                                "b", "host:1", at, java.time.Duration.ofMillis(30001)));
        assertThat(instance.startedAtMs()).isEqualTo(1787446800123L);
        assertThat(instance.uptimeMs()).isEqualTo(30001L);
        com.co.kc.imchat.broker.model.io.DiagnosticSummaryResponse summary =
                BrokerManagementHttpTransformer.INSTANCE.diagnosticSummaryResponseFrom(
                        new com.co.kc.imchat.broker.model.cqrs.dto.DiagnosticSummaryDTO(1L, 0L, at, null, at));
        assertThat(summary.lastExecutedAtMs()).isEqualTo(1787446800123L);
        assertThat(summary.lastFailureAtMs()).isNull();
        com.co.kc.imchat.broker.model.io.GossipRecordResponse record =
                BrokerManagementHttpTransformer.INSTANCE.gossipRecordFrom(
                        new com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO(at, "b",
                                com.co.kc.imchat.broker.model.enums.DiagnosticStatus.SUCCESS,
                                1, java.time.Duration.ofMillis(1001), null));
        assertThat(record.durationMs()).isEqualTo(1001L);
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void learnerEnumStatusAndLeaderEndpointPreserveManagementResponseContract(Status status) {
        AddLearnerResult result = new AddLearnerResult(
                "join", status, "127.0.0.1:19001", "processing result");

        BrokerManagementOperationResponse response =
                BrokerManagementHttpTransformer.INSTANCE.addLearnerResponseFrom(result);

        assertThat(result.leaderEndpoint()).isEqualTo("127.0.0.1:19001");
        assertThat(response.operationId()).isEqualTo("join");
        assertThat(response.status()).isEqualTo(status.name());
        assertThat(response.leaderEndpoint()).isEqualTo(result.leaderEndpoint());
        assertThat(response.message()).isEqualTo(result.message());
        CompleteLearnerSyncResult sync = new CompleteLearnerSyncResult(
                "sync", status, 7L, 7L, "127.0.0.1:19001", "processing result");
        BrokerManagementOperationResponse syncResponse =
                BrokerManagementHttpTransformer.INSTANCE.completeLearnerSyncResponseFrom(sync);
        assertThat(syncResponse.status()).isEqualTo(status.name());
        assertThat(syncResponse.acceptedEpoch()).isEqualTo(7L);
        assertThat(syncResponse.targetEpoch()).isEqualTo(7L);
        assertThat(syncResponse.leaderEndpoint()).isEqualTo(sync.leaderEndpoint());
    }
}
