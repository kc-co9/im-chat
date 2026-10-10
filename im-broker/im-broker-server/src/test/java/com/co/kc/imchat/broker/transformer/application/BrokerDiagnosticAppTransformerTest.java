package com.co.kc.imchat.broker.transformer.application;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.model.cqrs.dto.ConnectionRouteDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerDiagnosticAppTransformerTest {

    @Test
    void mapsConnectionLifecycleTimesToDiagnosticRoute() {
        Instant connectedAt = Instant.parse("2026-09-09T10:00:00Z");
        Instant refreshedAt = Instant.parse("2026-09-09T10:01:00Z");
        ConnectionRegistration connection = ConnectionRegistration.builder()
                .id(new ConnectionRouteId(new UserId(7496072726080262144L), new GatewayId("gateway-1")))
                .revision(new RouteRevision(7L)).updateExpiresAt(Instant.MAX)
                .connectedAt(connectedAt).refreshedAt(refreshedAt).build();

        ConnectionRouteDTO result = BrokerDiagnosticAppTransformer.INSTANCE.connectionFrom(connection);

        assertThat(result.registeredAt()).isEqualTo(connectedAt);
        assertThat(result.lastSeenAt()).isEqualTo(refreshedAt);
        assertThat(result.userId()).isEqualTo(7496072726080262144L);
        assertThat(result.gatewayId()).isEqualTo("gateway-1");
    }
}
