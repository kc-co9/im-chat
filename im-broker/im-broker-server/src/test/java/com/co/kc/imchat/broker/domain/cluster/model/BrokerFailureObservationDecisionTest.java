package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerFailureObservationDecisionTest {
    @Test
    void exposesQuorumAndSortedImmutableCountedObservers() {
        List<BrokerId> observers = new ArrayList<>(List.of(new BrokerId("c"), new BrokerId("b")));
        BrokerFailureObservationDecision decision = new BrokerFailureObservationDecision(
                BrokerFailureObservationEligibility.ELIGIBLE, new ObservationQuorum(2), observers, null);
        observers.clear();
        assertThat(decision.quorum()).isEqualTo(new ObservationQuorum(2));
        assertThat(decision.observerIds()).containsExactly(new BrokerId("b"), new BrokerId("c"));
        assertThatThrownBy(() -> decision.observerIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void jsonUsesNewDomainFieldNamesAndPreservesDecision() {
        BrokerFailureObservationDecision decision = new BrokerFailureObservationDecision(
                BrokerFailureObservationEligibility.INELIGIBLE, new ObservationQuorum(2),
                List.of(new BrokerId("b")), new DecisionReason("insufficient observations"));
        String json = JsonUtils.toJson(decision);
        JsonNode tree = JsonUtils.fromJson(json, JsonNode.class);
        assertThat(tree.has("quorum")).isTrue();
        assertThat(tree.has("observerIds")).isTrue();
        assertThat(tree.has("requiredReports")).isFalse();
        assertThat(tree.has("reportingVoters")).isFalse();
        assertThat(JsonUtils.fromJson(json, BrokerFailureObservationDecision.class)).isEqualTo(decision);
    }
}
