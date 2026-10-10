package com.co.kc.imchat.broker.domain.connection.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRegistrationTest {
    @Test
    void validatesEndpointAndPrincipalAtConstruction() {
        assertThatThrownBy(() -> new GatewayHost(" ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewayPort(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewayPort(0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewayPort(65536)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewayPrincipalId(" ")).isInstanceOf(IllegalStateException.class);
        assertThat(new GatewayPort(65535).value()).isEqualTo(65535);
        assertThatThrownBy(() -> new GatewayRegistration(new GatewayId("gateway-a"),
                new GatewayHost("localhost"), new GatewayPort(18012), null, Instant.EPOCH, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
    }
}
