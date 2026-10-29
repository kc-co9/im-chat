package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import static com.co.kc.imchat.common.constant.PeerIdentityConstants.BROKER_URI_PREFIX;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.access.BrokerObservationRequest;
import com.co.kc.imchat.broker.sdk.access.BrokerPeerRequest;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.plugin.gossip.model.param.GossipPeerRequest;
import java.util.Set;
import com.co.kc.imchat.broker.model.properties.BrokerGossipSettings;
import java.util.stream.Collectors;

/** Broker membership、Gossip 与 Lease/Fence 控制入口的 peer 身份边界。 */
public final class BrokerPeerAdmissionInterceptor implements BrokerRequestInterceptor {
    private final String localBrokerId;
    private final BrokerRaftAdapter brokerRaftAdapter;
    private final String clusterId;
    private final BrokerGossipSettings gossipProperties;

    public BrokerPeerAdmissionInterceptor(
            String localBrokerId,
            BrokerRaftAdapter stateRepository,
            String clusterId,
            BrokerGossipSettings gossipProperties) {
        if (localBrokerId == null || localBrokerId.isBlank()
                || stateRepository == null
                || clusterId == null || clusterId.isBlank() || gossipProperties == null) {
            throw new IllegalArgumentException("invalid Broker peer access configuration");
        }
        this.localBrokerId = localBrokerId;
        this.brokerRaftAdapter = stateRepository;
        this.clusterId = clusterId;
        this.gossipProperties = gossipProperties;
    }

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation,
            BrokerRequestChain chain) throws Exception {
        BrokerAdmissionType profile = invocation.operation().admissionType();
        if (profile == BrokerAdmissionType.BROKER_OBSERVATION) {
            validateObservation(invocation);
            return chain.proceed(invocation);
        }
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

    private void validateObservation(BrokerRequestInvocation<?> invocation) {
        BrokerCallerPrincipal principal = invocation.principal();
        if (principal == null || principal.callerRole() != BrokerCallerRole.BROKER) {
            throw new SecurityException("Broker observation requires authenticated Broker");
        }
        boolean typedRequest = switch (invocation.operation()) {
            case PROBE_BROKER -> invocation.request() instanceof BrokerProbeParams;
            case FETCH_BROKER_HEALTH_REPORT -> invocation.request() instanceof BrokerHealthReportParams;
            default -> false;
        };
        if (!typedRequest) {
            throw new SecurityException("Broker observation request capability is missing");
        }
        BrokerObservationRequest request = (BrokerObservationRequest) invocation.request();
        String caller = callerBrokerId(principal);
        if (!caller.equals(request.callerBrokerId()) || !localBrokerId.equals(request.receiverBrokerId())) {
            throw new SecurityException("Broker observation caller or target does not match");
        }
        if (!clusterId.equals(request.clusterId())) {
            throw new SecurityException("Broker observation cluster does not match");
        }
        BrokerCluster cluster = brokerRaftAdapter.getClusterSnapshot();
        if (!request.revision().equals(cluster.revision().value())
                || !request.epoch().equals(cluster.epoch().value())) {
            throw new SecurityException("Broker observation context does not match");
        }
        ConsensusMembership membership = brokerRaftAdapter.getConsensusMembership();
        Set<BrokerId> currentVoters = brokerRaftAdapter.getClusterSnapshot().findConsistentVoters(membership);
        BrokerCluster currentCluster = brokerRaftAdapter.getClusterSnapshot();
        if (!cluster.revision().equals(currentCluster.revision()) || !cluster.epoch().equals(currentCluster.epoch())) {
            throw new SecurityException("Broker observation context changed during admission");
        }
        if (!currentVoters.contains(new BrokerId(caller))) {
            throw new SecurityException("Broker observation caller is not a current voter");
        }
        if (request instanceof BrokerHealthReportParams report) {
            Set<String> targets = cluster.activeBrokers().stream()
                    .map(node -> node.id().value()).collect(Collectors.toSet());
            if (!targets.containsAll(report.brokerIds())) {
                throw new SecurityException("Broker observation brokerIds are not committed ACTIVE Brokers");
            }
            if (!currentVoters.contains(new BrokerId(localBrokerId))) {
                throw new SecurityException("Broker observation reporter is not a current voter");
            }
        }
    }

    private void validatePeer(BrokerRequestInvocation<?> invocation, String callerBrokerId) {
        boolean gossip = invocation.operation() == BrokerBoltOperation.GOSSIP_DIGEST
                || invocation.operation() == BrokerBoltOperation.GOSSIP_DELTA;
        if (gossip) {
            validateGossip(invocation, callerBrokerId);
            return;
        }
        String claimedBrokerId;
        if (invocation.request() instanceof BrokerPeerRequest request) {
            claimedBrokerId = request.brokerId();
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

    private void validateGossip(BrokerRequestInvocation<?> invocation, String callerBrokerId) {
        if (!(invocation.request() instanceof GossipPeerRequest request)) {
            throw new SecurityException("Broker Gossip request capability is missing");
        }
        BrokerCallerPrincipal principal = invocation.principal();
        // DEV 只沿已显式准入的 marker/context 放行普通 Gossip，不生成稳定身份或观察票。
        if (principal.callerRole() == BrokerCallerRole.TRUSTED_NETWORK
                && invocation.context() != null && invocation.context().developmentPlaintext()
                && !invocation.context().tlsVerified()) {
            return;
        }
        boolean bound = gossipProperties.trustedPeers().stream()
                .anyMatch(peer -> peer.observationId().equals(request.nodeId())
                        && peer.target().expectedPeerIdentity().equals(principal.callerIdentity()));
        if (!bound) {
            throw new SecurityException("Broker Gossip observation does not match trusted full identity pair");
        }
        if (!isCommitted(callerBrokerId)) {
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
        if (!principal.callerIdentity().startsWith(BROKER_URI_PREFIX)) {
            throw new SecurityException("Broker peer identity prefix does not match");
        }
        String brokerId = principal.callerIdentity().substring(BROKER_URI_PREFIX.length());
        if (brokerId.isBlank()) {
            throw new SecurityException("Broker peer identity is incomplete");
        }
        return brokerId;
    }

    private boolean isCommitted(String brokerId) {
        return brokerRaftAdapter.getCommittedState().brokers().stream()
                .anyMatch(node -> node.brokerId().equals(brokerId));
    }
}
