package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * 关闭旧路由纪元执行权的领域事实。
 *
 * <p>它记录一次集群操作要隔离的旧 {@link RoutingEpoch}、旧 epoch 的 committed ACTIVE Broker 冻结集合，
 * 以及生成 fence 时的 Leader 任期和多数派读取位置。提交该事实关闭权威侧旧 epoch grant；
 * 目标 Broker 完成本机 fence 或等待完整 quarantine 后，才允许提交新拓扑。</p>
 *
 * <p>该对象用于 BrokerCluster 拓扑变化、Broker 摘流和故障恢复场景，在新路由纪元生效前形成
 * 明确的旧纪元安全边界；冻结的目标 Broker 列表必须非空、唯一且按 Broker 身份稳定排序。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@EqualsAndHashCode
@ToString
public final class RoutingEpochFence implements Serializable {
    /* 触发 fence 的集群操作。 */
    private ClusterOperationId operationId;
    /* 本次关闭 grant 并要求隔离路由副作用准入的旧 epoch。 */
    private RoutingEpoch epoch;
    /* 生成 fence 时的权威 Leader 任期。 */
    private LeaderTerm leaderTerm;
    /* 证明 fence 已读到的多数派日志位置。 */
    private QuorumReadIndex quorumReadIndex;
    /* 本次隔离冻结的目标 Broker 集合；不表示已收到 ACK 或已完成隔离。 */
    private List<BrokerId> fencedBrokerIds;

    private RoutingEpochFence() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private RoutingEpochFence(ClusterOperationId operationId,
                              RoutingEpoch epoch, LeaderTerm leaderTerm,
                              QuorumReadIndex quorumReadIndex,
                              List<BrokerId> fencedBrokerIds) {
        this.operationId = operationId;
        this.epoch = epoch;
        this.leaderTerm = leaderTerm;
        this.quorumReadIndex = quorumReadIndex;
        this.fencedBrokerIds = fencedBrokerIds;
        validate();
    }

    public ClusterOperationId operationId() { return operationId; }
    public RoutingEpoch epoch() { return epoch; }
    public LeaderTerm leaderTerm() { return leaderTerm; }
    public QuorumReadIndex quorumReadIndex() { return quorumReadIndex; }
    public List<BrokerId> fencedBrokerIds() { return fencedBrokerIds; }

    /** 判断是否为同一操作、同一旧 epoch 已登记的隔离事实。 */
    public boolean belongsTo(ClusterOperationId operationId, RoutingEpoch epoch) {
        return this.operationId.equals(operationId) && this.epoch.equals(epoch);
    }

    /**
     * 使用当前领导权证据恢复隔离事实：旧任期拒绝，同任期复用，新任期生成独立事实。
     * 保留原操作、epoch 和冻结集合；新事实是否可以提交仍由集群聚合最终判断。
     */
    public RoutingEpochFence resume(QuorumRead quorumRead) {
        if (quorumRead.leaderTerm().compareTo(leaderTerm) < 0) {
            throw new IllegalStateException("fence leader term regressed");
        }
        if (quorumRead.leaderTerm().equals(leaderTerm)) {
            return this;
        }
        return builder()
                .operationId(operationId)
                .epoch(epoch)
                .leaderTerm(quorumRead.leaderTerm())
                .quorumReadIndex(quorumRead.readIndex())
                .fencedBrokerIds(fencedBrokerIds)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull("routing epoch fence is invalid",
                operationId, epoch, fencedBrokerIds);
        AssertUtils.domainPropNotEmpty("routing epoch fence holders are required",
                fencedBrokerIds);
        AssertUtils.allDomainPropNotNull("routing epoch fence quorum proof is invalid",
                leaderTerm, quorumReadIndex);
        AssertUtils.domainPropTrue("routing epoch fence holders must be unique",
                fencedBrokerIds.stream().distinct().count() == fencedBrokerIds.size());
        fencedBrokerIds = fencedBrokerIds.stream()
                .sorted(java.util.Comparator.comparing(BrokerId::value)).toList();
    }

    public static final class Builder {
        private final RoutingEpochFence fence = new RoutingEpochFence();

        public Builder operationId(ClusterOperationId value) {
            fence.operationId = value;
            return this;
        }
        public Builder epoch(RoutingEpoch value) { fence.epoch = value; return this; }
        public Builder leaderTerm(LeaderTerm value) { fence.leaderTerm = value; return this; }
        public Builder quorumReadIndex(QuorumReadIndex value) {
            fence.quorumReadIndex = value;
            return this;
        }
        public Builder fencedBrokerIds(List<BrokerId> value) {
            fence.fencedBrokerIds = value;
            return this;
        }

        public RoutingEpochFence build() {
            fence.validate();
            return fence;
        }
    }
}
