package com.co.kc.imchat.broker.sdk.model;

import com.co.kc.imchat.broker.sdk.access.BrokerObservationRequest;
import com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import com.co.kc.imchat.broker.sdk.model.result.BrokerProbeResult;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.co.kc.imchat.common.exception.SerializationException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BrokerObservationContractTest {
    @Test
    void roleNamedGettersAndJsonRoundTrip() {
        BrokerProbeParams probe = new BrokerProbeParams("cluster", "caller", "receiver", 1L, 2L, "nonce");
        BrokerHealthReportParams request = new BrokerHealthReportParams("cluster", "caller", "receiver", 1L, 2L, "nonce", List.of("observed"));
        BrokerHealthReportRow row = new BrokerHealthReportRow("observed", BrokerHealthStatus.UNHEALTHY, 10L, 30000L);
        BrokerHealthReportResult result = new BrokerHealthReportResult("cluster", "observer", 1L, 2L, "nonce", List.of(row));
        assertThat(((BrokerObservationRequest) probe).receiverBrokerId()).isEqualTo("receiver");
        assertThat(request.receiverBrokerId()).isEqualTo("receiver");
        assertThat(request.brokerIds()).containsExactly("observed");
        assertThat(result.observerBrokerId()).isEqualTo("observer");
        assertThat(row.brokerId()).isEqualTo("observed");
        assertJsonRole(probe, BrokerProbeParams.class, "receiverBrokerId", "targetBrokerId");
        assertJsonRole(request, BrokerHealthReportParams.class, "receiverBrokerId", "targetBrokerId");
        assertJsonRole(request, BrokerHealthReportParams.class, "brokerIds", "targetBrokerIds");
        assertJsonRole(result, BrokerHealthReportResult.class, "observerBrokerId", "reporterBrokerId");
        assertJsonRole(row, BrokerHealthReportRow.class, "brokerId", "targetBrokerId");
        BrokerProbeResult response = new BrokerProbeResult("cluster", "receiver", 1L, 2L, "nonce");
        assertThat(response.brokerId()).isEqualTo("receiver");
        assertThat(JsonUtils.toJson(response)).contains("\"brokerId\":\"receiver\"").doesNotContain("receiverBrokerId");
        assertThat(JsonUtils.fromJson(JsonUtils.toJson(response), BrokerProbeResult.class)).isEqualTo(response);
    }

    private <T> void assertJsonRole(T value, Class<T> type, String field, String oldField) {
        ObjectNode tree = JsonUtils.getMapper().valueToTree(value);
        assertThat(tree.has(field)).isTrue();
        assertThat(tree.has(oldField)).isFalse();
        assertThat(JsonUtils.fromJson(tree.toString(), type)).isEqualTo(value);
        tree.set(oldField, tree.remove(field));
        assertThatThrownBy(() -> JsonUtils.fromJson(tree.toString(), type))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void observationWireContractsContainNoAbsoluteExpiry() {
        for (Class<?> contract : List.of(BrokerProbeParams.class, BrokerHealthReportParams.class,
                BrokerProbeResult.class, BrokerHealthReportResult.class)) {
            assertThat(contract.getRecordComponents()).extracting(RecordComponent::getName)
                    .doesNotContain("expireAt", "expireAtMs");
        }
        assertThat(BrokerObservationRequest.class.getMethods())
                .extracting(Method::getName).doesNotContain("expireAt", "expireAtMs");
        assertThatThrownBy(() -> new BrokerProbeParams("cluster", " ", "b", 1L, 1L, "nonce"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializedObservationRequestsAndResultsHaveNoExpiryField() {
        for (Object value : List.of(
                new BrokerProbeParams("cluster", "a", "b", 1L, 2L, "nonce"),
                new BrokerHealthReportParams("cluster", "a", "b", 1L, 2L, "nonce", List.of("b")),
                new BrokerProbeResult("cluster", "b", 1L, 2L, "nonce"),
                new BrokerHealthReportResult("cluster", "b", 1L, 2L, "nonce", List.of(
                        new BrokerHealthReportRow("b", BrokerHealthStatus.UNHEALTHY, 10L, 30000L))))) {
            assertThat(JsonUtils.toJson(value)).contains("\"clusterId\":\"cluster\"", "\"nonce\":\"nonce\"")
                    .doesNotContain("expireAt", "expireAtMs");
        }
    }

    @Test
    void missingRevisionAndNegativeEpochAreRejected() {
        assertThatThrownBy(() -> new BrokerProbeParams("cluster", "a", "b", null, 1L, "nonce"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision");
        assertThatThrownBy(() -> new BrokerProbeParams("cluster", "a", "b", 1L, -1L, "nonce"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("epoch");
    }
    @Test
    void reportTargetsAreImmutableAndUnique() {
        List<String> targets = new ArrayList<>(List.of("b"));
        BrokerHealthReportParams request = new BrokerHealthReportParams("cluster", "a", "b", 1L, 1L, "nonce", targets);
        targets.clear();
        assertThat(request.brokerIds()).containsExactly("b");
        assertThatThrownBy(() -> request.brokerIds().add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new BrokerHealthReportParams("cluster", "a", "b", 1L, 1L, "nonce", List.of("b", "b")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    }
    @Test
    void rowsRejectMissingNegativeAndContradictoryDurations() {
        assertThatThrownBy(() -> new BrokerHealthReportRow("b", BrokerHealthStatus.UNHEALTHY, null, 30L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerHealthReportRow("b", BrokerHealthStatus.UNHEALTHY, -1L, 30L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerHealthReportRow("b", BrokerHealthStatus.HEALTHY, 0L, 30L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new BrokerHealthReportRow("b", BrokerHealthStatus.UNKNOWN, 0L, 0L).unreachableDurationMs()).isZero();
    }
    @Test
    void responseSnapshotIsImmutableAndRejectsDuplicateTargets() {
        List<BrokerHealthReportRow> rows = new ArrayList<>(List.of(
                new BrokerHealthReportRow("b", BrokerHealthStatus.UNHEALTHY, 1L, 30000L)));
        BrokerHealthReportResult result =
                new BrokerHealthReportResult("cluster", "a", 1L, 7L, "nonce", rows);
        rows.clear();
        assertThat(result.observations()).containsExactly(new BrokerHealthReportRow("b", BrokerHealthStatus.UNHEALTHY, 1L, 30000L));
        assertThatThrownBy(() -> result.observations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new BrokerHealthReportResult(
                "cluster", "a", 1L, 7L, "nonce", List.of(
                new BrokerHealthReportRow("b", BrokerHealthStatus.HEALTHY, 0L, 0L),
                new BrokerHealthReportRow("b", BrokerHealthStatus.UNHEALTHY, 0L, 30000L))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    }
}
