package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.domain.user.model.UserId;

import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;

/**
 * GatewayRouteTable 聚合内的连接路由实体，不是 Gateway 持有的 WebSocket 连接。
 *
 * <p>Broker 通过用户和 Gateway 的组合身份定位这条记录：ACTIVE 表示该用户可经此 Gateway 投递；
 * TOMBSTONE 保留已删除路由的修订号，阻止迟到的旧注册更新重新创建路由。GatewayRouteTable 统一决定
 * 注册、注销和完整快照如何更新记录，并在删除得到确认、旧更新失效和宽限期届满后回收 tombstone。</p>
 */
@Getter
@Accessors(fluent = true)
public final class ConnectionRegistration {
    /**
     * 连接路由实体身份。
     */
    private ConnectionRouteId id;
    /**
     * 当前路由状态的修订号，用于拒绝较旧的更新。
     */
    private RouteRevision revision;
    /**
     * 形成当前状态的路由更新有效截止时刻；不表示 ACTIVE 连接的断开时刻。
     */
    private Instant updateExpireAt;
    /**
     * 连接路由生命周期状态。
     */
    private ConnectionRouteStatus status;
    /**
     * 删除标记的创建时刻，用于计算回收宽限期；ACTIVE 时为空。
     */
    private Instant tombstonedAt;
    /**
     * 覆盖此删除标记的快照修订号或 drain 清理确认水位；确认前为空。
     */
    private RouteRevision coverageRevision;
    /* 首次形成活跃注册的时间，更新 revision 不重置。 */
    private Instant connectedAt;
    /* 最近一次已接受路由更新的时间。 */
    private Instant refreshedAt;

    private ConnectionRegistration(ConnectionRouteId id,
                            RouteRevision revision,
                            Instant updateExpireAt,
                            ConnectionRouteStatus status,
                            Instant tombstonedAt,
                            RouteRevision coverageRevision) {
        AssertUtils.allDomainPropNotNull("connection route is incomplete", id, revision, updateExpireAt, status);
        AssertUtils.domainPropTrue("active route cannot contain tombstone evidence",
                status != ConnectionRouteStatus.ACTIVE || (tombstonedAt == null && coverageRevision == null));
        AssertUtils.domainPropTrue("tombstone route requires creation time",
                status != ConnectionRouteStatus.TOMBSTONE || tombstonedAt != null);
        this.id = id;
        this.revision = revision;
        this.updateExpireAt = updateExpireAt;
        this.status = status;
        this.tombstonedAt = tombstonedAt;
        this.coverageRevision = coverageRevision;
        this.connectedAt = Instant.now();
        this.refreshedAt = connectedAt;
    }

    public static ConnectionRegistration active(ConnectionRouteId id,
                                  RouteRevision revision,
                                  Instant updateExpireAt) {
        return new ConnectionRegistration(id, revision, updateExpireAt,
                ConnectionRouteStatus.ACTIVE, null, null);
    }

    static ConnectionRegistration tombstone(ConnectionRouteId id,
                                     RouteRevision revision,
                                     Instant updateExpireAt,
                                     Instant tombstonedAt,
                                     RouteRevision coverageRevision) {
        return new ConnectionRegistration(id, revision, updateExpireAt,
                ConnectionRouteStatus.TOMBSTONE, tombstonedAt, coverageRevision);
    }

    static ConnectionRegistration tombstone(ConnectionRouteId id,
                                     RouteRevision revision,
                                     Instant updateExpireAt,
                                     Instant tombstonedAt) {
        return new ConnectionRegistration(id, revision, updateExpireAt,
                ConnectionRouteStatus.TOMBSTONE, tombstonedAt, null);
    }

    /** 按已有身份和时间恢复注册观察，时间字段仍与路由版本属于同一实体。 */
    public static Builder builder() {
        return new Builder();
    }

    private ConnectionRegistration() {
    }

    public static final class Builder {
        private final ConnectionRegistration registration = new ConnectionRegistration();

        public Builder id(ConnectionRouteId value) { registration.id = value; return this; }
        public Builder revision(RouteRevision value) { registration.revision = value; return this; }
        public Builder updateExpireAt(Instant value) { registration.updateExpireAt = value; return this; }
        public Builder connectedAt(Instant value) { registration.connectedAt = value; return this; }
        public Builder refreshedAt(Instant value) { registration.refreshedAt = value; return this; }

