package com.co.kc.imchat.broker.infrastructure.chain.interceptor;


import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerPeerRequest;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipPeerRequest;

/** Broker membership、Gossip 与 Lease/Fence 控制入口的 peer 身份边界。 */
public final class BrokerPeerAdmissionInterceptor implements BrokerRequestInterceptor {
    private final String localBrokerId;
    private final BrokerClusterConsensus stateRepository;
    private final String identityPrefix;

    public BrokerPeerAdmissionInterceptor(String localBrokerId,
                                       BrokerClusterConsensus stateRepository,
                                       String identityPrefix) {
        if (localBrokerId == null || localBrokerId.isBlank()
                || stateRepository == null || identityPrefix == null || identityPrefix.isBlank()) {
            throw new IllegalArgumentException("invalid Broker peer access configuration");
        }
        this.localBrokerId = localBrokerId;
        this.stateRepository = stateRepository;
        this.identityPrefix = identityPrefix;
    }

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation,
                            BrokerRequestChain chain) throws Exception {
        BrokerAdmissionType profile = invocation.operation().admissionType();
        if (profile != BrokerAdmissionType.BROKER_PEER
                && profile != BrokerAdmissionType.BROKER_CONTROL) {
            return chain.proceed(invocation);
        }
        BrokerCallerPrincipal principal = invocation.principal();
        if (principal == null
                || (principal.callerRole() != BrokerCallerRole.BROKER
                && principal.callerRole() != BrokerCallerRole.TRUSTED_NETWORK)) {
            throw new SecurityException("Broker peer identity is missing");
        }
        String callerBrokerId = callerBrokerId(principal);
        if (profile == BrokerAdmissionType.BROKER_CONTROL) {
            validateControl(invocation, callerBrokerId);
        } else {
            validatePeer(invocation, callerBrokerId);
        }
        return chain.proceed(invocation);
    }

    private void validatePeer(BrokerRequestInvocation<?> invocation, String callerBrokerId) {
        String claimedBrokerId;
        if (invocation.request() instanceof BrokerPeerRequest request) {
            claimedBrokerId = request.brokerId();
        } else if (invocation.request() instanceof GossipPeerRequest request) {
            claimedBrokerId = request.nodeId();
        } else {
            throw new SecurityException("Broker peer request capability is missing");
        }
        if (!callerBrokerId.equals(claimedBrokerId)) {
            throw new SecurityException("Broker peer identity does not match request");
        }
        if (invocation.operation() != BrokerBoltOperation.REGISTER_BROKER
                && !isCommitted(callerBrokerId)) {
            throw new SecurityException("Broker peer is not a committed member");
        }
    }

    private void validateControl(BrokerRequestInvocation<?> invocation, String callerBrokerId) {
        if (!isCommitted(callerBrokerId)) {
            throw new SecurityException("Broker control caller is not a committed member");
        }
        Object request = invocation.request();
        if (request instanceof RouteLeaseInstallParams install) {
            if (!localBrokerId.equals(install.brokerId())) {
                throw new SecurityException("Lease holder does not match target Broker");
            }
        }
        boolean validControlRequest = switch (invocation.operation()) {
            case FETCH_QUORUM_READ_INDEX -> request instanceof QuorumReadIndexParams;
            case PREPARE_ROUTE_LEASE -> request instanceof RouteLeasePrepareParams;
            case INSTALL_ROUTE_LEASE -> request instanceof RouteLeaseInstallParams;
            case FENCE_ROUTING_EPOCH -> request instanceof RoutingFenceParams;
            default -> false;
        };
        if (!validControlRequest) {
            throw new SecurityException("Broker control request capability is missing");
        }
    }

    private String callerBrokerId(BrokerCallerPrincipal principal) {
        if (principal.callerRole() == BrokerCallerRole.TRUSTED_NETWORK) {
            return localBrokerId;
        }
        if (!principal.callerIdentity().startsWith(identityPrefix)) {
            throw new SecurityException("Broker peer identity prefix does not match");
        }
        String brokerId = principal.callerIdentity().substring(identityPrefix.length());
        if (brokerId.isBlank()) {
            throw new SecurityException("Broker peer identity is incomplete");
        }
        return brokerId;
    }

    private boolean isCommitted(String brokerId) {
        return stateRepository.committedState().brokers().stream()
                .anyMatch(node -> node.brokerId().equals(brokerId));
    }
}
