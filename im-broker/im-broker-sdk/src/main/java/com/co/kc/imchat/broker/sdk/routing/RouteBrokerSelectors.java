package com.co.kc.imchat.broker.sdk.routing;

import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;

/**
 * 按已提交算法获取无状态 RouteBroker 选择器。
 */
public final class RouteBrokerSelectors {

    private static final RouteBrokerSelector RENDEZVOUS = new RendezvousHashRouteBrokerSelector();
    private static final RouteBrokerSelector MODULO = new ModuloHashRouteBrokerSelector();

    private RouteBrokerSelectors() {
    }

    public static RouteBrokerSelector forAlgorithm(BrokerSelectionAlgorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        return switch (algorithm) {
            case RENDEZVOUS_HASH -> RENDEZVOUS;
            case MODULO_HASH -> MODULO;
        };
    }
}
