package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteCleanupCheckpointTest {
    @Test
    void identitySeparatesOperationsAndGatewaysWhileCheckpointPreservesWatermark() {
        RouteCleanupId first = new RouteCleanupId(new ClusterOperationId("drain-1"), new GatewayId("gateway-a"));
        assertThat(first).isNotEqualTo(new RouteCleanupId(new ClusterOperationId("drain-2"), new GatewayId("gateway-a")));
        assertThat(first).isNotEqualTo(new RouteCleanupId(new ClusterOperationId("drain-1"), new GatewayId("gateway-b")));
        RouteCleanupCheckpoint checkpoint = new RouteCleanupCheckpoint(new RouteRevision(7L), new RouteCount(2L));
        checkpoint.checkRevision(new RouteRevision(7L));
        assertThatThrownBy(() -> checkpoint.checkRevision(new RouteRevision(8L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(checkpoint.removedRouteCount()).isEqualTo(new RouteCount(2L));
    }
}
