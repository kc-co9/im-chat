package com.co.kc.imchat.broker.infrastructure.domain.connection;

import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class InMemoryGatewayRegistry implements GatewayRegistry {
    private static final RegistryTransformer REGISTRY_TRANSFORMER = RegistryTransformer.INSTANCE;

    /* Gateway 身份到本机注册观察。 */
    private final Map<GatewayId, GatewayRegistration> gateways = new ConcurrentHashMap<>();
    private final Set<GatewayId> retiredGatewayIds = ConcurrentHashMap.newKeySet();

    @Override
    public void register(GatewayRegistration registration) {
        GatewayId gatewayId = registration.gatewayId();
        if (retiredGatewayIds.contains(gatewayId)) {
            throw new IllegalStateException("retired gatewayId cannot be reused: " + gatewayId);
        }
        gateways.compute(gatewayId, (ignored, existing) -> {
            if (existing != null && !java.util.Objects.equals(
                    existing.principalId(), registration.principalId())) {
                throw new SecurityException("gatewayId is already bound to another workload identity");
            }
            return registration;
        });
    }

    @Override
    public void unregister(GatewayId gatewayId) {
        gateways.remove(gatewayId);
        retiredGatewayIds.add(gatewayId);
    }

    @Override
    public boolean isRetired(GatewayId gatewayId) {
        return retiredGatewayIds.contains(gatewayId);
    }

    @Override
    public void heartbeat(GatewayId gatewayId) {
        gateways.computeIfPresent(gatewayId, (ignored, gateway) ->
                REGISTRY_TRANSFORMER.refreshGatewayHeartbeat(gateway));
    }

    @Override
    public Optional<GatewayRegistration> find(GatewayId gatewayId) {
        return Optional.ofNullable(gateways.get(gatewayId));
    }

    @Override
    public List<GatewayRegistration> list() {
        return List.copyOf(gateways.values());
    }
}
