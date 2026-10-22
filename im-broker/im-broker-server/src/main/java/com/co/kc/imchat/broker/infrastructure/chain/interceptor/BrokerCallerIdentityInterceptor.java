package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从受信 Bolt 上下文解析工作负载角色，并按 operation profile 做第一层准入。
 */
public final class BrokerCallerIdentityInterceptor implements BrokerRequestInterceptor {
    private final Map<BrokerCallerRole, String> identityPrefixes;
    private final Set<BrokerAdmissionType> trustedNetworkProfiles;

    public BrokerCallerIdentityInterceptor(Map<BrokerCallerRole, String> identityPrefixes,
                                           Set<BrokerAdmissionType> trustedNetworkProfiles) {
        if (identityPrefixes == null || identityPrefixes.isEmpty()
                || identityPrefixes.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank())
                || trustedNetworkProfiles == null) {
            throw new IllegalArgumentException("workload identity prefixes must not be empty");
        }
        this.identityPrefixes = Map.copyOf(identityPrefixes);
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
        if (admissionType == BrokerAdmissionType.BROKER_OBSERVATION) {
            String prefix = identityPrefixes.get(BrokerCallerRole.BROKER);
            if (!context.tlsVerified() || prefix == null) {
                throw new SecurityException("Broker observation requires verified TLS URI identity");
            }
            List<String> identities = context.peerUriIdentities().stream()
                    .filter(uri -> uri.startsWith(prefix)).toList();
            if (identities.size() != 1 || identities.getFirst().substring(prefix.length()).isBlank()) {
                throw new SecurityException("Broker observation requires unique Broker URI identity");
            }
            return new BrokerCallerPrincipal(identities.getFirst(), BrokerCallerRole.BROKER);
        }
        String identity = context.peerIdentity();
        if (identity != null && !identity.isBlank()) {
            return identityPrefixes.entrySet().stream()
                    .filter(entry -> identity.startsWith(entry.getValue()))
                    .map(entry -> new BrokerCallerPrincipal(identity, entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new SecurityException("unknown Broker workload identity"));
        }
        if (trustedNetworkProfiles.contains(admissionType) && context.remoteHost() != null
                && !context.remoteHost().isBlank()) {
            return new BrokerCallerPrincipal(
                    "trusted-network://" + context.remoteHost(),
                    BrokerCallerRole.TRUSTED_NETWORK);
        }
        throw new SecurityException("Broker request transport identity is required");
    }

    private Set<BrokerCallerRole> allowedRoles(BrokerAdmissionType profile) {
        return switch (profile) {
            case CLUSTER_READ -> EnumSet.of(BrokerCallerRole.GATEWAY,
                    BrokerCallerRole.MESSAGE, BrokerCallerRole.ACCOUNT,
                    BrokerCallerRole.BROKER, BrokerCallerRole.OPS);
            case BROKER_PEER, BROKER_CONTROL, BROKER_DATA_PLANE, BROKER_OBSERVATION -> EnumSet.of(BrokerCallerRole.BROKER);
            case GATEWAY_BOOTSTRAP, GATEWAY_ROUTE -> EnumSet.of(BrokerCallerRole.GATEWAY);
            case SERVICE_DATA_PLANE -> EnumSet.of(BrokerCallerRole.GATEWAY,
                    BrokerCallerRole.MESSAGE, BrokerCallerRole.ACCOUNT);
        };
    }
}
