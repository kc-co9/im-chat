package com.co.kc.imchat.broker.infrastructure.registry.connection;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.embedded.CaffeineCacheBuilder;
import com.co.kc.imchat.broker.domain.connection.model.RouteCleanupId;
import com.co.kc.imchat.broker.domain.connection.model.RouteCleanupCheckpoint;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryBatchConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.support.RouteCacheStub;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.connection.repository.BatchConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatch;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatchId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteBatchReception;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteChunkIndex;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.broker.domain.connection.model.UserId;

import static com.co.kc.imchat.broker.support.BrokerRoutingPropertiesFixture.routingProperties;
import com.co.kc.imchat.common.utils.HashUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class InMemoryBatchConnectionRegistryTest {
    @Test
    void jetCacheKeepsTheSamePendingBatchInstanceAcrossChunks() {
        Cache<GatewayRouteBatchId, GatewayRouteBatch> pending = CaffeineCacheBuilder.createCaffeineCacheBuilder()
                .limit(2).expireAfterWrite(10, TimeUnit.SECONDS).keyConvertor(key -> key).buildCache();
        Cache<RouteCleanupId, RouteCleanupCheckpoint> cleanups = CaffeineCacheBuilder.createCaffeineCacheBuilder()
                .limit(2).expireAfterWrite(10, TimeUnit.SECONDS).keyConvertor(key -> key).buildCache();
        InMemoryBatchConnectionRegistry registry = new InMemoryBatchConnectionRegistry(
                new InMemoryConnectionRegistry(), routingProperties(2, 1024L, Duration.ofSeconds(10), 2),
                pending, cleanups);
        GatewayRouteBatch batch = GatewayRouteBatch.builder()
                .batchId(new GatewayRouteBatchId(new GatewayId("gateway-a"), "batch-1"))
                .routingEpoch(new RoutingEpoch(7L)).routeRevision(new RouteRevision(3L))
                .routeBrokerId(new BrokerId("broker-a")).chunkCount(2)
                .contentDigest(new GatewayRouteSnapshotDigest(HashUtils.sha256HexLongs(List.of(101L, 102L))))
                .updateExpiresAt(Instant.MAX).build();
        try {
            assertThat(registry.acceptBatch(batch, new GatewayRouteChunkIndex(0), List.of(new UserId(101L))).status())
                    .isEqualTo(GatewayRouteBatchReception.Status.CHUNK_ACCEPTED);
            assertThat(pending.get(batch.batchId())).isSameAs(batch);
            assertThat(registry.acceptBatch(batch, new GatewayRouteChunkIndex(1), List.of(new UserId(102L))).status())
                    .isEqualTo(GatewayRouteBatchReception.Status.COMPLETE);
            assertThat(pending.get(batch.batchId())).isNull();
        } finally {
            pending.close();
            cleanups.close();
        }
    }

    @Test
    void reusesCleanupCountsUntilTheOldestCheckpointIsEvicted() {
        InMemoryConnectionRegistry storage = spy(new InMemoryConnectionRegistry());
        storage.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-a")), new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        storage.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(102L), new GatewayId("gateway-b")), new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        BatchConnectionRegistry registry = new InMemoryBatchConnectionRegistry(storage,
                routingProperties(1, 1024L, Duration.ofSeconds(10), 1),
                RouteCacheStub.local(Duration.ofSeconds(10), 1), RouteCacheStub.local(Duration.ofDays(3650), 1));
        ClusterOperationId operationId = new ClusterOperationId("drain-1");
        Instant deadline = Instant.ofEpochMilli(Long.MAX_VALUE);
        List<DrainProgressUpdate> firstGateway = List.of(confirmation("gateway-a", 3L));

        assertThat(registry.cleanupRoutes(operationId, firstGateway, deadline).removedRouteCount())
                .isEqualTo(new RouteCount(1L));
        assertThat(registry.cleanupRoutes(operationId, firstGateway, deadline).removedRouteCount())
                .isEqualTo(new RouteCount(1L));
        verify(storage, times(1)).purgeRoutes(new GatewayId("gateway-a"), new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE));

        registry.cleanupRoutes(operationId, List.of(confirmation("gateway-b", 3L)), deadline);
        assertThat(registry.cleanupRoutes(operationId, firstGateway, deadline).removedRouteCount())
                .isEqualTo(RouteCount.ZERO);
        verify(storage, times(2)).purgeRoutes(new GatewayId("gateway-a"), new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE));
        assertThat(registry.count()).isZero();
    }

    @Test
    void conflictingRetryWatermarkDoesNotDeleteNewerRoutes() {
        InMemoryConnectionRegistry storage = new InMemoryConnectionRegistry();
        storage.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(101L), new GatewayId("gateway-a")), new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        BatchConnectionRegistry registry = new InMemoryBatchConnectionRegistry(storage,
                routingProperties(1, 1024L, Duration.ofSeconds(10), 10),
                RouteCacheStub.local(Duration.ofSeconds(10), 1), RouteCacheStub.local(Duration.ofDays(3650), 10));
        ClusterOperationId operationId = new ClusterOperationId("drain-1");
        Instant deadline = Instant.ofEpochMilli(Long.MAX_VALUE);
        registry.cleanupRoutes(operationId, List.of(confirmation("gateway-a", 3L)), deadline);
        storage.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(102L), new GatewayId("gateway-a")), new RouteRevision(4L), Instant.ofEpochMilli(Long.MAX_VALUE)));

        assertThatThrownBy(() -> registry.cleanupRoutes(operationId,
                List.of(confirmation("gateway-a", 4L)), deadline))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("drain cleanup watermark changed");
        assertThat(registry.find(new UserId(102L))).hasSize(1);
    }

    @Test
    void batchAndOrdinaryWritesShareTheSameStoredRoutes() {
        InMemoryConnectionRegistry storage = new InMemoryConnectionRegistry();
        BatchConnectionRegistry batches = new InMemoryBatchConnectionRegistry(storage,
                routingProperties(1, 1024L, Duration.ofSeconds(10), 10),
                RouteCacheStub.local(Duration.ofSeconds(10), 1), RouteCacheStub.local(Duration.ofDays(3650), 10));
        ConnectionRegistry ordinary = batches;
        GatewayId gatewayId = new GatewayId("gateway-a");
        GatewayRouteBatch batch = GatewayRouteBatch.builder()
                .batchId(new GatewayRouteBatchId(gatewayId, "batch-1"))
                .routingEpoch(new RoutingEpoch(7L))
                .routeRevision(new RouteRevision(3L))
                .routeBrokerId(new BrokerId("broker-a"))
                .chunkCount(1)
                .contentDigest(new GatewayRouteSnapshotDigest(HashUtils.sha256HexLongs(List.of(101L))))
                .updateExpiresAt(Instant.ofEpochMilli(Long.MAX_VALUE)).build();

        GatewayRouteBatchReception reception = batches.acceptBatch(batch,
                new GatewayRouteChunkIndex(0), List.of(new UserId(101L)));
        assertThat(reception.status()).isEqualTo(GatewayRouteBatchReception.Status.COMPLETE);
        assertThat(ordinary.count()).isZero();
        batches.applyBatch(reception.batch().orElseThrow());
        assertThat(ordinary.find(new UserId(101L))).hasSize(1);
        assertThat(storage.find(new UserId(101L))).isEqualTo(ordinary.find(new UserId(101L)));

        ordinary.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(102L), gatewayId), new RouteRevision(4L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        assertThat(batches.findGatewayUsers(gatewayId)).containsExactly(new UserId(101L), new UserId(102L));
        assertThat(ordinary.find(new UserId(102L))).isEqualTo(storage.find(new UserId(102L)));
        assertThat(ordinary.purgeRoutes(gatewayId, new RouteRevision(3L), Instant.ofEpochMilli(Long.MAX_VALUE)).removed()).hasSize(1);
        assertThat(batches.findGatewayUsers(gatewayId)).containsExactly(new UserId(102L));
        ordinary.removeGateway(gatewayId);
        assertThat(storage.count()).isZero();
        assertThat(ordinary.list()).isEmpty();
    }

    private DrainProgressUpdate confirmation(String gatewayId, Long revision) {
        return DrainProgressUpdate.confirmGateway(new GatewayId(gatewayId), new RouteRevision(revision),
                new RouteCount(1L), new GatewayRouteSnapshotDigest(HashUtils.sha256HexLongs(List.of(101L))));
    }
}
