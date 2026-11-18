package com.co.kc.imchat.broker.sdk.routing;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteBrokerSelectorContractTest {

    @Test
    void rendezvousHashMatchesFrozenGoldenVectors() {
        RouteBrokerSelector selector = new RendezvousHashRouteBrokerSelector();
        BrokerClusterState state = state(BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                "broker-a", "broker-b", "broker-c");

        assertThat(selector.select(0L, state).brokerId()).isEqualTo("broker-a");
        assertThat(selector.select(2L, state).brokerId()).isEqualTo("broker-c");
        assertThat(selector.select(4L, state).brokerId()).isEqualTo("broker-b");
        assertThat(selector.select(Long.MAX_VALUE, state).brokerId()).isEqualTo("broker-b");
        assertThat(selector.select(-1L, state).brokerId()).isEqualTo("broker-c");
    }

    @Test
    void moduloHashMatchesFrozenGoldenVectors() {
        RouteBrokerSelector selector = new ModuloHashRouteBrokerSelector();
        BrokerClusterState state = state(BrokerSelectionAlgorithm.MODULO_HASH,
                "broker-a", "broker-b", "broker-c");

        assertThat(selector.select(0L, state).brokerId()).isEqualTo("broker-c");
        assertThat(selector.select(3L, state).brokerId()).isEqualTo("broker-b");
        assertThat(selector.select(4L, state).brokerId()).isEqualTo("broker-a");
        assertThat(selector.select(5L, state).brokerId()).isEqualTo("broker-b");
        assertThat(selector.select(-1L, state).brokerId()).isEqualTo("broker-c");
    }

    @Test
    void selectionDoesNotDependOnBrokerInputOrder() {
        List<BrokerNode> ordered = activeNodes("broker-a", "broker-b", "broker-c");
        List<BrokerNode> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);

        for (BrokerSelectionAlgorithm algorithm : BrokerSelectionAlgorithm.values()) {
            RouteBrokerSelector selector = RouteBrokerSelectors.forAlgorithm(algorithm);
            BrokerClusterState first = state(algorithm, ordered);
            BrokerClusterState second = state(algorithm, reversed);
            for (long userId = 0; userId < 1_000; userId++) {
                assertThat(selector.select(userId, first).brokerId())
                        .isEqualTo(selector.select(userId, second).brokerId());
            }
        }
    }

    @Test
    void rendezvousHashDistributesUsersAndLimitsMovementWhenNodeIsAdded() {
        RouteBrokerSelector selector = new RendezvousHashRouteBrokerSelector();
        BrokerClusterState before = state(BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                "broker-a", "broker-b", "broker-c");
        BrokerClusterState after = state(BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                "broker-a", "broker-b", "broker-c", "broker-d");
        Map<String, Integer> counts = new HashMap<>();
        int moved = 0;

        for (long userId = 0; userId < 100_000; userId++) {
            String oldBroker = selector.select(userId, before).brokerId();
            String newBroker = selector.select(userId, after).brokerId();
            counts.merge(newBroker, 1, Integer::sum);
            if (!oldBroker.equals(newBroker)) {
                moved++;
                assertThat(newBroker).isEqualTo("broker-d");
            }
        }

        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween(24_000, 26_000));
        assertThat(moved).isBetween(24_000, 26_000);
    }

    private BrokerClusterState state(BrokerSelectionAlgorithm algorithm, String... brokerIds) {
        return state(algorithm, activeNodes(brokerIds));
    }

    private BrokerClusterState state(BrokerSelectionAlgorithm algorithm,
                                     List<BrokerNode> brokers) {
        return new BrokerClusterState(7L, 3L, algorithm, brokers);
    }

    private List<BrokerNode> activeNodes(String... brokerIds) {
        return java.util.Arrays.stream(brokerIds)
                .map(brokerId -> new BrokerNode(brokerId, brokerId + ".internal:18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE))
                .toList();
    }
}
