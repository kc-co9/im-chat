package com.co.kc.imchat.broker.sdk.client;

import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.DrainRouteCleanupParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerTimeContractTest {
    private static final long EXPIRE_AT_MS = 1_700_000_000_123L;

    @Test
    void numericWireTimesUseExactMillisecondsAndOnlyNewKeys() {
        assertWireTime(new RouteRequestContext(3L, "operation", EXPIRE_AT_MS),
                "expireAtMs", "deadlineEpochMillis", EXPIRE_AT_MS);
        assertWireTime(new RouteLookupParams("broker", 3L, 1L, EXPIRE_AT_MS, 1),
                "expireAtMs", "deadlineEpochMillis", EXPIRE_AT_MS);
        assertWireTime(new GatewayRouteSnapshotParams("gateway", 3L, 4L, "broker",
                "batch", 0, 1, "checksum", EXPIRE_AT_MS, List.of(1L)),
                "updateExpireAtMs", "eventDeadline", EXPIRE_AT_MS);
        assertWireTime(new GatewayRouteSnapshotAckParams("gateway", 3L, 4L, 1L,
                "checksum", EXPIRE_AT_MS), "updateExpireAtMs", "eventDeadline", EXPIRE_AT_MS);
        assertWireTime(new DrainRouteCleanupParams("broker", "operation", 2L, 3L,
                EXPIRE_AT_MS, List.of()), "oldEventExpireAtMs", "eventDeadline", EXPIRE_AT_MS);
        assertWireTime(AcceptGatewayRoutesResult.retryAfter(3L, 127L),
                "retryAfterMs", "retryAfterMillis", 127L);
    }

    @Test
    void requiredRequestExpiriesRemainStrictlyPositive() {
        for (long expireAtMs : List.of(-1L, 0L)) {
            assertThatThrownBy(() -> new RouteRequestContext(3L, "operation", expireAtMs))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GatewayRouteSnapshotParams("gateway", 3L, 4L,
                    "broker", "batch", 0, 1, "checksum", expireAtMs, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GatewayRouteSnapshotAckParams("gateway", 3L, 4L,
                    0L, "checksum", expireAtMs)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new DrainRouteCleanupParams("broker", "operation", 2L,
                    3L, expireAtMs, List.of())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new RouteRequestContext(3L, "operation", 1L).expireAtMs()).isEqualTo(1L);
    }

    @Test
    void changingRoutingEpochPreservesOriginalExpiryAndOperationId() {
        RouteRequestContext original = new RouteRequestContext(3L, "operation", EXPIRE_AT_MS);
        assertThat(original.withRoutingEpoch(4L))
                .isEqualTo(new RouteRequestContext(4L, "operation", EXPIRE_AT_MS));
    }

    @Test
    void existingServiceObservationContractsRetainInstantPrecisionAndKeys() {
        Instant registeredAt = Instant.parse("2020-01-02T03:04:05.123456Z");
        Instant lastSeenAt = registeredAt.plusMillis(1L);
        BrokerEndpointDTO broker = new BrokerEndpointDTO("broker", "host", 18020,
                registeredAt, lastSeenAt);
        GatewayEndpointDTO gateway = new GatewayEndpointDTO("gateway", "host", 18021,
                "identity", registeredAt, lastSeenAt);
        for (Object dto : List.of(broker, gateway)) {
            ObjectNode payload = JsonUtils.getMapper().valueToTree(dto);
            assertThat(payload.has("registeredAt")).isTrue();
            assertThat(payload.has("lastSeenAt")).isTrue();
            assertThat(payload.has("registeredAtMs")).isFalse();
            assertThat(payload.has("lastSeenAtMs")).isFalse();
            assertThat(JsonUtils.fromJson(payload.toString(), dto.getClass())).isEqualTo(dto);
        }
    }

    private void assertWireTime(Object value, String key, String oldKey, long expectedMs) {
        ObjectNode payload = JsonUtils.getMapper().valueToTree(value);
        assertThat(payload.path(key).isIntegralNumber()).isTrue();
        assertThat(payload.path(key).longValue()).isEqualTo(expectedMs);
        assertThat(payload.has(oldKey)).isFalse();
        assertThat(payload.has("oldEventDeadline")).isFalse();
        assertThat(JsonUtils.fromJson(payload.toString(), value.getClass())).isEqualTo(value);
    }
}
