package com.co.kc.imchat.broker.sdk.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterStateTest {

    @Test
    void emptyActiveBrokerSetIsRejected() {
        BrokerNode ready = node("broker-a", BrokerMembershipRole.LEARNER,
                BrokerRoutingState.READY);

        assertThatThrownBy(() -> state(List.of(ready)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void duplicateBrokerIdentityIsRejected() {
        BrokerNode first = node("broker-a", BrokerMembershipRole.VOTER,
                BrokerRoutingState.ACTIVE);
        BrokerNode duplicate = new BrokerNode("broker-a", "broker-a.internal:19021",
                BrokerMembershipRole.LEARNER, BrokerRoutingState.READY);

        assertThatThrownBy(() -> state(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brokerId");
    }

    @Test
    void uncommittedMembershipCannotCarryRoutingTraffic() {
        assertThatThrownBy(() -> node("broker-a", BrokerMembershipRole.DISCOVERED,
                BrokerRoutingState.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("membership");
    }

    @Test
    void missingAlgorithmIsRejected() {
        BrokerNode active = node("broker-a", BrokerMembershipRole.VOTER,
                BrokerRoutingState.ACTIVE);

        assertThatThrownBy(() -> new BrokerClusterState(0L, 0L,
                null, List.of(active)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm");
    }

    private BrokerClusterState state(List<BrokerNode> brokers) {
        return new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, brokers);
    }

    private BrokerNode node(String brokerId, BrokerMembershipRole membership,
                            BrokerRoutingState routingState) {
        return new BrokerNode(brokerId, brokerId + ".internal:18020", membership, routingState);
    }
}
