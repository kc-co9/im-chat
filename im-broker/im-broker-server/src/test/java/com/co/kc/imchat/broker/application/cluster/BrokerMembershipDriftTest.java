package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.*;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrokerMembershipDriftTest {
    enum Evidence { CONSISTENT, DRIFT, UNAVAILABLE, TERM_CHANGED, REVISION_CHANGED,
        DRIFT_TERM_CHANGED, DRIFT_REVISION_CHANGED, REJECTED_CAPACITY }

    @ParameterizedTest
    @EnumSource(Evidence.class)
    void eachNewRequestReadsFreshConfigurationAndRevalidatesBeforeBegin(Evidence evidence) throws Exception {
        BrokerCluster cluster = cluster(0L);
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.getId()).thenReturn(new BrokerId("a"));
        when(local.isLeader()).thenReturn(true);
        when(local.getLeaderTerm()).thenReturn(Optional.of(new LeaderTerm(7L)));
        when(adapter.getClusterSnapshot()).thenAnswer(call -> cluster.snapshot());
        AtomicInteger reads = new AtomicInteger();
        when(adapter.readLeaderMembershipConfiguration()).thenAnswer(call -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("t5-drift-worker");
            reads.incrementAndGet();
            if (evidence == Evidence.UNAVAILABLE) { throw new IllegalStateException("diagnostic unavailable"); }
            if (evidence == Evidence.TERM_CHANGED || evidence == Evidence.DRIFT_TERM_CHANGED) {
                when(local.getLeaderTerm()).thenReturn(Optional.of(new LeaderTerm(8L)));
            }
            if (evidence == Evidence.REVISION_CHANGED || evidence == Evidence.DRIFT_REVISION_CHANGED) {
                when(adapter.getClusterSnapshot()).thenAnswer(ignored -> cluster(1L));
            }
            return new ConsensusMembership(evidence == Evidence.DRIFT
                    || evidence == Evidence.DRIFT_TERM_CHANGED || evidence == Evidence.DRIFT_REVISION_CHANGED
                    || (evidence == Evidence.REJECTED_CAPACITY && reads.get() <= 4096)
                    ? Set.of(new MembershipEndpoint("a:18120"))
                    : Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")), Set.of());
        });
        when(adapter.beginOperation(any())).thenReturn(CompletableFuture.completedFuture(
                BrokerClusterConsensusResult.unavailable(null, "controlled stop after admission")));
        ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), Thread.ofPlatform().daemon().name("t5-drift-worker").factory(),
                new ThreadPoolExecutor.AbortPolicy());
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(7L, 1L);
            com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics metrics =
                    new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics();
            io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            try {
                metrics.bindTo(registry);
                BrokerMembershipAppService service = new BrokerMembershipAppService(adapter, mock(BrokerPeerAdapter.class),
                        new MinimumVoterCount(1), executor, metrics,
                        new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
                int attempts = evidence == Evidence.REJECTED_CAPACITY ? 4097 : 2;
                for (int attempt = 0; attempt < attempts; attempt++) {
                    when(local.getLeaderTerm()).thenReturn(Optional.of(new LeaderTerm(7L)));
                    when(adapter.getClusterSnapshot()).thenAnswer(call -> cluster.snapshot());
                    service.demoteVoter(new DemoteVoterCommand("drift-" + attempt, "b", 0L, "b:18120"))
                            .toCompletableFuture().get(5, TimeUnit.SECONDS);
                }
                assertThat(reads.get()).isEqualTo(attempts);
                verify(adapter, times(evidence == Evidence.CONSISTENT ? 2
                        : evidence == Evidence.REJECTED_CAPACITY ? 1 : 0)).beginOperation(any());
                if (evidence == Evidence.DRIFT_TERM_CHANGED || evidence == Evidence.DRIFT_REVISION_CHANGED) {
                    assertThat(registry.get("im.broker.membership.configuration.drift").gauge().value()).isZero();
                    assertThat(registry.get("im.broker.membership.diagnostic.unavailable").gauge().value()).isEqualTo(1D);
                }
                verify(adapter, never()).changeMembershipConfiguration(any());
                assertThat(cluster.findObservers()).containsExactlyInAnyOrder(new BrokerId("a"), new BrokerId("b"));
            } finally {
                registry.close();
            }
        }
    }

    private BrokerCluster cluster(long revision) {
        return BrokerCluster.builder().revision(new ClusterRevision(revision)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                        new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.READY)))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"),
                        new BrokerId("b"), new MembershipEndpoint("b:18120"))).build();
    }
}
