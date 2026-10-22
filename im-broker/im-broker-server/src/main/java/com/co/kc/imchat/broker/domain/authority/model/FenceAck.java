package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 路由纪元隔离确认领域结果。
 *
 * <p>表示 Broker 已针对指定操作完成旧路由纪元的 fence，并回传本次隔离请求携带的
 * Leader 任期、fence 开始前本机当前租约的序列和操作标识。该结果用于权威侧判断旧纪元是否已经停止继续
 * 提供路由写入能力。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class FenceAck {
    /* 已完成 fence 的 Broker。 */
    private BrokerId brokerId;
    /* 被确认隔离的路由纪元。 */
    private RoutingEpoch routingEpoch;
    /* 本次隔离请求携带并由 ACK 回传的 Leader 任期。 */
    private LeaderTerm leaderTerm;
    /* 本次 fence 开始前本机当前租约的序列；无当前租约时为零。 */
    private LeaseSequence fencedLeaseSequence;
    /* 触发本次 fence 的集群操作。 */
    private ClusterOperationId operationId;

    private FenceAck() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private FenceAck(BrokerId brokerId, RoutingEpoch routingEpoch,
                     LeaderTerm leaderTerm, LeaseSequence fencedLeaseSequence,
                     ClusterOperationId operationId) {
        this.brokerId = brokerId;
        this.routingEpoch = routingEpoch;
        this.leaderTerm = leaderTerm;
        this.fencedLeaseSequence = fencedLeaseSequence;
        this.operationId = operationId;
        validate();
    }

    public BrokerId brokerId() {
        return brokerId;
    }

    public RoutingEpoch routingEpoch() {
        return routingEpoch;
    }

    public LeaderTerm leaderTerm() {
        return leaderTerm;
    }

    public LeaseSequence fencedLeaseSequence() {
        return fencedLeaseSequence;
    }

    public ClusterOperationId operationId() {
        return operationId;
    }

    /**
     * 核对 ACK 的 epoch、任期和操作标识是否匹配本次隔离事实。
     * 响应节点身份由调用方对照请求目标校验。
     */
    public boolean confirmsFence(RoutingEpochFence fence) {
        return fence.epoch().equals(routingEpoch) && fence.leaderTerm().equals(leaderTerm) && fence.operationId().equals(operationId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FenceAck that)) {
            return false;
        }
        return Objects.equals(brokerId, that.brokerId)
                && Objects.equals(routingEpoch, that.routingEpoch)
                && Objects.equals(leaderTerm, that.leaderTerm)
                && Objects.equals(fencedLeaseSequence, that.fencedLeaseSequence)
                && Objects.equals(operationId, that.operationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(brokerId, routingEpoch, leaderTerm, fencedLeaseSequence,
                operationId);
    }

    @Override
    public String toString() {
        return "FenceAck[brokerId=" + brokerId
                + ", routingEpoch=" + routingEpoch + ", leaderTerm=" + leaderTerm
                + ", fencedLeaseSequence=" + fencedLeaseSequence + ", operationId=" + operationId
                + "]";
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull("fence acknowledgement is incomplete",
                brokerId, routingEpoch, leaderTerm, fencedLeaseSequence, operationId);
    }

    public static final class Builder {
        private final FenceAck fenceAck = new FenceAck();

        public Builder brokerId(BrokerId value) {
            fenceAck.brokerId = value;
            return this;
        }

        public Builder routingEpoch(RoutingEpoch value) {
            fenceAck.routingEpoch = value;
            return this;
        }

        public Builder leaderTerm(LeaderTerm value) {
            fenceAck.leaderTerm = value;
            return this;
        }

        public Builder fencedLeaseSequence(LeaseSequence value) {
            fenceAck.fencedLeaseSequence = value;
            return this;
        }

        public Builder operationId(ClusterOperationId value) {
            fenceAck.operationId = value;
            return this;
        }

        public FenceAck build() {
            fenceAck.validate();
            return fenceAck;
        }
    }
}
