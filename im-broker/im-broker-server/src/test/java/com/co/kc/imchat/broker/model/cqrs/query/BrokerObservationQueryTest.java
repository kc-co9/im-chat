package com.co.kc.imchat.broker.model.cqrs.query;

import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthObservationDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthReportDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerProbeDTO;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.common.exception.SerializationException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerObservationQueryTest {
    @Test
    void roleNamedQueryAndDtoApiAndJson() {
        BrokerProbeQuery probe = new BrokerProbeQuery("cluster", "caller", "receiver", 1L, 2L, "nonce");
        BrokerHealthReportQuery request = new BrokerHealthReportQuery("cluster", "caller", "receiver", 1L, 2L, "nonce", List.of("observed"));
        BrokerHealthObservationDTO row = new BrokerHealthObservationDTO(
                "observed", BrokerHealthStatus.UNHEALTHY,
                Duration.ofMillis(10), Duration.ofSeconds(30));
        BrokerHealthReportDTO result = new BrokerHealthReportDTO(
                "cluster", "observer", 1L, 2L, "nonce", List.of(row));
        assertThat(probe.receiverBrokerId()).isEqualTo("receiver");
        assertThat(request.receiverBrokerId()).isEqualTo("receiver");
        assertThat(request.brokerIds()).containsExactly("observed");
        assertThat(result.observerBrokerId()).isEqualTo("observer");
        assertThat(row.brokerId()).isEqualTo("observed");
        BrokerProbeDTO response = new BrokerProbeDTO("cluster", "receiver", 1L, 2L, "nonce");
        assertThat(response.brokerId()).isEqualTo("receiver");
        assertThat(JsonUtils.toJson(response)).contains("\"brokerId\":\"receiver\"")
                .doesNotContain("receiverBrokerId");
        assertThat(JsonUtils.fromJson(JsonUtils.toJson(response), BrokerProbeDTO.class)).isEqualTo(response);
        assertJsonRole(probe, BrokerProbeQuery.class, "receiverBrokerId", "targetBrokerId", true);
        assertJsonRole(request, BrokerHealthReportQuery.class, "receiverBrokerId", "targetBrokerId", true);
        assertJsonRole(request, BrokerHealthReportQuery.class, "brokerIds", "targetBrokerIds", true);
        assertJsonRole(result, BrokerHealthReportDTO.class, "observerBrokerId", "reporterBrokerId", false);
        assertJsonRole(row, BrokerHealthObservationDTO.class, "brokerId", "targetBrokerId", true);
    }

    private <T> void assertJsonRole(T value, Class<T> type, String field, String oldField, boolean required) {
        ObjectNode tree = JsonUtils.getMapper().valueToTree(value);
        assertThat(tree.has(field)).isTrue();
        assertThat(tree.has(oldField)).isFalse();
        assertThat(JsonUtils.fromJson(tree.toString(), type)).isEqualTo(value);
        tree.set(oldField, tree.remove(field));
        if (required) {
            assertThatThrownBy(() -> JsonUtils.fromJson(tree.toString(), type))
                    .isInstanceOf(SerializationException.class);
        } else {
            assertThat(JsonUtils.fromJson(tree.toString(), type))
                    .extracting("observerBrokerId").isNull();
        }
    }

    @Test
    void observationApplicationContractsContainNoAbsoluteExpiry() {
        for (Class<?> contract : List.of(BrokerProbeQuery.class, BrokerHealthReportQuery.class,
                BrokerProbeDTO.class,
                BrokerHealthReportDTO.class)) {
            assertThat(contract.getRecordComponents()).extracting(RecordComponent::getName)
                    .doesNotContain("expireAt", "expireAtMs");
        }
    }

    @Test
    void rejectsMissingIdentityNonceAndNegativeVersions() {
        assertThatThrownBy(() -> new BrokerProbeQuery(null, "a", "b", 0L, 0L, "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerProbeQuery("c", "a", "b", null, 0L, "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerProbeQuery("c", "a", "b", 0L, -1L, "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerProbeQuery("c", "a", "b", 0L, 0L, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerProbeQuery("c", "a", " ", 0L, 0L, "n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportCopiesTargetsAndRejectsDuplicateOrBlankTargets() {
        List<String> targets = new ArrayList<>(List.of("b"));
        BrokerHealthReportQuery query = report(targets);
        targets.add("c");
        assertThat(query.brokerIds()).containsExactly("b");
        assertThatThrownBy(() -> query.brokerIds().add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report(List.of("b", "b"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report(List.of(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private BrokerHealthReportQuery report(List<String> targets) {
        return new BrokerHealthReportQuery("cluster", "a", "b", 0L, 0L, "nonce", targets);
    }
}
