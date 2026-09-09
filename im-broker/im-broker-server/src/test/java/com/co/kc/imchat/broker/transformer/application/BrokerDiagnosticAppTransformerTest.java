package com.co.kc.imchat.broker.transformer.application;

import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionRouteDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerDiagnosticAppTransformerTest {

    @Test
    void mapsConnectionLifecycleTimesToDiagnosticRoute() {
        Instant connectedAt = Instant.parse("2026-09-09T10:00:00Z");
        Instant refreshedAt = Instant.parse("2026-09-09T10:01:00Z");
        UserGatewayDTO connection = new UserGatewayDTO(
                7496072726080262144L, "gateway-1", connectedAt, refreshedAt);

        ConnectionRouteDTO result = BrokerDiagnosticAppTransformer.INSTANCE.connectionFrom(connection);

        assertThat(result.registeredAt()).isEqualTo(connectedAt);
        assertThat(result.lastSeenAt()).isEqualTo(refreshedAt);
    }
}
