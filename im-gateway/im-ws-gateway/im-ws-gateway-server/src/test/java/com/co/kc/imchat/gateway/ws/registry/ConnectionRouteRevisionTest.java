package com.co.kc.imchat.gateway.ws.registry;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRouteRevisionTest {

    @Test
    void mutationsAndSnapshotsShareOneMonotonicRevision() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel channel = new EmbeddedChannel();

        long registeredRevision = registry.register(
                101L, "session-1", "connection-1", channel);
        ConnectionRegistry.RouteSnapshot registeredSnapshot = registry.routeSnapshot();
        ConnectionRegistry.RouteMutation removed = registry.unregister("connection-1");
        ConnectionRegistry.RouteSnapshot removedSnapshot = registry.routeSnapshot();

        assertThat(registeredRevision).isEqualTo(1L);
        assertThat(registeredSnapshot.routeRevision()).isEqualTo(registeredRevision);
        assertThat(registeredSnapshot.userIds()).containsExactly(101L);
        assertThat(removed.routeRevision()).isEqualTo(2L);
        assertThat(removed.routeAbsent()).isTrue();
        assertThat(removedSnapshot.routeRevision()).isEqualTo(removed.routeRevision());
        assertThat(removedSnapshot.userIds()).isEmpty();
    }

    @Test
    void duplicateRemovalDoesNotAdvanceRevision() {
        ConnectionRegistry registry = new ConnectionRegistry();

        ConnectionRegistry.RouteMutation missing = registry.unregister("missing");

        assertThat(missing.routeRevision()).isZero();
        assertThat(missing.routeAbsent()).isFalse();
        assertThat(registry.routeSnapshot().routeRevision()).isZero();
    }

    @Test
    void removingOneOfMultipleConnectionsKeepsRoutePresentAtNewRevision() {
        ConnectionRegistry registry = new ConnectionRegistry();
        registry.register(101L, "session-1", "connection-1", new EmbeddedChannel());
        registry.register(101L, "session-1", "connection-2", new EmbeddedChannel());

        ConnectionRegistry.RouteMutation mutation = registry.unregister("connection-1");

        assertThat(mutation.routeRevision()).isEqualTo(3L);
        assertThat(mutation.routeAbsent()).isFalse();
        assertThat(registry.routeSnapshot().userIds()).containsExactly(101L);
    }
}
