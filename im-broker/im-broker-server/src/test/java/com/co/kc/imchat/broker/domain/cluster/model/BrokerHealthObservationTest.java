package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerHealthObservationTest {
    @Test
    void missingStatusIsRejectedByBuilderAndJsonCreator() {
        assertThatThrownBy(() -> BrokerHealthObservation.builder().brokerId(new BrokerId("a"))
                .observerBrokerId(new BrokerId("b")).observedAt(Instant.EPOCH)
                .unreachableBeginAt(Instant.EPOCH).build()).isInstanceOf(IllegalStateException.class);
        ObjectNode tree = JsonUtils.getMapper()
                .valueToTree(observation(BrokerHealthStatus.UNHEALTHY));
        tree.remove("status");
        String missing = tree.toString();
        assertThatThrownBy(() -> JsonUtils.fromJson(missing, BrokerHealthObservation.class))
                .isInstanceOf(RuntimeException.class).hasRootCauseMessage("observation identity is incomplete");
    }

    @ParameterizedTest
    @EnumSource(BrokerHealthStatus.class)
    void eachStateRoundTripsWithoutInferringUnhealthy(BrokerHealthStatus status) {
        BrokerHealthObservation original = observation(status);
        BrokerHealthObservation restored = JsonUtils.fromJson(JsonUtils.toJson(original), BrokerHealthObservation.class);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.status()).isEqualTo(status);
        assertThat(restored.brokerId()).isEqualTo(new BrokerId("a"));
        assertThat(restored.observerBrokerId()).isEqualTo(new BrokerId("b"));
        assertThat(restored.observedAt()).isEqualTo(Instant.EPOCH);
        assertThat(restored.unreachableBeginAt()).isEqualTo(
                status == BrokerHealthStatus.UNHEALTHY ? Instant.EPOCH : null);
        ObjectNode tree = JsonUtils.getMapper().valueToTree(original);
        assertThat(tree.has("brokerId")).isTrue();
        assertThat(tree.has("observerBrokerId")).isTrue();
        assertThat(tree.has("targetBrokerId")).isFalse();
        assertThat(tree.has("reporterBrokerId")).isFalse();
        assertThat(tree.has("unreachableAt")).isFalse();
        if (status == BrokerHealthStatus.UNHEALTHY) {
            assertThat(tree.hasNonNull("unreachableBeginAt")).isTrue();
        }
    }

    @Test
    void oldIdentityNamesAreNotJsonAliases() {
        ObjectNode tree = JsonUtils.getMapper().valueToTree(observation(BrokerHealthStatus.UNHEALTHY));
        tree.set("targetBrokerId", tree.remove("brokerId"));
        tree.set("reporterBrokerId", tree.remove("observerBrokerId"));
        assertThatThrownBy(() -> JsonUtils.fromJson(tree.toString(), BrokerHealthObservation.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void oldFailureBeginNameIsNotAJsonAlias() {
        ObjectNode tree = JsonUtils.getMapper().valueToTree(observation(BrokerHealthStatus.UNHEALTHY));
        tree.set("unreachableAt", tree.remove("unreachableBeginAt"));
        assertThatThrownBy(() -> JsonUtils.fromJson(tree.toString(), BrokerHealthObservation.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void onlyUnhealthyCanHaveAnOrderedFailureWindow() {
        assertThatThrownBy(() -> BrokerHealthObservation.builder().brokerId(new BrokerId("a"))
                .observerBrokerId(new BrokerId("b")).status(BrokerHealthStatus.UNHEALTHY)
                .observedAt(Instant.EPOCH).build()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BrokerHealthObservation.builder().brokerId(new BrokerId("a"))
                .observerBrokerId(new BrokerId("b")).status(BrokerHealthStatus.UNHEALTHY)
                .observedAt(Instant.EPOCH).unreachableBeginAt(Instant.EPOCH.plusNanos(1)).build())
                .isInstanceOf(IllegalStateException.class);
        for (BrokerHealthStatus status : List.of(BrokerHealthStatus.HEALTHY, BrokerHealthStatus.UNKNOWN)) {
            assertThatThrownBy(() -> BrokerHealthObservation.builder().brokerId(new BrokerId("a"))
                    .observerBrokerId(new BrokerId("b")).status(status).observedAt(Instant.EPOCH)
                    .unreachableBeginAt(Instant.EPOCH).build()).isInstanceOf(IllegalStateException.class);
        }
    }

    private BrokerHealthObservation observation(BrokerHealthStatus status) {
        return BrokerHealthObservation.builder().brokerId(new BrokerId("a"))
                .observerBrokerId(new BrokerId("b")).observedAt(Instant.EPOCH).status(status)
                .unreachableBeginAt(status == BrokerHealthStatus.UNHEALTHY ? Instant.EPOCH : null).build();
    }
}
