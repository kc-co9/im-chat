package com.co.kc.imchat.broker.transformer.interfaces;

import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.model.io.BrokerManagementOperationResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerManagementHttpTransformerTest {

    @ParameterizedTest
    @EnumSource(BrokerClusterConsensusStatus.class)
    void learnerEnumStatusAndLeaderEndpointPreserveManagementResponseContract(BrokerClusterConsensusStatus status) {
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
