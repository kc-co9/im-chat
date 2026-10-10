package com.co.kc.imchat.broker.transformer.domain;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerClusterDomainTransformerTest {

    private final BrokerClusterDomainTransformer transformer =
            BrokerClusterDomainTransformer.INSTANCE;

    @Test
    void restoresDomainAggregateAndProjectsSdkStateWithoutLosingMeaning() {
        BrokerClusterState sdkState = new BrokerClusterState(4L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));

        BrokerCluster aggregate = transformer.restoreAggregate(
                sdkState, List.of(), null, 10);
        BrokerClusterState projected = transformer.toSdkState(aggregate);

        assertThat(projected).isEqualTo(sdkState);
    }
}
