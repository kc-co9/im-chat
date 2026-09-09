package com.co.kc.imchat.broker.domain.registry.gateway.memory;

import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("realtime-behavior")
class InMemoryGatewayRegistryTest {

    @Test
    void returnsImmutableGatewaySnapshot() {
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry();
        registry.register("gateway-1", "127.0.0.1", 9001);

        List<GatewayEndpointDTO> snapshot = registry.list();
        registry.register("gateway-2", "127.0.0.1", 9002);

        assertThat(snapshot).extracting(GatewayEndpointDTO::gatewayId)
                .containsExactly("gateway-1");
        assertThatThrownBy(() -> snapshot.add(registry.find("gateway-2").orElseThrow()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listsGatewaysRegisteredConcurrently() {
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry();

        IntStream.range(0, 500).parallel()
                .forEach(index -> registry.register("gateway-" + index, "127.0.0.1", 9000 + index));

        assertThat(registry.list()).hasSize(500);
    }
}
