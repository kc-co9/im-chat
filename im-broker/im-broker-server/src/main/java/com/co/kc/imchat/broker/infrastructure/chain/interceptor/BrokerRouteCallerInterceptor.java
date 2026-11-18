package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import static com.co.kc.imchat.common.constant.PeerIdentityConstants.BROKER_URI_PREFIX;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;

/** 将 Broker 数据面请求声明绑定到传输身份和 committed member。 */
public final class BrokerRouteCallerInterceptor implements BrokerRequestInterceptor {
    private final BrokerRaftAdapter brokerRaftAdapter;
    private final boolean allowTrustedNetworkIdentity;

    public BrokerRouteCallerInterceptor(BrokerRaftAdapter brokerRaftAdapter,
                                        boolean allowTrustedNetworkIdentity) {
        this.brokerRaftAdapter = brokerRaftAdapter;
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
                && (BROKER_URI_PREFIX + requesterBrokerId).equals(principal.callerIdentity());
        boolean trustedNetwork = allowTrustedNetworkIdentity
                && principal.callerRole() == BrokerCallerRole.TRUSTED_NETWORK;
        if (!trustedTls && !trustedNetwork) {
            throw new SecurityException("Broker data-plane identity mismatch");
        }
        boolean committed = brokerRaftAdapter.getCommittedState().brokers().stream()
                .anyMatch(node -> node.brokerId().equals(requesterBrokerId));
        if (!committed) {
            throw new SecurityException("Broker data-plane requester is not committed");
        }
        return chain.proceed(invocation);
    }
}
