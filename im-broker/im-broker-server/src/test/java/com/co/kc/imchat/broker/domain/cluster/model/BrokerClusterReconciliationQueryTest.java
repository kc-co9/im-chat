package com.co.kc.imchat.broker.domain.cluster.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerClusterReconciliationQueryTest {
    @Test
    void activeBrokersRetainStableIdOrderFromUnsortedInputAndSnapshot() {
        BrokerCluster cluster = cluster(List.of());
        assertThat(cluster.activeBrokers()).extracting(node -> node.id().value())
                .containsExactly("a", "c");
        assertThat(cluster.snapshot().activeBrokers()).extracting(node -> node.id().value())
                .containsExactly("a", "c");
        assertThat(cluster.hasBlockingOperation()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(BrokerClusterOperationPhase.class)
    void blockingQueryReflectsOperationPhaseWithoutChangingHistory(BrokerClusterOperationPhase phase) {
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("op")).commandDigest(new CommandDigest("digest"))
                .type(BrokerClusterOperationType.ACTIVATE_BROKER).targetBrokerId(new BrokerId("b"))
                .acceptedRevision(new ClusterRevision(0L)).acceptedEpoch(new RoutingEpoch(0L))
                .resultEpoch(new RoutingEpoch(1L)).phase(phase).createdAt(Instant.EPOCH).build();
        BrokerCluster cluster = cluster(List.of(operation));
        boolean expected = switch (phase) {
            case PENDING, FENCING_OLD_EPOCH, RAFT_COMMITTED, AUTHORIZING_NEW_EPOCH -> true;
            default -> false;
        };
        assertThat(cluster.hasBlockingOperation()).isEqualTo(expected);
        assertThat(cluster.operations()).containsExactly(operation);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(2L));
    }

    private static BrokerCluster cluster(List<BrokerClusterOperation> operations) {
        return BrokerCluster.builder().revision(new ClusterRevision(2L)).epoch(new RoutingEpoch(1L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(node("c", BrokerTrafficState.ACTIVE), node("b", BrokerTrafficState.READY),
                        node("a", BrokerTrafficState.ACTIVE)))
                .membershipEndpoints(Map.of()).operations(operations)
                .operationHistoryCapacity(new OperationHistoryCapacity(100)).build();
    }

    private static BrokerNode node(String id, BrokerTrafficState traffic) {
        return new BrokerNode(new BrokerId(id), new BrokerRouteEndpoint(id + ":18020"),
                BrokerMembershipRole.VOTER, traffic);
    }
}
