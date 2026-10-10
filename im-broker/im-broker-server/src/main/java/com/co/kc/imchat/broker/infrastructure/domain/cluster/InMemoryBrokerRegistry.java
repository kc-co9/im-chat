package com.co.kc.imchat.broker.infrastructure.domain.cluster;

import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRegistration;
import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBrokerRegistry implements BrokerRegistry {
    private static final RegistryTransformer REGISTRY_TRANSFORMER = RegistryTransformer.INSTANCE;

    /* <BrokerId, BrokerRegistration> */
    private final Map<String, BrokerRegistration> brokers = new ConcurrentHashMap<>();

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
    public Optional<BrokerRegistration> find(String brokerId) {
        return Optional.ofNullable(brokers.get(brokerId));
    }

    @Override
    public List<BrokerRegistration> list() {
        return new ArrayList<>(brokers.values());
    }
}
