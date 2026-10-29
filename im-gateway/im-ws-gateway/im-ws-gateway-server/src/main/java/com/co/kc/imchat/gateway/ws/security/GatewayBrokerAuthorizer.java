package com.co.kc.imchat.gateway.ws.security;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;

import static com.co.kc.imchat.common.constant.PeerIdentityConstants.BROKER_URI_PREFIX;

import java.util.List;
import java.util.function.Supplier;

/**
 * 验证调用 Gateway 数据面的 Broker TLS workload identity。
 */
public final class GatewayBrokerAuthorizer {
    private final Supplier<BrokerClusterState> clusterState;
    private final boolean allowTrustedNetworkIdentity;

    public GatewayBrokerAuthorizer(Supplier<BrokerClusterState> clusterState,
                                   boolean allowTrustedNetworkIdentity) {
        if (clusterState == null) {
            throw new IllegalArgumentException("invalid Gateway Broker authorization");
        }
        this.clusterState = clusterState;
        this.allowTrustedNetworkIdentity = allowTrustedNetworkIdentity;
    }

    public void authorize(BoltRequestContext context) {
        if (context == null) {
            throw new SecurityException("Gateway data plane requires Broker transport identity");
        }
        if (!context.tlsVerified()) {
            if (allowTrustedNetworkIdentity && context.developmentPlaintext()) {
                return;
            }
            throw new SecurityException("Gateway data plane requires Broker mTLS identity");
        }
        List<String> identities = context.peerUriIdentities().stream()
                .filter(uri -> uri.startsWith(BROKER_URI_PREFIX) && uri.length() > BROKER_URI_PREFIX.length()).toList();
        if (identities.size() != 1) {
            throw new SecurityException("Gateway requires unique Broker leaf URI identity");
        }
        String peerIdentity = identities.getFirst();
        String brokerId = peerIdentity.substring(BROKER_URI_PREFIX.length());
        boolean committed = !brokerId.isBlank() && clusterState.get().brokers().stream()
                .anyMatch(node -> brokerId.equals(node.brokerId()));
        if (!committed) {
            throw new SecurityException("Gateway data-plane peer is not a committed Broker");
        }
    }
}
