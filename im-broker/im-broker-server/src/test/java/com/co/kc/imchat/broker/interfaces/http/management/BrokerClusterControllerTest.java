package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.model.io.BrokerClusterOperationResponse;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerClusterControllerTest {

    @Test
    void operationResponseDoesNotExposeInternalDigestOrMembershipEndpoint() {
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("operation-1"))
                .commandDigest(new CommandDigest("internal-digest"))
                .type(BrokerClusterOperationType.ACTIVATE_BROKER)
                .targetBrokerId(new BrokerId("broker-a"))
                .acceptedEpoch(new RoutingEpoch(3L))
                .acceptedRevision(new ClusterRevision(1L))
                .resultEpoch(new RoutingEpoch(4L))
                .phase(BrokerClusterOperationPhase.FENCING_OLD_EPOCH)
                .createdAt(Instant.parse("2026-10-09T00:00:00Z"))
                .build();
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        when(repository.findOperation(new ClusterOperationId("operation-1")))
                .thenReturn(Optional.of(operation));

        BrokerClusterOperationResponse response = new BrokerClusterController(repository)
                .operation("operation-1");

        assertThat(response.operationId()).isEqualTo("operation-1");
        assertThat(response.createdAtMs()).isEqualTo(1791504000000L);
        assertThat(response.completedAtMs()).isNull();
        assertThat(JsonUtils.toJson(response)).doesNotContain("\"createdAt\"", "\"completedAt\"");
        assertThat(response.phase()).isEqualTo("FENCING_OLD_EPOCH");
        assertThat(response.resultEpoch()).isEqualTo(4L);
        assertThat(JsonUtils.toJson(response)).doesNotContain("internal-digest", "commandDigest", "membershipEndpoint");
    }
}
