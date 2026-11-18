package com.co.kc.imchat.broker.sdk.cluster;

/**
 * 建群时确定的 RouteBroker 选择算法。
 */
public enum BrokerSelectionAlgorithm {
    RENDEZVOUS_HASH,
    MODULO_HASH
}
