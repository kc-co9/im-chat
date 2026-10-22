package com.co.kc.imchat.broker.infrastructure.interceptor;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import java.util.Map;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerPeerAdmissionInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.cluster.*;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerObservationAdmissionTest {
    private static final String PREFIX = "spiffe://im-chat/broker/";
    private final BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
    private final BrokerPeerAdmissionInterceptor interceptor = new BrokerPeerAdmissionInterceptor(
            "b", adapter, PREFIX, "cluster");

    @Test
    void boundCurrentVoterDelegatesWithoutRoutingLease() throws Exception {
        configure();
        Object result = interceptor.intercept(invocation(probe("a", "b", 1L, 7L)), ignored -> "probe");
        assertThat(result).isEqualTo("probe");
    }
    @Test
    void boundReportDelegatesWithoutAbsoluteDeadline() throws Exception {
        configure();
        BrokerHealthReportParams report = new BrokerHealthReportParams(
                "cluster", "a", "b", 1L, 7L, "late-report", List.of("a", "b"));
        Object result = interceptor.intercept(new BrokerRequestInvocation<>(
                BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT, report, null, principal()),
                invocation -> ((BrokerHealthReportParams) invocation.request()).brokerIds());
        assertThat(result).isEqualTo(List.of("a", "b"));
    }

    @Test
    void claimedCallerAndTargetRejectBeforeHandler() {
        configure();
        rejects(probe("forged", "b", 1L, 7L));
        rejects(probe("a", "other", 1L, 7L));
    }
    @Test
    void staleRevisionEpochAndWrongContextTypeRejectBeforeHandler() {
        configure();
        rejects(probe("a", "b", 2L, 7L));
        rejects(probe("a", "b", 1L, 8L));
        rejects(new Object());
    }
    @Test
    void emptyConsistentVoterResolutionFailsClosedBeforeHandler() {
        configure();
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("unknown:18120")), Set.of()));
        rejects(probe("a", "b", 1L, 7L));
    }
    @Test
    void wrongClusterAndNonVoterIdentityRejectBeforeHandler() {
        configure();
        rejects(new BrokerProbeParams("other", "a", "b", 1L, 7L, "nonce"));
        configureRoles(BrokerMembershipRole.LEARNER, BrokerMembershipRole.VOTER);
        rejects(probe("a", "b", 1L, 7L));
    }
    @Test
    void reportRequiresLocalVoterAndOnlyActiveTargets() {
        configure();
        BrokerHealthReportParams report = new BrokerHealthReportParams("cluster", "a", "b", 1L, 7L, "nonce", List.of("missing"));
        assertThatThrownBy(() -> interceptor.intercept(new BrokerRequestInvocation<>(
                BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT, report, null, principal()), ignored -> "unexpected"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("ACTIVE");
    }
    @Test
    void trustedNetworkPrincipalCannotBypassObservationVoterAdmission() {
        configure();
        AtomicBoolean delegated = new AtomicBoolean();
        BrokerRequestInvocation<?> request = new BrokerRequestInvocation<>(BrokerBoltOperation.PROBE_BROKER,
                probe("a", "b", 1L, 7L), null,
                new BrokerCallerPrincipal("trusted-network://host", BrokerCallerRole.TRUSTED_NETWORK));
        assertThatThrownBy(() -> interceptor.intercept(request, ignored -> {
            delegated.set(true);
            return null;
        })).isInstanceOf(SecurityException.class).hasMessageContaining("authenticated Broker");
        assertThat(delegated).isFalse();
    }

    @Test
    void learnerResponderCannotReturnVoterReport() {
        configure();
        configureRoles(BrokerMembershipRole.VOTER, BrokerMembershipRole.LEARNER);
        BrokerHealthReportParams report = new BrokerHealthReportParams("cluster", "a", "b", 1L, 7L, "nonce", List.of("a"));
        assertThatThrownBy(() -> interceptor.intercept(new BrokerRequestInvocation<>(
                BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT, report, null, principal()), ignored -> "unexpected"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("reporter");
    }

    @Test
    void revisionChangesDuringVoterResolutionDiscardRequest() {
        configure();
        BrokerCluster changed = BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(
                new BrokerClusterState(2L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                        List.of(new BrokerNode("b", "host-b:2", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE))),
                List.of(), null, 10);
        BrokerCluster initial = adapter.getClusterSnapshot();
        when(adapter.getClusterSnapshot()).thenReturn(initial, changed);
        rejects(probe("a", "b", 1L, 7L));
    }

    @Test
    void overlappingMembershipConstructionFailureNeverDelegatesAdmission() {
        configure();
        when(adapter.getConsensusMembership()).thenAnswer(ignored -> new ConsensusMembership(
                Set.of(new MembershipEndpoint("a:18120")), Set.of(new MembershipEndpoint("a:18120"))));
        AtomicBoolean delegated = new AtomicBoolean();
        assertThatThrownBy(() -> interceptor.intercept(invocation(probe("a", "b", 1L, 7L)), ignored -> {
            delegated.set(true);
            return null;
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("overlap");
        assertThat(delegated).isFalse();
    }

    private void configure() {
        configureRoles(BrokerMembershipRole.VOTER, BrokerMembershipRole.VOTER);
    }

    private void configureRoles(BrokerMembershipRole aRole, BrokerMembershipRole bRole) {
        BrokerClusterState state = new BrokerClusterState(1L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("a", "host-a:1", aRole, BrokerRoutingState.ACTIVE),
                        new BrokerNode("b", "host-b:2", bRole, BrokerRoutingState.ACTIVE)));
        BrokerCluster cluster = BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(state, List.of(), null, 10,
                Map.of("a", "a:18120", "b", "b:18120"));
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(
                aRole == bRole ? Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"))
                        : aRole == BrokerMembershipRole.VOTER ? Set.of(new MembershipEndpoint("a:18120"))
                        : Set.of(new MembershipEndpoint("b:18120")),
                aRole == bRole ? Set.of()
                        : aRole == BrokerMembershipRole.LEARNER ? Set.of(new MembershipEndpoint("a:18120"))
                        : Set.of(new MembershipEndpoint("b:18120"))));
    }
    private BrokerProbeParams probe(String caller, String target, Long revision, Long epoch) {
        return new BrokerProbeParams("cluster", caller, target, revision, epoch, "nonce");
    }
    private BrokerCallerPrincipal principal() {
        return new BrokerCallerPrincipal(PREFIX + "a", BrokerCallerRole.BROKER);
    }
    private BrokerRequestInvocation<?> invocation(Object request) {
        return new BrokerRequestInvocation<>(BrokerBoltOperation.PROBE_BROKER, request, null, principal());
    }
    private void rejects(Object request) {
        AtomicBoolean delegated = new AtomicBoolean();
        assertThatThrownBy(() -> interceptor.intercept(invocation(request), ignored -> {
            delegated.set(true);
            return null;
        })).isInstanceOf(SecurityException.class).hasMessageContaining("observation");
        assertThat(delegated).isFalse();
    }
}
