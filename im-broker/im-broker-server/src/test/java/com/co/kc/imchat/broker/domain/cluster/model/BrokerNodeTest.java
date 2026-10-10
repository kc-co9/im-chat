package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerNodeTest {

    @ParameterizedTest
    @EnumSource(BrokerTrafficState.class)
    void removalRequiresInactiveNodeAndNeverChangesIt(BrokerTrafficState traffic) {
        BrokerNode node = new BrokerNode(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"), BrokerMembershipRole.VOTER, traffic);
        BrokerNode before = node.copy();

        assertThat(node.checkOperation(BrokerClusterOperationType.REMOVE_BROKER).isEmpty())
                .isEqualTo(traffic == BrokerTrafficState.INACTIVE);
        assertThat(node).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = BrokerClusterOperationType.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"ADD_BROKER", "REMOVE_BROKER"})
    void assessmentAndEntityBehaviorAgreeWithoutAssessmentChangingState(BrokerClusterOperationType type) {
        for (BrokerMembershipRole role : BrokerMembershipRole.values()) {
            List<BrokerTrafficState> trafficStates = role == BrokerMembershipRole.DISCOVERED
                    || role == BrokerMembershipRole.LEARNER_SYNCING
                    ? List.of(BrokerTrafficState.INACTIVE) : List.of(BrokerTrafficState.values());
            for (BrokerTrafficState traffic : trafficStates) {
                BrokerNode node = new BrokerNode(new BrokerId("broker-b"),
                        new BrokerRouteEndpoint("10.0.0.2:18020"), role, traffic);
                BrokerNode before = node.copy();
                Optional<DecisionReason> violation = node.checkOperation(type);
                assertThat(node).isEqualTo(before);

                if (violation.isPresent()) {
                    assertThatThrownBy(() -> apply(node, type))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(violation.get().value());
                    assertThat(node).isEqualTo(before);
                } else {
                    apply(node, type);
                    assertThat(node).isNotEqualTo(before);
                }
            }
        }
    }

    private void apply(BrokerNode node, BrokerClusterOperationType type) {
        switch (type) {
            case ADD_LEARNER, START_LEARNER_SYNC -> node.joinAsLearner();
            case COMPLETE_LEARNER_SYNC -> node.completeLearnerSync();
            case PROMOTE_LEARNER, PROMOTE_BROKER -> node.promoteToVoter();
            case DEMOTE_VOTER, DEMOTE_BROKER -> node.demoteToLearner();
            case ACTIVATE_BROKER -> node.activate();
            case DEACTIVATE_BROKER, FAIL_BROKER -> node.deactivate();
            case DRAIN_BROKER -> node.drain();
            default -> throw new IllegalArgumentException("not a node transition");
        }
    }

    @Test
    void businessMethodsChangeOnlyMembershipAndTrafficState() {
        BrokerId id = new BrokerId("broker-b");
        BrokerRouteEndpoint endpoint = new BrokerRouteEndpoint("10.0.0.2:18020");
        BrokerNode node = BrokerNode.discovered(id, endpoint);

        node.joinAsLearner();
        node.completeLearnerSync();
        assertThat(node.membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
        assertThat(node.trafficState()).isEqualTo(BrokerTrafficState.READY);
        node.promoteToVoter();
        assertThat(node.membershipRole()).isEqualTo(BrokerMembershipRole.VOTER);
        node.activate();
        assertThat(node.trafficState()).isEqualTo(BrokerTrafficState.ACTIVE);
        node.drain();
        assertThat(node.trafficState()).isEqualTo(BrokerTrafficState.DRAINING);
        node.finishDrain();
        node.demoteToLearner();
        assertThat(node.membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
        assertThat(node.trafficState()).isEqualTo(BrokerTrafficState.INACTIVE);
        assertThat(node.id()).isEqualTo(id);
        assertThat(node.routeEndpoint()).isEqualTo(endpoint);
    }

    @Test
    void rejectedTransitionsLeaveNodeUnchanged() {
        BrokerNode node = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));
        BrokerNode before = node.copy();

        assertThatThrownBy(node::activate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(node::deactivate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(node::drain).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(node::finishDrain).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(node::completeLearnerSync).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(node::promoteToVoter).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(node::demoteToLearner).isInstanceOf(IllegalStateException.class);
        assertThat(node).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(BrokerMembershipRole.class)
    void onlyDiscoveredNodeCanJoinAsLearner(BrokerMembershipRole role) {
        BrokerNode node = new BrokerNode(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"), role, BrokerTrafficState.INACTIVE);

        assertThat(node.canJoinAsLearner()).isEqualTo(role == BrokerMembershipRole.DISCOVERED);
    }

    @Test
    void joinsAsLearnerByChangingItselfWithoutChangingItsCopy() {
        BrokerNode discovered = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));

        BrokerNode copy = discovered.copy();
        discovered.joinAsLearner();

        assertThat(copy.id()).isEqualTo(discovered.id());
        assertThat(copy.routeEndpoint()).isEqualTo(discovered.routeEndpoint());
        assertThat(discovered.membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(discovered.trafficState()).isEqualTo(BrokerTrafficState.INACTIVE);
        assertThat(copy.membershipRole()).isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(copy).isNotEqualTo(discovered);
    }

    @Test
    void rejectsRepeatedLearnerJoinForNonDiscoveredNode() {
        BrokerNode syncing = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));
        syncing.joinAsLearner();

        assertThatThrownBy(syncing::joinAsLearner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker membership must be DISCOVERED");
    }

    @Test
    void restoresCompleteNodeStateFromJson() {
        BrokerNode node = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));
        node.joinAsLearner();

        BrokerNode restored = JsonUtils.fromJson(JsonUtils.toJson(node), BrokerNode.class);

        assertThat(restored).isEqualTo(node);
        assertThat(restored.hashCode()).isEqualTo(node.hashCode());
    }

    @Test
    void javaSerializationRestoresIndependentMutableNode() throws Exception {
        BrokerNode original = BrokerNode.discovered(new BrokerId("broker-b"),
                new BrokerRouteEndpoint("10.0.0.2:18020"));
        original.joinAsLearner();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        BrokerNode restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (BrokerNode) input.readObject();
        }

        assertThat(restored).isEqualTo(original);
        restored.completeLearnerSync();
        assertThat(restored.membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
        assertThat(original.membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
    }
}
