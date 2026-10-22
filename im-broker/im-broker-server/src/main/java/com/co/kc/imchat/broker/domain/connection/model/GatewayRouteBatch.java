package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.domain.user.model.UserId;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.HashUtils;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 某个 Gateway 的一轮完整路由上报实体，逐步收集分片并保护批次内容一致性。
 * 相同分片幂等接受，身份、代次、水位、目标、分片总数、摘要或事件期限错配时拒绝收集。
 * 只有收齐且摘要匹配的内容才能用于更新路由；本实体不读写连接表，也不管理全局容量和线程锁。
 */
@Getter
@Accessors(fluent = true)
public final class GatewayRouteBatch {
    /* 同一 Gateway 的本轮上报身份。 */
    private GatewayRouteBatchId batchId;
    /* 本轮目标路由代次。 */
    private RoutingEpoch routingEpoch;
    /* 本轮完整路由的修订水位。 */
    private RouteRevision routeRevision;
    /* 本轮接收 Broker。 */
    private BrokerId routeBrokerId;
    /* 完整上报的分片数量。 */
    private Integer chunkCount;
    /* 完整有序用户内容的预期摘要。 */
    private GatewayRouteSnapshotDigest contentDigest;
    /* 本轮路由事件有效截止时间。 */
    private Instant updateExpireAt;
    /* 已接收用户载荷的估算字节，由应用层用于全局容量统计。 */
    private Long bytes = 0L;
    /* 已收集的分片；不向调用方暴露可变索引。 */
    @Getter(lombok.AccessLevel.NONE)
    private final Map<GatewayRouteChunkIndex, List<UserId>> chunks = new HashMap<>();

    private GatewayRouteBatch() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 判断输入是否属于本批次且不会改写已接收的同序号分片。 */
    public boolean accepts(GatewayRouteBatch incoming, GatewayRouteChunkIndex chunkIndex, List<UserId> users) {
        List<UserId> existing = chunks.get(chunkIndex);
        return chunkIndex.value() < chunkCount
                && batchId.equals(incoming.batchId)
                && routingEpoch.equals(incoming.routingEpoch) && routeRevision.equals(incoming.routeRevision)
                && routeBrokerId.equals(incoming.routeBrokerId) && chunkCount.equals(incoming.chunkCount)
                && contentDigest.equals(incoming.contentDigest) && updateExpireAt.equals(incoming.updateExpireAt)
                && (existing == null || existing.equals(users));
    }

    /** 是否已收集此序号，重复分片无需额外占用容量。 */
    public boolean containsChunk(GatewayRouteChunkIndex chunkIndex) {
        return chunks.containsKey(chunkIndex);
    }

    /** 收集匹配分片，返回新增估算字节；相同重复分片返回零，拒绝不修改本实体。 */
    public long collect(GatewayRouteBatch incoming, GatewayRouteChunkIndex chunkIndex, List<UserId> users) {
        if (!accepts(incoming, chunkIndex, users)) {
            throw new IllegalArgumentException("Gateway route chunk conflicts with batch");
        }
        if (containsChunk(chunkIndex)) {
            return 0L;
        }
        List<UserId> ownedUsers = List.copyOf(users);
        long addedBytes = Math.multiplyExact((long) ownedUsers.size(), Long.BYTES);
        long totalBytes = Math.addExact(bytes, addedBytes);
        chunks.put(chunkIndex, ownedUsers);
        bytes = totalBytes;
        return addedBytes;
    }

    public boolean complete() {
        return chunks.size() == chunkCount;
    }

    /** 仅完整批次允许按序号组装，返回不可变内容，不暴露内部集合。 */
    public List<UserId> orderedUsers() {
        if (!complete()) {
            throw new IllegalStateException("Gateway route batch is incomplete");
        }
        List<UserId> users = new ArrayList<>();
        for (int index = 0; index < chunkCount; index++) {
            users.addAll(chunks.get(new GatewayRouteChunkIndex(index)));
        }
        return List.copyOf(users);
    }

    /** 收齐后核对完整内容摘要，不改变已收集分片。 */
    public boolean contentVerified() {
        return complete() && contentDigest.value().equals(
                HashUtils.sha256HexLongs(orderedUsers().stream().map(UserId::value).toList()));
    }

    public static final class Builder {
        private final GatewayRouteBatch batch = new GatewayRouteBatch();

        public Builder batchId(GatewayRouteBatchId value) { batch.batchId = value; return this; }
        public Builder routingEpoch(RoutingEpoch value) { batch.routingEpoch = value; return this; }
        public Builder routeRevision(RouteRevision value) { batch.routeRevision = value; return this; }
        public Builder routeBrokerId(BrokerId value) { batch.routeBrokerId = value; return this; }
        public Builder chunkCount(Integer value) { batch.chunkCount = value; return this; }
        public Builder contentDigest(GatewayRouteSnapshotDigest value) { batch.contentDigest = value; return this; }
        public Builder updateExpireAt(Instant value) { batch.updateExpireAt = value; return this; }

        public GatewayRouteBatch build() {
            AssertUtils.allDomainPropNotNull("Gateway route batch is incomplete", batch.batchId,
                    batch.routingEpoch, batch.routeRevision, batch.routeBrokerId, batch.chunkCount,
                    batch.contentDigest, batch.updateExpireAt);
            AssertUtils.domainPropTrue("Gateway route chunk count must be positive", batch.chunkCount > 0);
            return batch;
        }
    }
}
