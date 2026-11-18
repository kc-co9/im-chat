package com.co.kc.imchat.broker.domain.cluster.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterFailureOperationQueryTest {
    @Test
    void returnsEmptyWithoutFailureOperations() {
        assertThat(cluster(List.of()).findUnfinishedFailureOperation()).isEmpty();
        assertThat(cluster(List.of(operation("other", BrokerClusterOperationType.ACTIVATE_BROKER,
                BrokerClusterOperationPhase.PENDING))).findUnfinishedFailureOperation()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(BrokerClusterOperationPhase.class)
    void selectsOnlyNonterminalFailureOperationsWithoutChangingCluster(BrokerClusterOperationPhase phase) {
        BrokerClusterOperation failure = operation("failure", BrokerClusterOperationType.FAIL_BROKER, phase);
        BrokerCluster cluster = cluster(List.of(failure));
        if (phase.terminal()) {
            assertThat(cluster.findUnfinishedFailureOperation()).isEmpty();
        } else {
            assertThat(cluster.findUnfinishedFailureOperation()).contains(failure);
        }
        assertThat(cluster.operations()).containsExactly(failure);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(5L));
        assertThat(cluster.epoch()).isEqualTo(new RoutingEpoch(2L));
    }

    @Test
    void ignoresTerminalAndOtherOperationsWhenSelectingUniqueFailure() {
        BrokerClusterOperation failure = operation("failure", BrokerClusterOperationType.FAIL_BROKER,
                BrokerClusterOperationPhase.REBUILDING_ROUTES);
        BrokerCluster cluster = cluster(List.of(
                operation("done", BrokerClusterOperationType.FAIL_BROKER, BrokerClusterOperationPhase.SUCCEEDED),
                operation("other", BrokerClusterOperationType.ACTIVATE_BROKER, BrokerClusterOperationPhase.PENDING),
                failure));
        assertThat(cluster.findUnfinishedFailureOperation()).contains(failure);
        assertThat(cluster.operations()).hasSize(3);
    }

    @Test
    void rejectsMultipleUnfinishedFailuresRatherThanChoosingOne() {
        BrokerCluster cluster = cluster(List.of(
                operation("first", BrokerClusterOperationType.FAIL_BROKER, BrokerClusterOperationPhase.FENCING_OLD_EPOCH),
                operation("second", BrokerClusterOperationType.FAIL_BROKER, BrokerClusterOperationPhase.REBUILDING_ROUTES)));
        assertThatThrownBy(cluster::findUnfinishedFailureOperation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("multiple unfinished failure operations");
        assertThat(cluster.operations()).hasSize(2);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(5L));
    }

    private static BrokerCluster cluster(List<BrokerClusterOperation> operations) {
        return BrokerCluster.builder().revision(new ClusterRevision(5L)).epoch(new RoutingEpoch(2L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"),
                        BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE)))
                .membershipEndpoints(Map.of()).operations(operations)
                .operationHistoryCapacity(new OperationHistoryCapacity(100)).build();
    }

    private static BrokerClusterOperation operation(String id, BrokerClusterOperationType type,
                                                   BrokerClusterOperationPhase phase) {
        return BrokerClusterOperation.builder().id(new ClusterOperationId(id))
                .commandDigest(new CommandDigest(id)).type(type).targetBrokerId(new BrokerId("b"))
                .acceptedEpoch(new RoutingEpoch(1L)).resultEpoch(new RoutingEpoch(2L))
                .acceptedRevision(new ClusterRevision(0L)).phase(phase).createdAt(Instant.EPOCH)
                .drainProgress(type.routeRecovery() ? new DrainProgress(List.of(), Instant.EPOCH, null) : null)
                .build();
    }
}
