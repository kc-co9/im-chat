package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRemovalIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerJoinIntent;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.cluster.model.VoterDemotionIntent;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerManagementCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PromoteLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BrokerClusterAppServiceChangeTest {

    @ParameterizedTest
    @ValueSource(strings = {"activate", "deactivate", "drain", "force"})
    void staleRoutingRequestReturnsConflictWithoutSideEffects(String action) {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), ready("broker-b"));
        BrokerClusterAppService service = routing(fixture, new InMemoryGatewayRegistry(),
                (brokerId, fence) -> { throw new AssertionError("must not fence"); });
        String status = switch (action) {
            case "activate" -> service.activate(
                    BrokerManagementHttpTransformer.INSTANCE.activateCommand(action, "broker-b", 99L))
                    .toCompletableFuture().join().status();
            case "deactivate" -> service.deactivate(
                    BrokerManagementHttpTransformer.INSTANCE.deactivateCommand(action, "broker-b", 99L))
                    .toCompletableFuture().join().status();
            case "drain" -> service.drain(
                    BrokerManagementHttpTransformer.INSTANCE.drainCommand(action, "broker-b", 99L))
                    .toCompletableFuture().join().status();
            case "force" -> service.forceDeactivate(
                    BrokerManagementHttpTransformer.INSTANCE.forceDeactivateCommand(action, "broker-b", 99L, "approved"))
                    .toCompletableFuture().join().status();
            default -> throw new IllegalArgumentException(action);
        };
        assertThat(status).isEqualTo("CONFLICT");
        assertThat(fixture.stateMachine.getCluster().operations()).isEmpty();
        assertThat(fixture.stateMachine.findRoutingFence()).isEmpty();
        verify(fixture.adapter, never()).beginOperation(any());
    }

    @Test
    void activationAdmissionFailureReturnsRejectedWithoutSideEffects() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerClusterAppService service = routing(fixture, new InMemoryGatewayRegistry(),
                (brokerId, fence) -> { throw new AssertionError("must not fence"); });
        assertThat(service.activate(
                BrokerManagementHttpTransformer.INSTANCE.activateCommand("activate", "broker-b", 0L))
                .toCompletableFuture().join().status()).isEqualTo("REJECTED");
        verify(fixture.adapter, never()).beginOperation(any());
        assertThat(fixture.stateMachine.findRoutingFence()).isEmpty();
    }

    @Test
    void activationCommitFailureDoesNotAuthorizeUncommittedEpoch() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), ready("broker-b"));
        BrokerClusterConsensusResult unavailable = new BrokerClusterConsensusResult(Status.UNAVAILABLE, null, null, "commit unavailable");
        doReturn(CompletableFuture.completedFuture(unavailable)).when(fixture.adapter).commitOperation(any());
        BrokerClusterAppService service = routing(fixture, new InMemoryGatewayRegistry(),
                (brokerId, fence) -> CompletableFuture.completedFuture(ack(brokerId, fence)));
        assertThatThrownBy(() -> service.activate(
                BrokerManagementHttpTransformer.INSTANCE.activateCommand("activate", "broker-b", 0L))
                .toCompletableFuture().join()).hasRootCauseMessage(
                        "failed to commit epoch topology: UNAVAILABLE: commit unavailable");
        assertThat(fixture.stateMachine.committedState().epoch()).isZero();
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("activate")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.FENCING_OLD_EPOCH);
    }

    @ParameterizedTest
    @CsvSource({"join,99,CONFLICT", "sync,99,CONFLICT", "promote,99,CONFLICT",
            "demote,99,CONFLICT", "remove,99,CONFLICT", "sync,0,REJECTED",
            "promote,0,REJECTED", "demote,0,REJECTED", "remove,0,REJECTED"})
    void localMembershipRejectionReturnsApplicationResultWithoutConsensusRead(
            String action, long expectedEpoch, String expectedStatus) {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        String status = switch (action) {
            case "join" -> service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                    action, "broker-b", expectedEpoch, endpoint)).toCompletableFuture().join().status().name();
            case "sync" -> service.confirmLearner(new ConfirmLearnerCommand(
                    action, "broker-b", expectedEpoch, endpoint)).toCompletableFuture().join().status().name();
            case "promote" -> service.promoteLearner(new PromoteLearnerCommand(
                    action, "broker-b", expectedEpoch, endpoint)).toCompletableFuture().join().status();
            case "demote" -> service.demoteVoter(new DemoteVoterCommand(
                    action, "broker-b", expectedEpoch, endpoint)).toCompletableFuture().join().status();
            case "remove" -> service.removeBroker(new RemoveBrokerCommand(
                    action, "broker-b", expectedEpoch, endpoint)).toCompletableFuture().join().status();
            default -> throw new IllegalArgumentException(action);
        };
        assertThat(status).isEqualTo(expectedStatus);
        assertThat(fixture.stateMachine.getCluster().operations()).isEmpty();
        verify(fixture.adapter, never()).getCommittedState();
        verify(fixture.adapter, never()).beginOperation(any());
        verify(fixture.adapter, never()).addLearner(any());
        verify(fixture.adapter, never()).promoteLearner(any());
        verify(fixture.adapter, never()).demoteVoter(any());
        verify(fixture.adapter, never()).removeMember(any());
    }

    @Test
    void learnerAdmissionFailureReturnsRejectedInsteadOfThrowing() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), ready("broker-b"));
        assertThat(membership(fixture, 1).addLearner(
                BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                        "join", "broker-b", 0L, "127.0.0.1:19001")).toCompletableFuture().join().status())
                .isEqualTo(Status.REJECTED);
        assertThat(fixture.stateMachine.getCluster().operations()).isEmpty();
        verify(fixture.adapter, never()).getCommittedState();
        verify(fixture.adapter, never()).beginOperation(any());
        verify(fixture.adapter, never()).addLearner(any());
    }

    @Test
    void successfulRemovalCanBeRetriedAfterEndpointBindingIsDeleted() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"),
                new BrokerNode("broker-b", "broker-b.example:18020", BrokerMembershipRole.LEARNER,
                        BrokerRoutingState.INACTIVE));
        MembershipEndpoint endpoint = new MembershipEndpoint("127.0.0.1:19001");
        fixture.stateMachine.restore(new BrokerClusterSnapshot(BrokerClusterSnapshot.CURRENT_FORMAT_VERSION,
                "cluster-a", fixture.stateMachine.committedState(), List.of(), null, Map.of("broker-b", endpoint.value())));
        fixture.learners.add(endpoint.value());
        BrokerMembershipAppService service = membership(fixture, 1);
        RemoveBrokerCommand command = new RemoveBrokerCommand(
                "remove", "broker-b", 0L, endpoint.value());

        assertThat(service.removeBroker(command).toCompletableFuture().join().status()).isEqualTo("APPLIED");
        assertThat(fixture.stateMachine.getCluster().membershipEndpoints()).doesNotContainKey(new BrokerId("broker-b"));
        long revision = fixture.stateMachine.committedState().revision();
        assertThat(service.removeBroker(command).toCompletableFuture().join().status()).isEqualTo("DUPLICATE");
        verify(fixture.adapter, times(1)).removeMember(endpoint);
        assertThat(fixture.stateMachine.committedState().revision()).isEqualTo(revision);
        assertThat(service.removeBroker(new RemoveBrokerCommand(
                "remove", "broker-b", 0L, "127.0.0.1:19002"))
                .toCompletableFuture().join().status()).isEqualTo("REJECTED");
        verify(fixture.adapter, times(1)).removeMember(any(MembershipEndpoint.class));
    }

    @Test
    void historicalSyncRetryKeepsOriginalEpochAfterActivation() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        ConfirmLearnerCommand sync = new ConfirmLearnerCommand(
                "sync", "broker-b", 0L, endpoint);
        service.confirmLearner(sync).toCompletableFuture().join();
        routing(fixture, new InMemoryGatewayRegistry(),
                (id, fence) -> CompletableFuture.completedFuture(ack(id, fence)))
                .activate(BrokerManagementHttpTransformer.INSTANCE.activateCommand("activate", "broker-b", 0L))
                .toCompletableFuture().join();

        assertThat(fixture.stateMachine.committedState().epoch()).isEqualTo(1L);
        CompleteLearnerSyncResult retry = service.confirmLearner(sync).toCompletableFuture().join();
        assertThat(retry.status()).isEqualTo(Status.DUPLICATE);
        assertThat(retry.acceptedEpoch()).isZero();
        assertThat(retry.targetEpoch()).isZero();
        CompleteLearnerSyncResult mismatch = service.confirmLearner(
                new ConfirmLearnerCommand(
                        "sync", "broker-b", 1L, endpoint)).toCompletableFuture().join();
        assertThat(mismatch.status()).isEqualTo(Status.REJECTED);
        assertThat(mismatch.acceptedEpoch()).isZero();
        assertThat(mismatch.targetEpoch()).isZero();
    }

    @Test
    void newSyncOnFollowerReturnsNotLeaderWithoutReadingMembership() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        Mockito.when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(false);
        doReturn("127.0.0.1:18121").when(fixture.adapter).getLeaderEndpoint();

        CompleteLearnerSyncResult result = service.confirmLearner(new ConfirmLearnerCommand(
                "sync", "broker-b", 0L, endpoint)).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(Status.NOT_LEADER);
        assertThat(result.leaderEndpoint()).isEqualTo("127.0.0.1:18121");
        verify(fixture.adapter, never()).getConsensusMembership();
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("sync"))).isEmpty();
    }

    @Test
    void alreadyReadyLearnerIsRejectedBeforeReadingRemoteProgress() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        service.confirmLearner(new ConfirmLearnerCommand(
                "sync", "broker-b", 0L, endpoint)).toCompletableFuture().join();

        CompleteLearnerSyncResult result = service.confirmLearner(
                new ConfirmLearnerCommand(
                        "another-sync", "broker-b", 0L, endpoint)).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        verify(fixture.adapter, times(1)).getConsensusMembership();
        verify(fixture.adapter, times(1)).confirmLeadership(any());
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("another-sync"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void progressReadFailureDistinguishesUnavailableFromLostLeadership(boolean stillLeader) {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        doAnswer(ignored -> {
            Mockito.when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(stillLeader);
            return CompletableFuture.failedFuture(new IllegalStateException("quorum read unavailable"));
        }).when(fixture.adapter).confirmLeadership(any());
        doReturn("127.0.0.1:18121").when(fixture.adapter).getLeaderEndpoint();

        assertThatThrownBy(() -> service.confirmLearner(
                new ConfirmLearnerCommand(
                        "sync", "broker-b", 0L, endpoint)).toCompletableFuture().join())
                .hasRootCauseMessage("quorum read unavailable");
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("sync"))).isEmpty();
    }

    @Test
    void pendingMembershipRejectsAnotherChangeBeforeExternalSideEffects() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"), discovered("broker-c"));
        MembershipEndpoint firstEndpoint = new MembershipEndpoint("127.0.0.1:19001");
        MembershipEndpoint secondEndpoint = new MembershipEndpoint("127.0.0.1:19002");
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.UNAVAILABLE, null, "unknown result")))
                .when(fixture.adapter).addLearner(firstEndpoint);
        BrokerMembershipAppService service = membership(fixture, 1);
        assertThat(service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join-1", "broker-b", 0L, firstEndpoint.value())).toCompletableFuture().join().status())
                .isEqualTo(Status.UNAVAILABLE);

        assertThat(service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join-2", "broker-c", 0L, secondEndpoint.value())).toCompletableFuture().join())
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo(Status.CONFLICT);
                    assertThat(result.message()).contains("another cluster change");
                });
        verify(fixture.adapter, never()).addLearner(secondEndpoint);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("join-2"))).isEmpty();
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("join-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.PENDING);
    }

    @Test
    void restoredFailedBrokerDoesNotBecomeActiveWithoutExplicitActivation() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), voter("broker-b"));
        routing(fixture, new InMemoryGatewayRegistry(),
                (id, fence) -> CompletableFuture.completedFuture(ack(id, fence)))
                .forceDeactivate(BrokerManagementHttpTransformer.INSTANCE.forceDeactivateCommand(
                        "failure", "broker-b", 0L, "confirmed failure")).toCompletableFuture().join();
        BrokerClusterSnapshot saved = BrokerClusterSnapshot.decode(
                BrokerClusterSnapshot.encode(fixture.stateMachine.snapshotState()));
        StatefulClusterAdapterFixture restored = new StatefulClusterAdapterFixture(saved.state());
        restored.stateMachine.restore(saved);

        BrokerCluster cluster = restored.stateMachine.getCluster();
        assertThat(cluster.findNode(new BrokerId("broker-b")).orElseThrow().trafficState())
                .isEqualTo(BrokerTrafficState.INACTIVE);
        assertThat(cluster.brokers().stream().filter(node -> node.participatesInRouting())
                .map(node -> node.id().value()).toList()).containsExactly("broker-a");
        assertThat(restored.stateMachine.committedState()).isEqualTo(fixture.stateMachine.committedState());
        assertThat(restored.stateMachine.findOperation(new ClusterOperationId("failure"))).contains(saved.operations().getFirst());
    }

    @Test
    void removalRejectionFailsOperationWithoutRemovingDomainNode() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"),
                new BrokerNode("broker-b", "broker-b.example:18020", BrokerMembershipRole.LEARNER,
                        BrokerRoutingState.INACTIVE));
        MembershipEndpoint endpoint = new MembershipEndpoint("127.0.0.1:19001");
        fixture.stateMachine.restore(new BrokerClusterSnapshot(BrokerClusterSnapshot.CURRENT_FORMAT_VERSION,
                "cluster-a", fixture.stateMachine.committedState(), List.of(), null, Map.of("broker-b", endpoint.value())));
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, "removal rejected")))
                .when(fixture.adapter).removeMember(endpoint);

        assertThat(membership(fixture, 1).removeBroker(new RemoveBrokerCommand(
                "remove", "broker-b", 0L, endpoint.value())).toCompletableFuture().join().status())
                .isEqualTo("REJECTED");
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("remove")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.FAILED);
        assertThat(fixture.stateMachine.getCluster().findNode(new BrokerId("broker-b"))).isPresent();
    }

    @Test
    void demotionRejectionRetainsPendingOperationAndVoterState() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        service.confirmLearner(new ConfirmLearnerCommand(
                "sync", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        service.promoteLearner(new PromoteLearnerCommand(
                "promote", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, "learner add rejected")))
                .when(fixture.adapter).demoteVoter(new MembershipEndpoint(endpoint));

        assertThat(service.demoteVoter(new DemoteVoterCommand(
                "demote", "broker-b", 0L, endpoint)).toCompletableFuture().join().status()).isEqualTo("REJECTED");
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("demote")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.PENDING);
        assertThat(fixture.stateMachine.committedState().brokers().stream()
                .filter(node -> node.brokerId().equals("broker-b")).findFirst().orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.VOTER);
    }

    @Test
    void unavailableFailureRecordingIsReturnedAndLeavesOperationPending() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        MembershipEndpoint endpoint = new MembershipEndpoint("127.0.0.1:19001");
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, "peer rejected")))
                .when(fixture.adapter).addLearner(endpoint);
        doAnswer(ignored -> CompletableFuture.completedFuture(new BrokerClusterConsensusResult(Status.UNAVAILABLE, null, null, "failure record unavailable")))
                .when(fixture.adapter).failMembershipOperation(any(), any());

        assertThat(membership(fixture, 1).addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint.value())).toCompletableFuture().join().status())
                .isEqualTo(Status.UNAVAILABLE);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("join")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEMOTE_VOTER", "REMOVE_BROKER"})
    void pendingReductionResumesFromSnapshotUsingFrozenMinimumDespiteChangedLeaderConfig(String type) {
        String endpoint = "broker-b.example:18120";
        StatefulClusterAdapterFixture first = fixture(voter("broker-a"),
                new BrokerNode("broker-b", "broker-b.example:18020", BrokerMembershipRole.VOTER,
                        BrokerRoutingState.INACTIVE), voter("broker-c"));
        first.stateMachine.restore(new BrokerClusterSnapshot(BrokerClusterSnapshot.CURRENT_FORMAT_VERSION,
                "cluster-a", first.stateMachine.committedState(), List.of(), null, Map.of("broker-b", endpoint)));
        BrokerManagementCommand command = type.equals("DEMOTE_VOTER")
                ? new DemoteVoterCommand("reduce-1", "broker-b", 0L, endpoint)
                : new RemoveBrokerCommand("reduce-1", "broker-b", 0L, endpoint);
        BrokerCluster cluster = first.stateMachine.getCluster();
        BrokerClusterOperation operation = type.equals("DEMOTE_VOTER")
                ? cluster.demoteVoter(VoterDemotionIntent.builder().operationId(new ClusterOperationId(command.operationId()))
                .commandDigest(command.digest()).expectedRevision(cluster.revision()).brokerId(new BrokerId(command.brokerId()))
                .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.now()).build()).operation()
                : cluster.removeBroker(BrokerRemovalIntent.builder().operationId(new ClusterOperationId(command.operationId()))
                .commandDigest(command.digest()).expectedRevision(cluster.revision()).brokerId(new BrokerId(command.brokerId()))
                .minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.now()).build()).operation();
        assertThat(first.stateMachine.apply(BrokerClusterRaftTransformer.INSTANCE.beginCommandFrom(operation)).accepted()).isTrue();
        StatefulClusterAdapterFixture recovered = new StatefulClusterAdapterFixture(first.stateMachine.committedState());
        recovered.stateMachine.restore(BrokerClusterSnapshot.decode(
                BrokerClusterSnapshot.encode(first.stateMachine.snapshotState())));
        recovered.voters.addAll(List.of("broker-a.example:18120", endpoint, "broker-c.example:18120"));

        membership(recovered, 3).resume(operation.id()).toCompletableFuture().join();

        BrokerClusterOperation completed = recovered.stateMachine.findOperation(new ClusterOperationId("reduce-1")).orElseThrow();
        assertThat(completed.phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(completed.minimumVoterCount()).isEqualTo(new MinimumVoterCount(1));
        assertThat(recovered.voters).doesNotContain(endpoint);
    }
    @Test
    void definitiveMembershipRejectionFailsPendingOperationAndReleasesNextChange() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        MembershipEndpoint endpoint = new MembershipEndpoint("127.0.0.1:19001");
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, "peer rejected")))
                .when(fixture.adapter).addLearner(endpoint);

        assertThat(membership(fixture, 1).addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint.value()))
                .toCompletableFuture().join().status()).isEqualTo(Status.REJECTED);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("join")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.FAILED);
        assertThat(fixture.stateMachine.getCluster().addLearner(LearnerJoinIntent.builder()
                .operationId(new ClusterOperationId("next"))
                .commandDigest(new CommandDigest("next-digest"))
                .expectedRevision(fixture.stateMachine.getCluster().revision())
                .brokerId(new BrokerId("broker-b"))
                .membershipEndpoint(endpoint)
                .changedAt(Instant.ofEpochMilli(2_000L))
                .build()).status())
                .isEqualTo(BrokerClusterDecision.Status.APPLIED);
    }

    @Test
    void unavailableMembershipKeepsPendingOperationForRecovery() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        MembershipEndpoint endpoint = new MembershipEndpoint("127.0.0.1:19001");
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.UNAVAILABLE, null, "timeout")))
                .when(fixture.adapter).addLearner(endpoint);

        assertThat(membership(fixture, 1).addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint.value()))
                .toCompletableFuture().join().status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("join")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.PENDING);
    }

    @Test
    void resumesPendingLearnerAdditionUsingCommittedEndpointBinding() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        MembershipEndpoint endpoint = new MembershipEndpoint("127.0.0.1:19001");
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.UNAVAILABLE, null, "timeout")))
                .when(fixture.adapter).addLearner(endpoint);
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint.value())).toCompletableFuture().join();
        fixture.learners.add(endpoint.value());
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.DUPLICATE, null, null)))
                .when(fixture.adapter).addLearner(endpoint);

        service.resume(new ClusterOperationId("join"))
                .toCompletableFuture().join();

        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("join")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(fixture.stateMachine.getCluster().operations()).hasSize(1);
        assertThat(fixture.stateMachine.getCluster().findNode(new BrokerId("broker-b"))
                .orElseThrow().membershipRole()).isEqualTo(
                        com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole.LEARNER_SYNCING);
    }

    @Test
    void twoStepPromotionRejectionRetainsPendingUntilMembershipIsReconciled() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand("join", "broker-b", 0L, endpoint))
                .toCompletableFuture().join();
        service.confirmLearner(new ConfirmLearnerCommand(
                "sync", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        doReturn(CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, "voter add rejected")))
                .when(fixture.adapter).promoteLearner(new MembershipEndpoint(endpoint));

        assertThat(service.promoteLearner(new PromoteLearnerCommand(
                "promote", "broker-b", 0L, endpoint))
                .toCompletableFuture().join().status()).isEqualTo("REJECTED");
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("promote")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.PENDING);
    }

    @Test
    void learnerSyncRejectsEndpointDifferentFromTheJoinedBroker() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String joinedEndpoint = "127.0.0.1:19001";
        String unrelatedEndpoint = "127.0.0.1:19002";
        service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, joinedEndpoint)).toCompletableFuture().join();
        fixture.learners.add(unrelatedEndpoint);

        assertThat(service.confirmLearner(
                new ConfirmLearnerCommand(
                        "sync", "broker-b", 0L, unrelatedEndpoint))
                .toCompletableFuture().join().status()).isEqualTo(Status.REJECTED);
        assertThat(fixture.stateMachine.getCluster().findNode(new BrokerId("broker-b"))
                .orElseThrow().membershipRole()).isEqualTo(
                        com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole.LEARNER_SYNCING);
    }

    @Test
    void learnerMembershipBindingSurvivesSnapshotRestore() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        String endpoint = "127.0.0.1:19001";
        membership(fixture, 1).addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        RaftBrokerClusterStateMachine restored = new RaftBrokerClusterStateMachine("cluster-a",
                fixture.stateMachine.committedState(), 1000, Map.of());

        restored.restore(BrokerClusterSnapshot.decode(
                BrokerClusterSnapshot.encode(fixture.stateMachine.snapshotState())));

        assertThat(restored.getCluster().hasMembershipEndpoint(
                new BrokerId("broker-b"), new MembershipEndpoint(endpoint))).isTrue();
        assertThat(restored.getCluster().hasMembershipEndpoint(
                new BrokerId("broker-b"), new MembershipEndpoint("127.0.0.1:19002"))).isFalse();
    }

    @Test
    void learnerSyncWaitsForLocallyAppliedQuorumReadIndex() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        String endpoint = "127.0.0.1:19001";
        membership(fixture, 1).addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                "join", "broker-b", 0L, endpoint)).toCompletableFuture().join();
        BrokerPeerAdapter peerAdapter =
                mock(BrokerPeerAdapter.class);
        doReturn(CompletableFuture.completedFuture(0L)).when(peerAdapter).fetchQuorumReadIndex(any());
        BrokerMembershipAppService service = new BrokerMembershipAppService(
                fixture.adapter, peerAdapter,
                new MinimumVoterCount(1));

        assertThat(service.confirmLearner(
                new ConfirmLearnerCommand(
                        "sync", "broker-b", 0L, endpoint))
                .toCompletableFuture().join().status()).isEqualTo(Status.REJECTED);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("sync"))).isEmpty();
        assertThat(fixture.stateMachine.getCluster().findNode(new BrokerId("broker-b"))
                .orElseThrow().membershipRole()).isEqualTo(
                        com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole.LEARNER_SYNCING);
    }

    @Test
    void activationRetryWithOriginalEpochDoesNotFenceAgain() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), ready("broker-b"));
        BrokerClusterAppService service = routing(fixture, new InMemoryGatewayRegistry(),
                (brokerId, fence) -> CompletableFuture.completedFuture(ack(brokerId, fence)));
        ActivateBrokerCommand command = BrokerManagementHttpTransformer.INSTANCE.activateCommand("activate", "broker-b", 0L);
        assertThat(service.activate(command).toCompletableFuture().join().status()).isEqualTo("APPLIED");
        long revision = fixture.stateMachine.committedState().revision();

        assertThat(service.activate(command).toCompletableFuture().join().status()).isEqualTo("DUPLICATE");
        assertThat(fixture.stateMachine.committedState().revision()).isEqualTo(revision);
        assertThat(fixture.stateMachine.getCluster().operations()).hasSize(1);
    }

    @Test
    void advancesLearnerThroughJoinSyncPromotionAndDemotion() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        assertThat(service.addLearner(BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand("join", "broker-b", 0L, endpoint))
                .toCompletableFuture().join().status()).isEqualTo(Status.APPLIED);
        assertThat(fixture.stateMachine.committedState().brokers().get(1).membershipRole())
                .isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(service.confirmLearner(new ConfirmLearnerCommand(
                "sync", "broker-b", 0L, endpoint)).toCompletableFuture().join().status())
                .isEqualTo(Status.APPLIED);
        assertThat(fixture.stateMachine.committedState().brokers().get(1).routingState())
                .isEqualTo(BrokerRoutingState.READY);
        assertThat(service.promoteLearner(new PromoteLearnerCommand(
                "promote", "broker-b", 0L, endpoint)).toCompletableFuture().join().status())
                .isEqualTo("APPLIED");
        assertThat(fixture.stateMachine.committedState().brokers().get(1).membershipRole())
                .isEqualTo(BrokerMembershipRole.VOTER);
        assertThat(service.demoteVoter(new DemoteVoterCommand(
                "demote", "broker-b", 0L, endpoint)).toCompletableFuture().join().status())
                .isEqualTo("APPLIED");
        assertThat(fixture.stateMachine.committedState().brokers().get(1).membershipRole())
                .isEqualTo(BrokerMembershipRole.LEARNER);
        assertThat(fixture.stateMachine.getCluster().operations())
                .allSatisfy(operation -> assertThat(operation.phase())
                        .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED));
    }

    @Test
    void membershipRetryDoesNotReapplyEarlierRoleChanges() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), discovered("broker-b"));
        BrokerMembershipAppService service = membership(fixture, 1);
        String endpoint = "127.0.0.1:19001";
        AddLearnerCommand join = BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand("join", "broker-b", 0L, endpoint);
        ConfirmLearnerCommand sync = new ConfirmLearnerCommand("sync", "broker-b", 0L, endpoint);
        PromoteLearnerCommand promote = new PromoteLearnerCommand("promote", "broker-b", 0L, endpoint);
        service.addLearner(join).toCompletableFuture().join();
        service.confirmLearner(sync).toCompletableFuture().join();
        service.promoteLearner(promote).toCompletableFuture().join();
        service.demoteVoter(new DemoteVoterCommand("demote", "broker-b", 0L, endpoint))
                .toCompletableFuture().join();
        long revision = fixture.stateMachine.committedState().revision();

        assertThat(service.addLearner(join).toCompletableFuture().join().status()).isEqualTo(Status.DUPLICATE);
        assertThat(service.confirmLearner(sync).toCompletableFuture().join().status()).isEqualTo(Status.DUPLICATE);
        assertThat(service.promoteLearner(promote).toCompletableFuture().join().status()).isEqualTo("DUPLICATE");
        verify(fixture.adapter, times(1)).addLearner(new MembershipEndpoint(endpoint));
        verify(fixture.adapter, times(1)).promoteLearner(new MembershipEndpoint(endpoint));
        verify(fixture.adapter, times(1)).getConsensusMembership();
        verify(fixture.adapter, times(1)).confirmLeadership(any());
        assertThat(fixture.stateMachine.committedState().revision()).isEqualTo(revision);
        assertThat(fixture.voters).doesNotContain(endpoint);
        assertThat(fixture.learners).containsExactly(endpoint);
    }

    @Test
    void demotionRejectsQuorumViolationBeforeExternalChange() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), voter("broker-b"), voter("broker-c"));
        fixture.stateMachine.restore(new BrokerClusterSnapshot(BrokerClusterSnapshot.CURRENT_FORMAT_VERSION,
                "cluster-a", fixture.stateMachine.committedState(), List.of(), null,
                Map.of("broker-b", "127.0.0.1:19001")));
        assertThat(membership(fixture, 3).demoteVoter(new DemoteVoterCommand(
                "demote", "broker-b", 0L, "127.0.0.1:19001"))
                .toCompletableFuture().join()).satisfies(result -> {
                    assertThat(result.status()).isEqualTo("REJECTED");
                    assertThat(result.message()).isEqualTo("membership change violates minimum voter count 3");
                });
        verify(fixture.adapter, never()).demoteVoter(any(MembershipEndpoint.class));
        assertThat(fixture.stateMachine.getCluster().operations()).isEmpty();
    }

    @Test
    void activationWaitsForFenceBeforePublishingNewEpoch() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), ready("broker-b"));
        CompletableFuture<FenceAck> acknowledgement = new CompletableFuture<>();
        BrokerClusterAppService service = routing(fixture, new InMemoryGatewayRegistry(),
                (brokerId, fence) -> acknowledgement);
        CompletableFuture<?> activation = service.activate(
                BrokerManagementHttpTransformer.INSTANCE.activateCommand("activate", "broker-b", 0L)).toCompletableFuture();
        assertThat(activation).isNotDone();
        assertThat(fixture.stateMachine.committedState().epoch()).isZero();
        RoutingEpochFence fence = fixture.stateMachine.findRoutingFence().orElseThrow();
        acknowledgement.complete(ack("broker-a", fence));
        activation.join();
        assertThat(fixture.stateMachine.committedState().epoch()).isEqualTo(1L);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("activate")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
    }

    @Test
    void staleEpochDoesNotFenceCurrentHolders() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), ready("broker-b"));
        BrokerClusterAppService service = routing(fixture, new InMemoryGatewayRegistry(),
                (brokerId, fence) -> { throw new AssertionError("must not fence"); });
        assertThat(service.activate(
                BrokerManagementHttpTransformer.INSTANCE.activateCommand("activate", "broker-b", 99L))
                .toCompletableFuture().join().status()).isEqualTo("CONFLICT");
        assertThat(fixture.stateMachine.findRoutingFence()).isEmpty();
    }

    @Test
    void drainFreezesGatewayEvidenceAndEventDeadline() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), voter("broker-b"));
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-a", "127.0.0.1", 18021, "identity-a"));
        Instant before = Instant.ofEpochMilli(Instant.now().toEpochMilli()).plusSeconds(10);
        routing(fixture, gateways, (id, fence) -> CompletableFuture.completedFuture(ack(id, fence)))
                .drain(BrokerManagementHttpTransformer.INSTANCE.drainCommand("drain", "broker-b", 0L))
                .toCompletableFuture().join();
        Instant after = Instant.now().plusSeconds(10);
        assertThat(fixture.stateMachine.committedState().brokers().get(1).routingState())
                .isEqualTo(BrokerRoutingState.DRAINING);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("drain")).orElseThrow().drainProgress())
                .satisfies(progress -> {
                    assertThat(progress.gateways()).hasSize(1);
                    assertThat(progress.gateways().get(0).gatewayId().value()).isEqualTo("gateway-a");
                    assertThat(progress.oldEventExpireAt()).isBetween(before, after);
                });
    }

    @Test
    void failureRemovalPreservesGatewayRecoveryEvidence() {
        StatefulClusterAdapterFixture fixture = fixture(voter("broker-a"), voter("broker-b"));
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-a", "127.0.0.1", 18021, "identity-a"));
        routing(fixture, gateways, (id, fence) -> CompletableFuture.completedFuture(ack(id, fence)))
                .forceDeactivate(BrokerManagementHttpTransformer.INSTANCE.forceDeactivateCommand(
                        "failure", "broker-b", 0L, "confirmed failure"))
                .toCompletableFuture().join();
        assertThat(fixture.stateMachine.committedState().brokers().get(1).routingState())
                .isEqualTo(BrokerRoutingState.INACTIVE);
        assertThat(fixture.stateMachine.findOperation(new ClusterOperationId("failure")).orElseThrow().drainProgress().gateways())
                .hasSize(1);
    }

    private BrokerClusterAppService routing(StatefulClusterAdapterFixture fixture,
                                            InMemoryGatewayRegistry gateways,
                                            BiFunction<String, RoutingEpochFence, CompletionStage<FenceAck>> peers) {
        doAnswer(invocation -> peers.apply(
                ((com.co.kc.imchat.broker.domain.cluster.model.BrokerNode) invocation.getArgument(0)).id().value(),
                invocation.getArgument(1))).when(fixture.peerAdapter).fence(any(), any());
        BrokerClusterAppService service = spy(new BrokerClusterAppService(fixture.adapter,
                fixture.peerAdapter,
                gateways, Duration.ofMillis(1), Duration.ofSeconds(10),
                mock(ScheduledExecutorService.class), Duration.ofSeconds(10), null, null, Duration.ofSeconds(60)));
        doReturn(CompletableFuture.completedFuture(true)).when(service).activateLease(any());
        return service;
    }

    private BrokerMembershipAppService membership(StatefulClusterAdapterFixture fixture, int minimumVoters) {
        BrokerPeerAdapter peerAdapter =
                mock(BrokerPeerAdapter.class);
        doReturn(CompletableFuture.completedFuture(1L)).when(peerAdapter).fetchQuorumReadIndex(any());
        return new BrokerMembershipAppService(fixture.adapter, peerAdapter,
                new MinimumVoterCount(minimumVoters));
    }

    private StatefulClusterAdapterFixture fixture(BrokerNode... nodes) {
        return new StatefulClusterAdapterFixture(new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(nodes)));
    }

    private BrokerNode voter(String id) {
        return new BrokerNode(id, id + ".example:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }

    private BrokerNode ready(String id) {
        return new BrokerNode(id, id + ".example:18020", BrokerMembershipRole.LEARNER, BrokerRoutingState.READY);
    }

    private BrokerNode discovered(String id) {
        return new BrokerNode(id, id + ".example:18020", BrokerMembershipRole.DISCOVERED, BrokerRoutingState.INACTIVE);
    }

    private FenceAck ack(String brokerId, RoutingEpochFence fence) {
        return FenceAck.builder().brokerId(new BrokerId(brokerId))
                .routingEpoch(fence.epoch()).leaderTerm(fence.leaderTerm())
                .fencedLeaseSequence(leaseSequence(1)).operationId(fence.operationId()).build();
    }
}
