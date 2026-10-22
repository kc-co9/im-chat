package com.co.kc.imchat.broker.infrastructure.registry.connection;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;
import java.time.Duration;

import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRouteRevisionTest {

    @Test
    void lateRegisterCannotResurrectNewerUnregisterTombstone() {
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-1")), new RouteRevision(1L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        registry.unregister(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-1")), new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE));
        boolean applied = registry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-1")), new RouteRevision(2L), Instant.ofEpochMilli(Long.MAX_VALUE)));

        assertThat(applied).isFalse();
        assertThat(registry.find(new UserId(101L))).isEmpty();
    }

    @Test
    void olderSnapshotCannotDeleteNewerIncrementalRegister() {
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();
        registry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-1")), new RouteRevision(5L), Instant.ofEpochMilli(Long.MAX_VALUE)));

        registry.reconcileRoutes(new GatewayId("gateway-1"), new RouteRevision(4L), Instant.ofEpochMilli(Long.MAX_VALUE), List.of());

        assertThat(registry.find(new UserId(101L))).hasSize(1);
    }

    @Test
    void tombstoneIsRetainedUntilUpdateExpiryDespiteNewerSnapshot() {
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();
        registry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-1")), new RouteRevision(1L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        registry.unregister(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-1")), new RouteRevision(2L), Instant.ofEpochMilli(Long.MAX_VALUE));

        assertThat(registry.collectExpiredTombstones(Duration.ofMillis(100L))).isZero();

        registry.reconcileRoutes(new GatewayId("gateway-1"), new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE), List.of());

        assertThat(registry.collectExpiredTombstones(Duration.ofMillis(100L))).isZero();
    }

    @Test
    void newGatewayProcessIdentityHasIndependentRevisionScope() {
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();
        registry.unregister(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-old")), new RouteRevision(9L), Instant.ofEpochMilli(Long.MAX_VALUE));

        boolean applied = registry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-new")), new RouteRevision(1L), Instant.ofEpochMilli(Long.MAX_VALUE)));

        assertThat(applied).isTrue();
        assertThat(registry.find(new UserId(101L))).extracting(route -> route.gatewayId().value())
                .containsExactly("gateway-new");
    }
}
