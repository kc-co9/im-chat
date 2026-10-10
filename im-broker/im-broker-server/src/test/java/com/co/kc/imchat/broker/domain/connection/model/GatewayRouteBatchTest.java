package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.HashUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRouteBatchTest {
    @Test
    void chunkIndexRejectsMissingAndNegativeValues() {
        assertThatThrownBy(() -> new GatewayRouteChunkIndex(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewayRouteChunkIndex(-1)).isInstanceOf(IllegalStateException.class);
        assertThat(new GatewayRouteChunkIndex(0).value()).isZero();
    }

    @Test
    void collectsOutOfOrderAndDoesNotChargeRepeatedChunks() {
        GatewayRouteBatch batch = batch("gateway-a", 7L, digest(101L, 102L));
        List<UserId> second = new ArrayList<>(List.of(new UserId(102L)));
        assertThat(batch.collect(batch, new GatewayRouteChunkIndex(1), second)).isEqualTo(Long.BYTES);
        second.clear();
        assertThat(batch.complete()).isFalse();
        assertThat(batch.contentVerified()).isFalse();
        assertThatThrownBy(batch::orderedUsers).isInstanceOf(IllegalStateException.class);
        assertThat(batch.collect(batch, new GatewayRouteChunkIndex(1), List.of(new UserId(102L)))).isZero();
        assertThat(batch.collect(batch, new GatewayRouteChunkIndex(0), List.of(new UserId(101L)))).isEqualTo(Long.BYTES);
        assertThat(batch.bytes()).isEqualTo(2L * Long.BYTES);
        assertThat(batch.complete()).isTrue();
        assertThat(batch.contentVerified()).isTrue();
        assertThat(batch.orderedUsers()).containsExactly(new UserId(101L), new UserId(102L));
        assertThatThrownBy(() -> batch.orderedUsers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsConflictingIdentityMetadataAndRepeatedContentWithoutMutation() {
        GatewayRouteBatch batch = batch("gateway-a", 7L, digest(101L, 102L));
        batch.collect(batch, new GatewayRouteChunkIndex(0), List.of(new UserId(101L)));
        assertThat(batch.accepts(batch("gateway-b", 7L, digest(101L, 102L)), new GatewayRouteChunkIndex(1), List.of(new UserId(102L)))).isFalse();
        assertThat(batch.accepts(batch("gateway-a", 8L, digest(101L, 102L)), new GatewayRouteChunkIndex(1), List.of(new UserId(102L)))).isFalse();
        assertThat(batch.accepts(batch("gateway-a", 7L, digest(999L)), new GatewayRouteChunkIndex(1), List.of(new UserId(102L)))).isFalse();
        assertThatThrownBy(() -> batch.collect(batch, new GatewayRouteChunkIndex(0), List.of(new UserId(999L))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch.collect(batch, new GatewayRouteChunkIndex(2), List.of(new UserId(102L))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(batch.bytes()).isEqualTo((long) Long.BYTES);
        batch.collect(batch, new GatewayRouteChunkIndex(1), List.of(new UserId(102L)));
        assertThat(batch.contentVerified()).isTrue();
    }

    @Test
    void completeBatchStillRequiresMatchingDigest() {
        GatewayRouteBatch batch = batch("gateway-a", 7L, digest(999L));
        batch.collect(batch, new GatewayRouteChunkIndex(0), List.of(new UserId(101L)));
        batch.collect(batch, new GatewayRouteChunkIndex(1), List.of(new UserId(102L)));
        assertThat(batch.complete()).isTrue();
        assertThat(batch.contentVerified()).isFalse();
    }

    private GatewayRouteBatch batch(String gatewayId, long epoch, GatewayRouteSnapshotDigest digest) {
        return GatewayRouteBatch.builder()
                .batchId(new GatewayRouteBatchId(new GatewayId(gatewayId), "batch-1"))
                .routingEpoch(new RoutingEpoch(epoch)).routeRevision(new RouteRevision(3L))
                .routeBrokerId(new BrokerId("broker-a")).chunkCount(2).contentDigest(digest)
                .updateExpiresAt(Instant.ofEpochMilli(5_000L)).build();
    }

    private GatewayRouteSnapshotDigest digest(Long... users) {
        return new GatewayRouteSnapshotDigest(HashUtils.sha256HexLongs(List.of(users)));
    }
}
