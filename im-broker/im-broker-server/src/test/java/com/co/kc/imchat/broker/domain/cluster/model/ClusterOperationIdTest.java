package com.co.kc.imchat.broker.domain.cluster.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterOperationIdTest {
    @Test
    void automaticFailureIdentityPreservesFormatAndIsStableForRetries() {
        ClusterOperationId operationId = ClusterOperationId.forAutomaticFailure(
                new BrokerClusterId("cluster-a"), new BrokerId("broker-b"), new RoutingEpoch(3L));
        assertThat(operationId.value()).isEqualTo("auto-failure:cluster-a:broker-b:3");
        assertThat(ClusterOperationId.forAutomaticFailure(
                new BrokerClusterId("cluster-a"), new BrokerId("broker-b"), new RoutingEpoch(3L)))
                .isEqualTo(operationId);
    }

    @Test
    void automaticFailureIdentitySeparatesClusterBrokerAndEpoch() {
        ClusterOperationId operationId = ClusterOperationId.forAutomaticFailure(
                new BrokerClusterId("cluster-a"), new BrokerId("broker-b"), new RoutingEpoch(3L));
        assertThat(ClusterOperationId.forAutomaticFailure(
                new BrokerClusterId("cluster-c"), new BrokerId("broker-b"), new RoutingEpoch(3L)))
                .isNotEqualTo(operationId);
        assertThat(ClusterOperationId.forAutomaticFailure(
                new BrokerClusterId("cluster-a"), new BrokerId("broker-c"), new RoutingEpoch(3L)))
                .isNotEqualTo(operationId);
        assertThat(ClusterOperationId.forAutomaticFailure(
                new BrokerClusterId("cluster-a"), new BrokerId("broker-b"), new RoutingEpoch(4L)))
                .isNotEqualTo(operationId);
    }
}
