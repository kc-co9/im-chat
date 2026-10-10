package com.co.kc.imchat.broker.adapter.cluster.membership;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.RecordComponent;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerConsensusMembershipResultTest {

    @Test
    void resultCarriesOutcomeWithoutMembershipSnapshot() {
        assertThat(BrokerConsensusMembershipResult.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("status", "leaderEndpoint", "message");
    }

    @ParameterizedTest
    @EnumSource(BrokerConsensusMembershipResult.Status.class)
    void onlyExplicitRejectionIsRejected(BrokerConsensusMembershipResult.Status status) {
        BrokerConsensusMembershipResult result =
                new BrokerConsensusMembershipResult(status, null, null);

        assertThat(result.rejected()).isEqualTo(status == BrokerConsensusMembershipResult.Status.REJECTED);
    }
}
