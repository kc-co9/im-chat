package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdvanceClusterOperationCommandTest {
    @Test
    void missingTimeIsRejectedButZeroIsValid() {
        AdvanceClusterOperationCommand.Builder builder = AdvanceClusterOperationCommand.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .commandDigest("digest-1")
                .expectedPhase(BrokerClusterOperationPhase.PENDING)
                .nextPhase(BrokerClusterOperationPhase.FAILED);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
        assertThat(builder.changedAtEpochMilli(0L).build().changedAtEpochMilli()).isZero();
        assertThatThrownBy(() -> builder.changedAtEpochMilli(-1L).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
