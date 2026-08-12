package com.co.kc.imchat.broker.domain.registry.gateway.memory;

import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.transformer.RegistryTransformer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGatewayRegistry implements GatewayRegistry {
    private static final RegistryTransformer REGISTRY_TRANSFORMER = RegistryTransformer.INSTANCE;

    /* <GatewayId, BrokerEndpointDTO> */
    private final Map<String, GatewayEndpointDTO> gateways = new ConcurrentHashMap<>();

    @Override
    public void register(String gatewayId, String host, int port) {
        gateways.put(gatewayId, REGISTRY_TRANSFORMER.gatewayEndpointFrom(gatewayId, host, port));
    }

    @Override
    public void unregister(String gatewayId) {
        gateways.remove(gatewayId);
    }

    @Override
    public void heartbeat(String gatewayId) {
        gateways.computeIfPresent(gatewayId, (ignored, gateway) ->
                REGISTRY_TRANSFORMER.refreshGatewayHeartbeat(gateway));
    }

    @Override
    public Optional<GatewayEndpointDTO> find(String gatewayId) {
        return Optional.ofNullable(gateways.get(gatewayId));
    }
}