        public ConnectionRegistration build() {
            AssertUtils.allDomainPropNotNull("connection registration is incomplete",
                    registration.id, registration.revision, registration.updateExpireAt,
                    registration.connectedAt, registration.refreshedAt);
            registration.status = ConnectionRouteStatus.ACTIVE;
            return registration;
        }
    }

    public UserId userId() {
        return id.userId();
    }

    public GatewayId gatewayId() {
        return id.gatewayId();
    }

    public boolean active() {
        return status == ConnectionRouteStatus.ACTIVE;
    }

    /**
     * 判断当前路由是否来自严格更新的 revision，可阻止较旧快照覆盖当前状态。
     */
    boolean supersedes(RouteRevision candidateRevision) {
        return revision.compareTo(candidateRevision) > 0;
    }

    /**
     * 判断当前路由 revision 是否已经达到候选 revision，覆盖相同 revision 的重复提交。
     */
    boolean hasRevisionAtLeast(RouteRevision candidateRevision) {
        return revision.compareTo(candidateRevision) >= 0;
    }

    /**
     * 应用不早于当前状态的 ACTIVE 更新。
     */
    ConnectionRegistration acceptActiveUpdate(RouteRevision updateRevision,
                                       Instant updateExpireAt) {
        AssertUtils.domainPropTrue("active update is older than connection route",
                !supersedes(updateRevision));
        ConnectionRegistration updated = active(id, updateRevision, updateExpireAt);
        updated.connectedAt = connectedAt;
        return updated;
    }

    /**
     * 应用严格更新的注销，将当前路由转换为尚未被快照覆盖的 tombstone。
     */
    ConnectionRegistration acceptRemovalUpdate(RouteRevision updateRevision,
                                        Instant updateExpireAt,
                                        Instant now) {
        AssertUtils.domainPropTrue("removal update is not newer than connection route", !hasRevisionAtLeast(updateRevision));
        ConnectionRegistration removed = tombstone(id, updateRevision, updateExpireAt, now);
        removed.connectedAt = connectedAt;
        removed.refreshedAt = now;
        return removed;
    }

    /**
     * 接受完整快照确认的删除，将缺失路由转换为已被该快照覆盖的 tombstone。
     */
    ConnectionRegistration acceptSnapshotRemoval(RouteRevision snapshotRevision,
                                          Instant updateExpireAt,
                                          Instant now) {
        AssertUtils.domainPropTrue("snapshot is older than connection route",
                !supersedes(snapshotRevision));
        ConnectionRegistration removed = tombstone(id, revision.max(snapshotRevision), updateExpireAt, now, snapshotRevision);
        removed.connectedAt = connectedAt;
        removed.refreshedAt = now;
        return removed;
    }

    /**
     * 判断当前 ACTIVE 路由是否位于 drain 已确认的清理水位内。
     */
    boolean canPurgeAt(RouteRevision confirmedRevision) {
        return active() && revision.compareTo(confirmedRevision) <= 0;
    }

    /**
     * 将当前 ACTIVE 路由转换为已由 drain 水位确认删除的 tombstone。
     */
    ConnectionRegistration purgeAt(RouteRevision confirmedRevision,
                            Instant updateExpireAt,
                            Instant now) {
        AssertUtils.domainPropTrue("connection route is outside purge watermark",
                canPurgeAt(confirmedRevision));
        ConnectionRegistration removed = tombstone(id, confirmedRevision, updateExpireAt, now, confirmedRevision);
        removed.connectedAt = connectedAt;
        removed.refreshedAt = now;
        return removed;
    }

    /**
     * 判断 tombstone 是否同时满足覆盖水位、更新有效期和回收宽限期，可从聚合索引中移除。
     */
    boolean canCollect(Instant now, Duration gracePeriod) {
        return status == ConnectionRouteStatus.TOMBSTONE
                && coverageRevision != null
                && coverageRevision.compareTo(revision) >= 0
                && !now.isBefore(updateExpireAt)
                && !now.isBefore(tombstonedAt.plus(gracePeriod));
    }
}
