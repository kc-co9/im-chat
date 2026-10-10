package com.co.kc.imchat.broker.domain.connection.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionRouteValueObjectTest {

    @Test
    void validatesRouteIdentityAndRevisionAtConstruction() {
        assertThat(new GatewayId("gateway-1").value()).isEqualTo("gateway-1");
        assertThat(new UserId(101L).value()).isEqualTo(101L);
        assertThat(new RouteRevision(3L).value()).isEqualTo(3L);

        assertThatThrownBy(() -> new GatewayId(" "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new UserId(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RouteRevision(-1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bindsSnapshotRevisionAndContentDigestAsOneValue() {
        GatewayRouteSnapshotDigest digest = new GatewayRouteSnapshotDigest(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");

        GatewayRouteSnapshotCheckpoint binding = new GatewayRouteSnapshotCheckpoint(
                new RouteRevision(7L), digest);

        assertThat(binding.revision()).isEqualTo(new RouteRevision(7L));
        assertThat(binding.snapshotDigest()).isEqualTo(digest);
        assertThatThrownBy(() -> new GatewayRouteSnapshotCheckpoint(null, digest))
                .isInstanceOf(IllegalStateException.class);
    }
}
