package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.common.constant.PeerIdentityConstants;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从受信 Bolt 上下文解析工作负载角色，并按 operation profile 做第一层准入。
 */
public final class BrokerCallerIdentityInterceptor implements BrokerRequestInterceptor {
    private static final Map<BrokerCallerRole, String> IDENTITY_PREFIXES = Map.of(
            BrokerCallerRole.BROKER, PeerIdentityConstants.BROKER_URI_PREFIX,
            BrokerCallerRole.GATEWAY, PeerIdentityConstants.GATEWAY_URI_PREFIX,
            BrokerCallerRole.MESSAGE, PeerIdentityConstants.MESSAGE_URI_PREFIX,
            BrokerCallerRole.ACCOUNT, PeerIdentityConstants.ACCOUNT_URI_PREFIX,
            BrokerCallerRole.OPS, PeerIdentityConstants.OPS_URI_PREFIX);
    private final Set<BrokerAdmissionType> trustedNetworkProfiles;

    public BrokerCallerIdentityInterceptor(Set<BrokerAdmissionType> trustedNetworkProfiles) {
        if (trustedNetworkProfiles == null) {
            throw new IllegalArgumentException("trusted network profiles must not be null");
        }
        this.trustedNetworkProfiles = Set.copyOf(trustedNetworkProfiles);
    }

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation, BrokerRequestChain chain) throws Exception {
        BrokerCallerPrincipal principal = resolve(invocation.context(), invocation.operation().admissionType());
        if (!allowedRoles(invocation.operation().admissionType()).contains(principal.callerRole())
                && principal.callerRole() != BrokerCallerRole.TRUSTED_NETWORK) {
            throw new SecurityException("workload identity is not allowed for Broker operation");
        }
        return chain.proceed(invocation.withPrincipal(principal));
    }

    private BrokerCallerPrincipal resolve(BoltRequestContext context,
            BrokerAdmissionType admissionType) {
        if (context == null) {
            throw new SecurityException("Broker request transport identity is required");
        }
        if (context.tlsVerified()) {
            List<BrokerCallerPrincipal> matching = IDENTITY_PREFIXES.entrySet().stream()
                    .flatMap(entry -> context.peerUriIdentities().stream()
                            .filter(uri -> uri.startsWith(entry.getValue()) && uri.length() > entry.getValue().length())
                            .map(uri -> new BrokerCallerPrincipal(uri, entry.getKey())))
                    .toList();
            if (matching.size() != 1) {
                throw new SecurityException("Broker operation/observation requires unique allowed leaf URI identity");
            }
            return matching.getFirst();
        }
        if (admissionType == BrokerAdmissionType.BROKER_OBSERVATION) {
            throw new SecurityException("Broker observation requires verified TLS URI identity");
        }
        if (context.developmentPlaintext() && trustedNetworkProfiles.contains(admissionType)
                && context.remoteHost() != null && !context.remoteHost().isBlank()) {
            return new BrokerCallerPrincipal("trusted-network://" + context.remoteHost(),
                    BrokerCallerRole.TRUSTED_NETWORK);
        }
        throw new SecurityException("Broker request transport identity is required");
    }

    private Set<BrokerCallerRole> allowedRoles(BrokerAdmissionType profile) {
        return switch (profile) {
            case CLUSTER_READ -> EnumSet.of(BrokerCallerRole.GATEWAY, BrokerCallerRole.MESSAGE,
                    BrokerCallerRole.ACCOUNT, BrokerCallerRole.BROKER, BrokerCallerRole.OPS);
            case BROKER_PEER, BROKER_CONTROL, BROKER_DATA_PLANE, BROKER_OBSERVATION ->
                EnumSet.of(BrokerCallerRole.BROKER);
            case GATEWAY_BOOTSTRAP, GATEWAY_ROUTE -> EnumSet.of(BrokerCallerRole.GATEWAY);
            case SERVICE_DATA_PLANE ->
                EnumSet.of(BrokerCallerRole.GATEWAY, BrokerCallerRole.MESSAGE, BrokerCallerRole.ACCOUNT);
        };
    }
}
