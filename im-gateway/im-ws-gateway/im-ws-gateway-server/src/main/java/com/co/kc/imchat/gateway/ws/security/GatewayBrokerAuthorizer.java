package com.co.kc.imchat.gateway.ws.security;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;

import java.util.function.Supplier;

/** 验证调用 Gateway 数据面的 Broker TLS workload identity。 */
public final class GatewayBrokerAuthorizer {
    private final Supplier<BrokerClusterState> clusterState;
    private final String identityPrefix;
    private final boolean allowTrustedNetworkIdentity;

    public GatewayBrokerAuthorizer(Supplier<BrokerClusterState> clusterState,
                                   String identityPrefix,
                                   boolean allowTrustedNetworkIdentity) {
        if (clusterState == null || identityPrefix == null || identityPrefix.isBlank()) {
            throw new IllegalArgumentException("invalid Gateway Broker authorization");
        }
        this.clusterState = clusterState;
        this.identityPrefix = identityPrefix;
        this.allowTrustedNetworkIdentity = allowTrustedNetworkIdentity;
    }

    public void authorize(BoltRequestContext context) {
        if (context == null) {
            throw new SecurityException("Gateway data plane requires Broker transport identity");
        }
        String peerIdentity = context.peerIdentity();
        if (peerIdentity == null) {
            if (allowTrustedNetworkIdentity && context.remoteHost() != null
                    && !context.remoteHost().isBlank()) {
                return;
            }
            throw new SecurityException("Gateway data plane requires Broker mTLS identity");
        }
        if (!peerIdentity.startsWith(identityPrefix)) {
            throw new SecurityException("Gateway data-plane peer is not a Broker");
        }
        String brokerId = peerIdentity.substring(identityPrefix.length());
        boolean committed = !brokerId.isBlank() && clusterState.get().brokers().stream()
                .anyMatch(node -> brokerId.equals(node.brokerId()));
        if (!committed) {
            throw new SecurityException("Gateway data-plane peer is not a committed Broker");
        }
    }
}
