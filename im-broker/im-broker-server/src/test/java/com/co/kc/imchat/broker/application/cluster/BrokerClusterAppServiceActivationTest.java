package com.co.kc.imchat.broker.application.cluster;

import java.time.Duration;



import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerClusterRaftTransformer;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;

import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.support.BrokerClusterCommandFixture;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import java.util.function.LongFunction;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.adapter.cluster.raft.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerClusterAppServiceActivationTest {

    @Test
    void authorizesAllActiveHoldersOnlyAfterNewEpochIsCommitted() {
        ImmediateRepository repository = committedRepository();
        List<String> activated = new ArrayList<>();
        BrokerClusterAppService service = service(repository,
                (broker, state, operationId) -> {
                    assertThat(repository.findOperation(new ClusterOperationId(operationId)).orElseThrow().phase())
                            .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
                    assertThat(state.epoch().value()).isEqualTo(1L);
                    activated.add(broker.id().value());
                    return CompletableFuture.completedFuture(true);
                });

        ActivateBrokerResult result = service.activate(new ActivateBrokerCommand("operation-1", "broker-b", 0L))
                .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo("DUPLICATE");
        assertThat(activated).containsExactly("broker-a", "broker-b");
        assertThat(repository.findOperation(new ClusterOperationId("operation-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
    }

    @Test
    void remainsAuthorizingWhenAnyActiveHolderHasNotAcceptedLease() {
        ImmediateRepository repository = committedRepository();
        BrokerClusterAppService service = service(repository,
                (broker, state, operationId) -> CompletableFuture.completedFuture(
                        broker.id().value().equals("broker-a")));

        ActivateBrokerResult result = service.activate(new ActivateBrokerCommand("operation-1", "broker-b", 0L))
                .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo("DUPLICATE");
        assertThat(result.acceptedEpoch()).isZero();
        assertThat(result.targetEpoch()).isEqualTo(1L);
        assertThat(repository.findOperation(new ClusterOperationId("operation-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
    }

    @Test
    void failedInstallationStillWaitsForRemainingBrokersBeforeCompletingRound() {
        ImmediateRepository repository = committedRepository();
        CompletableFuture<Boolean> pendingInstallation = new CompletableFuture<>();
        List<String> requestedBrokers = new ArrayList<>();
        BrokerClusterAppService service = service(repository, (broker, state, operationId) -> {
            requestedBrokers.add(broker.id().value());
            if (broker.id().value().equals("broker-a")) {
                return CompletableFuture.completedFuture(false);
            }
            return pendingInstallation;
        });

        CompletableFuture<ActivateBrokerResult> activation = service.activate(
                new ActivateBrokerCommand("operation-1", "broker-b", 0L)).toCompletableFuture();

        assertThat(requestedBrokers).containsExactly("broker-a", "broker-b");
        assertThat(activation).isNotDone();
        assertThat(repository.findOperation(new ClusterOperationId("operation-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);

        pendingInstallation.complete(true);
        assertThat(activation.join().status()).isEqualTo("DUPLICATE");
        assertThat(repository.findOperation(new ClusterOperationId("operation-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH);
    }

    private BrokerClusterAppService service(
            ImmediateRepository repository,
            LeaseActivation routeLeaseActivator) {
        BrokerClusterAdapter brokerClusterAdapter =
                mock(BrokerClusterAdapter.class);
        org.mockito.Mockito.doCallRealMethod().when(brokerClusterAdapter).saveRecoveryEvidence(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(brokerClusterAdapter).completeDrain(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(brokerClusterAdapter).forceDrain(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(brokerClusterAdapter).startEpochAuthorization(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(brokerClusterAdapter).completeEpochAuthorization(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(brokerClusterAdapter).verifyRouteRecovery(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(brokerClusterAdapter).completeRouteRecovery(any(), any());
        when(brokerClusterAdapter.committedState()).thenAnswer(
                ignored -> repository.committedState());
        when(brokerClusterAdapter.getCluster()).thenAnswer(
                ignored -> repository.getCluster());
        when(brokerClusterAdapter.findOperation(any(ClusterOperationId.class))).thenAnswer(
                invocation -> repository.findOperation(invocation.getArgument(0)));
        when(brokerClusterAdapter.registerOperation(any())).thenAnswer(invocation ->
                repository.submitChange(new BeginBrokerClusterOperationCommand(
                        BrokerClusterRaftTransformer.INSTANCE.proposalFrom(invocation.getArgument(0)))));
        when(brokerClusterAdapter.submitChange(any())).thenAnswer(
                invocation -> repository.submitChange(invocation.getArgument(0)));
        BrokerClusterAppService service = spy(new BrokerClusterAppService(brokerClusterAdapter,
                mock(GatewayRegistry.class),
                Duration.ofNanos(1_000L), Duration.ofSeconds(1), mock(java.util.concurrent.ScheduledExecutorService.class), java.time.Duration.ofSeconds(10)));
        org.mockito.Mockito.doAnswer(invocation -> {
            com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand command = invocation.getArgument(0);
            BrokerCluster cluster = repository.getCluster();
            return routeLeaseActivator.activate(cluster.findNode(
                    new com.co.kc.imchat.broker.domain.cluster.model.BrokerId(command.brokerId())).orElseThrow(),
                    cluster, command.operationId());
        })
                .when(service).activateLease(any());
        return service;
    }

    private ImmediateRepository committedRepository() {
        BrokerNode brokerA = node("broker-a", BrokerRoutingState.ACTIVE);
        BrokerNode brokerB = node("broker-b", BrokerRoutingState.READY);
        ImmediateRepository repository = new ImmediateRepository(new BrokerClusterState(0L, 0L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(brokerA, brokerB)));
        BrokerClusterConsensusResult result = BrokerClusterCommandFixture.applyTopologyChange(
                repository, BrokerClusterCommandFixture.topology(
                "operation-1", new ActivateBrokerCommand("operation-1", "broker-b", 0L).digest().value(),
                0L, "ACTIVATE_BROKER", "broker-b",
                List.of(brokerA, node("broker-b", BrokerRoutingState.ACTIVE)), 1_000L));
        assertThat(result.operation().phase())
                .isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);
        return repository;
    }

    private BrokerNode node(String brokerId, BrokerRoutingState state) {
        return new BrokerNode(brokerId, brokerId + ".example:18020",
                BrokerMembershipRole.VOTER, state);
    }

    @FunctionalInterface
    private interface LeaseActivation {
        CompletionStage<Boolean> activate(com.co.kc.imchat.broker.domain.cluster.model.BrokerNode broker,
                BrokerCluster cluster, String operationId);
    }

    private static final class ImmediateRepository
            implements BrokerClusterConsensusStub {

        private final RaftBrokerClusterStateMachine stateMachine;

        private ImmediateRepository(BrokerClusterState initialState) {
            stateMachine = new RaftBrokerClusterStateMachine("cluster-a", initialState);
        }

        @Override
        public BrokerCluster getCluster() {
            return stateMachine.getCluster();
        }

        @Override
        public BrokerClusterState committedState() {
            return stateMachine.committedState();
        }

        @Override
        public java.util.Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return stateMachine.findOperation(operationId);
        }

        @Override
        public java.util.concurrent.CompletionStage<BrokerClusterConsensusResult> submitChange(
                BrokerClusterConsensusCommand command) {
            return CompletableFuture.completedFuture(stateMachine.apply(command));
        }
    }
}
