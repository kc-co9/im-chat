package com.co.kc.imchat.broker.transformer.infrastructure;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerRegistration;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryTransformerTest {
    @Test
    void registryObservationsKeepSdkEndpointAndGossipPayloadShape() {
        Instant registered = Instant.ofEpochMilli(1_000L);
        Instant refreshed = Instant.ofEpochMilli(2_000L);
        BrokerRegistration broker = new BrokerRegistration("broker-a", "host-a", 18020, registered, refreshed);
        GatewayRegistration gateway = new GatewayRegistration(new GatewayId("gateway-a"), new GatewayHost("host-b"), new GatewayPort(18021), new GatewayPrincipalId("identity-a"), registered, refreshed);

        assertThat(RegistryTransformer.INSTANCE.brokerEndpointFrom(broker))
                .isEqualTo(new BrokerEndpointDTO("broker-a", "host-a", 18020, registered, refreshed));
        assertThat(RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway))
                .isEqualTo(new GatewayEndpointDTO("gateway-a", "host-b", 18021, "identity-a", registered, refreshed));
        assertThat(JsonUtils.fromJson(JsonUtils.toJson(broker), BrokerEndpointDTO.class))
                .isEqualTo(RegistryTransformer.INSTANCE.brokerEndpointFrom(broker));
        assertThat(JsonUtils.fromJson(JsonUtils.toJson(RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway)), GatewayEndpointDTO.class))
                .isEqualTo(RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway));
    }
}
