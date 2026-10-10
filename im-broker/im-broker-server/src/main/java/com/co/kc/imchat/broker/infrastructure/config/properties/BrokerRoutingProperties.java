package com.co.kc.imchat.broker.infrastructure.config.properties;

import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Broker 路由、Lease、Gateway 同步和内部访问的部署配置；派生隔离窗口不单独绑定。 */
@Data
@ConfigurationProperties(prefix = "im.broker.routing")
public class BrokerRoutingProperties {

    /* 首次建群的 RouteBroker 选择算法；已有集群不得单节点修改。 */
    private BrokerSelectionAlgorithm algorithm =
            BrokerSelectionAlgorithm.RENDEZVOUS_HASH;
    /* 单次 Lease 可持续的最长时间，也是旧授权隔离窗口的基准。 */
    private Duration maxLeaseDuration = Duration.ofSeconds(4);
    /* 本机等待安装的 Lease prepare 记录容量；满额时按缓存策略淘汰旧申请。 */
    private int leasePendingCapacity = 16;
    /* Leader 尝试续签路由 Lease 的周期，必须短于保守签发时长。 */
    private Duration renewalInterval = Duration.ofSeconds(1);
    /* 节点墙上时钟偏差的保守上界，签发时从有效期扣除。 */
    private Duration maxClockDriftAllowance = Duration.ofMillis(100);
    /* Lease 到期前停止新路由副作用的安全余量。 */
    private Duration leaseSafetyMargin = Duration.ofMillis(200);
    /* 线程调度或进程暂停可能延迟 Lease 失效观察的上界。 */
    private Duration maxSchedulingPause = Duration.ofSeconds(2);
    /* 隔离旧授权时预留的网络传播余量。 */
    private Duration networkSafetyMargin = Duration.ofMillis(500);
    /* 单机同时暂存的 Gateway 完整路由批次数量上限。 */
    private int maxPendingSnapshotBatches = 1_024;
    /* 单机未收齐快照的估算字节总上限。 */
    private long maxPendingSnapshotBytes = 64L * 1024L * 1024L;
    /* 未收齐快照批次在本机缓存中的保留期限。 */
    private Duration snapshotBatchTimeout = Duration.ofSeconds(10);
    /* 已覆盖的注销 tombstone 超期后继续保留的宽限期。 */
    private Duration routeTombstoneGracePeriod = Duration.ofMinutes(2);
    /* Gateway 工作负载身份前缀；生产环境需与证书发行策略一致。 */
    private String gatewayWorkloadIdentityPrefix = "spiffe://im-chat/gateway/";
    /* 仅受控本地网络允许无证书 Gateway 身份；生产应保持关闭。 */
    private boolean allowTrustedNetworkGatewayIdentity;
    /* 单个 Broker peer RouteLookup RPC 的超时，所有候选并发执行。 */
    private Duration routeLookupTimeout = Duration.ofMillis(300);
    /* 离线用户连续未命中时抑制重复全量 peer 查询的短窗口。 */
    private Duration routeLookupNegativeCache = Duration.ofMillis(50);
    /* 本机未命中缓存的最大用户数，满额由缓存策略淘汰。 */
    private int routeLookupNegativeCacheCapacity = 1_024;
    /* 本机已完成 drain 清理检查点的缓存容量上限。 */
    private int drainCleanupResultCapacity = 10_000;
    /* 旧路由事件允许延迟到达的最长业务期限。 */
    private Duration maxRouteEventTtl = Duration.ofSeconds(10);
    /* 排空或故障恢复 operation 等待证据的最长时间。 */
    private Duration drainOperationTimeout = Duration.ofMinutes(2);
    /* Leader 扫描未完成 drain/recovery 操作的间隔。 */
    private Duration drainReconcileInterval = Duration.ofSeconds(1);
    /* 向旧 Broker 发起 drain 路由清理 RPC 的超时。 */
    private Duration drainCleanupRpcTimeout = Duration.ofSeconds(3);
    /* Broker peer 工作负载身份前缀；需与集群 mTLS 身份一致。 */
    private String brokerWorkloadIdentityPrefix = "spiffe://im-chat/broker/";
    /* 仅受控本地网络允许无证书 Broker 身份；生产应保持关闭。 */
    private boolean allowTrustedNetworkBrokerIdentity;
    /* 消息服务请求 Broker 数据面的受信身份前缀。 */
    private String messageWorkloadIdentityPrefix = "spiffe://im-chat/message/";
    /* 账户服务请求 Broker 数据面的受信身份前缀。 */
    private String accountWorkloadIdentityPrefix = "spiffe://im-chat/account/";
    /* 运维服务请求 Broker 管理数据面的受信身份前缀。 */
    private String opsWorkloadIdentityPrefix = "spiffe://im-chat/im-ops/";
    /* 仅受控本地网络允许无证书服务身份；生产应保持关闭。 */
    private boolean allowTrustedNetworkServiceIdentity;

