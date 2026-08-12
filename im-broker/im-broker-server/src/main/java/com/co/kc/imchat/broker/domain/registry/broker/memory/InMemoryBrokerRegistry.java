package com.co.kc.imchat.broker.domain.registry.broker.memory;

import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.transformer.RegistryTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBrokerRegistry implements BrokerRegistry {
    private static final RegistryTransformer REGISTRY_TRANSFORMER = RegistryTransformer.INSTANCE;

    /* <BrokerId, BrokerEndpointDTO> */
    private final Map<String, BrokerEndpointDTO> brokers = new ConcurrentHashMap<>();

    @Override
    public void register(String brokerId, String host, int port) {
        brokers.put(brokerId, REGISTRY_TRANSFORMER.brokerEndpointFrom(brokerId, host, port));
    }

    @Override
    public void unregister(String brokerId) {
        brokers.remove(brokerId);
    }

    @Override
    public void heartbeat(String brokerId) {
        brokers.computeIfPresent(brokerId, (ignored, broker) ->
                REGISTRY_TRANSFORMER.refreshBrokerHeartbeat(broker));
    }

    @Override
    public Optional<BrokerEndpointDTO> find(String brokerId) {
        return Optional.ofNullable(brokers.get(brokerId));
    }

    @Override
    public List<BrokerEndpointDTO> list() {
        return new ArrayList<>(brokers.values());
    }
}
