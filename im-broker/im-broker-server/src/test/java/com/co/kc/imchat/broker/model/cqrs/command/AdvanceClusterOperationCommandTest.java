package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import org.junit.jupiter.api.Test;
import java.time.Instant;

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
        assertThat(builder.changedAt(Instant.EPOCH).build().changedAt()).isEqualTo(Instant.EPOCH);
        assertThatThrownBy(() -> builder.changedAt(Instant.EPOCH.minusNanos(1)).build())
                .isInstanceOf(IllegalArgumentException.class);
        Instant sampled = Instant.ofEpochSecond(1, 123_456_789);
        assertThat(builder.changedAt(sampled).build().changedAt())
                .isEqualTo(Instant.ofEpochMilli(1_123L));
    }
}