    public void validate() {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        if (gatewayWorkloadIdentityPrefix == null || gatewayWorkloadIdentityPrefix.isBlank()) {
            throw new IllegalArgumentException("gatewayWorkloadIdentityPrefix must not be blank");
        }
        if (brokerWorkloadIdentityPrefix == null || brokerWorkloadIdentityPrefix.isBlank()) {
            throw new IllegalArgumentException("brokerWorkloadIdentityPrefix must not be blank");
        }
        if (messageWorkloadIdentityPrefix == null || messageWorkloadIdentityPrefix.isBlank()
                || accountWorkloadIdentityPrefix == null || accountWorkloadIdentityPrefix.isBlank()
                || opsWorkloadIdentityPrefix == null || opsWorkloadIdentityPrefix.isBlank()) {
            throw new IllegalArgumentException("service workload identity prefixes must not be blank");
        }
        requirePositive(maxLeaseDuration, "maxLeaseDuration");
        requirePositive(renewalInterval, "renewalInterval");
        requireNonNegative(maxClockDriftAllowance, "maxClockDriftAllowance");
        requireNonNegative(leaseSafetyMargin, "leaseSafetyMargin");
        requireNonNegative(maxSchedulingPause, "maxSchedulingPause");
        requireNonNegative(networkSafetyMargin, "networkSafetyMargin");
        requirePositive(snapshotBatchTimeout, "snapshotBatchTimeout");
        requireNonNegative(routeTombstoneGracePeriod, "routeTombstoneGracePeriod");
        requirePositive(routeLookupTimeout, "routeLookupTimeout");
        requireNonNegative(routeLookupNegativeCache, "routeLookupNegativeCache");
        requirePositive(maxRouteEventTtl, "maxRouteEventTtl");
        requirePositive(drainOperationTimeout, "drainOperationTimeout");
        requirePositive(drainReconcileInterval, "drainReconcileInterval");
        requirePositive(drainCleanupRpcTimeout, "drainCleanupRpcTimeout");
        if (leasePendingCapacity <= 0 || routeLookupNegativeCacheCapacity <= 0 || drainCleanupResultCapacity <= 0) {
            throw new IllegalArgumentException("routing capacities must be positive");
        }
        if (maxPendingSnapshotBatches <= 0 || maxPendingSnapshotBytes <= 0L) {
            throw new IllegalArgumentException("snapshot pending limits must be positive");
        }
        if (leaseGrantDuration().isZero() || leaseGrantDuration().isNegative()) {
            throw new IllegalArgumentException(
                    "clock drift and lease safety margin consume maxLeaseDuration");
        }
        if (renewalInterval.compareTo(leaseGrantDuration()) >= 0) {
            throw new IllegalArgumentException(
                    "renewalInterval must be shorter than the conservative lease duration");
        }
    }

    public Duration leaseGrantDuration() {
        return maxLeaseDuration.minus(maxClockDriftAllowance).minus(leaseSafetyMargin);
    }

    public Duration quarantineDuration() {
        return maxLeaseDuration.plus(maxClockDriftAllowance)
                .plus(maxSchedulingPause).plus(networkSafetyMargin);
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String field) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
