package com.co.kc.imchat.broker.sdk.model.result;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MembershipPermitDrainedResultTest {
    @Test
    void rejectionFactoriesPreserveStatusEmptyLeaderAndReason() {
        assertThat(MembershipPermitDrainedResult.identityMismatch()).isEqualTo(
                new MembershipPermitDrainedResult(MembershipPermitDrainedResult.Status.REJECTED,
                        null, "confirmation identity mismatch"));
        assertThat(MembershipPermitDrainedResult.rejected("missing record")).isEqualTo(
                new MembershipPermitDrainedResult(MembershipPermitDrainedResult.Status.REJECTED,
                        null, "missing record"));
    }
}
