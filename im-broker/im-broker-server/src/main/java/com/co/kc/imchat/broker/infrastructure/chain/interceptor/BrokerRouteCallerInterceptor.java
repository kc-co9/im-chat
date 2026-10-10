package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;

/** 将 Broker 数据面请求声明绑定到传输身份和 committed member。 */
public final class BrokerRouteCallerInterceptor implements BrokerRequestInterceptor {
    private final BrokerClusterConsensus stateRepository;
    private final String identityPrefix;
    private final boolean allowTrustedNetworkIdentity;

    public BrokerRouteCallerInterceptor(BrokerClusterConsensus stateRepository,
                                            String identityPrefix,
                                            boolean allowTrustedNetworkIdentity) {
        if (stateRepository == null || identityPrefix == null || identityPrefix.isBlank()) {
            throw new IllegalArgumentException("invalid Broker data-plane access configuration");
        }
        this.stateRepository = stateRepository;
        this.identityPrefix = identityPrefix;
        this.allowTrustedNetworkIdentity = allowTrustedNetworkIdentity;
    }

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation,
                            BrokerRequestChain chain) throws Exception {
        if (invocation.operation().admissionType() != BrokerAdmissionType.BROKER_DATA_PLANE) {
            return chain.proceed(invocation);
        }
        BrokerCallerPrincipal principal = invocation.principal();
        if (!(invocation.request() instanceof BrokerDataPlaneRequest request)
                || principal == null) {
            throw new SecurityException("Broker data-plane identity is missing");
        }
        String requesterBrokerId = request.requesterBrokerId();
        boolean trustedTls = principal.callerRole() == BrokerCallerRole.BROKER
                && (identityPrefix + requesterBrokerId).equals(principal.callerIdentity());
        boolean trustedNetwork = allowTrustedNetworkIdentity
                && principal.callerRole() == BrokerCallerRole.TRUSTED_NETWORK;
        if (!trustedTls && !trustedNetwork) {
            throw new SecurityException("Broker data-plane identity mismatch");
        }
        boolean committed = stateRepository.committedState().brokers().stream()
                .anyMatch(node -> node.brokerId().equals(requesterBrokerId));
        if (!committed) {
            throw new SecurityException("Broker data-plane requester is not committed");
        }
        return chain.proceed(invocation);
    }
}
