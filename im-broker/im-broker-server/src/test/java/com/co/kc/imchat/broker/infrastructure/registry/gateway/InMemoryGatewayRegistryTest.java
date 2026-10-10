package com.co.kc.imchat.broker.infrastructure.registry.gateway;

import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("realtime-behavior")
class InMemoryGatewayRegistryTest {

    @Test
    void acceptsDomainRegistrationAndPreservesIdentityBindingAndRetirement() {
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry();
        GatewayId gatewayId = new GatewayId("gateway-1");
        GatewayRegistration registration = new GatewayRegistration(gatewayId, new GatewayHost("127.0.0.1"), new GatewayPort(9001), new GatewayPrincipalId("identity-a"), Instant.EPOCH, Instant.EPOCH);

        registry.register(registration);
        assertThat(registry.find(gatewayId)).containsSame(registration);
        assertThatThrownBy(() -> registry.register(new GatewayRegistration(gatewayId, new GatewayHost("127.0.0.2"), new GatewayPort(9002), new GatewayPrincipalId("identity-b"), Instant.EPOCH, Instant.EPOCH)))
                .isInstanceOf(SecurityException.class);
        assertThat(registry.find(gatewayId)).containsSame(registration);
        registry.heartbeat(gatewayId);
        assertThat(registry.find(gatewayId).orElseThrow().lastSeenAt()).isAfter(Instant.EPOCH);
        GatewayRegistration refreshed = registry.find(gatewayId).orElseThrow();
        assertThat(refreshed.principalId()).isEqualTo(registration.principalId());
        assertThat(refreshed.host()).isEqualTo(registration.host());
        assertThat(refreshed.port()).isEqualTo(registration.port());
        assertThat(refreshed.registeredAt()).isEqualTo(registration.registeredAt());

        registry.unregister(gatewayId);
        assertThat(registry.find(gatewayId)).isEmpty();
        assertThat(registry.isRetired(gatewayId)).isTrue();
        assertThatThrownBy(() -> registry.register(registration)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.find(gatewayId)).isEmpty();
    }

    @Test
    void returnsImmutableGatewaySnapshot() {
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry();
        registry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-1", "127.0.0.1", 9001, "test-identity"));

        List<GatewayRegistration> snapshot = registry.list();
        registry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-2", "127.0.0.1", 9002, "test-identity"));

        assertThat(snapshot).extracting(GatewayRegistration::gatewayId)
                .containsExactly(new GatewayId("gateway-1"));
        assertThatThrownBy(() -> snapshot.add(registry.find(new GatewayId("gateway-2")).orElseThrow()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listsGatewaysRegisteredConcurrently() {
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry();

        IntStream.range(0, 500).parallel()
                .forEach(index -> registry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-" + index, "127.0.0.1", 9000 + index, "test-identity")));

        assertThat(registry.list()).hasSize(500);
    }
}
